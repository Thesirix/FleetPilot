package com.fleetpilot.driver.sync;

import java.io.File;

import com.fleetpilot.driver.data.api.ApiClient;
import com.fleetpilot.driver.data.api.ApiException;
import com.fleetpilot.driver.data.api.ApiModels;
import com.fleetpilot.driver.data.local.OutboxItem;
import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.RequestBody;

/** Sends the items of the outbox to the REST API: each kind to its idempotent endpoint. */
public final class ApiTransport implements OutboxSender.Transport {

    private static final MediaType JPEG = MediaType.get("image/jpeg");

    private final ApiClient client;
    private final Gson gson = new Gson();

    public ApiTransport(ApiClient client) {
        this.client = client;
    }

    @Override
    public void sendRecord(OutboxItem item) throws ApiException {
        switch (item.kind) {
            case OutboxItem.MILEAGE -> client.execute(client.api().reportMileage(
                    gson.fromJson(item.payload, ApiModels.MileageReport.class)));
            case OutboxItem.FUEL -> client.execute(client.api().recordFuel(item.id,
                    gson.fromJson(item.payload, ApiModels.RecordFuel.class)));
            case OutboxItem.INSPECTION -> client.execute(client.api().recordInspection(item.id,
                    gson.fromJson(item.payload, ApiModels.RecordInspection.class)));
            case OutboxItem.INCIDENT -> client.execute(client.api().declareIncident(item.id,
                    gson.fromJson(item.payload, ApiModels.DeclareIncident.class)));
            default -> throw new IllegalStateException("Unknown outbox item " + item.kind);
        }
    }

    @Override
    public void sendPhoto(OutboxItem item, String photoId, File file) throws ApiException {
        client.execute(client.api().addPhoto(item.photoOwner, item.id, photoId, RequestBody.create(file, JPEG)));
    }
}
