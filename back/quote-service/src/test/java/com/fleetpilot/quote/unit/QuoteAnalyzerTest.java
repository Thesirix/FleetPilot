package com.fleetpilot.quote.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.quote.domain.analysis.QuoteAnalysis;
import com.fleetpilot.quote.domain.analysis.QuoteAnalysis.LineAnalysis;
import com.fleetpilot.quote.domain.analysis.QuoteAnalysis.Verdict;
import com.fleetpilot.quote.domain.analysis.QuoteAnalyzer;
import com.fleetpilot.quote.domain.analysis.QuoteLineInput;
import com.fleetpilot.quote.domain.model.Finding;
import com.fleetpilot.quote.domain.model.LineKind;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.TariffClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Quote analysis against the reference tariff")
class QuoteAnalyzerTest {

    private final QuoteAnalyzer analyzer = new QuoteAnalyzer(new TestTariff());

    static QuoteLineInput part(String code, String quantity, String price) {
        return new QuoteLineInput(LineKind.PART, code, "Part " + code, new BigDecimal(quantity), new BigDecimal(price));
    }

    static QuoteLineInput labour(String code, String hours, String rate) {
        return new QuoteLineInput(LineKind.LABOUR, code, "Labour " + code, new BigDecimal(hours), new BigDecimal(rate));
    }

    static QuoteLineInput supplies(String amount) {
        return new QuoteLineInput(LineKind.SUPPLIES, null, "Ingrédients", BigDecimal.ONE, new BigDecimal(amount));
    }

    private QuoteAnalysis analyze(QuoteLineInput... lines) {
        return analyzer.analyze(List.of(lines), TariffClass.CAR, Region.PAC);
    }

    @Nested
    @DisplayName("parts")
    class Parts {

        @Test
        void a_part_at_the_reference_price_is_compliant() {
            LineAnalysis line = analyze(part("PLAQ-AV", "1", "45.00")).lines().getFirst();
            assertThat(line.finding()).isNull();
            assertThat(line.referenceAmount()).isEqualByComparingTo("45.00");
        }

        @Test
        void up_to_ten_percent_above_the_reference_is_tolerated() {
            LineAnalysis line = analyze(part("PLAQ-AV", "1", "49.50")).lines().getFirst();
            assertThat(line.finding()).isNull();
        }

        @Test
        void beyond_the_tolerance_the_part_is_overpriced_and_the_whole_excess_is_due() {
            LineAnalysis line = analyze(part("DISQ-AV", "2", "130.00")).lines().getFirst();
            assertThat(line.finding()).isEqualTo(Finding.OVERPRICED_PART);
            assertThat(line.amount()).isEqualByComparingTo("260.00");
            assertThat(line.referenceAmount()).isEqualByComparingTo("190.00");
            assertThat(line.detail()).contains("+37 %");
        }

        @Test
        void a_cheaper_part_keeps_its_own_price_as_reference() {
            LineAnalysis line = analyze(part("PLAQ-AV", "1", "40.00")).lines().getFirst();
            assertThat(line.referenceAmount()).isEqualByComparingTo("40.00");
        }

        @Test
        void prices_depend_on_the_class_of_vehicle() {
            QuoteAnalysis truck = analyzer.analyze(List.of(part("PLAQ-AV", "1", "165.00")), TariffClass.TRUCK, Region.PAC);
            assertThat(truck.lines().getFirst().finding()).isNull();
            assertThat(truck.referenceAmount()).isEqualByComparingTo("165.00");
        }
    }

    @Nested
    @DisplayName("labour")
    class Labour {

        @Test
        void standard_time_at_the_negotiated_rate_is_compliant() {
            LineAnalysis line = analyze(labour("FREIN-AV-DISQ", "1.3", "72.00")).lines().getFirst();
            assertThat(line.finding()).isNull();
            assertThat(line.referenceAmount()).isEqualByComparingTo("93.60");
        }

        @Test
        void a_time_far_above_the_standard_time_is_flagged() {
            LineAnalysis line = analyze(labour("FREIN-AV-DISQ", "2.0", "72.00")).lines().getFirst();
            assertThat(line.finding()).isEqualTo(Finding.EXCESSIVE_TIME);
            assertThat(line.referenceQuantity()).isEqualByComparingTo("1.30");
            assertThat(line.referenceAmount()).isEqualByComparingTo("93.60");
        }

        @Test
        void a_rate_above_the_regional_rate_is_flagged() {
            LineAnalysis line = analyze(labour("REVISION", "1.0", "95.00")).lines().getFirst();
            assertThat(line.finding()).isEqualTo(Finding.OVERPRICED_RATE);
            assertThat(line.referenceAmount()).isEqualByComparingTo("72.00");
        }

        @Test
        void time_and_rate_both_too_high_are_reported_together() {
            LineAnalysis line = analyze(labour("REVISION", "1.5", "95.00")).lines().getFirst();
            assertThat(line.finding()).isEqualTo(Finding.EXCESSIVE_TIME);
            assertThat(line.detail()).contains("hourly rate");
            assertThat(line.referenceAmount()).isEqualByComparingTo("72.00");
        }

