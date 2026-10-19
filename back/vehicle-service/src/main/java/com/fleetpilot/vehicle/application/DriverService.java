package com.fleetpilot.vehicle.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.vehicle.application.command.DriverCommands.CreateDriver;
import com.fleetpilot.vehicle.application.command.DriverCommands.UpdateDriver;
import com.fleetpilot.vehicle.application.view.Views.AssignmentView;
import com.fleetpilot.vehicle.application.view.Views.DriverDetail;
import com.fleetpilot.vehicle.application.view.Views.DriverSummary;
import com.fleetpilot.vehicle.application.view.Views.IncidentView;
import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.Site;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.repository.AssignmentRepository;
import com.fleetpilot.vehicle.domain.repository.CompanyRepository;
import com.fleetpilot.vehicle.domain.repository.DriverRepository;
import com.fleetpilot.vehicle.domain.repository.FleetSpecifications;
import com.fleetpilot.vehicle.domain.repository.IncidentRepository;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.DriverCriteria;
import com.fleetpilot.vehicle.domain.repository.SiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the driver register. */
@Service
@Transactional
public class DriverService {

    static final String RESOURCE = "Driver";

    private final DriverRepository drivers;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final AssignmentRepository assignments;
    private final IncidentRepository incidents;
    private final TenantScope tenant;
    private final AuditLog audit;

    public DriverService(DriverRepository drivers, CompanyRepository companies, SiteRepository sites,
            AssignmentRepository assignments, IncidentRepository incidents, TenantScope tenant, AuditLog audit) {
        this.audit = audit;
        this.drivers = drivers;
        this.companies = companies;
        this.sites = sites;
        this.assignments = assignments;
        this.incidents = incidents;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public Page<DriverSummary> search(DriverCriteria criteria, Pageable pageable) {
        DriverCriteria scoped = criteria.withCompany(tenant.companyFilter(criteria.companyId()));
        Page<Driver> page = drivers.findAll(FleetSpecifications.drivers(scoped), pageable);
        List<UUID> ids = page.getContent().stream().map(Driver::getId).toList();
        Map<UUID, Vehicle> currentVehicles = ids.isEmpty() ? Map.of()
                : assignments.findByDriverIdInAndEndDateIsNull(ids).stream()
                        .collect(Collectors.toMap(a -> a.getDriver().getId(), Assignment::getVehicle, (a, b) -> a));
        return page.map(d -> DriverSummary.of(d, currentVehicles.get(d.getId())));
    }

    @Transactional(readOnly = true)
    public DriverDetail get(UUID id) {
        return toDetail(load(id));
    }

    public DriverDetail create(CreateDriver cmd) {
        tenant.checkAccess(cmd.companyId(), "Company", cmd.companyId());
        Company company = companies.findById(cmd.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", cmd.companyId()));
        String email = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (drivers.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_USED", "A driver with the e-mail " + email + " already exists");
        }
        if (drivers.existsByCompany_IdAndEmployeeNumber(company.getId(), cmd.employeeNumber().trim())) {
            throw new ConflictException("EMPLOYEE_NUMBER_ALREADY_USED",
                    "Employee number " + cmd.employeeNumber() + " is already used in this company");
        }
        Driver driver = new Driver(UUID.randomUUID(), company,
                new Driver.Profile(cmd.employeeNumber().trim(), cmd.firstName().trim(), cmd.lastName().trim(), email,
                        cmd.phone()),
                new Driver.License(cmd.licenseNumber().trim(), cmd.licenseCategories(), cmd.licenseIssuedOn(),
                        cmd.licenseExpiresOn()));
        driver.moveTo(findSite(cmd.siteId()));
        drivers.save(driver);
        audit.record("DRIVER_CREATED", "DRIVER", driver.getId(), driver.getFullName(), company.getId(),
                driver.getEmployeeNumber());
        return toDetail(driver);
    }

    public DriverDetail update(UUID id, UpdateDriver cmd) {
        Driver driver = load(id);
        if (!Objects.equals(cmd.version(), driver.getVersion())) {
            throw new ConflictException("STALE_VERSION",
                    "The driver was modified by someone else in the meantime. Reload and try again.");
        }
        String email = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (drivers.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflictException("EMAIL_ALREADY_USED", "A driver with the e-mail " + email + " already exists");
        }
        String employeeNumber = cmd.employeeNumber().trim();
        if (!employeeNumber.equals(driver.getEmployeeNumber())
                && drivers.existsByCompany_IdAndEmployeeNumber(driver.getCompanyId(), employeeNumber)) {
            throw new ConflictException("EMPLOYEE_NUMBER_ALREADY_USED",
                    "Employee number " + employeeNumber + " is already used in this company");
        }
        driver.updateProfile(new Driver.Profile(employeeNumber, cmd.firstName().trim(), cmd.lastName().trim(), email,
                cmd.phone()));
        driver.updateLicense(new Driver.License(cmd.licenseNumber().trim(), cmd.licenseCategories(),
                cmd.licenseIssuedOn(), cmd.licenseExpiresOn()));
        driver.moveTo(findSite(cmd.siteId()));
        if (!cmd.active() && driver.isActive()) {
            if (!assignments.findByDriverIdInAndEndDateIsNull(List.of(id)).isEmpty()) {
                throw new BusinessRuleException("DRIVER_HAS_VEHICLE",
                        "The driver still has a vehicle: end the assignment before deactivating them");
            }
            driver.deactivate();
        } else if (cmd.active()) {
            driver.reactivate();
        }
        drivers.saveAndFlush(driver);
        audit.record("DRIVER_UPDATED", "DRIVER", id, driver.getFullName(), driver.getCompanyId(),
                driver.isActive() ? null : "inactive");
        return toDetail(driver);
    }

    Driver load(UUID id) {
        Driver driver = drivers.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        tenant.checkAccess(driver.getCompanyId(), RESOURCE, id);
        return driver;
    }

    private DriverDetail toDetail(Driver driver) {
        List<Assignment> history = assignments.findByDriverIdOrderByStartDateDesc(driver.getId());
        Vehicle current = history.stream().filter(Assignment::isCurrent).findFirst().map(Assignment::getVehicle)
                .orElse(null);
        List<AssignmentView> assignmentViews = history.stream().map(AssignmentView::of).toList();
        int driven = assignmentViews.stream().mapToInt(AssignmentView::drivenKilometres).sum();
        List<IncidentView> incidentViews = incidents.findByDriverIdOrderByOccurredAtDesc(driver.getId()).stream()
                .map(IncidentView::of).toList();
        return new DriverDetail(DriverSummary.of(driver, current), driver.getLicenseNumber(), driver.getLicenseIssuedOn(),
                assignmentViews, incidentViews, driven, driver.getVersion() == null ? 0 : driver.getVersion());
    }

    private Site findSite(UUID siteId) {
        return siteId == null ? null
                : sites.findById(siteId).orElseThrow(() -> new ResourceNotFoundException("Site", siteId));
    }
}
