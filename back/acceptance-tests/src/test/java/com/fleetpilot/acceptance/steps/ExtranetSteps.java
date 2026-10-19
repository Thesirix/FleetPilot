package com.fleetpilot.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fleetpilot.acceptance.pages.ExtranetPage;
import com.fleetpilot.acceptance.pages.KeycloakLoginPage;
import com.fleetpilot.acceptance.support.Browser;
import io.cucumber.java.fr.Alors;
import io.cucumber.java.fr.Quand;
import io.cucumber.java.fr.Soit;

/** What a lessor does in the extranet (the historical application on Tomcat). */
public class ExtranetSteps {

    private final ExtranetPage extranet;
    private final KeycloakLoginPage keycloak;
    private String rememberedContract;
    private int expectedMileage;

    public ExtranetSteps(Browser browser) {
        this.extranet = new ExtranetPage(browser);
        this.keycloak = new KeycloakLoginPage(browser);
    }

    @Soit("je suis connecté à l'extranet en tant que {string}")
    public void connecte(String username) {
        extranet.open("/");
        keycloak.signIn(username);
    }

    @Alors("le loueur affiché est {string}")
    public void loueur(String lessor) {
        assertThat(extranet.lessor()).isEqualTo(lessor);
    }

    @Alors("tous les contrats listés commencent par {string}")
    public void contratsDuLoueur(String prefix) {
        assertThat(extranet.contractNumbers()).isNotEmpty().allMatch(number -> number.startsWith(prefix));
    }

    @Alors("l'indicateur {string} est supérieur à {int}")
    public void indicateur(String label, int minimum) {
        assertThat(extranet.figure(label)).isGreaterThan(minimum);
    }

    @Quand("je filtre sur {string}")
    public void filtre(String label) {
        extranet.filter(label);
    }

    @Quand("j'ouvre le premier contrat")
    public void premierContrat() {
        extranet.openFirstContract();
        rememberedContract = extranet.currentUrl();
    }

    @Quand("je saisis un relevé de {int} km de plus que le dernier")
    public void saisisReleve(int more) {
        expectedMileage = extranet.lastMileage() + more;
        extranet.recordReading(expectedMileage);
    }

    @Alors("l'extranet confirme l'enregistrement du relevé")
    public void confirmation() {
        assertThat(extranet.message()).startsWith("Relevé de").endsWith("enregistré.");
    }

    @Alors("le relevé saisi apparaît avec la source {string}")
    public void source(String source) {
        assertThat(extranet.readingLine(expectedMileage)).as("relevé de %d km", expectedMileage).endsWith(source);
    }

    @Quand("je me déconnecte de l'extranet")
    public void deconnexion() {
        extranet.signOut();
        assertThat(extranet.heading()).isEqualTo("Vous êtes déconnecté");
    }

    @Quand("j'ouvre l'adresse du contrat précédent")
    public void contratPrecedent() {
        extranet.open(rememberedContract.substring(rememberedContract.indexOf("/contract")));
    }

    @Alors("l'extranet répond {string}")
    public void reponse(String message) {
        assertThat(extranet.message()).isEqualTo(message);
    }
}
