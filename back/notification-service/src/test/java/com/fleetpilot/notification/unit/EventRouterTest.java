package com.fleetpilot.notification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.event.EventEnvelope;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.messaging.IncomingEvent;
import com.fleetpilot.notification.application.EventRouter;
import com.fleetpilot.notification.application.MailComposer;
import com.fleetpilot.notification.application.Ports.FleetDirectory;
import com.fleetpilot.notification.application.Ports.Person;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Who is told about which business event")
class EventRouterTest {

    static final UUID VEHICLE_WITH_DRIVER = UUID.randomUUID();
    static final UUID DRIVER = UUID.randomUUID();
    static final String TEAM = "gestion-flotte@fleetpilot.dev";

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final FleetDirectory fleet = new FleetDirectory() {
        @Override
        public Optional<Person> currentDriver(UUID vehicleId) {
            return VEHICLE_WITH_DRIVER.equals(vehicleId) ? driver(DRIVER) : Optional.empty();
        }

        @Override
        public Optional<Person> driver(UUID driverId) {
            return DRIVER.equals(driverId) ? Optional.of(new Person("Camille Martin", "camille.martin@neolia-energies.fr"))
                    : Optional.empty();
        }
    };
    private final EventRouter router = new EventRouter(fleet, TEAM, "Équipe de gestion de flotte");

    private IncomingEvent event(String type, Map<String, Object> payload) {
        EventEnvelope<Object> envelope = EventEnvelope.of(type, Instant.parse("2026-10-14T08:00:00Z"), "test",
                UUID.randomUUID(), null, payload);
        return IncomingEvent.parse(mapper, mapper.writeValueAsString(envelope));
    }

    private static Map<String, Object> garage() {
        return Map.of("garageId", UUID.randomUUID().toString(), "name", "Garage du Prado",
                "email", "atelier@garage-du-prado.fr", "city", "Marseille");
    }

    @Test
    void an_accepted_quote_sends_the_purchase_order_to_the_garage() {
        String quoteId = UUID.randomUUID().toString();
        List<EmailRequest> requests = router.route(event(EventTypes.QUOTE_ACCEPTED, Map.of("quoteId", quoteId,
                "reference", "DEV-2026-000042", "garage", garage(), "acceptedAmount", 190.0, "autoApproved", false)));

        assertThat(requests).singleElement().satisfies(r -> {
            assertThat(r.template()).isEqualTo("quote-accepted");
            assertThat(r.to()).isEqualTo("atelier@garage-du-prado.fr");
            assertThat(r.attachments()).containsExactly(MailComposer.PURCHASE_ORDER);
            assertThat(r.relatedId()).isEqualTo(quoteId);
            assertThat(r.model()).containsEntry("reference", "DEV-2026-000042").containsEntry("autoApproved", false);
            assertThat(r.model().get("garage")).asInstanceOf(MAP).containsEntry("city", "Marseille");
        });
    }

    @Test
    void a_reminder_goes_to_the_driver_or_to_the_team_when_the_vehicle_has_none() {
        Map<String, Object> deadline = Map.of("vehicleId", VEHICLE_WITH_DRIVER.toString(), "registration", "GH-482-KM",
                "type", "TECHNICAL_INSPECTION", "level", "D7", "currentMileage", 82_000);
        assertThat(router.route(event(EventTypes.DEADLINE_DUE, deadline)))
                .extracting(EmailRequest::to).containsExactly("camille.martin@neolia-energies.fr");

        Map<String, Object> orphan = Map.of("vehicleId", UUID.randomUUID().toString(), "registration", "GH-483-KM",
                "type", "SERVICE", "level", "OVERDUE", "currentMileage", 12);
        assertThat(router.route(event(EventTypes.DEADLINE_DUE, orphan))).extracting(EmailRequest::to).containsExactly(TEAM);
    }

    @Test
    void a_claim_alerts_the_team_and_acknowledges_the_driver() {
        List<EmailRequest> requests = router.route(event(EventTypes.INCIDENT_DECLARED, Map.of("incidentId",
                UUID.randomUUID().toString(), "reference", "SIN-2026-000301", "driverId", DRIVER.toString(),
                "registration", "GH-482-KM", "type", "COLLISION", "injuries", true)));
        assertThat(requests).extracting(EmailRequest::template, EmailRequest::to).containsExactly(
                tuple("incident-declared", TEAM),
                tuple("incident-received", "camille.martin@neolia-energies.fr"));
    }

    @Test
    void only_a_disputed_invoice_alerts_the_team() {
        Map<String, Object> ok = Map.of("quoteId", UUID.randomUUID().toString(), "disputed", false, "garage", garage());
        Map<String, Object> disputed = Map.of("quoteId", UUID.randomUUID().toString(), "disputed", true,
                "garage", garage());
        assertThat(router.route(event(EventTypes.QUOTE_INVOICED, ok))).isEmpty();
        assertThat(router.route(event(EventTypes.QUOTE_INVOICED, disputed))).extracting(EmailRequest::template)
                .containsExactly("invoice-disputed");
    }

    @Test
    void the_same_event_always_gives_the_same_request_id() {
        IncomingEvent event = event(EventTypes.QUOTE_REJECTED, Map.of("quoteId", UUID.randomUUID().toString(),
                "garage", garage()));
        assertThat(router.route(event).getFirst().requestId()).isEqualTo(router.route(event).getFirst().requestId());
    }

    @Test
    void events_without_a_reader_send_nothing() {
        assertThat(router.route(event(EventTypes.VEHICLE_UPDATED, Map.of("vehicleId", UUID.randomUUID().toString()))))
                .isEmpty();
        assertThat(router.route(event(EventTypes.DRIVER_ASSIGNED, Map.of("driverId", UUID.randomUUID().toString()))))
                .isEmpty();
    }

    @Test
    void only_a_critical_alert_of_a_connected_vehicle_is_mailed_to_the_team() {
        Map<String, Object> fault = Map.of("alertId", UUID.randomUUID().toString(), "vehicleId", UUID.randomUUID().toString(),
                "registration", "GH-482-KM", "type", "ENGINE_FAULT", "severity", "CRITICAL",
                "message", "P0217 : Surchauffe moteur", "raisedAt", "2026-10-14T07:30:00Z");
        assertThat(router.route(event(EventTypes.ALERT_RAISED, fault))).singleElement().satisfies(r -> {
            assertThat(r.template()).isEqualTo("vehicle-alert");
            assertThat(r.to()).isEqualTo(TEAM);
        });

        Map<String, Object> speeding = Map.of("vehicleId", UUID.randomUUID().toString(), "registration", "GH-482-KM",
                "type", "SPEEDING", "severity", "WARNING", "message", "152 km/h pour une limite de 130 km/h");
        assertThat(router.route(event(EventTypes.ALERT_RAISED, speeding))).isEmpty();
    }
}
