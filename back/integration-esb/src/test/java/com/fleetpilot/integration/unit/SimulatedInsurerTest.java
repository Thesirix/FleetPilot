package com.fleetpilot.integration.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fleetpilot.integration.partner.SimulatedInsurer;
import com.fleetpilot.integration.partner.SimulatedInsurer.Claim;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The simulated insurer handles a claim over time")
class SimulatedInsurerTest {

    private static final Instant DECLARED = Instant.parse("2026-10-15T08:00:00Z");

    private static Claim at(Duration after, String reference, String type, boolean injuries) {
        SimulatedInsurer insurer = new SimulatedInsurer(null, Clock.fixed(DECLARED.plus(after), ZoneOffset.UTC), "key",
                3, 8, 20);
        return insurer.state("AF-2026-004201", reference, type, injuries, DECLARED);
    }

    @Test
    void a_glass_breakage_is_assessed_agreed_then_settled_without_deductible() {
        assertThat(at(Duration.ofMinutes(1), "SIN-2026-000301", "GLASS_BREAKAGE", false).status()).isEqualTo("RECEIVED");
        Claim expertise = at(Duration.ofMinutes(4), "SIN-2026-000301", "GLASS_BREAKAGE", false);
        assertThat(expertise.status()).isEqualTo("EXPERTISE");
        assertThat(expertise.responsibility()).isEqualTo("NONE");
        assertThat(expertise.estimatedCost()).isBetween(new BigDecimal("320"), new BigDecimal("950"));
        assertThat(at(Duration.ofMinutes(9), "SIN-2026-000301", "GLASS_BREAKAGE", false).status()).isEqualTo("AGREED");
        Claim settled = at(Duration.ofMinutes(21), "SIN-2026-000301", "GLASS_BREAKAGE", false);
        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(settled.settledAmount()).isEqualByComparingTo(expertise.estimatedCost());
    }

    @Test
    void an_accident_with_injuries_engages_our_driver_and_the_deductible_applies() {
        Claim settled = at(Duration.ofHours(1), "SIN-2026-000302", "COLLISION", true);
        if (!"REFUSED".equals(settled.status())) {
            assertThat(settled.responsibility()).isEqualTo("FULL");
            assertThat(settled.settledAmount())
                    .isEqualByComparingTo(settled.estimatedCost().subtract(new BigDecimal("300")));
        }
    }

    @Test
    void the_same_claim_always_tells_the_same_story_and_some_claims_are_refused() {
        assertThat(at(Duration.ofHours(1), "SIN-2026-000302", "COLLISION", true))
                .isEqualTo(at(Duration.ofHours(1), "SIN-2026-000302", "COLLISION", true));
        long refused = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> at(Duration.ofHours(1), "SIN-2026-%06d".formatted(i), "COLLISION", false))
                .filter(c -> "REFUSED".equals(c.status())).count();
        assertThat(refused).isBetween(8L, 40L);
    }
}
