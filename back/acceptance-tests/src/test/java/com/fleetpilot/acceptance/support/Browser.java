package com.fleetpilot.acceptance.support;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * The browser of a scenario: Chrome (or Edge with -Dbrowser=edge), headless unless -Dheadless=false, in French. Selenium
 * Manager finds or downloads the driver that matches the installed browser. Created at the first use, closed after the
 * scenario by {@link Hooks}.
 */
public class Browser {

    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    private WebDriver driver;

    public WebDriver driver() {
        if (driver == null) {
            driver = start();
        }
        return driver;
    }

    public boolean started() {
        return driver != null;
    }

    /** A phone-sized window, for the driver space (the PWA of the drivers). */
    public void phone() {
        driver().manage().window().setSize(new Dimension(412, 915));
    }

    public void open(String url) {
        driver().get(url);
    }

    public WebElement waitFor(By locator) {
        return new WebDriverWait(driver(), TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    public WebElement waitForClickable(By locator) {
        return new WebDriverWait(driver(), TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator));
    }

    public <T> T waitUntil(Function<WebDriver, T> condition) {
        return waitUntil(condition, TIMEOUT);
    }

    /** Waits, trying again when the page re-renders the element being read (Vue replaces the rows of a table). */
    public <T> T waitUntil(Function<WebDriver, T> condition, Duration timeout) {
        return new WebDriverWait(driver(), timeout)
                .ignoring(StaleElementReferenceException.class)
                .until(condition::apply);
    }

    public List<WebElement> all(By locator) {
        return driver().findElements(locator);
    }

    /** Clicks through JavaScript: a sticky bar may cover the element at its scrolled position. */
    public void click(WebElement element) {
        ((JavascriptExecutor) driver()).executeScript("arguments[0].scrollIntoView({block: 'center'}); arguments[0].click();",
                element);
    }

    public String currentPath() {
        return java.net.URI.create(driver().getCurrentUrl()).getPath();
    }

    public byte[] screenshot() {
        return ((TakesScreenshot) driver()).getScreenshotAs(OutputType.BYTES);
    }

    public void quit() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    private static WebDriver start() {
        boolean headless = !"false".equals(System.getProperty("headless"));
        String language = "--lang=fr-FR";
        WebDriver created;
        if ("edge".equalsIgnoreCase(System.getProperty("browser"))) {
            EdgeOptions options = new EdgeOptions();
            options.addArguments(language, "--window-size=1440,900");
            if (headless) {
                options.addArguments("--headless=new");
            }
            created = new EdgeDriver(options);
        } else {
            ChromeOptions options = new ChromeOptions();
            options.addArguments(language, "--window-size=1440,900");
            options.setExperimentalOption("prefs", java.util.Map.of("intl.accept_languages", "fr-FR,fr"));
            if (headless) {
                options.addArguments("--headless=new");
            }
            created = new ChromeDriver(options);
        }
        created.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        return created;
    }
}
