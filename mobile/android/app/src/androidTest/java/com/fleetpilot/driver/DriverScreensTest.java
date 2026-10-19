package com.fleetpilot.driver;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
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

import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.fleetpilot.driver.ui.fuel.FuelFragment;
import com.fleetpilot.driver.ui.inspection.InspectionFragment;
import com.fleetpilot.driver.ui.login.LoginActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The screens of the driver app on the emulator, without the platform: the forms check what the driver types before
 * anything is sent.
 */
@RunWith(AndroidJUnit4.class)
public class DriverScreensTest {

    @Test
    public void a_signed_out_driver_is_asked_to_sign_in_with_the_company_account() {
        FleetPilotApp app = ApplicationProvider.getApplicationContext();
        app.auth().logout();
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.login)).check(matches(allOf(isDisplayed(), isEnabled(), withText("Se connecter"))));
            onView(withId(R.id.server)).check(matches(withText(containsString("localhost:8180"))));
        }
    }

    @Test
    public void a_damaged_point_of_the_inspection_needs_a_comment_before_saving() {
        FragmentScenario<InspectionFragment> scenario = FragmentScenario.launchInContainer(InspectionFragment.class,
                null, R.style.Theme_FleetPilot, Lifecycle.State.RESUMED);
        onView(withId(R.id.odometer)).perform(scrollTo(), replaceText("33600"), closeSoftKeyboard());
        onView(withId(R.id.save)).perform(scrollTo()).check(matches(isEnabled()));

        onView(allOf(withId(R.id.damaged), isDescendantOfA(withTagValue(is("TYRES"))))).perform(scrollTo(), click());
        onView(withId(R.id.defects)).check(matches(withText("1 défaut")));
        onView(withId(R.id.missing_comment)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.save)).check(matches(not(isEnabled())));

        onView(allOf(withId(R.id.comment), isDescendantOfA(withTagValue(is("TYRES")))))
                .perform(scrollTo(), replaceText("Pneu avant droit usé"), closeSoftKeyboard());
        onView(withId(R.id.save)).perform(scrollTo()).check(matches(isEnabled()));
        scenario.close();
    }

    @Test
    public void a_typo_in_the_quantity_of_a_fill_up_is_caught_at_once() {
        FragmentScenario<FuelFragment> scenario = FragmentScenario.launchInContainer(FuelFragment.class, null,
                R.style.Theme_FleetPilot, Lifecycle.State.RESUMED);
        onView(withId(R.id.odometer)).perform(scrollTo(), replaceText("33600"), closeSoftKeyboard());
        onView(withId(R.id.quantity)).perform(scrollTo(), replaceText("4"), closeSoftKeyboard());
        onView(withId(R.id.amount)).perform(scrollTo(), replaceText("80"), closeSoftKeyboard());
        onView(withId(R.id.unit_price)).check(matches(withText(containsString("vérifiez la quantité"))));
        onView(withId(R.id.save)).check(matches(not(isEnabled())));

        onView(withId(R.id.quantity)).perform(scrollTo(), replaceText("43"), closeSoftKeyboard());
        onView(withId(R.id.unit_price)).check(matches(withText("Soit 1,860 € par L")));
        onView(withId(R.id.save)).perform(scrollTo()).check(matches(isEnabled()));
        scenario.close();
    }
}
