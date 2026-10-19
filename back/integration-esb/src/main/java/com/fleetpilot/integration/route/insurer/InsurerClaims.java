package com.fleetpilot.integration.route.insurer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.integration.fleet.BusinessServices.IncidentModule;
import com.fleetpilot.integration.fleet.BusinessServices.Refused;
import com.fleetpilot.integration.journal.ExchangeJournal;
import com.fleetpilot.integration.journal.FileIntake;
import com.fleetpilot.integration.journal.Flow;
import com.fleetpilot.integration.journal.Outcome;
import com.fleetpilot.integration.journal.Outcome.Issue;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bus between FleetPilot and the fleet insurer: a claim declared in FleetPilot ({@code incident.declared}) is
 * declared to the insurer, then followed. What the insurer decides (expert report, agreement, settlement, refusal) is
 * written back to the claim in FleetPilot, so that the fleet manager follows everything in one place.
 */
@Component("insurerClaims")
public class InsurerClaims {

    static final String INSURER = "Assurflotte";
    static final String CLAIM = "fleetpilotClaim";
    private static final Logger LOG = LoggerFactory.getLogger(InsurerClaims.class);

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final ExchangeJournal journal;
    private final IncidentModule incidents;
    private final Clock clock;

    public InsurerClaims(JdbcClient jdbc, JsonMapper json, ExchangeJournal journal, IncidentModule incidents,
            Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.journal = journal;
        this.incidents = incidents;
        this.clock = clock;
    }

    /** A claim being followed. */
    public record OpenClaim(UUID incidentId, String incidentReference, String claimNumber, String insurerStatus,
            String fleetStatus) {
    }

    public boolean isDeclaration(String event) {
        return "incident.declared".equals(json.readTree(event).path("type").asString(null));
    }

    /**
     * Journals the declaration and builds the request of the insurer; the body becomes null when the claim was already
     * declared (the event was delivered twice), which ends the route.
     */
    public void prepareDeclaration(Exchange exchange) {
        String event = exchange.getMessage().getBody(String.class);
        JsonNode envelope = json.readTree(event);
        JsonNode payload = envelope.path("payload");
        UUID incidentId = UUID.fromString(payload.path("incidentId").asString());
        boolean known = jdbc.sql("select exists (select 1 from insurer_claim where incident_id = :id)")
                .param("id", incidentId).query(Boolean.class).single();
        if (known) {
            exchange.getMessage().setBody(null);
            return;
        }
        String reference = payload.path("reference").asString();
        UUID exchangeId = journal.open(Flow.INSURER_CLAIMS, reference, event, null, null);
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("policyNumber", policy(envelope.path("companyId").asString("")));
        declaration.put("reference", reference);
        declaration.put("registration", payload.path("registration").asString());
        declaration.put("type", payload.path("type").asString());
        declaration.put("occurredAt", payload.path("occurredAt").asString());
        declaration.put("injuries", payload.path("injuries").asBoolean(false));
        exchange.setProperty(FileIntake.JOURNAL_ID, exchangeId);
        exchange.setProperty(CLAIM, Map.of("incidentId", incidentId, "reference", reference,
                "registration", payload.path("registration").asString(),
                "companyId", envelope.path("companyId").asString("")));
        exchange.getMessage().setBody(json.writeValueAsString(declaration));
    }

    /**
     * The insurer acknowledged the claim and gave its number. Its decisions are left to the follow-up, which applies
     * them to FleetPilot in order, even if the insurer was already further along.
     */
    @SuppressWarnings("unchecked")
    public void declared(Exchange exchange) {
        JsonNode answer = json.readTree(exchange.getMessage().getBody(String.class));
        Map<String, Object> claim = exchange.getProperty(CLAIM, Map.class);
        String number = answer.path("claimNumber").asString();
        String companyId = (String) claim.get("companyId");
        jdbc.sql("""
                insert into insurer_claim (incident_id, incident_reference, claim_number, company_id, registration,
                    insurer_status, fleet_status, declared_at, last_synced_at)
                values (:incident, :reference, :number, :company, :registration, 'RECEIVED', 'DECLARED', :at, :at)
                on conflict (incident_id) do nothing""")
                .param("incident", claim.get("incidentId")).param("reference", claim.get("reference"))
                .param("number", number).param("company", companyId.isBlank() ? null : UUID.fromString(companyId))
                .param("registration", claim.get("registration"))
                .param("at", Timestamp.from(clock.instant()))
                .update();
        journal.complete(exchange.getProperty(FileIntake.JOURNAL_ID, UUID.class), new Outcome(INSURER, 1, 1, List.of(),
                "Sinistre %s (%s) déclaré à l'assureur : dossier %s".formatted(claim.get("reference"),
                        claim.get("registration"), number), number));
    }

    public List<OpenClaim> openClaims() {
        return jdbc.sql("""
                select incident_id, incident_reference, claim_number, insurer_status, fleet_status from insurer_claim
                where fleet_status not in ('CLOSED', 'REJECTED') order by declared_at""")
                .query((rs, row) -> new OpenClaim(rs.getObject("incident_id", UUID.class),
                        rs.getString("incident_reference"), rs.getString("claim_number"),
                        rs.getString("insurer_status"), rs.getString("fleet_status")))
                .list();
    }

