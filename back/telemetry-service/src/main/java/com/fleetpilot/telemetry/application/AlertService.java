package com.fleetpilot.telemetry.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.messaging.OutboxPublisher;
import com.fleetpilot.telemetry.application.TelemetryViews.AlertView;
import com.fleetpilot.telemetry.domain.Alert;
import com.fleetpilot.telemetry.domain.AlertRepository;
import com.fleetpilot.telemetry.domain.LiveVehicle.AlertCandidate;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The alert centre. A rule that fires on a vehicle already alerted for the same problem only counts a new occurrence;
 * a new alert is published on Kafka ({@code telemetry.alerts}), the notification service e-mails the critical ones.
 */
@Service
@Transactional
public class AlertService {

    /** Payload of the {@code alert.raised} event. */
    public record AlertRaised(UUID alertId, UUID vehicleId, String registration, Alert.Type type,
            Alert.Severity severity, String message, Double latitude, Double longitude, Instant raisedAt) {
    }

    private final AlertRepository alerts;
    private final OutboxPublisher outbox;
    private final TelemetryScope scope;
    private final Clock clock;

    public AlertService(AlertRepository alerts, OutboxPublisher outbox, TelemetryScope scope, Clock clock) {
        this.alerts = alerts;
        this.outbox = outbox;
        this.scope = scope;
        this.clock = clock;
    }

    /** Raises an alert, or counts a new occurrence of the alert already open for this problem. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void raise(AlertCandidate candidate, Alert.Context context, Instant at) {
        alerts.findUnresolved(context.vehicleId(), candidate.type()).ifPresentOrElse(
                open -> open.repeat(candidate.message(), candidate.value(), context, at),
                () -> {
                    Alert alert = alerts.save(new Alert(UUID.randomUUID(), context, candidate.type(),
                            candidate.severity(), candidate.message(), candidate.value(), at));
                    outbox.publish(EventTypes.TOPIC_ALERTS, EventTypes.ALERT_RAISED, context.companyId(), "vehicle",
                            context.vehicleId(), new AlertRaised(alert.getId(), context.vehicleId(),
                                    context.registration(), candidate.type(), candidate.severity(), candidate.message(),
                                    context.latitude(), context.longitude(), at));
                });
    }

    @Transactional(readOnly = true)
    public Page<AlertView> search(Alert.Status status, Alert.Type type, Alert.Severity severity, UUID companyId,
            UUID vehicleId, Pageable pageable) {
        UUID company = scope.companyFilter(companyId);
        Specification<Alert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (company != null) {
                predicates.add(cb.equal(root.get("companyId"), company));
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return alerts.findAll(spec, pageable).map(AlertView::of);
    }

    public AlertView acknowledge(UUID id) {
        Alert alert = load(id);
        alert.acknowledge(CurrentUser.username().orElse("FleetPilot"), clock.instant());
        return AlertView.of(alert);
    }

    public AlertView resolve(UUID id, String comment) {
        Alert alert = load(id);
        alert.resolve(CurrentUser.username().orElse("FleetPilot"), comment, clock.instant());
        return AlertView.of(alert);
    }

    private Alert load(UUID id) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert", id));
        scope.checkAccess(alert.getCompanyId(), "Alert", id);
        return alert;
    }
}
