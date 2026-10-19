package com.fleetpilot.vehicle.unit;

import static com.fleetpilot.vehicle.unit.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.AlertLevel;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Urgency;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Deadline urgency and reminders")
class DeadlineTest {

    private final Vehicle vehicle = Fixtures.vehicle(Fixtures.company("Néolia Énergies"));

    private Deadline dated(LocalDate due) {
        return new Deadline(UUID.randomUUID(), vehicle, DeadlineType.TECHNICAL_INSPECTION, due, null);
    }

    private Deadline byMileage(int dueMileage) {
        return new Deadline(UUID.randomUUID(), vehicle, DeadlineType.TYRES, null, dueMileage);
    }

    @ParameterizedTest(name = "due in {0} days -> {1}")
    @CsvSource({"-1, OVERDUE", "0, DUE_SOON", "30, DUE_SOON", "31, UPCOMING", "365, UPCOMING"})
    void urgency_by_date(int days, Urgency expected) {
        assertThat(dated(TODAY.plusDays(days)).urgency(TODAY, 10_000)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "due at {0} km with 40,000 km on the clock -> {1}")
    @CsvSource({"40000, OVERDUE", "39000, OVERDUE", "41500, DUE_SOON", "41501, UPCOMING"})
    void urgency_by_mileage(int dueMileage, Urgency expected) {
        assertThat(byMileage(dueMileage).urgency(TODAY, 40_000)).isEqualTo(expected);
    }

    @Test
    void whichever_limit_comes_first_applies() {
        Deadline service = new Deadline(UUID.randomUUID(), vehicle, DeadlineType.SERVICE, TODAY.plusMonths(8), 41_000);
        assertThat(service.urgency(TODAY, 40_000)).isEqualTo(Urgency.DUE_SOON);
        assertThat(service.urgency(TODAY, 41_000)).isEqualTo(Urgency.OVERDUE);
    }

    @Test
    void each_reminder_level_is_sent_once_from_the_earliest_to_the_most_severe() {
        Deadline deadline = dated(TODAY.plusDays(20));

        assertThat(deadline.alertToSend(TODAY, 0)).contains(AlertLevel.D30);
        deadline.markAlertSent(AlertLevel.D30);
        assertThat(deadline.alertToSend(TODAY, 0)).isEmpty();

        assertThat(deadline.alertToSend(TODAY.plusDays(14), 0)).contains(AlertLevel.D7);
        deadline.markAlertSent(AlertLevel.D7);

        assertThat(deadline.alertToSend(TODAY.plusDays(21), 0)).contains(AlertLevel.OVERDUE);
        deadline.markAlertSent(AlertLevel.OVERDUE);
        assertThat(deadline.alertToSend(TODAY.plusDays(40), 0)).isEmpty();
    }

    @Test
    void a_deadline_discovered_late_jumps_straight_to_the_overdue_reminder() {
        assertThat(dated(TODAY.minusDays(3)).alertToSend(TODAY, 0)).contains(AlertLevel.OVERDUE);
    }

    @Test
    void no_reminder_far_ahead_or_once_done() {
        assertThat(dated(TODAY.plusDays(90)).alertToSend(TODAY, 0)).isEmpty();
        Deadline done = dated(TODAY.minusDays(3));
        done.complete(TODAY, 15_000);
        assertThat(done.alertToSend(TODAY, 15_000)).isEmpty();
    }

    @Test
    void a_deadline_is_completed_only_once() {
        Deadline deadline = dated(TODAY);
        deadline.complete(TODAY, 15_000);

        assertThatThrownBy(() -> deadline.complete(TODAY, 15_000)).hasFieldOrPropertyWithValue("code", "DEADLINE_NOT_OPEN");
    }

    @Test
    void remaining_days_and_kilometres() {
        assertThat(dated(TODAY.plusDays(12)).daysRemaining(TODAY)).contains(12L);
        assertThat(byMileage(41_000).kilometresRemaining(40_250)).contains(750);
        assertThat(byMileage(41_000).daysRemaining(TODAY)).isEmpty();
    }
}
