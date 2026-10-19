package com.fleetpilot.vehicle.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Acquisition;
import com.fleetpilot.vehicle.domain.model.AlertLevel;
import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.CompanySegment;
import com.fleetpilot.vehicle.domain.model.CostCenter;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.LicenseCategory;
import com.fleetpilot.vehicle.domain.model.MileageReading;
import com.fleetpilot.vehicle.domain.model.MileageSource;
import com.fleetpilot.vehicle.domain.model.Site;
import com.fleetpilot.vehicle.domain.model.Urgency;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;

/**
 * Read models returned by the application services and exposed as-is by the REST API.
 *
 * <p>They are built inside the service transaction, so the API layer never touches lazy JPA associations.
 */
public final class Views {

    private Views() {
    }

    // ---------------------------------------------------------------- references

    public record CompanyRef(UUID id, String name) {
        public static CompanyRef of(Company company) {
            return new CompanyRef(company.getId(), company.getName());
        }
    }

    public record SiteRef(UUID id, String name, String city) {
        public static SiteRef of(Site site) {
            return site == null ? null : new SiteRef(site.getId(), site.getName(), site.getCity());
        }
    }

    public record CostCenterRef(UUID id, String code, String label) {
        public static CostCenterRef of(CostCenter costCenter) {
            return costCenter == null ? null
                    : new CostCenterRef(costCenter.getId(), costCenter.getCode(), costCenter.getLabel());
        }
    }

    public record DriverRef(UUID id, String fullName, String employeeNumber) {
        public static DriverRef of(Driver driver) {
            return driver == null ? null : new DriverRef(driver.getId(), driver.getFullName(), driver.getEmployeeNumber());
        }
    }

    public record VehicleRef(UUID id, String registration, String make, String model) {
        public static VehicleRef of(Vehicle vehicle) {
            return vehicle == null ? null
                    : new VehicleRef(vehicle.getId(), vehicle.getRegistration(), vehicle.getMake(), vehicle.getModel());
        }
    }

    // ---------------------------------------------------------------- companies

    public record CompanyView(UUID id, String name, String legalName, String siren, CompanySegment segment, String city,
            String address, String postalCode, String contactName, String contactEmail, String contactPhone,
            boolean active, long version) {
        public static CompanyView of(Company c) {
            return new CompanyView(c.getId(), c.getName(), c.getLegalName(), c.getSiren(), c.getSegment(), c.getCity(),
                    c.getAddress(), c.getPostalCode(), c.getContactName(), c.getContactEmail(), c.getContactPhone(),
                    c.isActive(), c.getVersion());
        }
    }

    /** A company in the administration: its details and the size of what it holds. */
    public record CompanyAdminView(CompanyView company, long vehicles, long activeVehicles, long drivers, long sites,
            long costCenters, long activeContracts) {
    }

    /** A site with what is based there; a site still holding vehicles or drivers cannot be deleted. */
    public record SiteAdminView(SiteView site, long vehicles, long drivers, long version) {
    }

    /** A cost center with the vehicles charged to it. */
    public record CostCenterAdminView(CostCenterView costCenter, long vehicles, long version) {
    }

    /** One line of the activity journal. */
    public record AuditView(UUID id, java.time.Instant occurredAt, String actor, String actorRole, UUID companyId,
            String action, String targetType, String targetId, String targetLabel, String details) {
        public static AuditView of(com.fleetpilot.vehicle.domain.model.AuditEntry a) {
            return new AuditView(a.getId(), a.getOccurredAt(), a.getActor(), a.getActorRole(), a.getCompanyId(),
                    a.getAction(), a.getTargetType(), a.getTargetId(), a.getTargetLabel(), a.getDetails());
        }
    }

    public record SiteView(UUID id, String name, String address, String postalCode, String city, BigDecimal latitude,
            BigDecimal longitude) {
        public static SiteView of(Site s) {
            return new SiteView(s.getId(), s.getName(), s.getAddress(), s.getPostalCode(), s.getCity(), s.getLatitude(),
                    s.getLongitude());
        }
    }

    public record CostCenterView(UUID id, String code, String label) {
        public static CostCenterView of(CostCenter c) {
            return new CostCenterView(c.getId(), c.getCode(), c.getLabel());
        }
    }

    // ---------------------------------------------------------------- vehicles

    public record VehicleSummary(UUID id, UUID companyId, String registration, VehicleCategory category, String make,
            String model, Energy energy, int critAir, int mileage, VehicleStatus status, SiteRef site,
            CostCenterRef costCenter, DriverRef currentDriver, int co2GramsPerKm, LocalDate firstRegistrationDate,
            Acquisition acquisition) {

        public static VehicleSummary of(Vehicle v, Driver currentDriver) {
            return new VehicleSummary(v.getId(), v.getCompanyId(), v.getRegistration(), v.getCategory(), v.getMake(),
                    v.getModel(), v.getEnergy(), v.getCritAir(), v.getMileage(), v.getStatus(), SiteRef.of(v.getSite()),
                    CostCenterRef.of(v.getCostCenter()), DriverRef.of(currentDriver), v.getCo2GramsPerKm(),
                    v.getFirstRegistrationDate(), v.getAcquisition());
        }
    }

    public record VehicleDetail(UUID id, CompanyRef company, String registration, String vin, VehicleCategory category,
            String make, String model, String trimLevel, Energy energy, int critAir, int co2GramsPerKm,
            Integer fiscalPower, LocalDate firstRegistrationDate, Acquisition acquisition, int mileage,
            Instant mileageReadAt, VehicleStatus status, Set<VehicleStatus> allowedTransitions, SiteRef site,
            CostCenterRef costCenter, AssignmentView currentAssignment, ContractView activeContract,
            List<DeadlineView> openDeadlines, List<MileageView> mileageHistory, long openIncidents, long version,
            Instant createdAt, Instant updatedAt) {
    }

