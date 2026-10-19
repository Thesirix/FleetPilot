package com.fleetpilot.quote.unit;

import static com.fleetpilot.quote.unit.QuoteAnalyzerTest.labour;
import static com.fleetpilot.quote.unit.QuoteAnalyzerTest.part;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.quote.domain.analysis.QuoteAnalyzer;
import com.fleetpilot.quote.domain.analysis.QuoteLineInput;
import com.fleetpilot.quote.domain.model.Garage;
import com.fleetpilot.quote.domain.model.GarageNetwork;
import com.fleetpilot.quote.domain.model.Quote;
import com.fleetpilot.quote.domain.model.QuoteChannel;
import com.fleetpilot.quote.domain.model.QuoteHistoryEntry.Action;
import com.fleetpilot.quote.domain.model.QuoteHistoryEntry;
import com.fleetpilot.quote.domain.model.QuoteStatus;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.VehicleRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Life of a garage quote")
class QuoteLifecycleTest {

    static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");
    static final BigDecimal AUTO_LIMIT = new BigDecimal("300");

    private final QuoteAnalyzer analyzer = new QuoteAnalyzer(new TestTariff());

    static Garage garage() {
        return new Garage(UUID.randomUUID(), "MRS-01", new Garage.Profile("Garage du Prado", GarageNetwork.INDEPENDENT,
                null, "12345678900011", "12 avenue du Prado", "13008", "Marseille", null, null,
                "atelier@garage-du-prado.fr", null, new BigDecimal("72.00")));
    }

    static VehicleRef vehicle() {
        return new VehicleRef(UUID.randomUUID(), UUID.randomUUID(), "GH-482-KM", "Peugeot", "308", "CAR", "DIESEL",
                "Marseille", NOW);
    }

    private Quote quote(VehicleRef vehicle, List<QuoteLineInput> lines) {
        return new Quote(UUID.randomUUID(), "DEV-2026-000001", garage(), vehicle,
                new Quote.Submission("GH-482-KM", "D26-0001", 82_000, "Freins avant", QuoteChannel.PORTAL, lines),
                "atelier@garage-du-prado.fr", NOW);
    }

    private Quote analysed(List<QuoteLineInput> lines) {
        Quote quote = quote(vehicle(), lines);
        quote.applyAnalysis(analyzer.analyze(quote.lineInputs(), quote.tariffClass(), Region.PAC), AUTO_LIMIT, NOW);
        return quote;
    }

    /** 140 EUR of brake discs (ref 95) and 2 h of labour (standard 1.3 h): 95.40 EUR of overcharge. */
    private Quote overpriced() {
        return analysed(List.of(part("DISQ-AV", "1", "140.00"), labour("FREIN-AV-DISQ", "2.0", "72.00")));
    }

    /** 45 + 95 + 93.60: compliant, 233.60 EUR, under the automatic approval limit. */
    private static List<QuoteLineInput> compliantLines() {
        return List.of(part("PLAQ-AV", "1", "45.00"), part("DISQ-AV", "1", "95.00"),
                labour("FREIN-AV-DISQ", "1.3", "72.00"));
    }

    @Nested
    @DisplayName("reception and analysis")
    class Analysis {

        @Test
        void a_quote_for_an_unknown_vehicle_waits_for_identification() {
            Quote quote = quote(null, compliantLines());
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.RECEIVED);
            assertThat(quote.getCompanyId()).isNull();
            assertThatThrownBy(() -> quote.applyAnalysis(null, AUTO_LIMIT, NOW))
                    .hasMessageContaining("vehicle must be identified");

            VehicleRef identified = vehicle();
            quote.identifyVehicle(identified, "claire.morel", NOW);
            assertThat(quote.getCompanyId()).isEqualTo(identified.getCompanyId());
            assertThat(quote.getHistory()).extracting(QuoteHistoryEntry::getAction)
                    .containsExactly(Action.SUBMITTED, Action.VEHICLE_IDENTIFIED);
        }

        @Test
        void a_small_compliant_quote_is_approved_without_human_action() {
            Quote quote = analysed(compliantLines());
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
            assertThat(quote.isAutoApproved()).isTrue();
            assertThat(quote.getAcceptedAmount()).isEqualByComparingTo("233.60");
            assertThat(quote.savings()).isEqualByComparingTo("0");
            assertThat(quote.getHistory()).extracting(QuoteHistoryEntry::getAction)
                    .containsExactly(Action.SUBMITTED, Action.ANALYSED, Action.AUTO_APPROVED);
        }

