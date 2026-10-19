package com.fleetpilot.driver.util;

import java.util.List;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

/**
 * The position of the phone, from the Android location service (no Google Play services needed): the last known
 * position when it is recent, a fresh fix otherwise.
 */
public final class Positions {

    private static final long RECENT_MS = 2 * 60 * 1000L;

    public interface Callback {
        void done(@Nullable Location location);
    }

    private Positions() {
    }

    public static boolean allowed(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public static void current(Context context, Callback callback) {
        if (!allowed(context)) {
            callback.done(null);
            return;
        }
        LocationManager manager = context.getSystemService(LocationManager.class);
        Location best = null;
        for (String provider : List.of(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!manager.isProviderEnabled(provider)) {
                continue;
            }
            Location last = manager.getLastKnownLocation(provider);
            if (last != null && (best == null || last.getTime() > best.getTime())) {
                best = last;
            }
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < RECENT_MS) {
            callback.done(best);
            return;
        }
        String provider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                : manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
        if (provider == null) {
            callback.done(best);
            return;
        }
        Location fallback = best;
        LocationManagerCompat.getCurrentLocation(manager, provider, new CancellationSignal(),
                ContextCompat.getMainExecutor(context), location -> callback.done(location != null ? location : fallback));
    }
}
