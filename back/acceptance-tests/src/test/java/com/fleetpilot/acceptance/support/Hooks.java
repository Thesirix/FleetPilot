package com.fleetpilot.acceptance.support;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;

/** After each scenario: a screenshot in the report when it failed, then the browser is closed. */
public class Hooks {

    private final Browser browser;

    public Hooks(Browser browser) {
        this.browser = browser;
    }

    @After
    public void closeBrowser(Scenario scenario) {
        if (scenario.isFailed() && browser.started()) {
            scenario.attach(browser.screenshot(), "image/png", "Écran au moment de l'échec");
        }
        browser.quit();
    }
}
