package com.fleetpilot.driver.data.local;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(NotificationState state);

    @Query("select * from notification_state where id in (:ids)")
    List<NotificationState> find(List<String> ids);

    @Query("delete from notification_state")
    void clear();
}
