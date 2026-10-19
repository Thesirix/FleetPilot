package com.fleetpilot.integration.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import com.fleetpilot.integration.demo.PartnerFiles;
import com.fleetpilot.integration.demo.PartnerFiles.DemoFile;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Garage;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Operation;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Part;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Tariff;
import com.fleetpilot.integration.fleet.FleetDirectory.FleetVehicle;
import com.fleetpilot.integration.fleet.FleetDirectory.LeaseContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

@DisplayName("Partner files of the demonstration")
class PartnerFilesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    static List<FleetVehicle> fleet() {
        List<FleetVehicle> fleet = new ArrayList<>();
        String[][] kinds = {{"CAR", "PETROL"}, {"CAR", "DIESEL"}, {"VAN", "DIESEL"}, {"CAR", "ELECTRIC"},
            {"TRUCK", "DIESEL"}, {"CAR", "HYBRID"}};
        for (int i = 0; i < 240; i++) {
            String[] kind = kinds[i % kinds.length];
            fleet.add(new FleetVehicle(UUID.nameUUIDFromBytes(("v" + i).getBytes()), UUID.randomUUID(),
                    "%s-%03d-%s".formatted("AB", 100 + i, "CD"), kind[0], "Renault", "Clio", kind[1], "ACTIVE",
                    20_000 + i * 310));
        }
        return fleet;
    }

    static Map<String, Garage> garages() {
        Map<String, Garage> garages = new LinkedHashMap<>();
        for (String code : List.of("MRS-01", "MRS-02", "LYS-02", "LYS-06", "PAR-01")) {
            garages.put(code, new Garage(UUID.randomUUID(), code, "Garage " + code, "4560127003297" + code.charAt(5),
                    "Ville", new BigDecimal("72.00")));
        }
        return garages;
    }

    static Tariff tariff() {
        List<Part> parts = new ArrayList<>();
        for (String code : List.of("DISQ-AV", "PLAQ-AV", "FILT-HUILE", "HUILE-5W30", "FILT-AIR", "FILT-HAB", "BATT-12V",
                "KIT-EMBR", "PNEU")) {
            parts.add(new Part(code, "Pièce " + code, new BigDecimal("45.00"), new BigDecimal("58.00")));
        }
        List<Operation> operations = new ArrayList<>();
        for (String code : List.of("FREIN-AV-DISQ", "PARE-CHOC", "PEINTURE", "REVISION", "BATTERIE", "EMBRAY",
                "PNEU-MONT-2")) {
            operations.add(new Operation(code, "Opération " + code, "MECH", new BigDecimal("1.20"),
                    new BigDecimal("1.40")));
        }
        return new Tariff(parts, operations);
    }

    private static Validator validator() throws SAXException {
        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(PartnerFilesTest.class.getResource("/schemas/garage-quote.xsd"));
        return schema.newValidator();
    }

    @Test
    void the_garage_quotes_follow_their_schema_except_the_one_made_to_break_it() throws Exception {
        List<DemoFile> quotes = new PartnerFiles(TODAY).garageQuotes(fleet(), garages(), tariff());

        assertThat(quotes).hasSize(7).allSatisfy(f -> assertThat(f.folder()).isEqualTo("garages"));
        Validator validator = validator();
        for (DemoFile file : quotes) {
            if (file.name().contains("GCH-2231")) {
                assertThatThrownBy(() -> validator.validate(new StreamSource(new StringReader(file.content()))))
                        .isInstanceOf(SAXException.class).hasMessageContaining("cvc-");
            } else {
                validator.validate(new StreamSource(new StringReader(file.content())));
            }
        }
        assertThat(quotes.getLast().content()).isEqualTo(quotes.getFirst().content());
    }

    @Test
    void the_lessor_invoice_bills_every_contract_running_last_month_plus_the_mistakes() {
        List<LeaseContract> contracts = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            contracts.add(new LeaseContract(UUID.randomUUID(), "Arval", "ARV-2024-%06d".formatted(100_000 + i),
                    UUID.randomUUID(), "AB-%03d-CD".formatted(100 + i), LocalDate.of(2024, 1, 1),
                    LocalDate.of(2028, 1, 1), new BigDecimal("450.00"), i == 7 ? "ENDED" : "ACTIVE",
                    i == 7 ? LocalDate.of(2026, 6, 30) : null));
        }
        DemoFile clean = new PartnerFiles(TODAY).lessorInvoice("Arval", "ARV", contracts, false);
        DemoFile wrong = new PartnerFiles(TODAY).lessorInvoice("Arval", "ARV", contracts, true);

        assertThat(clean.name()).isEqualTo("arv_factures_2026-09.csv");
        assertThat(clean.content().lines()).hasSize(1 + 119).first().asString().startsWith("loueur;numero_facture");
        assertThat(wrong.content().lines()).hasSize(1 + 119 + 3);
        assertThat(wrong.content()).contains(";ARV-2024-100007;").contains(";ARV-2024-199871;");
    }

    @Test
    void the_fuel_cards_cover_eight_weeks_and_contain_the_anomalies_to_find() {
        List<DemoFile> files = new PartnerFiles(TODAY).fuelCards("Carbu Pro", "CBP", fleet(), false);

        assertThat(files).hasSize(4).extracting(DemoFile::name).first()
                .isEqualTo("cbp_transactions_2026-08-20_2026-09-02.csv");
        String last = files.getLast().content();
        assertThat(last).contains(";GK-118-TR;").contains(";86,40;");
        assertThat(files).allSatisfy(f -> assertThat(f.content().lines().skip(1))
                .allSatisfy(line -> assertThat(line.split(";")).hasSize(12)));
        assertThat(new PartnerFiles(TODAY).fuelCards("Carbu Pro", "CBP", fleet(), false)).isEqualTo(files);
    }
}
