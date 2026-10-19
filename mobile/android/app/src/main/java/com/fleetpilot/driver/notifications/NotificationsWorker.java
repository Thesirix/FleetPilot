package com.fleetpilot.driver.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.data.local.NotificationDao;
import com.fleetpilot.driver.data.local.NotificationState;

/**
 * Asks the server every 15 minutes (the shortest period Android allows) what the driver should know, and shows what is
 * new in the status bar: an overdue deadline, a serious fault raised by the box of the vehicle, a claim that moves on.
 */
public class NotificationsWorker extends Worker {

    private static final String NAME = "notifications";
    /** The most serious three; the list in the app says the rest. */
    static final int MAX_AT_ONCE = 3;

    public NotificationsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(NotificationsWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME);
    }

    @NonNull
    @Override
    public Result doWork() {
        FleetPilotApp app = (FleetPilotApp) getApplicationContext();
        if (!app.auth().isSignedIn()) {
            return Result.success();
        }
        List<DriverNotification> list = app.repository().fetchNotifications();
        if (list == null) {
            return Result.retry();
        }
        NotificationDao dao = app.database().notifications();
        List<DriverNotification> fresh = toAnnounce(list, dao.find(list.stream().map(n -> n.id).collect(Collectors.toList())));
        Notifier.show(app, fresh);
        for (DriverNotification n : fresh) {
            NotificationState state = new NotificationState();
            state.id = n.id;
            state.announced = true;
            dao.save(state);
        }
        return Result.success();
    }

    /** The notifications never shown yet, most serious first (the server sorts them so), at most three. */
    static List<DriverNotification> toAnnounce(List<DriverNotification> list, List<NotificationState> known) {
        Map<String, NotificationState> byId = known.stream()
                .collect(Collectors.toMap(s -> s.id, Function.identity(), (a, b) -> a));
        List<DriverNotification> fresh = new ArrayList<>();
        for (DriverNotification n : list) {
            NotificationState state = byId.get(n.id);
            if ((state == null || !state.announced && !state.seen) && fresh.size() < MAX_AT_ONCE) {
                fresh.add(n);
            }
        }
        return fresh;
    }
}
