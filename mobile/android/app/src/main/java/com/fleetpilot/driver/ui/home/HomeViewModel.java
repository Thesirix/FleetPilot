package com.fleetpilot.driver.ui.home;

import java.util.List;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DriverNotification;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.api.ApiModels.MileageReport;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.ui.DriverViewModel;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Resource;

/** The home screen: the vehicle of the driver, what waits in the outbox, the latest notifications. */
public class HomeViewModel extends DriverViewModel {

    public HomeViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Resource<DriverSpace>> space() {
        return repository.space();
    }

    public LiveData<List<OutboxItem>> outbox() {
        return repository.outbox();
    }

    public LiveData<Resource<List<DriverNotification>>> notifications() {
        return repository.notifications();
    }

    public void refresh() {
        repository.refreshSpace();
        repository.refreshNotifications();
    }

    public void reportMileage(int mileage) {
        submit(item(OutboxItem.MILEAGE,
                getApplication().getString(R.string.outbox_mileage, Formats.km(mileage)), new MileageReport(mileage)));
    }

    public void retryOutbox() {
        repository.retryOutbox(report -> { });
    }

    public void discard(String id) {
        repository.discard(id);
    }
}
