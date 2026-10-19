package com.fleetpilot.vehicle.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.FleetPilotException;
import com.fleetpilot.vehicle.application.AssignmentService;
import com.fleetpilot.vehicle.application.ContractService;
import com.fleetpilot.vehicle.application.DeadlineService;
import com.fleetpilot.vehicle.application.DriverService;
import com.fleetpilot.vehicle.application.FleetSweepService;
import com.fleetpilot.vehicle.application.ReferenceDataService;
import com.fleetpilot.vehicle.application.VehicleService;
import com.fleetpilot.vehicle.application.command.DriverCommands.Assign;
import com.fleetpilot.vehicle.application.command.DriverCommands.CreateDriver;
import com.fleetpilot.vehicle.application.command.DriverCommands.EndAssignment;
import com.fleetpilot.vehicle.application.command.FleetCommands.CompleteDeadline;
import com.fleetpilot.vehicle.application.command.FleetCommands.CreateContract;
import com.fleetpilot.vehicle.application.command.VehicleCommands.ChangeStatus;
import com.fleetpilot.vehicle.application.command.VehicleCommands.CreateVehicle;
import com.fleetpilot.vehicle.application.command.VehicleCommands.RecordMileage;
import com.fleetpilot.vehicle.application.view.Views.AssignmentView;
import com.fleetpilot.vehicle.application.view.Views.ContractView;
import com.fleetpilot.vehicle.application.view.Views.DeadlineView;
import com.fleetpilot.vehicle.application.view.Views.VehicleDetail;
import com.fleetpilot.vehicle.domain.model.Acquisition;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.LicenseCategory;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;
import io.cucumber.java.fr.Alors;
import io.cucumber.java.fr.Et;
import io.cucumber.java.fr.Mais;
import io.cucumber.java.fr.Quand;
import io.cucumber.java.fr.Soit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Step definitions of the fleet scenarios. They drive the application services, the same entry points as the REST
 * API, and read the results as a fleet manager would see them.
 */
public class FleetSteps {

    private static final DateTimeFormatter FRENCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Map<String, VehicleCategory> CATEGORIES = Map.of("VL", VehicleCategory.CAR, "VU",
            VehicleCategory.VAN, "PL", VehicleCategory.TRUCK, "Engin", VehicleCategory.MACHINE);
    private static final Map<String, VehicleStatus> STATUSES = Map.of("en service", VehicleStatus.ACTIVE, "au garage",
            VehicleStatus.IN_REPAIR, "immobilisé", VehicleStatus.IMMOBILIZED);
    private static final Map<String, DeadlineType> DEADLINES = Map.of("contrôle technique",
            DeadlineType.TECHNICAL_INSPECTION, "révision", DeadlineType.SERVICE, "remplacement des pneus",
            DeadlineType.TYRES, "chronotachygraphe", DeadlineType.TACHOGRAPH);

    @Autowired
    private ReferenceDataService referenceData;
    @Autowired
    private VehicleService vehicles;
    @Autowired
    private DriverService drivers;
    @Autowired
    private AssignmentService assignments;
    @Autowired
    private ContractService contracts;
    @Autowired
    private DeadlineService deadlines;
    @Autowired
    private FleetSweepService sweep;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private Clock clock;

    private UUID companyId;
    private final Map<String, UUID> vehicleIds = new HashMap<>();
    private final Map<String, UUID> driverIds = new HashMap<>();
    private AssignmentView lastAssignment;
    private FleetPilotException lastError;
    private String lastVehicle;
    private UUID lastContract;

    // ---------------------------------------------------------------- context

    @Soit("l'entreprise cliente {string}")
    public void l_entreprise_cliente(String name) {
        companyId = referenceData.companies().stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow().id();
    }

