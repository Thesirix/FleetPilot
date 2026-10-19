package com.fleetpilot.driver.data.local;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(OutboxItem item);

    @Query("select * from outbox where id = :id")
    OutboxItem find(String id);

    /** Waiting items, oldest first: they are sent in the order they were entered. */
    @Query("select * from outbox where errorCode is null order by createdAt")
    List<OutboxItem> pending();

    @Query("select * from outbox order by createdAt")
    LiveData<List<OutboxItem>> watchAll();

    @Query("delete from outbox where id = :id")
    void delete(String id);
}
