package com.fleetpilot.driver.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** The last answer of the server for a screen, so the app shows something without network. */
@Entity(tableName = "cache")
public class CachedJson {

    @PrimaryKey
    @NonNull
    public String key = "";

    @NonNull
    public String json = "";

    public long savedAt;
}
