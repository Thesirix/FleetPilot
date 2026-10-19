package com.fleetpilot.quote.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.quote.domain.model.Garage;
import com.fleetpilot.quote.domain.model.Quote;
import com.fleetpilot.quote.domain.model.QuoteStatus;
import com.fleetpilot.quote.domain.model.VehicleRef;
import com.fleetpilot.quote.unit.SqlTariff;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Demo garages and quotes")
class QuoteDemoGeneratorTest {

    static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    static QuoteDemoGenerator.Result data;

    static List<VehicleRef> fleet() {
        String[] cities = {"Marseille", "Vitrolles", "Courbevoie", "Lyon", "Lille", "Bordeaux"};
        String[] categories = {"CAR", "CAR", "VAN", "TRUCK"};
        String[] energies = {"DIESEL", "PETROL", "ELECTRIC", "HYBRID"};
        List<VehicleRef> vehicles = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            vehicles.add(new VehicleRef(UUID.nameUUIDFromBytes(("v" + i).getBytes()), UUID.nameUUIDFromBytes(
                    ("c" + i % 3).getBytes()), "AB-%03d-CD".formatted(100 + i), "Renault", "Clio",
                    categories[i % categories.length], energies[i % energies.length], i % 50 == 0 ? null : cities[i % cities.length],
                    NOW));
        }
        return vehicles;
    }

    @BeforeAll
    static void generate() {
        data = new QuoteDemoGenerator(QuoteDemoGenerator.DEFAULT_SEED, NOW, PARIS, new SqlTariff()).generate(fleet());
    }

    @Test
    void the_network_has_a_realistic_size_with_unique_identifiers() {
        int expected = QuoteDemoCatalog.TOWNS.stream().mapToInt(QuoteDemoCatalog.Town::garages).sum();
        assertThat(expected).isEqualTo(169);
        assertThat(data.garages()).hasSize(expected);
        assertThat(data.garages()).extracting(Garage::getCode).doesNotHaveDuplicates();
        assertThat(data.garages()).extracting(Garage::getSiret).doesNotHaveDuplicates();
        assertThat(data.garages()).extracting(Garage::getName).contains("Garage du Prado", "Carrosserie du Vieux-Port");
        assertThat(data.garages()).filteredOn(Garage::isApproved).hasSizeGreaterThan(data.garages().size() * 3 / 4);
    }

    @Test
    void featured_garages_have_the_ids_of_their_keycloak_accounts() {
        Garage prado = data.garages().stream().filter(g -> g.getCode().equals("MRS-01")).findFirst().orElseThrow();
        assertThat(prado.getId()).isEqualTo(QuoteDemoGenerator.uuid("garage", "MRS-01"));
        assertThat(prado.getEmail()).isEqualTo("atelier@garage-du-prado.fr");
    }

    @Test
    void a_year_of_quotes_covers_every_status() {
        assertThat(data.quotes()).hasSize(750);
        Set<QuoteStatus> statuses = data.quotes().stream().map(Quote::getStatus).collect(Collectors.toSet());
        assertThat(statuses).containsExactlyInAnyOrder(QuoteStatus.values());
        assertThat(data.quotes()).filteredOn(q -> q.getStatus() == QuoteStatus.RECEIVED).hasSize(5);
        assertThat(data.quotes()).filteredOn(Quote::isInvoiceDisputed).isNotEmpty();
        assertThat(data.quotes()).filteredOn(Quote::isAutoApproved).isNotEmpty();
        assertThat(data.quotes()).filteredOn(q -> q.getStatus().isOpen() && q.getCounterOffer() != null).isNotEmpty();
    }

    @Test
    void negotiation_saves_money_and_references_follow_the_submission_order() {
        BigDecimal savings = data.quotes().stream().map(Quote::savings).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(savings).isPositive();
        List<Quote> quotes = data.quotes();
        for (int i = 1; i < quotes.size(); i++) {
            assertThat(quotes.get(i).getSubmittedAt()).isAfterOrEqualTo(quotes.get(i - 1).getSubmittedAt());
        }
        assertThat(quotes).extracting(Quote::getReference).doesNotHaveDuplicates();
        assertThat(quotes).allSatisfy(q -> assertThat(q.getSubmittedAt()).isBeforeOrEqualTo(NOW));
        assertThat(quotes).allSatisfy(q -> q.getHistory().forEach(h -> assertThat(h.getAt()).isBeforeOrEqualTo(NOW)));
    }

    @Test
    void the_same_seed_gives_the_same_data() {
        QuoteDemoGenerator.Result again = new QuoteDemoGenerator(QuoteDemoGenerator.DEFAULT_SEED, NOW, PARIS,
                new SqlTariff()).generate(fleet());
        assertThat(again.quotes()).extracting(Quote::getId, Quote::getAmount, Quote::getStatus)
                .containsExactlyElementsOf(data.quotes().stream()
                        .map(q -> tuple(q.getId(), q.getAmount(), q.getStatus())).toList());
    }
}
