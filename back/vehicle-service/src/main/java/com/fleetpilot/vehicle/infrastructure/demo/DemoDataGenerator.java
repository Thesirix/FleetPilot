package com.fleetpilot.vehicle.infrastructure.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.CompanySegment;
import com.fleetpilot.vehicle.domain.model.DeadlinePolicy;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.infrastructure.demo.DemoCatalog.CompanySpec;
import com.fleetpilot.vehicle.infrastructure.demo.DemoCatalog.ModelSpec;
import com.fleetpilot.vehicle.infrastructure.demo.DemoCatalog.SiteSpec;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.AssignmentRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.CompanyRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.ContractRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.CostCenterRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.DeadlineRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.DriverRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.IncidentRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.MileageRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.SiteRow;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.VehicleRow;

/**
 * Generates a realistic fleet for three fictitious client companies.
 *
 * <p>The generation is deterministic: the same seed and the same reference date always give exactly the same data,
 * identifiers included, so every installation shows the same demo. Dates are relative to the reference date (the
 * install day) so that deadlines, contract ends and claims always look current.
 */
final class DemoDataGenerator {

    static final long DEFAULT_SEED = 20_261_014L;

    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final String PLATE_LETTERS = "ABCDEFGHJKLMNPQRSTVWXYZ";
    private static final LocalDate SIV_START = LocalDate.of(2009, 4, 15);
    private static final String[] LESSOR_CODES = {"AYV", "ARV", "LSY", "ALP", "F2M", "ATH"};
    private static final int[] LESSOR_WEIGHTS = {30, 25, 12, 13, 10, 10};

    private final Random random;
    private final LocalDate today;
    private final DemoDataset data = new DemoDataset();
    private final Set<String> registrations = new HashSet<>();
    private final Set<String> vins = new HashSet<>();
    private final Set<String> emails = new HashSet<>();
    private int contractSequence;

    /** Everything the generator needs to know about a vehicle after creating it. */
    private record Generated(VehicleRow row, ModelSpec model, double annualKm, boolean inFleet, boolean assignable) {
    }

    private record DriverInfo(DriverRow row, boolean heavyLicence, boolean featured) {
    }

    DemoDataGenerator(long seed, LocalDate today) {
        this.random = new Random(seed);
        this.today = today;
    }

    DemoDataset generate() {
        for (CompanySpec spec : DemoCatalog.COMPANIES) {
            generateCompany(spec);
        }
        assignIncidentReferences();
        return data;
    }

    // ---------------------------------------------------------------- company, sites, cost centers

    private void generateCompany(CompanySpec spec) {
        UUID companyId = uuid("company", spec.code());
        data.companies.add(new CompanyRow(companyId, spec.name(), spec.legalName(), spec.siren(), spec.segment().name(),
                spec.city()));
        List<UUID> siteIds = new ArrayList<>();
        for (int i = 0; i < spec.sites().size(); i++) {
            SiteSpec site = spec.sites().get(i);
            UUID id = uuid("site", spec.code() + i);
            siteIds.add(id);
            data.sites.add(new SiteRow(id, companyId, site.name(), site.address(), site.postalCode(), site.city(),
                    decimal(site.latitude(), 6), decimal(site.longitude(), 6)));
        }
        List<UUID> costCenterIds = new ArrayList<>();
        for (String[] costCenter : spec.costCenters()) {
            UUID id = uuid("cost-center", costCenter[0]);
            costCenterIds.add(id);
            data.costCenters.add(new CostCenterRow(id, companyId, costCenter[0], costCenter[1]));
        }

        List<DriverInfo> drivers = new ArrayList<>();
        for (int i = 0; i < spec.drivers(); i++) {
            drivers.add(generateDriver(spec, companyId, siteIds, i));
        }
        List<Generated> vehicles = new ArrayList<>();
        for (int i = 0; i < spec.vehicles(); i++) {
            Generated vehicle = generateVehicle(spec, companyId, siteIds, costCenterIds, i);
            vehicles.add(vehicle);
            generateContract(vehicle);
            generateDeadlines(vehicle);
            generateMileageHistory(vehicle);
        }
        Map<UUID, AssignmentRow> currentAssignments = generateAssignments(vehicles, drivers);
        baseFeaturedDrivers(spec, siteIds, drivers, currentAssignments);
        generateIncidents(spec, vehicles, currentAssignments);
    }

