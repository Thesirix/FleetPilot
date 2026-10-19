package com.fleetpilot.vehicle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.vehicle.application.command.DriverCommands.Assign;
import com.fleetpilot.vehicle.application.view.Views.AssignmentView;
import com.fleetpilot.vehicle.domain.event.DomainEventPublisher;
import com.fleetpilot.vehicle.domain.event.FleetEvents;
import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.LicenseCategory;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;
import com.fleetpilot.vehicle.domain.repository.AssignmentRepository;
import com.fleetpilot.vehicle.unit.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Assigning a vehicle to a driver")
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignments;
    @Mock
    private VehicleService vehicleService;
    @Mock
    private DriverService driverService;
    @Mock
    private DomainEventPublisher events;

    private AssignmentService service;
    private final Company company = Fixtures.company("NÃ©olia Ã‰nergies");
    private Vehicle vehicle;
    private Driver driver;

    @BeforeEach
    void setUp() {
        TenantScope everyCompany = Optional::empty;
        Clock clock = Clock.fixed(Fixtures.NOW, ZoneId.of("Europe/Paris"));
        service = new AssignmentService(assignments, vehicleService, driverService, events, everyCompany, clock,
                mock(AuditLog.class));
        vehicle = Fixtures.vehicle(company);
        driver = Fixtures.driver(company, LicenseCategory.B);
    }

    private void given(Vehicle v, Driver d) {
        when(vehicleService.load(v.getId())).thenReturn(v);
        when(driverService.load(d.getId())).thenReturn(d);
    }

    @Test
    void hands_the_vehicle_over_and_publishes_an_event() {
        given(vehicle, driver);
        when(assignments.findByVehicleIdAndEndDateIsNull(vehicle.getId())).thenReturn(Optional.empty());
        when(assignments.findByDriverIdAndEndDateIsNullAndKind(driver.getId(), Assignment.Kind.PERMANENT))
                .thenReturn(Optional.empty());
        when(assignments.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentView view = service.assign(vehicle.getId(), new Assign(driver.getId(), null, null));

        assertThat(view.current()).isTrue();
        assertThat(view.kind()).isEqualTo(Assignment.Kind.PERMANENT);
        assertThat(view.startDate()).isEqualTo(Fixtures.TODAY);
        assertThat(view.startMileage()).isEqualTo(15_000);
        verify(events).publish(eq(EventTypes.DRIVER_ASSIGNED), eq(company.getId()), eq("vehicle"), eq(vehicle.getId()),
                any(FleetEvents.DriverAssigned.class));
    }

    @Test
    void refuses_a_vehicle_that_is_not_in_service() {
        vehicle.changeStatus(VehicleStatus.IN_REPAIR);
        given(vehicle, driver);

        assertThatThrownBy(() -> service.assign(vehicle.getId(), new Assign(driver.getId(), null, null)))
                .hasFieldOrPropertyWithValue("code", "VEHICLE_NOT_AVAILABLE");
        verifyNoInteractions(events);
    }

    @Test
    void refuses_a_driver_without_the_right_licence() {
        Vehicle truck = Fixtures.vehicle(company, Fixtures.of(VehicleCategory.TRUCK), 120_000);
        given(truck, driver);

        assertThatThrownBy(() -> service.assign(truck.getId(), new Assign(driver.getId(), null, null)))
                .hasFieldOrPropertyWithValue("code", "LICENSE_NOT_VALID")
                .hasMessageContaining("PL");
    }

    @Test
    void refuses_a_driver_of_another_company() {
        Driver outsider = Fixtures.driver(Fixtures.company("Transports Calanques Logistique"), LicenseCategory.B);
        given(vehicle, outsider);

        assertThatThrownBy(() -> service.assign(vehicle.getId(), new Assign(outsider.getId(), null, null)))
                .hasFieldOrPropertyWithValue("code", "DRIVER_OF_ANOTHER_COMPANY");
    }

    @Test
    void refuses_an_inactive_driver() {
        driver.deactivate();
        given(vehicle, driver);

        assertThatThrownBy(() -> service.assign(vehicle.getId(), new Assign(driver.getId(), null, null)))
                .hasFieldOrPropertyWithValue("code", "DRIVER_INACTIVE");
    }

    @Test
    void refuses_a_vehicle_that_already_has_a_driver() {
        given(vehicle, driver);
        Driver current = Fixtures.driver(company, LicenseCategory.B);
        when(assignments.findByVehicleIdAndEndDateIsNull(vehicle.getId())).thenReturn(Optional.of(
                new Assignment(UUID.randomUUID(), vehicle, current, Assignment.Kind.PERMANENT, Fixtures.TODAY.minusYears(1))));

        assertThatThrownBy(() -> service.assign(vehicle.getId(), new Assign(driver.getId(), null, null)))
                .hasFieldOrPropertyWithValue("code", "VEHICLE_ALREADY_ASSIGNED");
        verify(assignments, never()).save(any());
    }

    @Test
    void a_driver_has_only_one_permanent_vehicle_but_may_borrow_a_pool_vehicle() {
        given(vehicle, driver);
        Vehicle own = Fixtures.vehicle(company);
        when(assignments.findByVehicleIdAndEndDateIsNull(vehicle.getId())).thenReturn(Optional.empty());
        when(assignments.findByDriverIdAndEndDateIsNullAndKind(driver.getId(), Assignment.Kind.PERMANENT))
                .thenReturn(Optional.of(new Assignment(UUID.randomUUID(), own, driver, Assignment.Kind.PERMANENT,
                        Fixtures.TODAY.minusYears(1))));

        assertThatThrownBy(() -> service.assign(vehicle.getId(), new Assign(driver.getId(), Assignment.Kind.PERMANENT, null)))
                .hasFieldOrPropertyWithValue("code", "DRIVER_ALREADY_HAS_VEHICLE");

        when(assignments.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AssignmentView pool = service.assign(vehicle.getId(), new Assign(driver.getId(), Assignment.Kind.TEMPORARY, null));
        assertThat(pool.kind()).isEqualTo(Assignment.Kind.TEMPORARY);
    }

    @Test
    void an_assignment_cannot_be_planned_more_than_thirty_days_ahead() {
        given(vehicle, driver);

        assertThatThrownBy(() -> service.assign(vehicle.getId(),
                new Assign(driver.getId(), null, Fixtures.TODAY.plusDays(31))))
                .hasFieldOrPropertyWithValue("code", "ASSIGNMENT_TOO_FAR_AHEAD");
    }
}
