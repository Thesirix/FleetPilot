package com.fleetpilot.driver.data.repository;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.fleetpilot.driver.data.api.ApiClient;
import com.fleetpilot.driver.data.api.ApiException;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.api.ApiModels.FuelEntry;
import com.fleetpilot.driver.data.api.ApiModels.Inspection;
import com.fleetpilot.driver.data.api.ApiModels.NearbyGarage;
import com.fleetpilot.driver.data.local.AppDatabase;
import com.fleetpilot.driver.data.local.CachedJson;
import com.fleetpilot.driver.data.local.NotificationState;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.sync.ApiTransport;
import com.fleetpilot.driver.sync.OutboxSender;
import com.fleetpilot.driver.sync.SyncWorker;
import com.fleetpilot.driver.util.Resource;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import retrofit2.Call;

/**
 * The single source of data of the screens. Reads go to the server and keep a copy of the answer in Room, shown when
 * the network is down; writes go through the outbox, so nothing the driver enters is ever lost.
 */
public final class DriverRepository {

    /** Outcome of something the driver submitted. */
    public enum Outcome { SENT, QUEUED, REJECTED }

    public record Submission(Outcome outcome, @Nullable String code, @Nullable String message) {
    }

    public interface Callback<T> {
        void done(T result);
    }

    private final Context context;
    private final ApiClient client;
    private final AppDatabase db;
    private final Gson gson = new Gson();
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    /** Sends one batch at a time: two runs in parallel would only send the same records twice. */
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Resource<DriverSpace>> space = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<FuelEntry>>> fuelEntries = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Inspection>>> inspections = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<DriverNotification>>> notifications = new MutableLiveData<>();
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);

    public DriverRepository(Context context, ApiClient client, AppDatabase db) {
        this.context = context.getApplicationContext();
        this.client = client;
        this.db = db;
    }

    // ---------------------------------------------------------------- reads

    public LiveData<Resource<DriverSpace>> space() {
        return space;
    }

    public void refreshSpace() {
        load("space", client.api().me(), DriverSpace.class, space);
    }

    public LiveData<Resource<List<FuelEntry>>> fuelEntries() {
        return fuelEntries;
    }

    public void refreshFuelEntries() {
        load("fuel-entries", client.api().fuelEntries(), new TypeToken<List<FuelEntry>>() { }.getType(), fuelEntries);
    }

    public LiveData<Resource<List<Inspection>>> inspections() {
        return inspections;
    }

    public void refreshInspections() {
        load("inspections", client.api().inspections(), new TypeToken<List<Inspection>>() { }.getType(), inspections);
    }

    public LiveData<Resource<List<DriverNotification>>> notifications() {
        return notifications;
    }

    /** Number of notifications the driver has not opened yet, for the badge of the bell. */
    public LiveData<Integer> unread() {
        return unread;
    }

    public void refreshNotifications() {
        io.execute(() -> {
            List<DriverNotification> list = fetchNotifications();
            if (list != null) {
                notifications.postValue(Resource.fresh(list));
                unread.postValue(countUnread(list));
            } else {
                List<DriverNotification> cached = cached("notifications",
                        new TypeToken<List<DriverNotification>>() { }.getType());
                notifications.postValue(cached != null ? Resource.stale(cached) : Resource.failed("NETWORK_ERROR"));
            }
        });
    }

    /** Blocking, for the background worker: the notifications, or null without network. */
    @Nullable
    public List<DriverNotification> fetchNotifications() {
        try {
            List<DriverNotification> list = client.execute(client.api().notifications());
            save("notifications", list);
            return list;
        } catch (ApiException e) {
            return null;
        }
    }

    public void markNotificationsSeen(List<DriverNotification> list) {
        io.execute(() -> {
            for (DriverNotification n : list) {
                NotificationState state = new NotificationState();
                state.id = n.id;
                state.seen = true;
                state.announced = true;
                db.notifications().save(state);
            }
            unread.postValue(0);
        });
    }

    private int countUnread(List<DriverNotification> list) {
        List<String> ids = new ArrayList<>();
        list.forEach(n -> ids.add(n.id));
        long seen = db.notifications().find(ids).stream().filter(s -> s.seen).count();
        return (int) (list.size() - seen);
    }

    public void nearbyGarages(double latitude, double longitude, int radiusKm,
            Callback<Resource<List<NearbyGarage>>> callback) {
        io.execute(() -> {
            try {
                callback.done(Resource.fresh(client.execute(client.api().nearbyGarages(latitude, longitude, radiusKm, 20))));
            } catch (ApiException e) {
                callback.done(Resource.failed(e.code()));
            }
        });
    }

    // ---------------------------------------------------------------- writes

    public LiveData<List<OutboxItem>> outbox() {
        return db.outbox().watchAll();
    }

    /**
     * Keeps the item on the phone, then tries to send it at once. Refused at once (an implausible price...), it is
     * taken back out of the outbox: the driver corrects the form. Without network, the background worker sends it.
     */
    public void submit(OutboxItem item, Callback<Submission> callback) {
        io.execute(() -> {
            db.outbox().save(item);
            sender.execute(() -> {
                flushNow();
                OutboxItem left = db.outbox().find(item.id);
                Submission result;
                if (left == null) {
                    result = new Submission(Outcome.SENT, null, null);
                } else if (left.isRejected() && !left.recordSent) {
                    db.outbox().delete(left.id);
                    result = new Submission(Outcome.REJECTED, left.errorCode, left.errorMessage);
                } else {
                    SyncWorker.schedule(context);
                    result = new Submission(Outcome.QUEUED, null, null);
                }
                callback.done(result);
            });
        });
    }

    /** Sends what waits now (the driver tapped "retry"). */
    public void retryOutbox(Callback<OutboxSender.Report> callback) {
        sender.execute(() -> callback.done(flushNow()));
    }

    public void discard(String id) {
        io.execute(() -> db.outbox().delete(id));
    }

    private OutboxSender.Report flushNow() {
        OutboxSender.Report report = new OutboxSender(new OutboxSender.Store() {
            @Override
            public List<OutboxItem> pending() {
                return db.outbox().pending();
            }

            @Override
            public void save(OutboxItem item) {
                db.outbox().save(item);
            }

            @Override
            public void delete(String id) {
                db.outbox().delete(id);
            }
        }, new ApiTransport(client)).flush();
        if (report.sent() > 0) {
            refreshAfterSync();
        }
        return report;
    }

    /** The server has new records: the screens read them again. */
    public void refreshAfterSync() {
        refreshSpace();
        refreshFuelEntries();
        refreshInspections();
    }

    /** Signing out forgets everything the phone kept about the driver. */
    public void clear() {
        io.execute(() -> {
            db.cache().clear();
            db.notifications().clear();
        });
    }

    // ---------------------------------------------------------------- cache

    private <T> void load(String key, Call<T> call, Type type, MutableLiveData<Resource<T>> target) {
        Resource<T> current = target.getValue();
        target.postValue(Resource.loading(current == null ? null : current.data));
        io.execute(() -> {
            try {
                T data = client.execute(call);
                save(key, data);
                target.postValue(Resource.fresh(data));
            } catch (ApiException e) {
                T cached = cached(key, type);
                if (cached != null && e.isTransient()) {
                    target.postValue(Resource.stale(cached));
                } else {
                    target.postValue(Resource.failed(e.code()));
                }
            }
        });
    }

    private void save(String key, Object data) {
        CachedJson value = new CachedJson();
        value.key = key;
        value.json = gson.toJson(data);
        value.savedAt = System.currentTimeMillis();
        db.cache().save(value);
    }

    @Nullable
    private <T> T cached(String key, Type type) {
        CachedJson value = db.cache().find(key);
        return value == null ? null : gson.fromJson(value.json, type);
    }
}
