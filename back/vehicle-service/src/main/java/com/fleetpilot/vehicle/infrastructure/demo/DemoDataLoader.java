package com.fleetpilot.vehicle.infrastructure.demo;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the demo data set into an empty database at startup, when {@code fleetpilot.demo-data.enabled} is true.
 * A database that already contains companies is left untouched, so restarting the service never duplicates data.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.demo-data.enabled", havingValue = "true")
public class DemoDataLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataLoader.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final DemoDriverRecords driverRecords;

    public DemoDataLoader(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
        this.driverRecords = new DemoDriverRecords(jdbc, transactions, clock);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() {
        Long companies = jdbc.queryForObject("select count(*) from company", Long.class);
        if (companies != null && companies > 0) {
            LOG.info("Demo data not loaded: the database already contains {} companies", companies);
        } else {
            loadFleet();
        }
        // Added after the fleet (and to a fleet installed before the driver app existed)
        driverRecords.loadIfEmpty();
        // The administration: company details and an activity journal rebuilt from the records above
        new DemoAdministration(jdbc).loadIfEmpty(clock.instant());
    }

    private void loadFleet() {
        long start = System.nanoTime();
        DemoDataset data = new DemoDataGenerator(DemoDataGenerator.DEFAULT_SEED, LocalDate.now(clock)).generate();
        transactions.executeWithoutResult(status -> insert(data));
        LOG.info("Demo data loaded: {} companies, {} vehicles, {} drivers, {} contracts, {} deadlines, {} claims "
                        + "({} rows in {} ms)", data.companies.size(), data.vehicles.size(), data.drivers.size(),
                data.contracts.size(), data.deadlines.size(), data.incidents.size(), data.totalRows(),
                (System.nanoTime() - start) / 1_000_000);
    }

    void insert(DemoDataset data) {
        batch("insert into company (id, name, legal_name, siren, segment, city) values (?, ?, ?, ?, ?, ?)",
                data.companies, r -> new Object[] {r.id(), r.name(), r.legalName(), r.siren(), r.segment(), r.city()});
        batch("insert into site (id, company_id, name, address, postal_code, city, latitude, longitude) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)", data.sites,
                r -> new Object[] {r.id(), r.companyId(), r.name(), r.address(), r.postalCode(), r.city(), r.latitude(),
                    r.longitude()});
        batch("insert into cost_center (id, company_id, code, label) values (?, ?, ?, ?)", data.costCenters,
                r -> new Object[] {r.id(), r.companyId(), r.code(), r.label()});
        batch("insert into driver (id, company_id, site_id, employee_number, first_name, last_name, email, phone, "
                        + "license_number, license_categories, license_issued_on, license_expires_on, active) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", data.drivers,
                r -> new Object[] {r.id(), r.companyId(), r.siteId(), r.employeeNumber(), r.firstName(), r.lastName(),
                    r.email(), r.phone(), r.licenseNumber(), r.licenseCategories(), date(r.licenseIssuedOn()),
                    date(r.licenseExpiresOn()), r.active()});
        batch("insert into vehicle (id, company_id, site_id, cost_center_id, registration, vin, category, make, model, "
                        + "trim_level, energy, crit_air, co2_g_km, fiscal_power, first_registration_date, acquisition, "
                        + "mileage, mileage_read_at, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.vehicles,
                r -> new Object[] {r.id(), r.companyId(), r.siteId(), r.costCenterId(), r.registration(), r.vin(),
                    r.category(), r.make(), r.model(), r.trimLevel(), r.energy(), r.critAir(), r.co2(), r.fiscalPower(),
                    date(r.firstRegistrationDate()), r.acquisition(), r.mileage(), Timestamp.from(r.mileageReadAt()),
                    r.status()});
        batch("insert into mileage_reading (id, vehicle_id, mileage, read_at, source) values (?, ?, ?, ?, ?)",
                data.mileageReadings,
                r -> new Object[] {r.id(), r.vehicleId(), r.mileage(), Timestamp.from(r.readAt()), r.source()});
        batch("insert into assignment (id, vehicle_id, driver_id, kind, start_date, end_date, start_mileage, end_mileage) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)", data.assignments,
                r -> new Object[] {r.id(), r.vehicleId(), r.driverId(), r.kind(), date(r.startDate()), date(r.endDate()),
                    r.startMileage(), r.endMileage()});
        batch("insert into lease_contract (id, vehicle_id, lessor, contract_number, start_date, duration_months, end_date, "
                        + "contractual_mileage, start_mileage, monthly_rent, maintenance_included, tyres_included, "
                        + "replacement_vehicle, status, ended_on) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.contracts,
                r -> new Object[] {r.id(), r.vehicleId(), r.lessor(), r.contractNumber(), date(r.startDate()),
                    r.durationMonths(), date(r.endDate()), r.contractualMileage(), r.startMileage(), r.monthlyRent(),
                    r.maintenanceIncluded(), r.tyresIncluded(), r.replacementVehicle(), r.status(), date(r.endedOn())});
        batch("insert into deadline (id, vehicle_id, type, due_date, due_mileage, status, completed_on, completed_mileage) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)", data.deadlines,
                r -> new Object[] {r.id(), r.vehicleId(), r.type(), date(r.dueDate()), r.dueMileage(), r.status(),
                    date(r.completedOn()), r.completedMileage()});
        batch("insert into incident (id, vehicle_id, driver_id, reference, type, occurred_at, location, latitude, "
                        + "longitude, description, third_party_involved, injuries, responsibility, estimated_cost, status, "
                        + "closed_on) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", data.incidents,
                r -> new Object[] {r.id(), r.vehicleId(), r.driverId(), r.reference(), r.type(),
                    Timestamp.from(r.occurredAt()), r.location(), r.latitude(), r.longitude(), r.description(),
                    r.thirdPartyInvolved(), r.injuries(), r.responsibility(), r.estimatedCost(), r.status(),
                    date(r.closedOn())});
        jdbc.queryForObject("select setval('incident_reference_seq', ?)", Long.class, data.incidents.size() + 1L);
    }

    private <T> void batch(String sql, List<T> rows, Function<T, Object[]> mapper) {
        jdbc.batchUpdate(sql, rows.stream().map(mapper).toList());
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }
}