    @Soit("nous sommes le {string}")
    public void nous_sommes_le(String date) {
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.parse(date, FRENCH_DATE));
    }

    @Soit("un véhicule {string} de catégorie {string} au statut {string} avec {int} km au compteur")
    public void un_vehicule(String registration, String category, String status, int mileage) {
        UUID id = createVehicle(registration, CATEGORIES.get(category), Energy.DIESEL, LocalDate.of(2024, 5, 2), mileage,
                Acquisition.LEASE);
        VehicleStatus target = STATUSES.get(status);
        if (target != VehicleStatus.ACTIVE) {
            vehicles.changeStatus(id, new ChangeStatus(target));
        }
    }

    @Soit("un conducteur {string} titulaire du permis {string}")
    public void un_conducteur(String fullName, String licences) {
        String[] names = fullName.split(" ", 2);
        int n = SEQUENCE.incrementAndGet();
        Set<LicenseCategory> categories = Arrays.stream(licences.split(",")).map(String::trim)
                .map(LicenseCategory::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(LicenseCategory.class)));
        UUID id = drivers.create(new CreateDriver(companyId, null, "BDD-%05d".formatted(n), names[0], names[1],
                "bdd.%d.%s@neolia-energies.fr".formatted(n, UUID.randomUUID()), "06 00 00 00 00", "1203%08d".formatted(n),
                categories, LocalDate.of(2012, 3, 1), LocalDate.of(2033, 1, 19))).driver().id();
        driverIds.put(fullName, id);
    }

    @Soit("le véhicule {string} est affecté à {string}")
    public void le_vehicule_est_affecte(String registration, String driver) {
        lastAssignment = assignments.assign(vehicleIds.get(registration), new Assign(driverIds.get(driver), null, null));
    }

    @Soit("une voiture {string} électrique immatriculée pour la première fois le {string} avec {int} km")
    public void une_voiture_electrique(String registration, String date, int mileage) {
        createVehicle(registration, VehicleCategory.CAR, Energy.ELECTRIC, LocalDate.parse(date, FRENCH_DATE), mileage,
                Acquisition.LEASE);
    }

    @Soit("un contrat LLD chez {string} de {int} mois et {int} km démarré le {string}")
    public void un_contrat(String lessor, int months, int kilometres, String start) {
        ContractView contract = contracts.create(vehicleIds.get(lastVehicle), new CreateContract(lessor,
                "BDD-" + UUID.randomUUID().toString().substring(0, 8), LocalDate.parse(start, FRENCH_DATE), months,
                kilometres, new BigDecimal("459.00"), true, false, false));
        lastContract = contract.id();
    }

    // ---------------------------------------------------------------- actions

    @Quand("j'affecte le véhicule {string} à {string}")
    public void j_affecte(String registration, String driver) {
        try {
            lastAssignment = assignments.assign(vehicleIds.get(registration), new Assign(driverIds.get(driver), null, null));
            lastError = null;
        } catch (FleetPilotException e) {
            lastError = e;
        }
    }

    @Quand("{string} restitue le véhicule avec {int} km au compteur")
    public void restitue(String driver, int mileage) {
        assertThat(lastAssignment.driver().fullName()).isEqualTo(driver);
        lastAssignment = assignments.end(lastAssignment.id(), new EndAssignment(null, mileage));
    }

    @Quand("j'ajoute une voiture {string} à essence immatriculée pour la première fois le {string} avec {int} km")
    public void j_ajoute_une_voiture(String registration, String date, int mileage) {
        createVehicle(registration, VehicleCategory.CAR, Energy.PETROL, LocalDate.parse(date, FRENCH_DATE), mileage,
                Acquisition.OWNED);
    }

    @Quand("j'ajoute un camion {string} immatriculé pour la première fois le {string} avec {int} km")
    public void j_ajoute_un_camion(String registration, String date, int mileage) {
        createVehicle(registration, VehicleCategory.TRUCK, Energy.DIESEL, LocalDate.parse(date, FRENCH_DATE), mileage,
                Acquisition.OWNED);
    }

    @Quand("la {string} est réalisée aujourd'hui à {int} km")
    public void echeance_realisee(String deadline, int mileage) {
        deadlines.complete(openDeadline(deadline).id(), new CompleteDeadline(null, mileage));
    }

    @Quand("le conducteur déclare {int} km au compteur")
    public void declare_kilometrage(int mileage) {
        vehicles.recordMileage(vehicleIds.get(lastVehicle), new RecordMileage(mileage, null, null));
    }

    // ---------------------------------------------------------------- outcomes

    @Alors("l'affectation est acceptée")
    public void acceptee() {
        assertThat(lastError).as("error").isNull();
        assertThat(lastAssignment.current()).isTrue();
    }

    @Alors("l'affectation est refusée avec le motif {string}")
    public void refusee(String code) {
        assertThat(lastError).as("the assignment should have been refused").isNotNull();
        assertThat(lastError.getCode()).isEqualTo(code);
    }

    @Alors("le conducteur actuel du véhicule {string} est {string}")
    public void conducteur_actuel(String registration, String driver) {
        assertThat(vehicle(registration).currentAssignment().driver().fullName()).isEqualTo(driver);
    }

    @Alors("le kilométrage de prise en charge est de {int} km")
    public void kilometrage_prise_en_charge(int mileage) {
        assertThat(lastAssignment.startMileage()).isEqualTo(mileage);
    }

    @Alors("le véhicule {string} n'a plus de conducteur")
    public void plus_de_conducteur(String registration) {
        assertThat(vehicle(registration).currentAssignment()).isNull();
    }

    @Alors("l'affectation totalise {int} km parcourus")
    public void km_parcourus(int kilometres) {
        assertThat(lastAssignment.drivenKilometres()).isEqualTo(kilometres);
    }

    @Alors("le kilométrage du véhicule {string} est de {int} km")
    public void kilometrage_du_vehicule(String registration, int mileage) {
        assertThat(vehicle(registration).mileage()).isEqualTo(mileage);
    }

    @Alors("son/sa {string} est prévu(e) le {string}")
    public void echeance_prevue_le(String deadline, String date) {
        assertThat(openDeadline(deadline).dueDate()).isEqualTo(LocalDate.parse(date, FRENCH_DATE));
    }

    @Alors("son/sa {string} est prévu(e) à {int} km")
    public void echeance_prevue_a(String deadline, int mileage) {
        assertThat(openDeadline(deadline).dueMileage()).isEqualTo(mileage);
    }

    @Alors("son/sa {string} est prévu(e) le {string} ou à {int} km")
    public void echeance_prevue_le_ou_a(String deadline, String date, int mileage) {
        DeadlineView due = openDeadline(deadline);
        assertThat(due.dueDate()).isEqualTo(LocalDate.parse(date, FRENCH_DATE));
        assertThat(due.dueMileage()).isEqualTo(mileage);
    }

    @Alors("le contrat est en risque de dépassement de plus de {int} km")
    public void risque_de_depassement(int kilometres) {
        ContractView contract = contracts.get(lastContract);
        assertThat(contract.projection().overrunRisk()).isTrue();
        assertThat(contract.projection().projectedOverrunKm()).isGreaterThan(kilometres);
    }

    @Et("le balayage quotidien signale le dépassement du contrat")
    public void le_balayage_signale() {
        sweep.run();
        assertThat(overrunEvents()).isEqualTo(1);
    }

    @Mais("un second balayage ne le signale pas à nouveau")
    public void second_balayage() {
        sweep.run();
        assertThat(overrunEvents()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private UUID createVehicle(String registration, VehicleCategory category, Energy energy, LocalDate firstRegistration,
            int mileage, Acquisition acquisition) {
        boolean zero = energy.isZeroEmission();
        int n = SEQUENCE.incrementAndGet();
        VehicleDetail created = vehicles.create(new CreateVehicle(companyId, null, null, registration,
                "VF1BD%012d".formatted(n + System.nanoTime() % 1_000_000_000L), category, "Renault", "Modèle de test",
                null, energy, zero ? 0 : energy == Energy.DIESEL ? 2 : 1, zero ? 0 : 140, 5, firstRegistration,
                acquisition, mileage, null));
        vehicleIds.put(registration, created.id());
        lastVehicle = registration;
        return created.id();
    }

    private VehicleDetail vehicle(String registration) {
        return vehicles.get(vehicleIds.get(registration));
    }

    private DeadlineView openDeadline(String label) {
        DeadlineType type = DEADLINES.get(label);
        List<DeadlineView> open = vehicle(lastVehicle).openDeadlines();
        return open.stream().filter(d -> d.type() == type).findFirst()
                .orElseThrow(() -> new AssertionError("No open deadline " + label + " in " + open));
    }

    private long overrunEvents() {
        return jdbc.sql("select count(*) from outbox_event where event_type = 'contract.mileage-overrun' "
                        + "and aggregate_id = :vehicle")
                .param("vehicle", vehicleIds.get(lastVehicle))
                .query(Long.class).single();
    }
}
