package com.fleetpilot.driver.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * What the phone already did with a notification of the server: shown in the status bar (announced), then read in
 * the app (seen). The id of a notification changes when the situation gets worse, so an overdue deadline is announced
 * again even if it was seen when it was only due soon.
 */
@Entity(tableName = "notification_state")
public class NotificationState {

    @PrimaryKey
    @NonNull
    public String id = "";

    public boolean announced;

    public boolean seen;
}
