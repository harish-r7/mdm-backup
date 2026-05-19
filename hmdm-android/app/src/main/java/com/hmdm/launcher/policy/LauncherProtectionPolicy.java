package com.hmdm.launcher.policy;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.ui.MainActivity;
import com.hmdm.launcher.util.Utils;

public class LauncherProtectionPolicy {

    private static final String TAG = "LOCK_TEST";

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
            Log.d(TAG, "Default launcher before apply = " + Utils.getDefaultLauncher(context));

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

            Log.d(TAG, "Launcher protection removed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error while removing launcher protection", e);
        }
    }
}
