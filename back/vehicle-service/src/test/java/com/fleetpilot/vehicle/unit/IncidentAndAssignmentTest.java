package com.fleetpilot.vehicle.unit;

import static com.fleetpilot.vehicle.unit.Fixtures.NOW;
import static com.fleetpilot.vehicle.unit.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.LicenseCategory;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Claims and assignments")
class IncidentAndAssignmentTest {

    private final Company company = Fixtures.company("Néolia Énergies");
    private final Vehicle vehicle = Fixtures.vehicle(company);

    private Incident incident() {
        return new Incident(UUID.randomUUID(), "SIN-2026-000001", vehicle, null,
                new Incident.Declaration(Incident.Type.COLLISION, NOW.minusSeconds(3600), "A7, Marseille", null, null,
                        "Choc arrière", true, false), NOW);
    }

    @Test
    void a_claim_goes_from_declaration_to_closure() {
        Incident incident = incident();
        assertThat(incident.getStatus()).isEqualTo(Incident.Status.DECLARED);
        assertThat(incident.getResponsibility()).isEqualTo(Incident.Responsibility.UNKNOWN);

        incident.moveTo(Incident.Status.UNDER_REVIEW, TODAY);
        incident.assess(Incident.Responsibility.PARTIAL, new BigDecimal("1840.50"));
        incident.moveTo(Incident.Status.IN_REPAIR, TODAY);
        incident.moveTo(Incident.Status.CLOSED, TODAY);

        assertThat(incident.isOpen()).isFalse();
        assertThat(incident.getClosedOn()).isEqualTo(TODAY);
        assertThatThrownBy(() -> incident.assess(Incident.Responsibility.FULL, null))
                .hasFieldOrPropertyWithValue("code", "INCIDENT_CLOSED");
    }

    @Test
    void a_claim_cannot_skip_the_review() {
        assertThatThrownBy(() -> incident().moveTo(Incident.Status.CLOSED, TODAY))
                .hasFieldOrPropertyWithValue("code", "INVALID_STATUS_TRANSITION");
    }

    @Test
    void a_claim_cannot_be_declared_in_the_future() {
        assertThatThrownBy(() -> new Incident(UUID.randomUUID(), "SIN-2026-000002", vehicle, null,
                new Incident.Declaration(Incident.Type.THEFT, NOW.plusSeconds(60), "Lyon", null, null, "Vol", false, false),
                NOW))
                .hasFieldOrPropertyWithValue("code", "INCIDENT_IN_FUTURE");
    }

    @Test
    void an_assignment_records_the_kilometres_driven() {
        Assignment assignment = new Assignment(UUID.randomUUID(), vehicle, Fixtures.driver(company, LicenseCategory.B),
                Assignment.Kind.PERMANENT, TODAY.minusMonths(6));
        assertThat(assignment.getStartMileage()).isEqualTo(15_000);
        assertThat(assignment.drivenKilometres(21_000)).isEqualTo(6_000);

        assignment.end(TODAY, 22_500);

        assertThat(assignment.isCurrent()).isFalse();
        assertThat(assignment.drivenKilometres(30_000)).isEqualTo(7_500);
        assertThatThrownBy(() -> assignment.end(TODAY, 23_000))
                .hasFieldOrPropertyWithValue("code", "ASSIGNMENT_ALREADY_ENDED");
    }

    @Test
    void the_return_cannot_precede_the_handover() {
        Assignment assignment = new Assignment(UUID.randomUUID(), vehicle, Fixtures.driver(company, LicenseCategory.B),
                Assignment.Kind.TEMPORARY, TODAY);

        assertThatThrownBy(() -> assignment.end(TODAY.minusDays(1), 15_100))
                .hasFieldOrPropertyWithValue("code", "ASSIGNMENT_END_BEFORE_START");
        assertThatThrownBy(() -> assignment.end(TODAY, 14_000))
                .hasFieldOrPropertyWithValue("code", "MILEAGE_DECREASE");
    }
}
