package com.fleetpilot.driver.ui;

import java.util.UUID;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.fleetpilot.driver.FleetPilotApp;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.data.repository.DriverRepository;
import com.fleetpilot.driver.data.repository.DriverRepository.Submission;
import com.google.gson.Gson;

/** Shared by the view models of the screens: the repository, and the submission of what the driver entered. */
public abstract class DriverViewModel extends AndroidViewModel {

    protected final DriverRepository repository;
    protected final Gson gson = new Gson();
    private final MutableLiveData<Event<Submission>> submitted = new MutableLiveData<>();
    private final MutableLiveData<Boolean> sending = new MutableLiveData<>(false);

    protected DriverViewModel(@NonNull Application application) {
        super(application);
        repository = ((FleetPilotApp) application).repository();
    }

    public LiveData<Event<Submission>> submitted() {
        return submitted;
    }

    public LiveData<Boolean> sending() {
        return sending;
    }

    /** Builds an outbox item with a new id; the id is also the id of the record on the server. */
    protected OutboxItem item(String kind, String summary, Object payload) {
        OutboxItem item = new OutboxItem();
        item.id = UUID.randomUUID().toString();
        item.kind = kind;
        item.summary = summary;
        item.payload = gson.toJson(payload);
        item.createdAt = System.currentTimeMillis();
        return item;
    }

    protected void submit(OutboxItem item) {
        sending.setValue(true);
        repository.submit(item, result -> {
            sending.postValue(false);
            submitted.postValue(new Event<>(result));
        });
    }
}
