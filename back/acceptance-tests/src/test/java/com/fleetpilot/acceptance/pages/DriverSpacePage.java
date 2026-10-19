package com.fleetpilot.acceptance.pages;

import com.fleetpilot.acceptance.support.Browser;
import org.openqa.selenium.By;

/** The driver space of the portal (installable web app): home, fill-up form and history. */
public class DriverSpacePage {

    private final Browser browser;

    public DriverSpacePage(Browser browser) {
        this.browser = browser;
    }

    public String greeting() {
        return browser.waitFor(By.cssSelector("main h1")).getText().trim();
    }

    public String vehicleSite() {
        return browser.waitFor(By.xpath("//div[contains(@class,'facts')]/div[3]/strong")).getText().trim();
    }

    public void openTab(String label) {
        browser.click(browser.waitFor(By.xpath("//nav[contains(@class,'tabbar')]//a[normalize-space()='" + label + "']")));
    }

    public void fillUp(String quantity, String amount, String station) {
        browser.waitFor(By.id("quantity")).sendKeys(quantity);
        browser.driver().findElement(By.id("amount")).sendKeys(amount);
        browser.driver().findElement(By.id("station")).sendKeys(station);
    }

    public String unitPrice() {
        return browser.waitFor(By.cssSelector(".price")).getText().trim();
    }

    public void save(String label) {
        browser.click(browser.waitForClickable(By.xpath("//button[normalize-space()='" + label + "']")));
    }

    public void waitForToast(String text) {
        browser.waitUntil(driver -> driver.findElements(By.cssSelector(".toast")).stream()
                .anyMatch(toast -> toast.getText().contains(text)));
    }

    public boolean historyContains(String text) {
        return browser.waitUntil(driver -> driver.findElements(By.cssSelector(".list li")).stream()
                .anyMatch(item -> item.getText().contains(text)));
    }
}
