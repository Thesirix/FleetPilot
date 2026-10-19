package com.fleetpilot.driver;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import android.Manifest;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import com.fleetpilot.driver.ui.login.LoginActivity;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The whole day of a driver against the running platform (docker compose, adb reverse of ports 80 and 8180): sign-in on
 * the real Keycloak page, then every screen and every form sent to the API, and a refusal of the server shown in French.
 * Jean Dupont drives GR-832-SJ in Marseille. What it records carries the markers of tools/scripts/clean-test-data.sh
 * (reading 37 km above the previous one, station "Station test ...", inspection comment and claim description of the
 * end-to-end suites), so the demo data is restored after the run.
 *
 * <p>Left out of the default run (see app/build.gradle.kts), run with {@code ./gradlew connectedDebugAndroidTest -Pplatform}.
 */
@RunWith(AndroidJUnit4.class)
public class PlatformJourneyTest {

    private static final String USER = "jean.dupont@neolia-energies.fr";
    private static final String PASSWORD = "FleetPilot2026!";
    private static final long TIMEOUT = 30_000;

    @Rule
    public final GrantPermissionRule permissions = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS);

    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Test
    public void a_driver_signs_in_and_goes_through_every_screen_and_form() {
        FleetPilotApp app = ApplicationProvider.getApplicationContext();
        app.auth().logout();
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            signIn();

            // Home: greeting, the vehicle and its mileage
            waitFor(withText("Bonjour Jean"));
            waitFor(allOf(withId(R.id.plate), withText("GR-832-SJ")));
            int mileage = digits(text(withId(R.id.mileage)));

            // Mileage lower than the last reading: refused by the server, the reason in French
            reportMileage(mileage - 100);
            confirmed("Le kilométrage ne peut pas être inférieur au dernier relevé.");
            int typed = mileage + 37;
            reportMileage(typed);
            confirmed("Kilométrage enregistré");
            waitFor(allOf(withId(R.id.mileage), withText(containsString(group(typed)))));

            // Vehicle file: contract and services of the lessor
            onView(withId(R.id.open_vehicle)).perform(scrollTo(), click());
            waitFor(withText("Fiche du véhicule"));
            waitFor(withText(containsString("Berlingo")));
            pressBack();

            // Fill-up: diesel, fuel card
            onView(withId(R.id.tab_fuel)).perform(click());
            waitFor(allOf(withId(R.id.vehicle_line), withText(containsString("GR-832-SJ"))));
            onView(withId(R.id.odometer)).perform(scrollTo(), replaceText(String.valueOf(typed)), closeSoftKeyboard());
            onView(withId(R.id.quantity)).perform(scrollTo(), replaceText("43"), closeSoftKeyboard());
            onView(withId(R.id.amount)).perform(scrollTo(), replaceText("80"), closeSoftKeyboard());
            onView(withId(R.id.station)).perform(scrollTo(), replaceText("Station test Android, Marseille"),
                    closeSoftKeyboard());
            onView(withId(R.id.save)).perform(scrollTo(), click());
            confirmed("Plein enregistré");
            waitFor(allOf(withText(containsString("Station test Android")), withText(containsString(group(typed))),
                    withText(containsString(String.valueOf(typed / 1000)))));

            // Inspection: one damaged point with its comment
            onView(withId(R.id.tab_inspection)).perform(click());
            onView(withId(R.id.odometer)).perform(scrollTo(), replaceText(String.valueOf(typed)), closeSoftKeyboard());
            onView(allOf(withId(R.id.damaged), isDescendantOfA(withTagValue(is("TYRES"))))).perform(scrollTo(), click());
            onView(allOf(withId(R.id.comment), isDescendantOfA(withTagValue(is("TYRES")))))
                    .perform(scrollTo(), replaceText("Pneu avant droit usé sur le flanc"), closeSoftKeyboard());
            onView(withId(R.id.save)).perform(scrollTo()).check(matches(isEnabled())).perform(click());
            confirmed("État des lieux enregistré");

            // Claim: where and what, then followed on the home screen
            onView(withId(R.id.tab_incident)).perform(click());
            onView(withId(R.id.where)).perform(scrollTo(), replaceText("Parking du Vieux-Port, Marseille"));
            onView(withId(R.id.what)).perform(scrollTo(), replaceText("Rétroviseur arraché sur le parking, test Android"));
            onView(withId(R.id.send)).perform(scrollTo()).check(matches(isEnabled())).perform(click());
            confirmed("Sinistre déclaré");

            // Approved garages around the vehicle, on the map and in the list
            onView(withId(R.id.tab_garages)).perform(click());
            waitFor(allOf(withId(R.id.status), withText(containsString("agréé"))));

            // Notifications and profile, from the toolbar
            onView(withId(R.id.action_notifications)).perform(click());
            waitFor(withText("Échéances de votre véhicule, alertes de son boîtier et suivi de vos sinistres."));
            pressBack();
            onView(withId(R.id.action_profile)).perform(click());
            waitFor(withText(USER));
            waitFor(withText("Se déconnecter"));
            pressBack();

            // Back home: everything reached the server, nothing waits on the phone (a refusal seen at once is not kept)
            onView(withId(R.id.tab_home)).perform(click());
            waitFor(withText("Bonjour Jean"));
            waitHidden(withId(R.id.outbox_card));
        }
    }

    /** The Keycloak page in the Custom Tab: typed in when no session is open yet. */
    private void signIn() {
        onView(withId(R.id.login)).perform(click());
        BySelector username = By.res("username");
        if (device.wait(Until.hasObject(username), TIMEOUT)) {
            device.findObject(username).setText(USER);
            device.findObject(By.res("password")).setText(PASSWORD);
            UiObject2 submit = device.findObject(By.res("kc-login"));
            if (submit == null) {
                submit = device.findObject(By.text("Sign In"));
            }
            submit.click();
        } else {
            skipChromeWelcome();
            if (device.wait(Until.hasObject(username), TIMEOUT)) {
                signIn();
                return;
            }
        }
    }

    /** First start of Chrome on a fresh emulator: its welcome screens come before the page. */
    private void skipChromeWelcome() {
        for (String label : List.of("Use without an account", "Accept & continue", "No thanks", "Continue")) {
            UiObject2 button = device.findObject(By.text(label));
            if (button != null) {
                button.click();
                device.waitForIdle();
            }
        }
    }

    private void reportMileage(int mileage) {
        onView(withId(R.id.report_mileage)).perform(scrollTo(), click());
        onView(withId(R.id.mileage_input)).perform(replaceText(String.valueOf(mileage)), closeSoftKeyboard());
        onView(withText("Enregistrer")).perform(click());
    }

    /** Waits for a view: the app talks to the API on its own threads, which Espresso does not follow. */
    private static void waitFor(Matcher<View> matcher) {
        long end = System.currentTimeMillis() + TIMEOUT;
        while (true) {
            try {
                onView(matcher).check(matches(isDisplayed()));
                return;
            } catch (NoMatchingViewException | AssertionError e) {
                if (System.currentTimeMillis() > end) {
                    throw e;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            } catch (RuntimeException e) {
                // several views match while a screen replaces another
                if (System.currentTimeMillis() > end) {
                    throw e;
                }
            }
        }
    }

    /** The message of the server is shown, then goes away before the next tap (it lies over the bottom of the form). */
    private static void confirmed(String message) {
        waitFor(withText(message));
        long end = System.currentTimeMillis() + TIMEOUT;
        while (true) {
            try {
                onView(withText(message)).check(doesNotExist());
                return;
            } catch (AssertionError e) {
                if (System.currentTimeMillis() > end) {
                    throw e;
                }
                SystemClock.sleep(250);
            }
        }
    }

    private static void waitHidden(Matcher<View> matcher) {
        long end = System.currentTimeMillis() + TIMEOUT;
        while (true) {
            try {
                onView(matcher).check(matches(not(isDisplayed())));
                return;
            } catch (AssertionError e) {
                if (System.currentTimeMillis() > end) {
                    throw e;
                }
                SystemClock.sleep(250);
            }
        }
    }

    private static String text(Matcher<View> matcher) {
        waitFor(matcher);
        AtomicReference<String> text = new AtomicReference<>();
        onView(matcher).check((view, error) -> text.set(((TextView) view).getText().toString()));
        return text.get();
    }

    private static int digits(String text) {
        return Integer.parseInt(text.replaceAll("[^0-9]", ""));
    }

    /** The thousands of a mileage as the app writes them, whatever the space used ("33 959"). */
    private static String group(int mileage) {
        return String.valueOf(mileage % 1000 + 1000).substring(1);
    }
}