    /**
     * Moves the drivers who have a demo login, and the vehicles they drive, to the featured site (Marseille). Done
     * after every draw, so the rest of the data set stays the same.
     */
    private void baseFeaturedDrivers(CompanySpec spec, List<UUID> siteIds, List<DriverInfo> drivers,
            Map<UUID, AssignmentRow> currentAssignments) {
        int index = -1;
        for (int i = 0; i < spec.sites().size(); i++) {
            if (spec.sites().get(i).name().equals(DemoCatalog.FEATURED_SITE)) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        UUID site = siteIds.get(index);
        for (DriverInfo driver : drivers) {
            if (!driver.featured()) {
                continue;
            }
            DriverRow d = driver.row();
            data.drivers.replaceAll(row -> row.id().equals(d.id()) ? new DriverRow(d.id(), d.companyId(), site,
                    d.employeeNumber(), d.firstName(), d.lastName(), d.email(), d.phone(), d.licenseNumber(),
                    d.licenseCategories(), d.licenseIssuedOn(), d.licenseExpiresOn(), d.active()) : row);
            currentAssignments.values().stream()
                    .filter(assignment -> assignment.driverId().equals(d.id()))
                    .forEach(assignment -> data.vehicles.replaceAll(v -> v.id().equals(assignment.vehicleId())
                            ? new VehicleRow(v.id(), v.companyId(), site, v.costCenterId(), v.registration(), v.vin(),
                                    v.category(), v.make(), v.model(), v.trimLevel(), v.energy(), v.critAir(), v.co2(),
                                    v.fiscalPower(), v.firstRegistrationDate(), v.acquisition(), v.mileage(),
                                    v.mileageReadAt(), v.status())
                            : v));
        }
    }

    // ---------------------------------------------------------------- drivers

    private DriverInfo generateDriver(CompanySpec spec, UUID companyId, List<UUID> siteIds, int index) {
        String firstName = pick(DemoCatalog.FIRST_NAMES);
        String lastName = pick(DemoCatalog.LAST_NAMES);
        String[] featured = DemoCatalog.featuredDriver(spec.code(), index);
        if (featured != null) {
            firstName = featured[0];
            lastName = featured[1];
        }
        String email = uniqueEmail(firstName, lastName, spec.emailDomain());
        double heavyShare = switch (spec.segment()) {
            case SME -> 0.55;
            case PUBLIC_SECTOR -> 0.15;
            case LARGE_ACCOUNT -> 0.04;
        };
        boolean heavy = random.nextDouble() < heavyShare;
        String categories = heavy ? (random.nextBoolean() ? "B,C,CE" : "B,C") : "B";
        int age = 22 + random.nextInt(42);
        LocalDate issued = today.minusYears(age - 18L - random.nextInt(Math.max(1, Math.min(10, age - 19))))
                .minusDays(random.nextInt(365));
        LocalDate expires = issued.isAfter(LocalDate.of(2013, 1, 19)) ? issued.plusYears(15) : LocalDate.of(2033, 1, 19);
        if (random.nextDouble() < 0.012) {
            expires = today.minusDays(10 + random.nextInt(200));
            issued = expires.minusYears(15);
        }
        boolean active = random.nextDouble() > 0.03;
        if (featured != null) {
            heavy = false;
            categories = "B";
            issued = LocalDate.of(2014, 6, 12);
            expires = LocalDate.of(2029, 6, 12);
            active = true;
        }
        String phone = "0%d %02d %02d %02d %02d".formatted(6 + random.nextInt(2), random.nextInt(100), random.nextInt(100),
                random.nextInt(100), random.nextInt(100));
        String licenceNumber = "%02d%02d%05d%03d".formatted(issued.getYear() % 100, issued.getMonthValue(),
                random.nextInt(100_000), random.nextInt(1000));
        DriverRow row = new DriverRow(uuid("driver", spec.code() + index), companyId, siteIds.get(weightedSite(siteIds.size())),
                "%s-%05d".formatted(spec.code(), index + 1), firstName, lastName, email, phone, licenceNumber, categories,
                issued, expires, active);
        data.drivers.add(row);
        return new DriverInfo(row, heavy, featured != null);
    }

    private String uniqueEmail(String firstName, String lastName, String domain) {
        String base = slug(firstName) + "." + slug(lastName);
        String email = base + "@" + domain;
        for (int n = 2; !emails.add(email); n++) {
            email = base + n + "@" + domain;
        }
        return email;
    }

    // ---------------------------------------------------------------- vehicles

    private Generated generateVehicle(CompanySpec spec, UUID companyId, List<UUID> siteIds, List<UUID> costCenterIds,
            int index) {
        VehicleCategory category = VehicleCategory.values()[weighted(spec.categoryMix().stream().mapToInt(Integer::intValue).toArray())];
        ModelSpec model = pickModel(category, spec);
        boolean lease = random.nextDouble() < spec.leaseShare() && category != VehicleCategory.MACHINE;
        int maxAgeDays = lease ? 57 * 30 : spec.segment() == CompanySegment.PUBLIC_SECTOR ? 9 * 365 : 6 * 365;
        int ageDays = 15 + random.nextInt(maxAgeDays);
        // Local authorities keep old diesel vans for a long time: they are the ones the low-emission zones target.
        boolean oldDiesel = spec.segment() == CompanySegment.PUBLIC_SECTOR && !lease && category == VehicleCategory.VAN
                && model.energy() == Energy.DIESEL && random.nextDouble() < 0.3;
        if (oldDiesel) {
            ageDays = (int) ChronoUnit.DAYS.between(LocalDate.of(2008, 3, 1), today) - random.nextInt(900);
        }

        double roll = random.nextDouble();
        String status = roll < 0.025 ? "DISPOSED" : roll < 0.055 ? "IN_REPAIR" : roll < 0.07 ? "IMMOBILIZED"
                : roll < 0.08 ? "AWAITING_DELIVERY" : "ACTIVE";
        if ("AWAITING_DELIVERY".equals(status)) {
            ageDays = random.nextInt(12);
        }
        LocalDate firstRegistration = today.minusDays(ageDays);
        double factor = switch (category) {
            case CAR -> 1.0;
            case VAN -> 0.9;
            case TRUCK -> 1.5;
            case MACHINE -> 0.05;
        };
        double annualKm = spec.annualKm() * factor * (0.6 + random.nextDouble() * 0.7) * (oldDiesel ? 0.5 : 1);
        int mileage = "AWAITING_DELIVERY".equals(status) ? random.nextInt(40) : (int) (annualKm * ageDays / 365.0);

        int co2 = model.co2() == 0 ? 0 : model.co2() - 3 + random.nextInt(7);
        VehicleRow row = new VehicleRow(uuid("vehicle", spec.code() + index), companyId,
                siteIds.get(weightedSite(siteIds.size())), costCenterIds.get(random.nextInt(costCenterIds.size())),
                registration(firstRegistration), vin(model.wmi(), firstRegistration), category.name(), model.make(),
                model.model(), model.trim(), model.energy().name(), critAir(model.energy(), firstRegistration), co2,
                model.fiscalPower(), firstRegistration, lease ? "LEASE" : "OWNED", mileage,
                at(today.minusDays(random.nextInt(21)), 18), status);
        data.vehicles.add(row);
        boolean inFleet = !"DISPOSED".equals(status);
        boolean assignable = Set.of("ACTIVE", "IN_REPAIR", "IMMOBILIZED").contains(status);
        return new Generated(row, model, annualKm, inFleet, assignable);
    }

    private ModelSpec pickModel(VehicleCategory category, CompanySpec spec) {
        List<ModelSpec> candidates = DemoCatalog.MODELS.stream()
                .filter(m -> m.category() == category)
                .filter(m -> spec.segment() != CompanySegment.SME
                        || !"Goupil".equals(m.make()))
                .toList();
        int[] weights = candidates.stream().mapToInt(ModelSpec::weight).toArray();
        return candidates.get(weighted(weights));
    }

    /** French registrations are issued in sequence: the left letters give the approximate registration date. */
    private String registration(LocalDate firstRegistration) {
        long months = Math.max(0, ChronoUnit.MONTHS.between(SIV_START, firstRegistration));
        int combo = (int) Math.min(months * 82 / 100, PLATE_LETTERS.length() * PLATE_LETTERS.length() - 1L);
        String left = "" + PLATE_LETTERS.charAt(combo / PLATE_LETTERS.length()) + PLATE_LETTERS.charAt(combo % PLATE_LETTERS.length());
        String plate;
        do {
            String right = "" + PLATE_LETTERS.charAt(random.nextInt(PLATE_LETTERS.length()))
                    + PLATE_LETTERS.charAt(random.nextInt(PLATE_LETTERS.length()));
            plate = "%s-%03d-%s".formatted(left, 1 + random.nextInt(999), right);
        } while (!registrations.add(plate) || plate.contains("SS"));
        return plate;
    }

    private String vin(String wmi, LocalDate firstRegistration) {
        String vin;
        do {
            StringBuilder sb = new StringBuilder(wmi.length() == 3 ? wmi : "VF1");
            for (int i = 0; i < 5; i++) {
                sb.append(VIN_CHARS.charAt(random.nextInt(VIN_CHARS.length())));
            }
            sb.append(random.nextInt(10)).append(modelYearCode(firstRegistration.getYear()))
                    .append(VIN_CHARS.charAt(random.nextInt(24))).append("%06d".formatted(random.nextInt(1_000_000)));
            vin = sb.toString();
        } while (!vins.add(vin));
        return vin;
    }

    private static char modelYearCode(int year) {
        String codes = "ABCDEFGHJKLMNPRSTVWXY123456789";
        return codes.charAt(Math.floorMod(year - 2010, codes.length()));
    }

    /** Crit'Air sticker from the energy and the Euro standard implied by the first registration date. */
    private static int critAir(Energy energy, LocalDate firstRegistration) {
        if (energy.isZeroEmission()) {
            return 0;
        }
        if (energy != Energy.DIESEL) {
            return 1;
        }
        if (!firstRegistration.isBefore(LocalDate.of(2011, 1, 1))) {
            return 2;
        }
        return firstRegistration.isBefore(LocalDate.of(2006, 1, 1)) ? 4 : 3;
    }

    // ---------------------------------------------------------------- contracts

    private void generateContract(Generated vehicle) {
        VehicleRow v = vehicle.row();
        if (!"LEASE".equals(v.acquisition()) || "AWAITING_DELIVERY".equals(v.status())) {
            return;
        }
        long ageDays = ChronoUnit.DAYS.between(v.firstRegistrationDate(), today);
        int duration = ageDays + 60 < 36 * 30.4 ? 36 : ageDays + 60 < 48 * 30.4 ? 48 : 60;
        boolean underestimated = random.nextDouble() < 0.12;
        double plannedAnnual = vehicle.annualKm() * (underestimated ? 0.75 : 1.0 + random.nextDouble() * 0.25);
        int contractual = Math.max(20_000, (int) Math.round(plannedAnnual * duration / 12 / 5000.0) * 5000);
        double kmFactor = 0.9 + Math.min(0.3, contractual / (duration / 12.0) / 100_000.0);
        double durationFactor = 1 + (48 - duration) * 0.004;
        BigDecimal rent = decimal(vehicle.model().rent() * kmFactor * durationFactor * (0.95 + random.nextDouble() * 0.1), 2);
        int lessor = weighted(LESSOR_WEIGHTS);
        LocalDate start = v.firstRegistrationDate();
        boolean ended = "DISPOSED".equals(v.status());
        contractSequence++;
        data.contracts.add(new ContractRow(uuid("contract", v.id().toString()), v.id(), DemoCatalog.LESSORS.get(lessor),
                "%s-%d-%06d".formatted(LESSOR_CODES[lessor], start.getYear(), 104_000 + contractSequence * 7),
                start, duration, start.plusMonths(duration), contractual, 5 + random.nextInt(50), rent,
                random.nextDouble() < 0.85, random.nextDouble() < 0.6, random.nextDouble() < 0.4,
                ended ? "ENDED" : "ACTIVE", ended ? latest(start.plusDays(30), today.minusDays(10 + random.nextInt(200))) : null));
        if (!ended) {
            data.deadlines.add(new DeadlineRow(uuid("deadline", v.id() + "CONTRACT_END"), v.id(),
                    DeadlineType.CONTRACT_END.name(), start.plusMonths(duration), null, "OPEN", null, null));
        }
    }

    // ---------------------------------------------------------------- deadlines

    private void generateDeadlines(Generated vehicle) {
        VehicleRow v = vehicle.row();
        if (!vehicle.inFleet()) {
            return;
        }
        VehicleCategory category = VehicleCategory.valueOf(v.category());
        Energy energy = Energy.valueOf(v.energy());
        for (DeadlinePolicy.Due due : DeadlinePolicy.initialSchedule(category, energy, v.firstRegistrationDate(),
                v.mileage(), today)) {
            LocalDate dueDate = due.dueDate();
            Integer dueMileage = due.dueMileage();
            double roll = random.nextDouble();
            if (dueDate != null && roll < 0.04) {
                dueDate = today.minusDays(1 + random.nextInt(45));
            } else if (dueDate != null && roll < 0.11) {
                dueDate = today.plusDays(random.nextInt(31));
            } else if (dueMileage != null && roll < 0.14) {
                dueMileage = v.mileage() + 200 - random.nextInt(1600);
            }
            data.deadlines.add(new DeadlineRow(uuid("deadline", v.id() + due.type().name()), v.id(), due.type().name(),
                    dueDate, dueMileage, "OPEN", null, null));
            addCompletedOccurrence(v, vehicle.annualKm(), due.type(), due.dueDate(), category);
        }
    }

    /** The previous occurrence of an inspection or service, done a few days before it was due. */
    private void addCompletedOccurrence(VehicleRow v, double annualKm, DeadlineType type, LocalDate nextDue,
            VehicleCategory category) {
        if (nextDue == null || (type != DeadlineType.TECHNICAL_INSPECTION && type != DeadlineType.SERVICE)) {
            return;
        }
        Period period = type == DeadlineType.SERVICE || category == VehicleCategory.TRUCK ? Period.ofYears(1)
                : Period.ofYears(2);
        LocalDate previousDue = nextDue.minus(period);
        LocalDate done = previousDue.minusDays(random.nextInt(20));
        if (done.isBefore(v.firstRegistrationDate().plusMonths(6)) || done.isAfter(today)) {
            return;
        }
        int mileageThen = mileageOn(v, annualKm, done);
        data.deadlines.add(new DeadlineRow(uuid("deadline-done", v.id() + type.name()), v.id(), type.name(), previousDue,
                null, "DONE", done, mileageThen));
    }

    // ---------------------------------------------------------------- mileage history

    private void generateMileageHistory(Generated vehicle) {
        VehicleRow v = vehicle.row();
        data.mileageReadings.add(new MileageRow(uuid("mileage", v.id() + "now"), v.id(), v.mileage(), v.mileageReadAt(),
                "TELEMETRY"));
        String[] sources = {"TELEMETRY", "TELEMETRY", "DRIVER", "DRIVER", "GARAGE", "MANAGER"};
        for (int quarter = 1; quarter <= 8; quarter++) {
            LocalDate date = today.minusMonths(3L * quarter).minusDays(random.nextInt(10));
            if (!date.isAfter(v.firstRegistrationDate())) {
                break;
            }
            data.mileageReadings.add(new MileageRow(uuid("mileage", v.id() + "q" + quarter), v.id(),
                    mileageOn(v, vehicle.annualKm(), date), at(date, 9 + random.nextInt(9)),
                    sources[random.nextInt(sources.length)]));
        }
    }

    // ---------------------------------------------------------------- assignments

    private Map<UUID, AssignmentRow> generateAssignments(List<Generated> vehicles, List<DriverInfo> drivers) {
        List<DriverInfo> eligible = new ArrayList<>(drivers.stream()
                .filter(d -> d.row().active() && d.row().licenseExpiresOn().isAfter(today)).toList());
        Collections.shuffle(eligible, random);
        // About 7 % of the drivers have no vehicle of their own (new hires, occasional drivers using pool vehicles):
        // they are the ones a fleet manager can assign a vehicle to in the demo.
        eligible = new ArrayList<>(eligible.subList(eligible.size() * 7 / 100, eligible.size()));
        Deque<DriverInfo> heavy = new ArrayDeque<>(eligible.stream().filter(DriverInfo::heavyLicence).toList());
        Deque<DriverInfo> light = new ArrayDeque<>(eligible.stream().filter(d -> !d.heavyLicence() && !d.featured())
                .toList());
        // The drivers who have a login in the demo (Keycloak) always get a vehicle first.
        drivers.stream().filter(DriverInfo::featured).sorted(Comparator.comparing(d -> d.row().employeeNumber()))
                .toList().reversed().forEach(light::addFirst);
        Map<UUID, AssignmentRow> current = new HashMap<>();
        for (Generated vehicle : vehicles) {
            VehicleRow v = vehicle.row();
            VehicleCategory category = VehicleCategory.valueOf(v.category());
            if (!vehicle.assignable() || category == VehicleCategory.MACHINE || random.nextDouble() < 0.1) {
                continue;
            }
            DriverInfo driver = category == VehicleCategory.TRUCK ? heavy.pollFirst()
                    : !light.isEmpty() ? light.pollFirst() : heavy.pollLast();
            if (driver == null) {
                continue;
            }
            heavy.remove(driver);
            light.remove(driver);
            long ageDays = ChronoUnit.DAYS.between(v.firstRegistrationDate(), today);
            LocalDate start = v.firstRegistrationDate().plusDays(ageDays > 400 && random.nextDouble() < 0.4
                    ? 200 + random.nextInt((int) ageDays - 200) : random.nextInt(8));
            if (start.isAfter(today)) {
                start = today;
            }
            AssignmentRow assignment = new AssignmentRow(uuid("assignment", v.id().toString()), v.id(),
                    driver.row().id(), "PERMANENT", start, null, mileageOn(v, vehicle.annualKm(), start), null);
            data.assignments.add(assignment);
            current.put(v.id(), assignment);
            DriverRow previous = drivers.get(random.nextInt(drivers.size())).row();
            if (start.isAfter(v.firstRegistrationDate().plusDays(60)) && !previous.id().equals(driver.row().id())) {
                LocalDate previousStart = v.firstRegistrationDate().plusDays(random.nextInt(10));
                data.assignments.add(new AssignmentRow(uuid("assignment-previous", v.id().toString()), v.id(),
                        previous.id(), "PERMANENT", previousStart, start.minusDays(1),
                        mileageOn(v, vehicle.annualKm(), previousStart), mileageOn(v, vehicle.annualKm(), start)));
            }
        }
        return current;
    }

    // ---------------------------------------------------------------- incidents

    private void generateIncidents(CompanySpec spec, List<Generated> vehicles, Map<UUID, AssignmentRow> currentAssignments) {
        int count = spec.vehicles() * 22 / 100;
        Map<UUID, SiteRow> sites = new HashMap<>();
        data.sites.forEach(s -> sites.put(s.id(), s));
        for (int i = 0; i < count; i++) {
            Generated vehicle = vehicles.get(random.nextInt(vehicles.size()));
            VehicleRow v = vehicle.row();
            long ageDays = ChronoUnit.DAYS.between(v.firstRegistrationDate(), today);
            if (ageDays < 40 || VehicleCategory.MACHINE.name().equals(v.category())) {
                continue;
            }
            long daysAgo = 1 + random.nextInt((int) Math.min(730, ageDays - 30));
            AssignmentRow assignment = currentAssignments.get(v.id());
            UUID driverId = assignment != null && !assignment.startDate().isAfter(today.minusDays(daysAgo))
                    ? assignment.driverId() : null;
            data.incidents.add(incident(spec, v, driverId, sites.get(v.siteId()), daysAgo, i));
        }
    }

    private IncidentRow incident(CompanySpec spec, VehicleRow v, UUID driverId, SiteRow site, long daysAgo, int index) {
        String type = DemoIncidents.TYPES[weighted(DemoIncidents.TYPE_WEIGHTS)];
        Instant occurredAt = at(today.minusDays(daysAgo), 6 + random.nextInt(14)).plusSeconds(random.nextInt(3600));
        String status;
        double roll = random.nextDouble();
        if (daysAgo > 120) {
            status = roll < 0.86 ? "CLOSED" : "REJECTED";
        } else if (daysAgo > 30) {
            status = roll < 0.3 ? "UNDER_REVIEW" : roll < 0.6 ? "IN_REPAIR" : "CLOSED";
        } else {
            status = roll < 0.5 ? "DECLARED" : roll < 0.85 ? "UNDER_REVIEW" : "IN_REPAIR";
        }
        boolean assessed = !"DECLARED".equals(status);
        String responsibility = !assessed ? "UNKNOWN"
                : Set.of("THEFT", "VANDALISM", "WEATHER").contains(type) ? "NONE"
                : new String[] {"NONE", "PARTIAL", "FULL", "FULL"}[random.nextInt(4)];
        BigDecimal cost = assessed ? decimal(DemoIncidents.cost(type, random), 2) : null;
        boolean thirdParty = "COLLISION".equals(type) && random.nextDouble() < 0.65;
        boolean injuries = thirdParty && random.nextDouble() < 0.08;
        LocalDate closedOn = null;
        if ("CLOSED".equals(status) || "REJECTED".equals(status)) {
            LocalDate candidate = today.minusDays(daysAgo).plusDays(20 + random.nextInt(70));
            closedOn = candidate.isAfter(today) ? today : candidate;
        }
        String location = "%d %s, %s".formatted(1 + random.nextInt(180), pick(DemoCatalog.STREETS), site.city());
        return new IncidentRow(uuid("incident", spec.code() + index), v.id(), driverId, null, type,
                occurredAt, location, jitter(site.latitude()), jitter(site.longitude()),
                DemoIncidents.description(type, random), thirdParty, injuries, responsibility, cost, status, closedOn);
    }

    /** References are numbered in chronological order, per year: SIN-2026-000042. */
    private void assignIncidentReferences() {
        List<IncidentRow> sorted = new ArrayList<>(data.incidents);
        sorted.sort(Comparator.comparing(IncidentRow::occurredAt));
        data.incidents.clear();
        int sequence = 0;
        for (IncidentRow row : sorted) {
            sequence++;
            int year = row.occurredAt().atZone(ZoneOffset.UTC).getYear();
            data.incidents.add(new IncidentRow(row.id(), row.vehicleId(), row.driverId(), "SIN-%d-%06d".formatted(year, sequence),
                    row.type(), row.occurredAt(), row.location(), row.latitude(), row.longitude(), row.description(),
                    row.thirdPartyInvolved(), row.injuries(), row.responsibility(), row.estimatedCost(), row.status(),
                    row.closedOn()));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static LocalDate latest(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static int mileageOn(VehicleRow v, double annualKm, LocalDate date) {
        long days = Math.max(0, ChronoUnit.DAYS.between(v.firstRegistrationDate(), date));
        return Math.min(v.mileage(), (int) (annualKm * days / 365.0));
    }

    private int weighted(int[] weights) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /** The first site of a company (its headquarters or main depot) holds more vehicles than the others. */
    private int weightedSite(int count) {
        return random.nextDouble() < 0.3 ? 0 : random.nextInt(count);
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    private BigDecimal jitter(BigDecimal coordinate) {
        return coordinate.add(BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.12)).setScale(6, RoundingMode.HALF_UP);
    }

    private static Instant at(LocalDate date, int hour) {
        return date.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC);
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    static UUID uuid(String kind, String key) {
        return UUID.nameUUIDFromBytes(("fleetpilot-demo:" + kind + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static String slug(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");
    }
}
