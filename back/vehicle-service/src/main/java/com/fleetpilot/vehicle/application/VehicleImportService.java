package com.fleetpilot.vehicle.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.FleetPilotException;
import com.fleetpilot.vehicle.application.command.VehicleCommands.CreateVehicle;
import com.fleetpilot.vehicle.domain.model.Acquisition;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.Site;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.repository.SiteRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk import of vehicles from a CSV file, as exported by Excel in France (semicolon separated, UTF-8).
 *
 * <p>Each line is imported in its own transaction: valid lines are created even when others are rejected, and the
 * report tells exactly which lines failed and why, so the user can fix and re-import only those.
 *
 * <pre>
 * registration;vin;category;make;model;trim;energy;crit_air;co2;fiscal_power;first_registration;acquisition;mileage;site
 * GH-482-KM;VF1RJA00X68123456;VL;Renault;Megane E-Tech;Techno;Electrique;0;0;4;2024-03-15;LLD;18250;Marseille - Arenc
 * </pre>
 */
@Service
public class VehicleImportService {

    static final int MAX_LINES = 5_000;
    static final List<String> REQUIRED_COLUMNS = List.of("registration", "vin", "category", "make", "model", "energy",
            "crit_air", "co2", "first_registration", "acquisition", "mileage");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    private static final Map<String, Energy> ENERGY_ALIASES = Map.ofEntries(
            Map.entry("ESSENCE", Energy.PETROL), Map.entry("GAZOLE", Energy.DIESEL), Map.entry("ELECTRIQUE", Energy.ELECTRIC),
            Map.entry("HYBRIDE", Energy.HYBRID), Map.entry("HYBRIDE RECHARGEABLE", Energy.PLUG_IN_HYBRID),
            Map.entry("GPL", Energy.LPG), Map.entry("GNV", Energy.CNG), Map.entry("HYDROGENE", Energy.HYDROGEN));

    private static final Map<String, Acquisition> ACQUISITION_ALIASES = Map.of("LLD", Acquisition.LEASE,
            "LOCATION", Acquisition.LEASE, "ACHAT", Acquisition.OWNED, "PROPRIETE", Acquisition.OWNED);

    /** Result of an import. */
    public record ImportReport(int total, int created, List<RejectedLine> rejected) {
    }

    /** A line that could not be imported, with every reason. */
    public record RejectedLine(long line, String registration, List<String> errors) {
    }

    private final VehicleService vehicleService;
    private final SiteRepository sites;
    private final Validator validator;
    private final TransactionTemplate transactions;
    private final TenantScope tenant;

    public VehicleImportService(VehicleService vehicleService, SiteRepository sites, Validator validator,
            TransactionTemplate transactions, TenantScope tenant) {
        this.vehicleService = vehicleService;
        this.sites = sites;
        this.validator = validator;
        this.transactions = transactions;
        this.tenant = tenant;
    }