        @Test
        void a_compliant_quote_above_the_limit_goes_to_review() {
            Quote quote = analysed(List.of(part("DISQ-AV", "4", "95.00")));
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.IN_REVIEW);
            assertThat(quote.isAutoApproved()).isFalse();
        }

        @Test
        void an_overpriced_quote_is_to_negotiate_with_its_findings() {
            Quote quote = overpriced();
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.TO_NEGOTIATE);
            assertThat(quote.getOvercharge()).isEqualByComparingTo("95.40");
            assertThat(quote.getReferenceAmount()).isEqualByComparingTo("188.60");
            assertThat(quote.getFindingCount()).isEqualTo(2);
            assertThat(quote.getLines().get(0).overcharge()).isEqualByComparingTo("45.00");
        }
    }

    @Nested
    @DisplayName("negotiation")
    class Negotiation {

        @Test
        void the_garage_accepts_the_counter_offer_and_the_saving_is_measured() {
            Quote quote = overpriced();
            quote.proposeCounterOffer(new BigDecimal("190"), "Alignement sur le tarif", "claire.morel", NOW);
            quote.acceptCounterOffer("atelier@garage-du-prado.fr", NOW.plusSeconds(3600));

            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
            assertThat(quote.getAcceptedAmount()).isEqualByComparingTo("190.00");
            assertThat(quote.savings()).isEqualByComparingTo("94.00");
            assertThat(quote.getCounterOffer()).isNull();
            assertThat(quote.getDecidedBy()).isEqualTo("atelier@garage-du-prado.fr");
        }

        @Test
        void a_counter_offer_must_be_below_the_quoted_amount() {
            Quote quote = overpriced();
            assertThatThrownBy(() -> quote.proposeCounterOffer(new BigDecimal("300"), null, "claire.morel", NOW))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("lower than the quoted amount");
        }

        @Test
        void after_a_refusal_the_manager_decides() {
            Quote quote = overpriced();
            quote.proposeCounterOffer(new BigDecimal("190"), null, "claire.morel", NOW);
            quote.refuseCounterOffer("Pièce d'origine", "atelier@garage-du-prado.fr", NOW);
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.TO_NEGOTIATE);
            assertThatThrownBy(() -> quote.acceptCounterOffer("atelier@garage-du-prado.fr", NOW))
                    .hasMessageContaining("no counter-offer");
        }

        @Test
        void a_revised_quote_is_analysed_again() {
            Quote quote = overpriced();
            quote.revise(compliantLines(), "Nouvelle version alignée", "atelier@garage-du-prado.fr", NOW);
            assertThat(quote.getRevision()).isEqualTo(2);
            assertThat(quote.getOvercharge()).isEqualByComparingTo("0");
            quote.applyAnalysis(analyzer.analyze(quote.lineInputs(), quote.tariffClass(), Region.PAC), AUTO_LIMIT, NOW);
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
            assertThat(quote.getInitialAmount()).isEqualByComparingTo("284.00");
            assertThat(quote.savings()).isEqualByComparingTo("50.40");
        }
    }

    @Nested
    @DisplayName("decision")
    class Decision {

        @Test
        void accepting_an_overcharged_quote_requires_a_justification() {
            Quote quote = overpriced();
            assertThatThrownBy(() -> quote.accept(" ", "claire.morel", NOW))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("justification");
            quote.accept("Véhicule immobilisé", "claire.morel", NOW);
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
            assertThat(quote.getAcceptedAmount()).isEqualByComparingTo("284.00");
        }

        @Test
        void a_rejection_needs_a_reason_and_closes_the_quote() {
            Quote quote = overpriced();
            assertThatThrownBy(() -> quote.reject("", "claire.morel", NOW)).hasMessageContaining("reason");
            quote.reject("Couvert par le contrat de maintenance", "claire.morel", NOW);
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.REJECTED);
            assertThat(quote.savings()).isNull();
            assertThatThrownBy(() -> quote.accept("x", "claire.morel", NOW)).hasMessageContaining("REJECTED");
        }

        @Test
        void a_quote_cannot_be_decided_before_its_analysis() {
            Quote quote = quote(null, compliantLines());
            assertThatThrownBy(() -> quote.accept("ok", "claire.morel", NOW)).hasMessageContaining("analysed first");
        }
    }

    @Nested
    @DisplayName("work and invoice")
    class Invoice {

        @Test
        void an_invoice_matching_the_accepted_amount_closes_the_quote() {
            Quote quote = analysed(compliantLines());
            quote.complete(LocalDate.of(2026, 10, 15), "atelier@garage-du-prado.fr", NOW);
            quote.invoice("F26-00042", new BigDecimal("233.60"), LocalDate.of(2026, 10, 16), "atelier", NOW);
            assertThat(quote.getStatus()).isEqualTo(QuoteStatus.INVOICED);
            assertThat(quote.invoiceVariance()).isEqualByComparingTo("0");
            assertThat(quote.isInvoiceDisputed()).isFalse();
        }

        @Test
        void a_small_difference_is_tolerated_a_larger_one_is_disputed() {
            Quote tolerated = analysed(compliantLines());
            tolerated.invoice("F1", new BigDecimal("243.00"), LocalDate.of(2026, 10, 16), "atelier", NOW);
            assertThat(tolerated.isInvoiceDisputed()).isFalse();

            Quote disputed = analysed(compliantLines());
            disputed.invoice("F2", new BigDecimal("260.00"), LocalDate.of(2026, 10, 16), "atelier", NOW);
            assertThat(disputed.isInvoiceDisputed()).isTrue();
            assertThat(disputed.invoiceVariance()).isEqualByComparingTo("26.40");
            assertThat(disputed.getCompletedOn()).isEqualTo(LocalDate.of(2026, 10, 16));
        }

        @Test
        void an_open_quote_cannot_be_invoiced() {
            Quote quote = overpriced();
            assertThatThrownBy(() -> quote.invoice("F1", BigDecimal.TEN, LocalDate.of(2026, 10, 16), "atelier", NOW))
                    .hasMessageContaining("cannot go from TO_NEGOTIATE to INVOICED");
        }
    }

    @Test
    void a_garage_outside_the_covered_regions_is_refused() {
        assertThatThrownBy(() -> new Garage(UUID.randomUUID(), "X", new Garage.Profile("Garage", GarageNetwork.DEALER,
                "Renault", "12345678900011", "1 rue", "97400", "Saint-Denis", null, null, "a@b.fr", null,
                BigDecimal.TEN))).hasMessageContaining("97400");
    }

    @Test
    void regions_come_from_the_department() {
        assertThat(Region.ofPostalCode("13008")).contains(Region.PAC);
        assertThat(Region.ofPostalCode("92400")).contains(Region.IDF);
        assertThat(Region.ofPostalCode("69007")).contains(Region.ARA);
        assertThat(Region.ofPostalCode("2A004")).isEmpty();
        assertThat(Region.ofPostalCode(null)).isEmpty();
    }
}
