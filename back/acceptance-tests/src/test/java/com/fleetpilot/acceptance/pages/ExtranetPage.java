package com.fleetpilot.acceptance.pages;

import java.util.List;

import com.fleetpilot.acceptance.support.Browser;
import com.fleetpilot.acceptance.support.Platform;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** The extranet of the lessors (JSP on Tomcat behind Apache): its figures, its table of contracts, a contract. */
public class ExtranetPage {

    private final Browser browser;

    public ExtranetPage(Browser browser) {
        this.browser = browser;
    }

    public void open(String path) {
        browser.open(Platform.EXTRANET + path);
    }

    public String lessor() {
        return browser.waitFor(By.cssSelector(".account .lessor")).getText().trim();
    }

    public int figure(String label) {
        WebElement kpi = browser.waitFor(By.xpath("//a[contains(@class,'kpi')][.//span[normalize-space()='" + label + "']]"));
        return Integer.parseInt(kpi.findElement(By.cssSelector(".kpi-value")).getText().replaceAll("\\D", ""));
    }

    public void filter(String label) {
        browser.click(browser.waitFor(By.xpath("//a[contains(@class,'kpi')][.//span[normalize-space()='" + label + "']]")));
    }

    public List<String> contractNumbers() {
        return browser.all(By.cssSelector("tbody td:first-child a")).stream().map(WebElement::getText).toList();
    }

    public void openFirstContract() {
        browser.click(browser.waitFor(By.cssSelector("tbody td:first-child a")));
        browser.waitFor(By.id("mileage"));
    }

    public String contractNumber() {
        return browser.waitFor(By.cssSelector("main h1")).getText().trim();
    }

    /** The last reading written in the form ("Le dernier relevé connu est de 86 269 km"). */
    public int lastMileage() {
        return Integer.parseInt(browser.waitFor(By.cssSelector("form.form")).findElement(By.xpath("preceding-sibling::p/strong"))
                .getText().replaceAll("\\D", ""));
    }

    public void recordReading(int mileage) {
        browser.waitFor(By.id("mileage")).sendKeys(String.valueOf(mileage));
        browser.click(browser.driver().findElement(By.xpath("//button[normalize-space()='Enregistrer le relevé']")));
    }

    public String message() {
        return browser.waitFor(By.cssSelector(".flash, .notice p")).getText().trim();
    }

    /** The line of the readings of the vehicle with this mileage (date, mileage and source), or an empty text. */
    public String readingLine(int mileage) {
        return browser.all(By.xpath("//h2[normalize-space()='Relevés du véhicule']/following::tbody/tr")).stream()
                .map(WebElement::getText)
                .filter(line -> line.replaceAll("[\\s\\u00a0\\u202f]", "").contains(mileage + "km"))
                .findFirst()
                .orElse("");
    }

    public String currentUrl() {
        return browser.driver().getCurrentUrl();
    }

    public void signOut() {
        browser.click(browser.waitFor(By.xpath("//button[normalize-space()='Se déconnecter']")));
        browser.waitUntil(driver -> driver.getCurrentUrl().contains("/signed-out"));
    }

    public String heading() {
        return browser.waitFor(By.cssSelector("main h1")).getText().trim();
    }
}
