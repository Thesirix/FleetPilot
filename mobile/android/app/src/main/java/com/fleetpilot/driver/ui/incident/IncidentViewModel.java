package com.fleetpilot.driver.ui.incident;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.fleetpilot.driver.R;
import com.fleetpilot.driver.data.api.ApiModels.DeclareIncident;
import com.fleetpilot.driver.data.api.ApiModels.DriverSpace;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.fleetpilot.driver.ui.DriverViewModel;
import com.fleetpilot.driver.util.Formats;
import com.fleetpilot.driver.util.Labels;
import com.fleetpilot.driver.util.Resource;

/** A claim with its position and photos; the claims of the driver and their progress below. */
public class IncidentViewModel extends DriverViewModel {

    public static final List<String> TYPES = List.of("COLLISION", "GLASS_BREAKAGE", "MECHANICAL_FAILURE", "VANDALISM",
            "WEATHER", "THEFT", "FIRE");

    public IncidentViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Resource<DriverSpace>> space() {
        return repository.space();
    }

    public LiveData<List<OutboxItem>> outbox() {
        return repository.outbox();
    }

    public void refresh() {
        repository.refreshSpace();
    }

    public void declare(String type, Instant occurredAt, String location, @Nullable Double latitude,
            @Nullable Double longitude, String description, boolean thirdParty, boolean injuries, List<File> photos) {
        DeclareIncident declaration = new DeclareIncident();
        declaration.type = type;
        declaration.occurredAt = occurredAt.toString();
        declaration.location = location.trim();
        declaration.latitude = latitude;
        declaration.longitude = longitude;
        declaration.description = description.trim();
        declaration.thirdPartyInvolved = thirdParty;
        declaration.injuries = injuries;
        String summary = getApplication().getString(R.string.outbox_incident,
                Labels.label(getApplication(), "incident_type", type), Formats.date(declaration.occurredAt));
        OutboxItem item = item(OutboxItem.INCIDENT, summary, declaration);
        item.photoOwner = "incidents";
        item.photoPaths = String.join("\n", photos.stream().map(File::getPath).collect(Collectors.toList()));
        submit(item);
    }
}
