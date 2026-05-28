package com.hmdm.launcher.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;

public class GeofencePolicy {
    private static final String PREF_GEOFENCE_OUTSIDE = "geofence_outside";
    private static final long MAX_LOCATION_AGE_MS = 10 * 60 * 1000;

    public static boolean isConfigured(ServerConfig config) {
        return config != null &&
                Boolean.TRUE.equals(config.getLocationSettingsEnabled()) &&
                config.getLocationLatitude() != null &&
                config.getLocationLongitude() != null &&
                config.getLocationRadius() != null &&
                config.getLocationRadius() > 0;
    }

    public static boolean isOutside(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE);
        return preferences.getBoolean(PREF_GEOFENCE_OUTSIDE, false);
    }

    public static void clearIfDisabled(Context context, ServerConfig config) {
        if (!isConfigured(config) && isOutside(context)) {
            setOutside(context, false);
        }
    }

    public static void evaluate(Context context, Location location) {
        ServerConfig config = SettingsHelper.getInstance(context).getConfig();
        if (!isConfigured(config) || location == null) {
            clearIfDisabled(context, config);
            return;
        }

        long locationAgeMs = System.currentTimeMillis() - location.getTime();
        if (locationAgeMs > MAX_LOCATION_AGE_MS) {
            RemoteLogger.log(context, Const.LOG_WARN, "Ignoring stale geofence location: age="
                    + locationAgeMs + "ms, lat=" + location.getLatitude()
                    + ", lon=" + location.getLongitude());
            setOutside(context, false);
            return;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                config.getLocationLatitude(),
                config.getLocationLongitude(),
                location.getLatitude(),
                location.getLongitude(),
                results
        );
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 0f;
        boolean outside = results[0] > config.getLocationRadius() + accuracy;
        RemoteLogger.log(context, Const.LOG_INFO, "Geofence check: targetLat="
                + config.getLocationLatitude() + ", targetLon=" + config.getLocationLongitude()
                + ", deviceLat=" + location.getLatitude() + ", deviceLon=" + location.getLongitude()
                + ", distance=" + results[0] + "m, radius=" + config.getLocationRadius()
                + "m, accuracy=" + (location.hasAccuracy() ? accuracy + "m" : "unknown")
                + ", age=" + locationAgeMs + "ms"
                + ", outside=" + outside);
        setOutside(context, outside);
    }

    private static void setOutside(Context context, boolean outside) {
        SharedPreferences preferences = context.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE);
        boolean oldValue = preferences.getBoolean(PREF_GEOFENCE_OUTSIDE, false);
        if (oldValue == outside) {
            return;
        }

        preferences.edit().putBoolean(PREF_GEOFENCE_OUTSIDE, outside).apply();
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Const.ACTION_GEOFENCE_STATE_CHANGED));
    }
}
