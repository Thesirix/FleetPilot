package com.fleetpilot.acceptance.pages;

import java.time.Duration;

import com.fleetpilot.acceptance.support.Browser;
import com.fleetpilot.acceptance.support.Platform;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

/** The sign-in page of Keycloak (FleetPilot theme), shared by the portal, the driver space and the extranet. */
public class KeycloakLoginPage {

    private final Browser browser;

    public KeycloakLoginPage(Browser browser) {
        this.browser = browser;
    }

    /** Signs in, then waits until the browser has left Keycloak. */
    public void signIn(String username) {
        browser.waitFor(By.id("username")).sendKeys(username);
        browser.driver().findElement(By.id("password")).sendKeys(Platform.PASSWORD);
        browser.driver().findElement(By.id("kc-login")).click();
        // The first sign-in of a run warms up Keycloak and the portal: a longer wait than for a page
        try {
            browser.waitUntil(driver -> !driver.getCurrentUrl().contains("/realms/")
                    && !driver.getCurrentUrl().contains("/callback"), Duration.ofSeconds(60));
        } catch (TimeoutException e) {
            throw new IllegalStateException("Still signing in at " + browser.driver().getCurrentUrl() + " ("
                    + browser.driver().getTitle() + ")", e);
        }
    }
}
