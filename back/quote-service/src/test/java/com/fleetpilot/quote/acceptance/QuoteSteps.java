package com.fleetpilot.quote.acceptance;

import static com.fleetpilot.quote.integration.IntegrationTestBase.PRADO;
import static com.fleetpilot.quote.integration.IntegrationTestBase.admin;
import static com.fleetpilot.quote.integration.IntegrationTestBase.garage;
import static com.fleetpilot.quote.integration.IntegrationTestBase.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.fr.Alors;
import io.cucumber.java.fr.Et;
import io.cucumber.java.fr.Mais;
import io.cucumber.java.fr.Quand;
import io.cucumber.java.fr.Soit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps of the quote scenarios. They go through the REST API with the tokens of a garage, a fleet manager and an
 * administrator, exactly as the portal and the garage space do.
 */
public class QuoteSteps {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Map<String, String> KINDS = Map.of("pièce", "PART", "main-d'oeuvre", "LABOUR",
            "fournitures", "SUPPLIES");
    private static final Map<String, String> STATUSES = Map.of("accepté", "ACCEPTED", "à négocier", "TO_NEGOTIATE",
            "en analyse", "IN_REVIEW", "refusé", "REJECTED");
    private static final Map<String, String> FINDINGS = Map.of("pièce trop chère", "OVERPRICED_PART",
            "temps hors barème", "EXCESSIVE_TIME", "taux horaire trop élevé", "OVERPRICED_RATE", "doublon", "DUPLICATE",
            "fournitures excessives", "EXCESSIVE_SUPPLIES", "hors tarif", "UNKNOWN_ITEM");

    @Autowired
    private MockMvc mvc;

    private String registration;
    private String quote;
    private MvcResult last;

