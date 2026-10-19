package com.fleetpilot.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fleetpilot.acceptance.pages.DriverSpacePage;
import com.fleetpilot.acceptance.pages.KeycloakLoginPage;
import com.fleetpilot.acceptance.pages.PortalPage;
import com.fleetpilot.acceptance.support.Browser;
import io.cucumber.java.fr.Alors;
import io.cucumber.java.fr.Quand;
import io.cucumber.java.fr.Soit;

/** What a user does in the web portal and in the driver space. */
public class PortalSteps {

    private final Browser browser;
    private final PortalPage portal;
    private final DriverSpacePage driverSpace;
    private final KeycloakLoginPage keycloak;

    public PortalSteps(Browser browser) {
        this.browser = browser;
        this.portal = new PortalPage(browser);
        this.driverSpace = new DriverSpacePage(browser);
        this.keycloak = new KeycloakLoginPage(browser);
    }

    // ---------------------------------------------------------------- sign-in and navigation

    @Soit("je suis connecté au portail en tant que {string}")
    public void connecte(String username) {
        portal.open("/");
        keycloak.signIn(username);
        browser.waitUntil(driver -> !browser.currentPath().startsWith("/auth/"));
    }

    @Soit("je suis connecté au portail en tant que {string} sur un téléphone")
    public void connecteSurTelephone(String username) {
        browser.phone();
        connecte(username);
    }

    @Quand("j'ouvre la page {string} depuis le menu")
    public void ouvreDepuisLeMenu(String label) {
        portal.openFromMenu(label);
        portal.waitForHeading(label);
    }

    /** "{n}" in a value is replaced by a number proper to this run, so that a name or a SIREN stays unique. */
    private static final String RUN = String.valueOf(System.currentTimeMillis() % 10_000_000);

    private static String unique(String value) {
        return value.replace("{n}", "%07d".formatted(Long.parseLong(RUN)));
    }

    @Quand("je clique sur {string}")
    public void cliqueSur(String text) {
        portal.click(text);
    }

    @Quand("je remplis le champ {string} avec {string}")
    public void remplis(String label, String value) {
        portal.fill(label, unique(value));
    }

    @Alors("un message confirme {string}")
    public void messageConfirme(String text) {
        portal.waitForMessage(unique(text));
    }

    @Alors("le menu ne propose pas {string}")
    public void menuSans(String label) {
        assertThat(portal.menuOffers(label)).as("menu entry %s", label).isFalse();
    }

    @Alors("le menu propose {string}")
    public void menuAvec(String label) {
        assertThat(portal.menuOffers(label)).as("menu entry %s", label).isTrue();
    }

    @Quand("j'ouvre l'adresse {string}")
    public void ouvreAdresse(String path) {
        portal.open(path);
    }

    @Quand("je recherche {string}")
    public void recherche(String text) {
        portal.search(text);
        portal.waitForFirstRow(text);
    }

    @Quand("je choisis {string} dans la liste {string}")
    public void choisis(String option, String label) {
        portal.select(label, option);
        portal.waitForFirstRow(option);
    }

    @Quand("j'ouvre la première ligne")
    public void ouvrePremiereLigne() {
        portal.openFirstRow();
    }

    @Quand("j'ouvre l'onglet {string}")
    public void ouvreOnglet(String label) {
        portal.openTab(label);
    }

    // ---------------------------------------------------------------- what the page shows

    @Alors("le titre de la page est {string}")
    public void titre(String title) {
        portal.waitForHeading(unique(title));
        assertThat(portal.heading()).isEqualTo(unique(title));
    }

    @Alors("le choix de l'entreprise n'est pas proposé")
    public void pasDeChoixEntreprise() {
        portal.rows();
        assertThat(portal.hasSelect("Entreprise")).as("sélecteur d'entreprise").isFalse();
    }

    @Alors("le tableau contient {string}")
    public void tableauContient(String text) {
        browser.waitUntil(driver -> portal.rows().stream().anyMatch(row -> row.getText().contains(text)));
    }

    @Alors("la page indique {string}")
    public void pageIndique(String text) {
        portal.waitForText(text);
    }

    @Alors("au moins une ligne du devis est signalée")
    public void ligneSignalee() {
        portal.waitForText("Anomalies");
        assertThat(portal.count("tr.flagged")).as("lignes signalées").isPositive();
    }

    // ---------------------------------------------------------------- driver space

    @Alors("je suis dans l'espace conducteur et je lis {string}")
    public void espaceConducteur(String greeting) {
        browser.waitUntil(driver -> browser.currentPath().startsWith("/conducteur"));
        assertThat(driverSpace.greeting()).isEqualTo(greeting);
    }

    @Alors("le site de mon véhicule est {string}")
    public void siteDuVehicule(String site) {
        assertThat(driverSpace.vehicleSite()).isEqualTo(site);
    }

    @Quand("j'ouvre l'onglet {string} de l'application conducteur")
    public void ongletConducteur(String label) {
        driverSpace.openTab(label);
    }

    @Quand("je saisis un plein de {string} litres pour {string} euros à la station {string}")
    public void saisisPlein(String quantity, String amount, String station) {
        driverSpace.fillUp(quantity, amount, station);
    }

    @Alors("le prix au litre affiché est {string}")
    public void prixAuLitre(String text) {
        browser.waitUntil(driver -> driverSpace.unitPrice().equals(text));
    }

    @Quand("j'enregistre avec {string}")
    public void enregistre(String button) {
        driverSpace.save(button);
    }

    @Alors("le message {string} s'affiche")
    public void message(String text) {
        driverSpace.waitForToast(text);
    }

    @Alors("l'historique contient {string}")
    public void historique(String text) {
        assertThat(driverSpace.historyContains(text)).isTrue();
    }
}
