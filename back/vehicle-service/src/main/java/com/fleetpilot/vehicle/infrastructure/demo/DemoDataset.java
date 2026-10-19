package com.fleetpilot.vehicle.infrastructure.demo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rows of the demo data set, one list per table, in insertion order. */
final class DemoDataset {

    record CompanyRow(UUID id, String name, String legalName, String siren, String segment, String city) {
    }

    record SiteRow(UUID id, UUID companyId, String name, String address, String postalCode, String city,
            BigDecimal latitude, BigDecimal longitude) {
    }

    record CostCenterRow(UUID id, UUID companyId, String code, String label) {
    }

    record DriverRow(UUID id, UUID companyId, UUID siteId, String employeeNumber, String firstName, String lastName,
            String email, String phone, String licenseNumber, String licenseCategories, LocalDate licenseIssuedOn,
            LocalDate licenseExpiresOn, boolean active) {
    }

    record VehicleRow(UUID id, UUID companyId, UUID siteId, UUID costCenterId, String registration, String vin,
            String category, String make, String model, String trimLevel, String energy, int critAir, int co2,
            Integer fiscalPower, LocalDate firstRegistrationDate, String acquisition, int mileage, Instant mileageReadAt,
            String status) {
    }

    record MileageRow(UUID id, UUID vehicleId, int mileage, Instant readAt, String source) {
    }

    record AssignmentRow(UUID id, UUID vehicleId, UUID driverId, String kind, LocalDate startDate, LocalDate endDate,
            int startMileage, Integer endMileage) {
    }

    record ContractRow(UUID id, UUID vehicleId, String lessor, String contractNumber, LocalDate startDate,
            int durationMonths, LocalDate endDate, int contractualMileage, int startMileage, BigDecimal monthlyRent,
            boolean maintenanceIncluded, boolean tyresIncluded, boolean replacementVehicle, String status,
            LocalDate endedOn) {
    }

    record DeadlineRow(UUID id, UUID vehicleId, String type, LocalDate dueDate, Integer dueMileage, String status,
            LocalDate completedOn, Integer completedMileage) {
    }

    record IncidentRow(UUID id, UUID vehicleId, UUID driverId, String reference, String type, Instant occurredAt,
            String location, BigDecimal latitude, BigDecimal longitude, String description, boolean thirdPartyInvolved,
            boolean injuries, String responsibility, BigDecimal estimatedCost, String status, LocalDate closedOn) {
    }

    final List<CompanyRow> companies = new ArrayList<>();
    final List<SiteRow> sites = new ArrayList<>();
    final List<CostCenterRow> costCenters = new ArrayList<>();
    final List<DriverRow> drivers = new ArrayList<>();
    final List<VehicleRow> vehicles = new ArrayList<>();
    final List<MileageRow> mileageReadings = new ArrayList<>();
    final List<AssignmentRow> assignments = new ArrayList<>();
    final List<ContractRow> contracts = new ArrayList<>();
    final List<DeadlineRow> deadlines = new ArrayList<>();
    final List<IncidentRow> incidents = new ArrayList<>();

    int totalRows() {
        return companies.size() + sites.size() + costCenters.size() + drivers.size() + vehicles.size()
                + mileageReadings.size() + assignments.size() + contracts.size() + deadlines.size() + incidents.size();
    }
}