    @Soit("le garage {string} à Marseille")
    public void garageAMarseille(String name) throws Exception {
        String json = mvc.perform(get("/api/v1/garages/me").with(garage(PRADO))).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<String>read(json, "$.name")).isEqualTo(name);
        assertThat(JsonPath.<String>read(json, "$.city")).isEqualTo("Marseille");
    }

    @Et("le véhicule {string} de la flotte, une voiture")
    public void vehiculeDeLaFlotte(String plate) {
        this.registration = plate;
    }

    @Quand("le garage envoie un devis avec les lignes suivantes :")
    public void leGarageEnvoieUnDevis(List<Map<String, String>> rows) throws Exception {
        String lines = rows.stream().map(row -> """
                {"kind":"%s","code":"%s","label":"%s","quantity":%s,"unitPrice":%s}""".formatted(
                KINDS.get(row.get("type")), row.get("code"), row.get("libellé"), number(row.get("quantité")),
                number(row.get("prix")))).reduce((a, b) -> a + "," + b).orElseThrow();
        submit("[" + lines + "]");
    }

    @Soit("un devis de {euros} € à négocier envoyé par le garage")
    public void unDevisANegocier(BigDecimal amount) throws Exception {
        submit("""
                [{"kind":"PART","code":"DISQ-AV","label":"Disques avant","quantity":1,"unitPrice":140.00},
                 {"kind":"LABOUR","code":"FREIN-AV-DISQ","label":"Freins","quantity":2.0,"unitPrice":72.00}]""");
        assertThat(read("$.status")).isEqualTo("TO_NEGOTIATE");
        assertThat(decimal("$.amount")).isEqualByComparingTo(amount);
    }

    @Soit("un devis conforme de {euros} € envoyé par le garage")
    public void unDevisConforme(BigDecimal amount) throws Exception {
        BigDecimal discs = amount.divide(new BigDecimal("95.00"));
        submit("""
                [{"kind":"PART","code":"DISQ-AV","label":"Disques avant","quantity":%s,"unitPrice":95.00}]"""
                .formatted(discs.toPlainString()));
        assertThat(read("$.status")).isEqualTo("IN_REVIEW");
    }

    @Soit("un devis accepté de {euros} €")
    public void unDevisAccepte(BigDecimal amount) throws Exception {
        submit("""
                [{"kind":"PART","code":"PLAQ-AV","label":"Plaquettes avant","quantity":1,"unitPrice":45.00},
                 {"kind":"LABOUR","code":"FREIN-AV","label":"Plaquettes","quantity":0.8,"unitPrice":72.00}]""");
        assertThat(decimal("$.acceptedAmount")).isEqualByComparingTo(amount);
    }

    @Quand("le gestionnaire propose {euros} € au garage")
    public void leGestionnairePropose(BigDecimal amount) throws Exception {
        act(manager(), "/counter-offer", "{\"amount\":" + amount + "}");
    }

    @Et("le garage accepte la contre-proposition")
    public void leGarageAccepte() throws Exception {
        act(garage(PRADO), "/counter-offer/answer", "{\"accept\":true}");
    }

    @Quand("un gestionnaire de flotte tente de l'accepter")
    public void unGestionnaireTente() throws Exception {
        act(manager(), "/accept", "{\"comment\":\"Urgent\"}");
    }

    @Mais("un administrateur peut l'accepter")
    public void unAdministrateurAccepte() throws Exception {
        act(admin(), "/accept", "{\"comment\":\"Double validation\"}");
        assertThat(read("$.status")).isEqualTo("ACCEPTED");
    }

    @Quand("le garage envoie une facture de {euros} €")
    public void leGarageFacture(BigDecimal amount) throws Exception {
        act(garage(PRADO), "/invoice", "{\"number\":\"F-%d\",\"amount\":%s}".formatted(SEQUENCE.incrementAndGet(),
                amount.toPlainString()));
    }

    @Alors("le devis est {string} automatiquement pour {euros} €")
    public void accepteAutomatiquement(String status, BigDecimal amount) {
        assertThat(read("$.status")).isEqualTo(STATUSES.get(status));
        assertThat(JsonPath.<Boolean>read(body(), "$.autoApproved")).isTrue();
        assertThat(decimal("$.acceptedAmount")).isEqualByComparingTo(amount);
    }

    @Alors("le devis est {string}")
    public void leDevisEst(String status) {
        assertThat(read("$.status")).isEqualTo(STATUSES.get(status));
    }

    @Alors("le devis est {string} pour {euros} €")
    public void leDevisEstPour(String status, BigDecimal amount) {
        assertThat(read("$.status")).isEqualTo(STATUSES.get(status));
        assertThat(decimal("$.acceptedAmount")).isEqualByComparingTo(amount);
    }

    @Et("aucune ligne n'est signalée")
    public void aucuneLigneSignalee() {
        assertThat(JsonPath.<Integer>read(body(), "$.findingCount")).isZero();
    }

    @Et("la ligne {string} est signalée {string} avec un surcoût de {euros} €")
    public void laLigneEstSignalee(String code, String finding, BigDecimal overcharge) {
        List<Map<String, Object>> lines = JsonPath.read(body(), "$.lines[?(@.code == '" + code + "')]");
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().get("finding")).isEqualTo(FINDINGS.get(finding));
        assertThat(new BigDecimal(lines.getFirst().get("overcharge").toString())).isEqualByComparingTo(overcharge);
    }

    @Alors("la ligne {int} est signalée {string} avec un surcoût de {euros} €")
    public void laLigneNumeroEstSignalee(int position, String finding, BigDecimal overcharge) {
        Map<String, Object> line = JsonPath.read(body(), "$.lines[" + (position - 1) + "]");
        assertThat(line.get("finding")).isEqualTo(FINDINGS.get(finding));
        assertThat(new BigDecimal(line.get("overcharge").toString())).isEqualByComparingTo(overcharge);
    }

    @Et("le montant au tarif de référence est de {euros} €")
    public void montantDeReference(BigDecimal amount) {
        assertThat(decimal("$.referenceAmount")).isEqualByComparingTo(amount);
    }

    @Et("l'économie réalisée est de {euros} €")
    public void economieRealisee(BigDecimal amount) {
        assertThat(decimal("$.savings")).isEqualByComparingTo(amount);
    }

    @Alors("il est refusé avec le message {string}")
    public void refuseAvecLeMessage(String code) {
        assertThat(last.getResponse().getStatus()).isEqualTo(422);
        assertThat(read("$.code")).isEqualTo(code);
    }

    @Alors("la facture est conforme")
    public void factureConforme() {
        assertThat(read("$.status")).isEqualTo("INVOICED");
        assertThat(JsonPath.<Boolean>read(body(), "$.invoiceDisputed")).isFalse();
    }

    @Alors("la facture est contestée")
    public void factureContestee() {
        assertThat(JsonPath.<Boolean>read(body(), "$.invoiceDisputed")).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private void submit(String lines) throws Exception {
        String request = """
                {"registration":"%s","garageReference":"BDD-%d","description":"Scénario","lines":%s}"""
                .formatted(registration, SEQUENCE.incrementAndGet(), lines);
        last = mvc.perform(post("/api/v1/quotes").with(garage(PRADO)).contentType(MediaType.APPLICATION_JSON)
                .content(request)).andReturn();
        assertThat(last.getResponse().getStatus()).as(body()).isEqualTo(201);
        quote = read("$.id");
    }

    private void act(RequestPostProcessor who, String path, String request) throws Exception {
        last = mvc.perform(post("/api/v1/quotes/" + quote + path).with(who).contentType(MediaType.APPLICATION_JSON)
                .content(request)).andReturn();
    }

    private String body() {
        try {
            return last.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String read(String path) {
        return JsonPath.read(body(), path);
    }

    private BigDecimal decimal(String path) {
        return new BigDecimal(JsonPath.read(body(), path).toString());
    }

    private static String number(String french) {
        return french.replace(" ", "").replace(',', '.');
    }
}
