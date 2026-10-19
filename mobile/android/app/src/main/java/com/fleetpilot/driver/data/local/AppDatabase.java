package com.fleetpilot.driver.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/** The local database of the app (Room on SQLite): the outbox, the offline cache and the notifications state. */
@Database(entities = {OutboxItem.class, CachedJson.class, NotificationState.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract OutboxDao outbox();

    public abstract CacheDao cache();

    public abstract NotificationDao notifications();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "fleetpilot.db")
                            .build();
                }
            }
        }
        return instance;
    }
}
