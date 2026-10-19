package com.fleetpilot.driver.data.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Something the driver entered, kept on the phone until the server has it: a mileage reading, a fill-up, an
 * inspection or a claim, with its photos (JPEG files in the private storage of the app). The id is generated on the
 * phone and is the id of the record on the server, so sending it twice stores it once.
 */
@Entity(tableName = "outbox")
public class OutboxItem {

    public static final String MILEAGE = "MILEAGE";
    public static final String FUEL = "FUEL";
    public static final String INSPECTION = "INSPECTION";
    public static final String INCIDENT = "INCIDENT";

    @PrimaryKey
    @NonNull
    public String id = "";

    @NonNull
    public String kind = "";

    /** What the driver sees in the list of waiting items. */
    @NonNull
    public String summary = "";

    /** The body of the request, in JSON. */
    @NonNull
    public String payload = "";

    /** "incidents" or "inspections" for a record with photos. */
    @Nullable
    public String photoOwner;

    /** Paths of the photo files, one per line; the file name (without .jpg) is the id of the photo. */
    @NonNull
    public String photoPaths = "";

    @NonNull
    public String photosSent = "";

    public boolean recordSent;

    public long createdAt;

    public int attempts;

    /** Set when the server refused the item: it will not go through as it is. */
    @Nullable
    public String errorCode;

    @Nullable
    public String errorMessage;

    public List<String> photos() {
        return split(photoPaths);
    }

    public List<String> sentPhotos() {
        return split(photosSent);
    }

    public void markPhotoSent(String photoId) {
        List<String> sent = new ArrayList<>(sentPhotos());
        sent.add(photoId);
        photosSent = String.join("\n", sent);
    }

    public boolean isRejected() {
        return errorCode != null;
    }

    private static List<String> split(String value) {
        return value.isEmpty() ? List.of() : Arrays.asList(value.split("\n"));
    }
}