        @Test
        void without_a_negotiated_rate_in_the_region_only_the_time_is_checked() {
            QuoteAnalysis analysis = analyzer.analyze(List.of(labour("REVISION", "1.0", "120.00")), TariffClass.CAR,
                    Region.GES);
            assertThat(analysis.lines().getFirst().finding()).isNull();
        }

        @Test
        void the_rate_depends_on_the_labour_category() {
            assertThat(analyze(labour("DIAG", "0.5", "78.00")).lines().getFirst().finding()).isNull();
            assertThat(analyze(labour("REVISION", "1.0", "78.00")).lines().getFirst().finding()).isNull();
            assertThat(analyze(labour("REVISION", "1.0", "80.00")).lines().getFirst().finding())
                    .isEqualTo(Finding.OVERPRICED_RATE);
        }
    }

    @Nested
    @DisplayName("duplicates, supplies and unknown items")
    class Others {

        @Test
        void the_second_line_with_the_same_code_is_a_duplicate_and_not_due() {
            QuoteAnalysis analysis = analyze(labour("DIAG", "0.5", "78.00"), labour("DIAG", "0.5", "78.00"));
            assertThat(analysis.lines().get(0).finding()).isNull();
            assertThat(analysis.lines().get(1).finding()).isEqualTo(Finding.DUPLICATE);
            assertThat(analysis.lines().get(1).referenceAmount()).isEqualByComparingTo("0");
            assertThat(analysis.overcharge()).isEqualByComparingTo("39.00");
        }

        @Test
        void a_part_and_an_operation_may_share_a_code() {
            QuoteAnalysis analysis = analyze(part("PLAQ-AV", "1", "45.00"), labour("FREIN-AV-DISQ", "1.3", "72.00"),
                    part("DISQ-AV", "1", "95.00"));
            assertThat(analysis.findingCount()).isZero();
        }

        @Test
        void supplies_are_allowed_up_to_six_percent_of_the_work_with_a_minimum() {
            // work at reference: 45 + 93.60 = 138.60 -> 6 % = 8.32, below the 15 EUR minimum
            QuoteAnalysis small = analyze(part("PLAQ-AV", "1", "45.00"), labour("FREIN-AV-DISQ", "1.3", "72.00"),
                    supplies("15.00"));
            assertThat(small.lines().get(2).finding()).isNull();

            QuoteAnalysis excessive = analyze(part("PLAQ-AV", "1", "45.00"), labour("FREIN-AV-DISQ", "1.3", "72.00"),
                    supplies("40.00"));
            LineAnalysis supplies = excessive.lines().get(2);
            assertThat(supplies.finding()).isEqualTo(Finding.EXCESSIVE_SUPPLIES);
            assertThat(supplies.referenceAmount()).isEqualByComparingTo("15.00");
        }

        @Test
        void an_item_absent_from_the_tariff_must_be_checked_without_any_saving_claimed() {
            QuoteAnalysis analysis = analyze(part("KIT-ATTELAGE", "1", "250.00"),
                    new QuoteLineInput(LineKind.PART, null, "Nettoyage", BigDecimal.ONE, new BigDecimal("60.00")));
            assertThat(analysis.lines()).extracting(LineAnalysis::finding)
                    .containsOnly(Finding.UNKNOWN_ITEM);
            assertThat(analysis.overcharge()).isEqualByComparingTo("0");
            assertThat(analysis.referenceAmount()).isEqualByComparingTo(analysis.amount());
            assertThat(analysis.verdict()).isEqualTo(Verdict.TO_CHECK);
        }
    }

    @Nested
    @DisplayName("verdict")
    class Verdicts {

        @Test
        void a_compliant_quote() {
            QuoteAnalysis analysis = analyze(part("FILT-HUILE", "1", "12.00"), part("HUILE-5W30", "5", "11.00"),
                    labour("REVISION", "1.0", "72.00"));
            assertThat(analysis.verdict()).isEqualTo(Verdict.COMPLIANT);
            assertThat(analysis.amount()).isEqualByComparingTo("139.00");
            assertThat(analysis.referenceAmount()).isEqualByComparingTo("139.00");
        }

        @Test
        void a_tiny_overcharge_is_not_worth_a_negotiation() {
            // 2 tyres at 105 (ref 95, +10.5 %): 20 EUR over on a 2,585 EUR quote, below 1 %
            QuoteAnalysis analysis = analyze(part("PNEU", "2", "105.00"), part("DISQ-AV", "25", "95.00"));
            assertThat(analysis.overcharge()).isEqualByComparingTo("20.00");
            assertThat(analysis.verdict()).isEqualTo(Verdict.COMPLIANT);
        }

        @Test
        void a_significant_overcharge_must_be_negotiated() {
            QuoteAnalysis analysis = analyze(part("DISQ-AV", "1", "140.00"), labour("FREIN-AV-DISQ", "2.0", "72.00"));
            assertThat(analysis.overcharge()).isEqualByComparingTo("95.40");
            assertThat(analysis.verdict()).isEqualTo(Verdict.TO_NEGOTIATE);
        }
    }

    @Test
    void a_line_needs_a_positive_quantity_and_a_price() {
        assertThatThrownBy(() -> part("PLAQ-AV", "0", "45.00")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> part("PLAQ-AV", "1", "-1")).isInstanceOf(BusinessRuleException.class);
        assertThat(part(" plaq-av ", "1", "45").code()).isEqualTo("PLAQ-AV");
    }
}
