package com.fleetpilot.driver.ui;

import android.Manifest;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.databinding.ActivityMainBinding;
import com.fleetpilot.driver.notifications.NotificationsWorker;
import com.fleetpilot.driver.notifications.Notifier;
import com.fleetpilot.driver.ui.fuel.FuelFragment;
import com.fleetpilot.driver.ui.garage.GaragesFragment;
import com.fleetpilot.driver.ui.home.HomeFragment;
import com.fleetpilot.driver.ui.incident.IncidentFragment;
import com.fleetpilot.driver.ui.inspection.InspectionFragment;
import com.fleetpilot.driver.ui.login.LoginActivity;
import com.fleetpilot.driver.ui.notifications.NotificationsFragment;
import com.fleetpilot.driver.ui.profile.ProfileFragment;
import com.fleetpilot.driver.ui.vehicle.VehicleFragment;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;

/**
 * The app once signed in: five tabs at the bottom (home, fill-up, inspection, claim, garages), the notifications and
 * the profile in the top bar, and a banner while the phone is offline.
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_SCREEN = "screen";
    public static final String SCREEN_NOTIFICATIONS = "notifications";

    private ActivityMainBinding binding;
    private BadgeDrawable badge;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FleetPilotApp app = (FleetPilotApp) getApplication();
        if (!app.auth().isSignedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_notifications) {
                open(new NotificationsFragment());
                return true;
            }
            if (item.getItemId() == R.id.action_profile) {
                open(new ProfileFragment());
                return true;
            }
            return false;
        });
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.tabs.setOnItemSelectedListener(item -> {
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            show(tabFragment(item.getItemId()));
            return true;
        });
        binding.tabs.setOnItemReselectedListener(item ->
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE));
        // The back arrow follows the screen shown: none on the five tabs, one on the screens opened above them
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentResumed(@NonNull FragmentManager manager, @NonNull Fragment fragment) {
                updateUpButton(fragment);
            }
        }, false);

        if (savedInstanceState == null) {
            show(new HomeFragment());
            if (SCREEN_NOTIFICATIONS.equals(getIntent().getStringExtra(EXTRA_SCREEN))) {
                open(new NotificationsFragment());
            }
        }
        attachBadge();
        app.repository().unread().observe(this, count -> {
            badge.setVisible(count != null && count > 0);
            badge.setNumber(count == null ? 0 : count);
        });
        app.repository().refreshNotifications();
        askNotificationPermission();
        watchNetwork();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (SCREEN_NOTIFICATIONS.equals(intent.getStringExtra(EXTRA_SCREEN))) {
            open(new NotificationsFragment());
        }
    }

    @Override
    protected void onDestroy() {
        if (networkCallback != null) {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        }
        super.onDestroy();
    }

    /** Switches to one of the five tabs (from the shortcuts of the home screen). */
    public void selectTab(int tabId) {
        binding.tabs.setSelectedItemId(tabId);
    }

    /** Opens a screen above the current tab; back returns to the tab. */
    public void open(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in,
                        android.R.anim.fade_out)
                .replace(R.id.content, fragment)
                .addToBackStack(fragment.getClass().getSimpleName())
                .commit();
    }

    public void openVehicle() {
        open(new VehicleFragment());
    }

    /** Signs out: tokens, cache and background work are forgotten, then the Keycloak session is closed too. */
    public void logout() {
        FleetPilotApp app = (FleetPilotApp) getApplication();
        NotificationsWorker.cancel(this);
        app.repository().clear();
        Intent endSession = app.auth().logout();
        if (endSession != null) {
            startActivity(endSession);
        }
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void show(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content, fragment)
                .commit();
    }

    private static Fragment tabFragment(int id) {
        if (id == R.id.tab_fuel) {
            return new FuelFragment();
        }
        if (id == R.id.tab_inspection) {
            return new InspectionFragment();
        }
        if (id == R.id.tab_incident) {
            return new IncidentFragment();
        }
        if (id == R.id.tab_garages) {
            return new GaragesFragment();
        }
        return new HomeFragment();
    }

    private void updateUpButton(Fragment shown) {
        boolean nested = shown instanceof NotificationsFragment || shown instanceof ProfileFragment
                || shown instanceof VehicleFragment;
        if (nested) {
            binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        } else {
            binding.toolbar.setNavigationIcon((android.graphics.drawable.Drawable) null);
        }
        binding.toolbar.setNavigationContentDescription(nested ? getString(R.string.back) : null);
    }

    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    private void attachBadge() {
        badge = BadgeDrawable.create(this);
        badge.setBackgroundColor(getColor(R.color.danger));
        badge.setVisible(false);
        BadgeUtils.attachBadgeDrawable(badge, binding.toolbar, R.id.action_notifications);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.allowed(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Shows the offline banner while there is no network; when it comes back, the outbox leaves on its own. */
    private void watchNetwork() {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        boolean online = manager.getActiveNetwork() != null;
        binding.offline.setVisibility(online ? View.GONE : View.VISIBLE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> binding.offline.setVisibility(View.GONE));
                ((FleetPilotApp) getApplication()).repository().retryOutbox(report -> { });
            }

            @Override
            public void onLost(@NonNull Network network) {
                // The default network is gone; another one becoming the default calls onAvailable again
                runOnUiThread(() -> binding.offline.setVisibility(View.VISIBLE));
            }
        };
        manager.registerDefaultNetworkCallback(networkCallback);
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, bars.right,
                    bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