    public record MileageView(int mileage, Instant readAt, MileageSource source) {
        public static MileageView of(MileageReading r) {
            return new MileageView(r.getMileage(), r.getReadAt(), r.getSource());
        }
    }

    // ---------------------------------------------------------------- drivers

    public record DriverSummary(UUID id, UUID companyId, String employeeNumber, String firstName, String lastName,
            String fullName, String email, String phone, SiteRef site, Set<LicenseCategory> licenseCategories,
            LocalDate licenseExpiresOn, boolean active, VehicleRef currentVehicle) {

        public static DriverSummary of(Driver d, Vehicle currentVehicle) {
            return new DriverSummary(d.getId(), d.getCompanyId(), d.getEmployeeNumber(), d.getFirstName(),
                    d.getLastName(), d.getFullName(), d.getEmail(), d.getPhone(), SiteRef.of(d.getSite()),
                    d.getLicenseCategories(), d.getLicenseExpiresOn(), d.isActive(), VehicleRef.of(currentVehicle));
        }
    }

    public record DriverDetail(DriverSummary driver, String licenseNumber, LocalDate licenseIssuedOn,
            List<AssignmentView> assignments, List<IncidentView> incidents, int drivenKilometres, long version) {
    }

    // ---------------------------------------------------------------- assignments

    public record AssignmentView(UUID id, VehicleRef vehicle, DriverRef driver, Assignment.Kind kind,
            LocalDate startDate, LocalDate endDate, int startMileage, Integer endMileage, int drivenKilometres,
            boolean current) {

        public static AssignmentView of(Assignment a) {
            return new AssignmentView(a.getId(), VehicleRef.of(a.getVehicle()), DriverRef.of(a.getDriver()), a.getKind(),
                    a.getStartDate(), a.getEndDate(), a.getStartMileage(), a.getEndMileage(),
                    a.drivenKilometres(a.getVehicle().getMileage()), a.isCurrent());
        }
    }

    // ---------------------------------------------------------------- contracts

    public record ContractView(UUID id, VehicleRef vehicle, String lessor, String contractNumber, LocalDate startDate,
            LocalDate endDate, int durationMonths, int contractualMileage, int startMileage, BigDecimal monthlyRent,
            BigDecimal totalRent, boolean maintenanceIncluded, boolean tyresIncluded, boolean replacementVehicle,
            LeaseContract.Status status, LocalDate endedOn, Long daysRemaining,
            LeaseContract.MileageProjection projection) {

        public static ContractView of(LeaseContract c, LocalDate today) {
            boolean active = c.getStatus() == LeaseContract.Status.ACTIVE;
            Vehicle vehicle = Objects.requireNonNull(c.getVehicle(), "a contract always has a vehicle");
            return new ContractView(c.getId(), VehicleRef.of(vehicle), c.getLessor(), c.getContractNumber(),
                    c.getStartDate(), c.getEndDate(), c.getDurationMonths(), c.getContractualMileage(),
                    c.getStartMileage(), c.getMonthlyRent(), c.getTotalRent(), c.isMaintenanceIncluded(),
                    c.isTyresIncluded(), c.isReplacementVehicle(), c.getStatus(), c.getEndedOn(),
                    active ? ChronoUnit.DAYS.between(today, c.getEndDate()) : null,
                    active ? c.projectMileage(vehicle.getMileage(), today) : null);
        }
    }

    // ---------------------------------------------------------------- deadlines

    public record DeadlineView(UUID id, VehicleRef vehicle, DeadlineType type, LocalDate dueDate, Integer dueMileage,
            Deadline.Status status, Urgency urgency, Long daysRemaining, Integer kilometresRemaining,
            LocalDate completedOn, Integer completedMileage, AlertLevel lastAlert) {

        public static DeadlineView of(Deadline d, LocalDate today) {
            int mileage = d.getVehicle().getMileage();
            boolean open = d.getStatus() == Deadline.Status.OPEN;
            return new DeadlineView(d.getId(), VehicleRef.of(d.getVehicle()), d.getType(), d.getDueDate(),
                    d.getDueMileage(), d.getStatus(), open ? d.urgency(today, mileage) : null,
                    open ? d.daysRemaining(today).orElse(null) : null,
                    open ? d.kilometresRemaining(mileage).orElse(null) : null,
                    d.getCompletedOn(), d.getCompletedMileage(), d.getLastAlert());
        }
    }

    // ---------------------------------------------------------------- incidents

    public record IncidentView(UUID id, String reference, VehicleRef vehicle, DriverRef driver, Incident.Type type,
            Instant occurredAt, String location, BigDecimal latitude, BigDecimal longitude, String description,
            boolean thirdPartyInvolved, boolean injuries, Incident.Responsibility responsibility,
            BigDecimal estimatedCost, Incident.Status status, LocalDate closedOn, Instant createdAt) {

        public static IncidentView of(Incident i) {
            return new IncidentView(i.getId(), i.getReference(), VehicleRef.of(i.getVehicle()), DriverRef.of(i.getDriver()),
                    i.getType(), i.getOccurredAt(), i.getLocation(), i.getLatitude(), i.getLongitude(),
                    i.getDescription(), i.isThirdPartyInvolved(), i.isInjuries(), i.getResponsibility(),
                    i.getEstimatedCost(), i.getStatus(), i.getClosedOn(), i.getCreatedAt());
        }
    }
}
