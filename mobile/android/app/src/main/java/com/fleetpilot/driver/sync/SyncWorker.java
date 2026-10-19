package com.fleetpilot.driver.sync;

import java.util.List;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.data.local.OutboxDao;
import com.fleetpilot.driver.data.local.OutboxItem;

/**
 * Sends the outbox in the background as soon as there is a network, even when the app is closed: WorkManager waits
 * for the connection, runs the worker, and runs it again with a growing delay while the server is unreachable.
 */
public class SyncWorker extends Worker {

    private static final String NAME = "outbox";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Asks for a run when the network is available. */
    public static void schedule(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        FleetPilotApp app = (FleetPilotApp) getApplicationContext();
        OutboxDao dao = app.database().outbox();
        OutboxSender sender = new OutboxSender(new OutboxSender.Store() {
            @Override
            public List<OutboxItem> pending() {
                return dao.pending();
            }

            @Override
            public void save(OutboxItem item) {
                dao.save(item);
            }

            @Override
            public void delete(String id) {
                dao.delete(id);
            }
        }, new ApiTransport(app.apiClient()));
        OutboxSender.Report report = sender.flush();
        if (report.sent() > 0) {
            app.repository().refreshAfterSync();
        }
        return report.interrupted() ? Result.retry() : Result.success();
    }
}
