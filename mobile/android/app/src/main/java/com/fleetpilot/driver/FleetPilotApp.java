package com.fleetpilot.driver;

import android.app.Application;
import com.fleetpilot.driver.auth.AuthManager;
import com.fleetpilot.driver.data.api.ApiClient;
import com.fleetpilot.driver.data.local.AppDatabase;
import com.fleetpilot.driver.data.repository.DriverRepository;
import com.fleetpilot.driver.notifications.NotificationsWorker;
import com.fleetpilot.driver.notifications.Notifier;
import com.fleetpilot.driver.sync.SyncWorker;
import org.osmdroid.config.Configuration;

/** Builds the few shared objects of the app once (a hand-written service locator: no DI framework needed). */
public class FleetPilotApp extends Application {

    private AuthManager auth;
    private ApiClient apiClient;
    private DriverRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        auth = new AuthManager(this);
        apiClient = new ApiClient(auth);
        repository = new DriverRepository(this, apiClient, database());
        Notifier.createChannels(this);
        // The OpenStreetMap tile servers ask every app to identify itself
        Configuration.getInstance().setUserAgentValue(getPackageName() + "/" + BuildConfig.VERSION_NAME);
        if (auth.isSignedIn()) {
            SyncWorker.schedule(this);
            NotificationsWorker.schedule(this);
        }
    }

    public AuthManager auth() {
        return auth;
    }

    public ApiClient apiClient() {
        return apiClient;
    }

    public AppDatabase database() {
        return AppDatabase.get(this);
    }

    public DriverRepository repository() {
        return repository;
    }
}