    /**
     * Applies what the insurer decided since the last poll to the claim in FleetPilot. The bus may poll late and find
     * the claim several steps further: the expert report is then applied anyway, and the claim walks through each
     * status in turn (declared, under review, in repair, closed), as FleetPilot only allows one step at a time. A
     * manager may also have moved the claim by hand: a transition FleetPilot refuses is skipped, the claim keeps its
     * status, and the journal says so.
     */
    public void synchronise(Exchange exchange) {
        OpenClaim claim = exchange.getProperty(CLAIM, OpenClaim.class);
        JsonNode answer = json.readTree(exchange.getMessage().getBody(String.class));
        String status = answer.path("status").asString();
        jdbc.sql("update insurer_claim set last_synced_at = :at where incident_id = :id")
                .param("at", Timestamp.from(clock.instant())).param("id", claim.incidentId()).update();
        if (status.equals(claim.insurerStatus())) {
            return;
        }
        String responsibility = answer.path("responsibility").asString(null);
        BigDecimal estimate = answer.path("estimatedCost").isNumber() ? answer.path("estimatedCost").decimalValue() : null;
        String fleetStatus = claim.fleetStatus();
        List<String> news = new ArrayList<>();
        try {
            if ("RECEIVED".equals(claim.insurerStatus()) && responsibility != null) {
                incidents.assess(claim.incidentId(), responsibility, estimate);
                news.add("expertise reçue, responsabilité %s, %s estimés".formatted(responsibility(responsibility),
                        money(estimate)));
            }
            switch (status) {
                case "EXPERTISE" -> fleetStatus = walk(claim, fleetStatus, "UNDER_REVIEW");
                case "AGREED" -> {
                    fleetStatus = walk(claim, fleetStatus, "IN_REPAIR");
                    news.add("accord de l'assureur, réparation lancée");
                }
                case "SETTLED" -> {
                    fleetStatus = walk(claim, fleetStatus, "CLOSED");
                    news.add("indemnisé %s, dossier clos".formatted(money(answer.path("settledAmount").decimalValue())));
                }
                case "REFUSED" -> {
                    fleetStatus = walk(claim, walk(claim, fleetStatus, "UNDER_REVIEW"), "REJECTED");
                    news.add("refusé par l'assureur : " + answer.path("refusalReason").asString(""));
                }
                default -> news.add("statut " + status);
            }
        } catch (Refused refused) {
            LOG.warn("Claim {} not updated in FleetPilot: {}", claim.incidentReference(), refused.getMessage());
            news.add("statut %s non reporté dans FleetPilot : %s".formatted(status, refused.getMessage()));
        }
        jdbc.sql("update insurer_claim set insurer_status = :status, fleet_status = :fleet where incident_id = :id")
                .param("status", status).param("fleet", fleetStatus).param("id", claim.incidentId()).update();
        UUID id = journal.open(Flow.INSURER_CLAIMS, claim.claimNumber(), answer.toString(), null, null);
        journal.complete(id, new Outcome(INSURER, 1, 1, List.of(), "Dossier %s (%s) : %s".formatted(
                claim.claimNumber(), claim.incidentReference(), String.join(" ; ", news)), claim.claimNumber()));
    }

    /** The insurer could not be reached or refused the declaration after the retries. */
    public void declarationFailed(Exchange exchange) {
        UUID id = exchange.getProperty(FileIntake.JOURNAL_ID, UUID.class);
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (id != null) {
            journal.complete(id, Outcome.rejected(INSURER, Issue.error(null, null, "INSURER_UNREACHABLE",
                    cause == null ? "Assureur injoignable" : cause.getMessage()), "Déclaration à l'assureur en échec"));
        }
        LOG.error("Claim declaration failed", cause);
    }

    /** The statuses of a claim in FleetPilot, in the order a claim goes through them. */
    private static final List<String> STEPS = List.of("DECLARED", "UNDER_REVIEW", "IN_REPAIR", "CLOSED");

    /**
     * Moves the claim in FleetPilot to the target status, one step at a time; a claim already there or further stays
     * where it is. REJECTED is reached from DECLARED or UNDER_REVIEW only.
     */
    private String walk(OpenClaim claim, String current, String target) {
        if ("REJECTED".equals(target)) {
            if ("DECLARED".equals(current) || "UNDER_REVIEW".equals(current)) {
                incidents.moveTo(claim.incidentId(), target);
                return target;
            }
            return current;
        }
        String status = current;
        int index = STEPS.indexOf(current);
        int goal = STEPS.indexOf(target);
        while (index >= 0 && index < goal) {
            status = STEPS.get(++index);
            incidents.moveTo(claim.incidentId(), status);
        }
        return status;
    }

    /** The fleet policy of a client company: one contract per company at the insurer. */
    static String policy(String companyId) {
        return companyId.isBlank() ? "FLT-000000"
                : "FLT-%06d".formatted(Math.abs(UUID.fromString(companyId).getMostSignificantBits() % 1_000_000));
    }

    private static String responsibility(String value) {
        return switch (value == null ? "" : value) {
            case "NONE" -> "non engagée";
            case "PARTIAL" -> "partagée";
            case "FULL" -> "entière";
            default -> "à déterminer";
        };
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "-" : NumberFormat.getCurrencyInstance(Locale.FRANCE).format(amount);
    }
}
