package com.fleetpilot.driver.util;

import androidx.annotation.Nullable;

/**
 * What a screen shows for data that comes from the server: the data itself, whether it is the last copy kept on the
 * phone (no network), and the failure when there is nothing to show.
 */
public final class Resource<T> {

    @Nullable
    public final T data;
    public final boolean loading;
    /** True when the data is the copy saved during the last visit: the network is unavailable. */
    public final boolean stale;
    @Nullable
    public final String error;

    private Resource(@Nullable T data, boolean loading, boolean stale, @Nullable String error) {
        this.data = data;
        this.loading = loading;
        this.stale = stale;
        this.error = error;
    }

    public static <T> Resource<T> loading(@Nullable T previous) {
        return new Resource<>(previous, true, false, null);
    }

    public static <T> Resource<T> fresh(T data) {
        return new Resource<>(data, false, false, null);
    }

    public static <T> Resource<T> stale(T data) {
        return new Resource<>(data, false, true, null);
    }

    public static <T> Resource<T> failed(String error) {
        return new Resource<>(null, false, false, error);
    }
}
