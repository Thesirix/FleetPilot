package com.fleetpilot.driver.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(CachedJson value);

    @Query("select * from cache where `key` = :key")
    CachedJson find(String key);

    @Query("delete from cache")
    void clear();
}