    public ImportReport importCsv(UUID companyId, InputStream content) {
        tenant.checkAccess(companyId, "Company", companyId);
        Map<String, UUID> sitesByName = sites.findByCompanyIdOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(s -> key(s.getName()), Site::getId, (a, b) -> a));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(';').setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true)
                .setIgnoreHeaderCase(true).get();
        try (Reader reader = new InputStreamReader(content, StandardCharsets.UTF_8);
                CSVParser parser = CSVParser.parse(reader, format)) {
            List<String> missing = REQUIRED_COLUMNS.stream()
                    .filter(column -> parser.getHeaderNames().stream().noneMatch(column::equalsIgnoreCase))
                    .toList();
            if (!missing.isEmpty()) {
                throw new BusinessRuleException("CSV_COLUMNS_MISSING", "Missing columns: " + String.join(", ", missing));
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.size() > MAX_LINES) {
                throw new BusinessRuleException("CSV_TOO_LARGE", "A file can contain at most " + MAX_LINES + " vehicles");
            }
            return importRecords(companyId, records, sitesByName);
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            throw new BusinessRuleException("CSV_UNREADABLE", "The file is not a valid CSV: " + e.getMessage());
        }
    }

    private ImportReport importRecords(UUID companyId, List<CSVRecord> records, Map<String, UUID> sitesByName) {
        int created = 0;
        List<RejectedLine> rejected = new ArrayList<>();
        for (CSVRecord row : records) {
            long line = row.getRecordNumber() + 1;
            String registration = value(row, "registration");
            List<String> errors = new ArrayList<>();
            CreateVehicle command = toCommand(companyId, row, sitesByName, errors);
            if (command != null) {
                validator.validate(command).stream()
                        .sorted(Comparator.comparing((ConstraintViolation<CreateVehicle> v) -> v.getPropertyPath().toString()))
                        .forEach(v -> errors.add(v.getPropertyPath() + ": " + v.getMessage()));
            }
            if (errors.isEmpty()) {
                try {
                    transactions.executeWithoutResult(status -> vehicleService.create(command));
                    created++;
                } catch (FleetPilotException e) {
                    errors.add(e.getMessage());
                }
            }
            if (!errors.isEmpty()) {
                rejected.add(new RejectedLine(line, registration, List.copyOf(errors)));
            }
        }
        return new ImportReport(records.size(), created, rejected);
    }

    private static CreateVehicle toCommand(UUID companyId, CSVRecord row, Map<String, UUID> sitesByName,
            List<String> errors) {
        VehicleCategory category = parse(row, "category", errors, VehicleImportService::category);
        Energy energy = parse(row, "energy", errors, VehicleImportService::energy);
        Acquisition acquisition = parse(row, "acquisition", errors, VehicleImportService::acquisition);
        Integer critAir = parse(row, "crit_air", errors, VehicleImportService::critAir);
        Integer co2 = parse(row, "co2", errors, Integer::valueOf);
        Integer mileage = parse(row, "mileage", errors, v -> Integer.valueOf(v.replace(" ", "")));
        Integer fiscalPower = optional(row, "fiscal_power").isEmpty() ? null
                : parse(row, "fiscal_power", errors, Integer::valueOf);
        LocalDate firstRegistration = parse(row, "first_registration", errors, VehicleImportService::date);
        if (!errors.isEmpty()) {
            return null;
        }
        UUID siteId = null;
        Optional<String> siteName = optional(row, "site");
        if (siteName.isPresent()) {
            siteId = sitesByName.get(key(siteName.get()));
            if (siteId == null) {
                errors.add("site: unknown site \"" + siteName.get() + "\"");
            }
        }
        return new CreateVehicle(companyId, siteId, null, value(row, "registration"), value(row, "vin"), category,
                value(row, "make"), value(row, "model"), optional(row, "trim").orElse(null), energy, critAir, co2,
                fiscalPower, firstRegistration, acquisition, mileage, null);
    }

    private static <T> T parse(CSVRecord row, String column, List<String> errors, Function<String, T> parser) {
        String raw = value(row, column);
        if (raw == null || raw.isBlank()) {
            errors.add(column + ": is required");
            return null;
        }
        try {
            return parser.apply(raw);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            errors.add(column + ": invalid value \"" + raw + "\"");
            return null;
        }
    }

    private static String value(CSVRecord row, String column) {
        return row.isMapped(column) && row.isSet(column) ? row.get(column) : null;
    }

    private static Optional<String> optional(CSVRecord row, String column) {
        return Optional.ofNullable(value(row, column)).filter(v -> !v.isBlank());
    }

    static VehicleCategory category(String raw) {
        String k = key(raw);
        return Arrays.stream(VehicleCategory.values())
                .filter(c -> c.name().equalsIgnoreCase(k) || key(c.frenchCode()).equals(k))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(raw));
    }

    static Energy energy(String raw) {
        String k = key(raw).replace('_', ' ');
        return Optional.ofNullable(ENERGY_ALIASES.get(k)).orElseGet(() -> Energy.valueOf(k.replace(' ', '_')));
    }

    static Acquisition acquisition(String raw) {
        String k = key(raw);
        return Optional.ofNullable(ACQUISITION_ALIASES.get(k)).orElseGet(() -> Acquisition.valueOf(k));
    }

    static Integer critAir(String raw) {
        return Set.of("E", "0").contains(key(raw)) ? 0 : Integer.valueOf(raw.trim());
    }

    static LocalDate date(String raw) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), format);
            } catch (DateTimeParseException ignored) {
                // try the next accepted format
            }
        }
        throw new IllegalArgumentException(raw);
    }

    /** Upper case without accents, so that "Électrique", "electrique" and "ELECTRIQUE" match. */
    static String key(String raw) {
        String noAccents = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toUpperCase(Locale.ROOT);
    }
}
