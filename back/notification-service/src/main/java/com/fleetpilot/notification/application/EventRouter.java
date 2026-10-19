package com.fleetpilot.notification.application;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.messaging.IncomingEvent;
import com.fleetpilot.notification.application.Ports.FleetDirectory;
import com.fleetpilot.notification.application.Ports.Person;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Decides who must be told about a business event, and with which e-mail.
 *
 * <ul>
 *   <li>quote accepted: the garage, with the purchase order in PDF; rejected or counter-offer: the garage;</li>
 *   <li>invoice disputed, claim declared, contract heading for a mileage overrun: the fleet management team;</li>
 *   <li>deadline reminder (D-30, D-7, overdue): the driver of the vehicle, or the team when it has no driver;</li>
 *   <li>vehicle handed over: the driver; claim declared: an acknowledgement to the driver too;</li>
 *   <li>critical alert of a connected vehicle (serious engine fault): the fleet management team, at once.</li>
 * </ul>
 *
 * The request id is derived from the event id and the recipient: a redelivered event never sends an e-mail twice.
 */
@Component
public class EventRouter {

    private final FleetDirectory fleet;
    private final Person team;

    public EventRouter(FleetDirectory fleet, @Value("${fleetpilot.notifications.team-address}") String teamAddress,
            @Value("${fleetpilot.notifications.team-name}") String teamName) {
        this.fleet = fleet;
        this.team = new Person(teamName, teamAddress);
    }

    public List<EmailRequest> route(IncomingEvent event) {
        return switch (event.type()) {
            case EventTypes.QUOTE_ACCEPTED -> garage(event, "quote-accepted", List.of(MailComposer.PURCHASE_ORDER));
            case EventTypes.QUOTE_REJECTED -> garage(event, "quote-rejected", List.of());
            case EventTypes.QUOTE_COUNTER_OFFER -> garage(event, "quote-counter-offer", List.of());
            case EventTypes.QUOTE_INVOICED -> event.payload().path("disputed").asBoolean(false)
                    ? List.of(request(event, team, "invoice-disputed", "quote", event.text("quoteId"), List.of()))
                    : List.of();
            case EventTypes.DEADLINE_DUE -> deadline(event);
            case EventTypes.DRIVER_ASSIGNED -> event.uuid("driverId").flatMap(fleet::driver)
                    .map(driver -> List.of(request(event, driver, "vehicle-assigned", "vehicle", event.text("vehicleId"),
                            List.of())))
                    .orElse(List.of());
            case EventTypes.INCIDENT_DECLARED -> incident(event);
            case EventTypes.CONTRACT_MILEAGE_OVERRUN -> List.of(request(event, team, "contract-overrun", "vehicle",
                    event.text("vehicleId"), List.of()));
            case EventTypes.ALERT_RAISED -> "CRITICAL".equals(event.text("severity"))
                    ? List.of(request(event, team, "vehicle-alert", "vehicle", event.text("vehicleId"), List.of()))
                    : List.of();
            default -> List.of();
        };
    }

    private List<EmailRequest> garage(IncomingEvent event, String template, List<String> attachments) {
        JsonNode garage = event.payload().path("garage");
        String email = garage.path("email").asString(null);
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return List.of(request(event, new Person(garage.path("name").asString(null), email), template, "quote",
                event.text("quoteId"), attachments));
    }

    private List<EmailRequest> deadline(IncomingEvent event) {
        Optional<Person> driver = event.uuid("vehicleId").flatMap(fleet::currentDriver);
        return List.of(request(event, driver.orElse(team), "deadline-reminder", "vehicle", event.text("vehicleId"),
                List.of()));
    }

    private List<EmailRequest> incident(IncomingEvent event) {
        List<EmailRequest> requests = new ArrayList<>();
        requests.add(request(event, team, "incident-declared", "incident", event.text("incidentId"), List.of()));
        event.uuid("driverId").flatMap(fleet::driver).ifPresent(driver -> requests.add(
                request(event, driver, "incident-received", "incident", event.text("incidentId"), List.of())));
        return requests;
    }

    private static EmailRequest request(IncomingEvent event, Person to, String template, String relatedType,
            String relatedId, List<String> attachments) {
        Map<String, Object> model = new HashMap<>();
        event.payload().properties().forEach(field -> model.put(field.getKey(), value(field.getValue())));
        model.put("occurredAt", event.occurredAt().toString());
        UUID requestId = UUID.nameUUIDFromBytes((event.eventId() + ":" + template + ":" + to.email())
                .getBytes(StandardCharsets.UTF_8));
        return new EmailRequest(requestId.toString(), template, to.email(), to.name(), event.companyId(), relatedType,
                relatedId, model, attachments);
    }

    /** Payload values as plain Java values (text, numbers, booleans, nested maps) for the templates. */
    private static Object value(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.properties().forEach(field -> map.put(field.getKey(), value(field.getValue())));
            return map;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        return node.asString();
    }
}
