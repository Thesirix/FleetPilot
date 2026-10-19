package com.fleetpilot.acceptance.pages;

import java.util.List;

import com.fleetpilot.acceptance.support.Browser;
import com.fleetpilot.acceptance.support.Platform;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/** Any page of the web portal (Nuxt): its title, its menu, its search field, its tables and its tabs. */
public class PortalPage {

    private static final By HEADING = By.cssSelector("main h1");
    private static final By ROWS = By.cssSelector("main table tbody tr");

    private final Browser browser;

    public PortalPage(Browser browser) {
        this.browser = browser;
    }

    public void open(String path) {
        browser.open(Platform.PORTAL + path);
    }

    /** Opens a page from the menu on the left, as a user does. */
    public void openFromMenu(String label) {
        browser.click(browser.waitForClickable(By.xpath("//nav//a[normalize-space()=" + literal(label) + "]")));
    }

    public String heading() {
        return browser.waitFor(HEADING).getText().trim();
    }

    public void waitForHeading(String title) {
        browser.waitUntil(driver -> driver.findElements(HEADING).stream().anyMatch(h -> h.getText().trim().equals(title)));
    }

    public void search(String text) {
        WebElement field = browser.waitFor(By.cssSelector("main input[type=search]"));
        field.clear();
        field.sendKeys(text);
    }

    public void select(String label, String option) {
        new Select(browser.waitFor(By.cssSelector("select[aria-label='" + label + "']"))).selectByVisibleText(option);
    }

    public boolean hasSelect(String label) {
        return !browser.all(By.cssSelector("select[aria-label='" + label + "']")).isEmpty();
    }

    /** The rows of the table once loaded (the skeleton rows of the loading state are not counted). */
    public List<WebElement> rows() {
        browser.waitUntil(driver -> driver.findElements(By.cssSelector("main table tbody tr .skeleton")).isEmpty()
                && !driver.findElements(ROWS).isEmpty());
        return browser.all(ROWS);
    }

    /** Waits until the first row contains the text (after a search, the table is refreshed). */
    public void waitForFirstRow(String text) {
        browser.waitUntil(driver -> {
            List<WebElement> rows = driver.findElements(ROWS);
            return !rows.isEmpty() && rows.get(0).getText().contains(text);
        });
    }

    public void openFirstRow() {
        String before = browser.driver().getCurrentUrl();
        // Clicked again only if the table was re-rendered under the first click (the page has not changed)
        browser.waitUntil(driver -> {
            if (!driver.getCurrentUrl().equals(before)) {
                return true;
            }
            List<WebElement> rows = driver.findElements(ROWS);
            if (!rows.isEmpty()) {
                browser.click(rows.get(0));
            }
            return false;
        });
    }

    public void openTab(String label) {
        browser.click(browser.waitForClickable(By.xpath("//*[@role='tab'][contains(normalize-space(), '" + label + "')]")));
    }

    public String text() {
        return browser.waitFor(By.cssSelector("main")).getText();
    }

    public void waitForText(String text) {
        browser.waitUntil(driver -> driver.findElement(By.cssSelector("main")).getText().contains(text));
    }

    public int count(String cssSelector) {
        return browser.all(By.cssSelector(cssSelector)).size();
    }

    /** Clicks the button or the link that shows this text. */
    public void click(String text) {
        String same = "normalize-space()=" + literal(text);
        browser.click(browser.waitForClickable(
                By.xpath("//main//*[self::button or self::a][" + same + " or .//text()[" + same + "]]")));
    }

    /** Types in the field whose label shows this text. */
    public void fill(String label, String value) {
        WebElement caption = browser.waitFor(By.xpath("//main//label[normalize-space()=" + literal(label) + "]"));
        WebElement field = browser.driver().findElement(By.id(caption.getAttribute("for")));
        field.clear();
        field.sendKeys(value);
    }

    /** Waits for a message of the portal (the toasts are outside the main area). */
    public void waitForMessage(String text) {
        browser.waitUntil(driver -> driver.findElement(By.tagName("body")).getText().contains(text));
    }

    public boolean menuOffers(String label) {
        return !browser.all(By.xpath("//nav//a[normalize-space()=" + literal(label) + "]")).isEmpty();
    }
    /** A text as an XPath literal: concat() when it holds an apostrophe, such as "Journal d'activité". */
    static String literal(String text) {
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        return "concat('" + text.replace("'", "', \"'\", '") + "')";
    }
}
