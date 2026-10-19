package com.fleetpilot.integration.partner;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.Optional;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The claims API of the fleet insurer, as the bus sees it from outside (API key, its own vocabulary and claim
 * numbers). On a demonstration install there is no insurer to call, so the bus plays it under {@code /partners/insurer}:
 * a claim is received, assessed by an expert after a few minutes, agreed, then settled; about one claim in ten is
 * refused. Everything is derived from the claim and the time elapsed, so the story is the same after a restart.
 */
@Hidden
@RestController
@RequestMapping("/partners/insurer/claims")
public class SimulatedInsurer {

    static final String INSURER = "Assurflotte";
    private static final BigDecimal DEDUCTIBLE = new BigDecimal("300.00");

    private final JdbcClient jdbc;
    private final Clock clock;
    private final String apiKey;
    private final Duration expertiseAfter;
    private final Duration agreementAfter;
    private final Duration settlementAfter;

    public SimulatedInsurer(JdbcClient jdbc, Clock clock,
            @Value("${fleetpilot.integration.insurer.api-key}") String apiKey,
            @Value("${fleetpilot.integration.insurer.expertise-after}") long expertiseMinutes,
            @Value("${fleetpilot.integration.insurer.agreement-after}") long agreementMinutes,
            @Value("${fleetpilot.integration.insurer.settlement-after}") long settlementMinutes) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.apiKey = apiKey;
        this.expertiseAfter = Duration.ofMinutes(expertiseMinutes);
        this.agreementAfter = Duration.ofMinutes(agreementMinutes);
        this.settlementAfter = Duration.ofMinutes(settlementMinutes);
    }

    /** A claim as the insurer receives it. */
    public record Declaration(@NotBlank String policyNumber, @NotBlank String reference, @NotBlank String registration,
            @NotBlank String type, @NotNull Instant occurredAt, boolean injuries) {
    }

    /**
     * A claim as the insurer reports it.
     *
     * @param status RECEIVED, EXPERTISE (expert report: responsibility and estimate), AGREED (repair agreed),
     *               SETTLED (paid) or REFUSED
     */
    public record Claim(String claimNumber, String reference, String status, String responsibility,
            BigDecimal estimatedCost, BigDecimal settledAmount, String refusalReason) {
    }

    @PostMapping
    public ResponseEntity<?> declare(@RequestHeader(name = "X-Api-Key", required = false) String key,
            @Valid @RequestBody Declaration declaration) {
        if (!apiKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String existing = jdbc.sql("select claim_number from simulated_insurer_claim where incident_ref = :ref")
                .param("ref", declaration.reference()).query(String.class).optional().orElse(null);
        if (existing != null) {
            return ResponseEntity.ok(claim(existing).orElseThrow());
        }
        int sequence = jdbc.sql("select count(*) + 1 from simulated_insurer_claim").query(Integer.class).single();
        String number = "AF-%d-%06d".formatted(Year.now(clock).getValue(), 4_200 + sequence);
        jdbc.sql("""
                insert into simulated_insurer_claim (claim_number, policy_number, incident_ref, registration,
                    incident_type, occurred_at, injuries, declared_at)
                values (:number, :policy, :ref, :registration, :type, :occurred, :injuries, :declared)""")
                .param("number", number).param("policy", declaration.policyNumber())
                .param("ref", declaration.reference()).param("registration", declaration.registration())
                .param("type", declaration.type()).param("occurred", Timestamp.from(declaration.occurredAt()))
                .param("injuries", declaration.injuries()).param("declared", Timestamp.from(clock.instant()))
                .update();
        return ResponseEntity.status(HttpStatus.CREATED).body(claim(number).orElseThrow());
    }

    @GetMapping("/{number}")
    public ResponseEntity<Claim> get(@RequestHeader(name = "X-Api-Key", required = false) String key,
            @PathVariable String number) {
        if (!apiKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return claim(number).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    private Optional<Claim> claim(String number) {
        return jdbc.sql("""
                select claim_number, incident_ref, incident_type, injuries, declared_at
                from simulated_insurer_claim where claim_number = :number""")
                .param("number", number)
                .query((rs, row) -> state(rs.getString("claim_number"), rs.getString("incident_ref"),
                        rs.getString("incident_type"), rs.getBoolean("injuries"),
                        rs.getTimestamp("declared_at").toInstant()))
                .optional();
    }

    /** Where the claim stands, from its features and the time since it was declared. */
    public Claim state(String number, String reference, String type, boolean injuries, Instant declaredAt) {
        Duration age = Duration.between(declaredAt, clock.instant());
        long seed = UUID.nameUUIDFromBytes(reference.getBytes()).getLeastSignificantBits() & Long.MAX_VALUE;
        if (age.compareTo(expertiseAfter) < 0) {
            return new Claim(number, reference, "RECEIVED", null, null, null, null);
        }
        String responsibility = switch (type) {
            case "GLASS_BREAKAGE", "WEATHER", "THEFT", "VANDALISM", "FIRE" -> "NONE";
            default -> injuries || seed % 3 == 0 ? "FULL" : seed % 3 == 1 ? "PARTIAL" : "NONE";
        };
        BigDecimal estimate = estimate(type, seed);
        boolean refused = !"GLASS_BREAKAGE".equals(type) && seed % 10 == 7;
        if (age.compareTo(agreementAfter) < 0) {
            return new Claim(number, reference, "EXPERTISE", responsibility, estimate, null, null);
        }
        if (refused) {
            return new Claim(number, reference, "REFUSED", responsibility, estimate, null,
                    "THEFT".equals(type) ? "Garantie vol non souscrite pour ce véhicule"
                            : "Déclaration reçue hors du délai contractuel de cinq jours ouvrés");
        }
        if (age.compareTo(settlementAfter) < 0) {
            return new Claim(number, reference, "AGREED", responsibility, estimate, null, null);
        }
        BigDecimal settled = "NONE".equals(responsibility) ? estimate : estimate.subtract(DEDUCTIBLE).max(BigDecimal.ZERO);
        return new Claim(number, reference, "SETTLED", responsibility, estimate, settled, null);
    }

    private static BigDecimal estimate(String type, long seed) {
        int[] range = switch (type) {
            case "GLASS_BREAKAGE" -> new int[] {320, 950};
            case "THEFT" -> new int[] {6_000, 18_000};
            case "FIRE" -> new int[] {4_000, 15_000};
            case "WEATHER" -> new int[] {600, 3_200};
            case "VANDALISM" -> new int[] {450, 2_400};
            default -> new int[] {900, 5_500};
        };
        long amount = range[0] + seed % (range[1] - range[0]);
        return BigDecimal.valueOf(amount / 10 * 10).setScale(2);
    }
}
