package com.hmdm.launcher.policy;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.ui.MainActivity;
import com.hmdm.launcher.util.Utils;

public class LauncherProtectionPolicy {

    private static final String TAG = "LOCK_TEST";
    private static final String PREFS_NAME = "custom_policy";
    private static final String PREF_PROTECTION_APPLIED = "launcher_protection_applied";
    private static final String PREF_LAST_APPLY_TIME = "launcher_protection_last_apply_time";
    private static final long APPLY_THROTTLE_MS = 60 * 1000;

    public static void apply(Context context) {
        Log.d(TAG, "apply() called");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "Android version below Lollipop. Skipping.");
            return;
        }

        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (dpm == null) {
            Log.d(TAG, "DevicePolicyManager is null");
            return;
        }

        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.d(TAG, "App is NOT Device Owner. Cannot apply launcher lock.");
            return;
        }

        ComponentName admin =
                new ComponentName(context, AdminReceiver.class);

        ComponentName homeActivity =
                new ComponentName(context, MainActivity.class);

        IntentFilter homeFilter = new IntentFilter(Intent.ACTION_MAIN);
        homeFilter.addCategory(Intent.CATEGORY_HOME);
        homeFilter.addCategory(Intent.CATEGORY_DEFAULT);

        try {
            String defaultLauncher = Utils.getDefaultLauncher(context);
            boolean alreadyDefaultLauncher = context.getPackageName().equalsIgnoreCase(defaultLauncher);
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean protectionApplied = prefs.getBoolean(PREF_PROTECTION_APPLIED, false);
            long lastApplyTime = prefs.getLong(PREF_LAST_APPLY_TIME, 0);
            long now = System.currentTimeMillis();

            Log.d(TAG, "Default launcher before apply = " + defaultLauncher);

            if (alreadyDefaultLauncher && protectionApplied) {
                Log.d(TAG, "Headwind already default launcher. Skipping launcher protection apply.");
                return;
            }

            if (alreadyDefaultLauncher && now - lastApplyTime < APPLY_THROTTLE_MS) {
                Log.d(TAG, "Launcher protection apply throttled.");
                return;
            }

            dpm.addPersistentPreferredActivity(
                    admin,
                    homeFilter,
                    homeActivity
            );

            Log.d(TAG, "Persistent preferred HOME set to Headwind MDM");

            dpm.addUserRestriction(
                    admin,
                    UserManager.DISALLOW_APPS_CONTROL
            );

            Log.d(TAG, "DISALLOW_APPS_CONTROL applied");

            // DISALLOW_CONFIG_DEFAULT_APPS is not a valid UserManager constant
            // Removed to fix lint error

            Log.d(TAG, "Default launcher after apply = " + Utils.getDefaultLauncher(context));
            Log.d(TAG, "Launcher protection applied successfully");
            prefs.edit()
                    .putBoolean(PREF_PROTECTION_APPLIED, true)
                    .putLong(PREF_LAST_APPLY_TIME, now)
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "Error while applying launcher protection", e);
        }
    }

    public static void remove(Context context) {
        Log.d(TAG, "remove() called");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "Android version below Lollipop. Skipping.");
            return;
        }

        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (dpm == null) {
            Log.d(TAG, "DevicePolicyManager is null");
            return;
        }

        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.d(TAG, "App is NOT Device Owner. Cannot remove launcher lock.");
            return;
        }

        ComponentName admin =
                new ComponentName(context, AdminReceiver.class);

        try {
            dpm.clearPackagePersistentPreferredActivities(
                    admin,
                    context.getPackageName()
            );

            Log.d(TAG, "Persistent preferred HOME cleared for Headwind MDM");

            dpm.clearUserRestriction(
                    admin,
                    UserManager.DISALLOW_APPS_CONTROL
            );

            Log.d(TAG, "DISALLOW_APPS_CONTROL cleared");

            // DISALLOW_CONFIG_DEFAULT_APPS is not a valid UserManager constant
            // Removed to fix lint error

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_PROTECTION_APPLIED, false)
                    .putLong(PREF_LAST_APPLY_TIME, 0)
                    .apply();

            Log.d(TAG, "Launcher protection removed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error while removing launcher protection", e);
        }
    }
}
