/*
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmdm.launcher.pro.service;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.json.RemoteLogItem;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.util.RemoteLogger;

public class CheckForegroundApplicationService extends Service {
    private static final long CHECK_INTERVAL_MS = 5000L;
    private static final long INITIAL_LOOKBACK_MS = 30000L;
    private static final String PREF_LAST_EVENT_TS = "APP_USAGE_LAST_EVENT_TS";
    private static final String PREF_CURRENT_PACKAGE = "APP_USAGE_CURRENT_PACKAGE";
    private static final String PREF_CURRENT_STARTED_AT = "APP_USAGE_CURRENT_STARTED_AT";
    private static final String LOG_PREFIX = "APP_USAGE";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable usageCheck = new Runnable() {
        @Override
        public void run() {
            collectUsageEvents();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(usageCheck);
        handler.post(usageCheck);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        closeCurrentSession(System.currentTimeMillis(), "service_stop");
        handler.removeCallbacks(usageCheck);
        super.onDestroy();
    }

    private void collectUsageEvents() {
        if (!ProUtils.checkUsageStatistics(this)) {
            RemoteLogger.log(this, Const.LOG_WARN, LOG_PREFIX + " usage_stats_permission_missing");
            return;
        }

        long now = System.currentTimeMillis();
        SharedPreferences preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        long from = preferences.getLong(PREF_LAST_EVENT_TS, now - INITIAL_LOOKBACK_MS);
        if (from > now || now - from > 24 * 60 * 60 * 1000L) {
            from = now - INITIAL_LOOKBACK_MS;
        }

        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return;
        }

        UsageEvents events = usageStatsManager.queryEvents(from, now);
        UsageEvents.Event event = new UsageEvents.Event();
        long lastEventTime = from;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getPackageName() == null) {
                continue;
            }
            lastEventTime = Math.max(lastEventTime, event.getTimeStamp());
            handleUsageEvent(event);
        }

        preferences.edit().putLong(PREF_LAST_EVENT_TS, Math.max(lastEventTime + 1, now)).apply();
    }

    private void handleUsageEvent(UsageEvents.Event event) {
        int eventType = event.getEventType();
        if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            startSession(event.getPackageName(), event.getTimeStamp());
        } else if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
            finishSession(event.getPackageName(), event.getTimeStamp(), "background");
        }
    }

    private void startSession(String packageName, long startedAt) {
        SharedPreferences preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        String currentPackage = preferences.getString(PREF_CURRENT_PACKAGE, null);
        long currentStartedAt = preferences.getLong(PREF_CURRENT_STARTED_AT, 0L);

        if (currentPackage != null && !currentPackage.equals(packageName)) {
            logSession(currentPackage, currentStartedAt, startedAt, "switch");
        }

        preferences.edit()
                .putString(PREF_CURRENT_PACKAGE, packageName)
                .putLong(PREF_CURRENT_STARTED_AT, startedAt)
                .apply();
    }

    private void finishSession(String packageName, long finishedAt, String reason) {
        SharedPreferences preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        String currentPackage = preferences.getString(PREF_CURRENT_PACKAGE, null);
        long currentStartedAt = preferences.getLong(PREF_CURRENT_STARTED_AT, 0L);

        if (currentPackage != null && currentPackage.equals(packageName)) {
            logSession(currentPackage, currentStartedAt, finishedAt, reason);
            preferences.edit()
                    .remove(PREF_CURRENT_PACKAGE)
                    .remove(PREF_CURRENT_STARTED_AT)
                    .apply();
        }
    }

    private void closeCurrentSession(long finishedAt, String reason) {
        SharedPreferences preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        String currentPackage = preferences.getString(PREF_CURRENT_PACKAGE, null);
        long currentStartedAt = preferences.getLong(PREF_CURRENT_STARTED_AT, 0L);

        if (currentPackage != null) {
            logSession(currentPackage, currentStartedAt, finishedAt, reason);
            preferences.edit()
                    .remove(PREF_CURRENT_PACKAGE)
                    .remove(PREF_CURRENT_STARTED_AT)
                    .apply();
        }
    }

    private void logSession(String packageName, long startedAt, long finishedAt, String reason) {
        if (startedAt <= 0 || finishedAt <= startedAt) {
            return;
        }

        long durationMs = finishedAt - startedAt;
        String message = LOG_PREFIX +
                " package=" + packageName +
                " app=\"" + getApplicationLabel(packageName) + "\"" +
                " openedAt=" + startedAt +
                " closedAt=" + finishedAt +
                " durationMs=" + durationMs +
                " reason=" + reason;

        RemoteLogItem item = new RemoteLogItem();
        item.setTimestamp(finishedAt);
        item.setLogLevel(Const.LOG_INFO);
        item.setPackageId(getPackageName());
        item.setMessage(message);
        RemoteLogger.postLog(this, item);
    }

    private String getApplicationLabel(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(applicationInfo);
            return label != null ? label.toString().replace("\"", "'") : packageName;
        } catch (Exception e) {
            return packageName;
        }
    }
}
