package com.fleetpilot.vehicle.unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Acquisition;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.CompanySegment;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.LicenseCategory;
import com.fleetpilot.vehicle.domain.model.Site;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;

/** Builders of domain objects for unit tests. */
public final class Fixtures {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    public static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    private Fixtures() {
    }

    public static Company company(String name) {
        return new Company(UUID.randomUUID(), name, name + " SA", "938417623", CompanySegment.LARGE_ACCOUNT, "Paris");
    }

    public static Site site(Company company) {
        return new Site(UUID.randomUUID(), company, "Marseille - Arenc", "40 boulevard de Dunkerque", "13002", "Marseille",
                new BigDecimal("43.3089"), new BigDecimal("5.3693"));
    }

    public static Vehicle.Specification car(Energy energy) {
        boolean zero = energy.isZeroEmission();
        return new Vehicle.Specification(VehicleCategory.CAR, "Renault", "Megane E-Tech", "EV60", energy, zero ? 0 : 1,
                zero ? 0 : 118, 4, LocalDate.of(2024, 3, 15));
    }

    public static Vehicle.Specification of(VehicleCategory category) {
        return new Vehicle.Specification(category, "Renault Trucks", "D Wide", null, Energy.DIESEL, 2, 780, null,
                LocalDate.of(2023, 6, 1));
    }

    public static Vehicle vehicle(Company company) {
        return vehicle(company, car(Energy.ELECTRIC), 15_000);
    }

    public static Vehicle vehicle(Company company, Vehicle.Specification spec, int mileage) {
        return new Vehicle(UUID.randomUUID(), company, "GH-482-KM", "VF1RJA00X68123456", spec, Acquisition.LEASE, mileage,
                NOW.minusSeconds(86_400), VehicleStatus.ACTIVE);
    }

    public static Driver driver(Company company, LicenseCategory... categories) {
        return driver(company, LocalDate.of(2041, 5, 2), categories);
    }

    public static Driver driver(Company company, LocalDate licenseExpiry, LicenseCategory... categories) {
        return new Driver(UUID.randomUUID(), company,
                new Driver.Profile("NEO-00042", "Camille", "Martin", "camille.martin@neolia-energies.fr", "06 12 34 56 78"),
                new Driver.License("260512345678", Set.of(categories), LocalDate.of(2026, 5, 2).minusYears(15),
                        licenseExpiry));
    }
}
