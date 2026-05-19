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

package com.hmdm.launcher.pro;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.util.Log;
import android.view.View;

import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.util.Utils;

import java.util.Calendar;

/**
 * These functions are available in Pro-version only
 * In a free version, the class contains stubs
 */
public class ProUtils {

    public static boolean isPro() {
        return false;
    }

    public static boolean kioskModeRequired(Context context) {
        if (context == null) {
            return false;
        }
        try {
            SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
            if (settingsHelper == null) {
                return false;
            }
            ServerConfig config = settingsHelper.getConfig();
            return config != null && config.isKioskMode() && config.getMainApp() != null && config.getMainApp().trim().length() > 0;
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to determine kiosk mode requirement", e);
            return false;
        }
    }

    public static void initCrashlytics(Context context) {
        // Stub
    }

    public static void sendExceptionToCrashlytics(Throwable e) {
        // Stub
    }

    // Start the service checking if the foreground app is allowed to the user (by usage statistics)
    public static boolean checkAccessibilityService(Context context) {
        // Stub
        return true;
    }

    // Pro-version
    public static boolean checkUsageStatistics(Context context) {
        // Stub
        return true;
    }

    // Add a transparent view on top of the status bar which prevents user interaction with the status bar
    public static View preventStatusBarExpansion(Activity activity) {
        // Stub
        return null;
    }

    // Add a transparent view on top of a swipeable area at the right (opens app list on Samsung tablets)
    public static View preventApplicationsList(Activity activity) {
        // Stub
        return null;
    }

    public static View createKioskUnlockButton(Activity activity) {
        // Stub
        return null;
    }

    public static boolean isKioskAppInstalled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            ServerConfig config = SettingsHelper.getInstance(context).getConfig();
            if (config != null) {
                String kioskApp = config.getMainApp();
                if (kioskApp != null && kioskApp.trim().length() > 0) {
                    if (kioskApp.equals(context.getPackageName())) {
                        return true;
                    }
                    return Utils.isPackageInstalled(context, kioskApp);
                }
            }
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to detect kiosk app installation", e);
        }
        return Utils.isPackageInstalled(context, Const.KIOSK_BROWSER_PACKAGE_NAME);
    }

    public static boolean isKioskModeRunning(Context context) {
        if (context == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    return activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
                }
            } catch (Exception e) {
                Log.w(Const.LOG_TAG, "Failed to detect current lock task state", e);
            }
        }
        return false;
    }

    public static Intent getKioskAppIntent(String kioskApp, Activity activity) {
        if (activity == null || kioskApp == null) {
            return null;
        }
        try {
            kioskApp = kioskApp.trim();
            if (kioskApp.length() == 0) {
                return null;
            }
            PackageManager packageManager = activity.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(kioskApp);
            if (intent == null) {
                return null;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return intent;
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to build kiosk app intent", e);
            return null;
        }
    }

    private static boolean startSelfKiosk(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        if (!Utils.isDeviceOwner(activity)) {
            Log.w(Const.LOG_TAG, "Cannot start self kiosk because the app is not device owner");
            return false;
        }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) {
                Log.w(Const.LOG_TAG, "DevicePolicyManager is null, cannot start self kiosk");
                return false;
            }
            ComponentName admin = new ComponentName(activity, AdminReceiver.class);
            dpm.setLockTaskPackages(admin, new String[]{activity.getPackageName()});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
            activity.startLockTask();
            return true;
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "Failed to start self kiosk", e);
            return false;
        }
    }

    // Start COSU kiosk mode
    public static boolean startCosuKioskMode(String kioskApp, Activity activity, boolean enableSettings) {
        if (activity == null || kioskApp == null) {
            return false;
        }
        kioskApp = kioskApp.trim();
        if (kioskApp.length() == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        if (!Utils.isDeviceOwner(activity)) {
            Log.w(Const.LOG_TAG, "Cannot start kiosk mode because the app is not device owner");
            return false;
        }
        try {
            if (kioskApp.equals(activity.getPackageName())) {
                return startSelfKiosk(activity);
            }
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) {
                Log.w(Const.LOG_TAG, "DevicePolicyManager is null, cannot start kiosk app");
                return false;
            }
            ComponentName admin = new ComponentName(activity, AdminReceiver.class);
            dpm.setLockTaskPackages(admin, new String[]{kioskApp});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
            Intent intent = getKioskAppIntent(kioskApp, activity);
            if (intent == null) {
                Log.w(Const.LOG_TAG, "Kiosk app intent is null for package: " + kioskApp);
                return false;
            }
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(Const.LOG_TAG, "Failed to start COSU kiosk mode", e);
            return false;
        }
    }

    // Set/update kiosk mode options (lock task features)
    public static void updateKioskOptions(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !Utils.isDeviceOwner(activity)) {
            return;
        }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) {
                return;
            }
            ComponentName admin = new ComponentName(activity, AdminReceiver.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to update kiosk options", e);
        }
    }

    // Update app list in the kiosk mode
    public static void updateKioskAllowedApps(String kioskApp, Activity activity, boolean enableSettings) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !Utils.isDeviceOwner(activity)) {
            return;
        }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null) {
                return;
            }
            ComponentName admin = new ComponentName(activity, AdminReceiver.class);
            if (kioskApp != null && kioskApp.trim().length() > 0) {
                if (kioskApp.equals(activity.getPackageName())) {
                    dpm.setLockTaskPackages(admin, new String[]{activity.getPackageName()});
                } else {
                    dpm.setLockTaskPackages(admin, new String[]{activity.getPackageName(), kioskApp});
                }
            } else {
                dpm.setLockTaskPackages(admin, new String[]{});
            }
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to update kiosk allowed apps", e);
        }
    }

    public static void unlockKiosk(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            activity.stopLockTask();
            if (Utils.isDeviceOwner(activity)) {
                DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null) {
                    ComponentName admin = new ComponentName(activity, AdminReceiver.class);
                    dpm.setLockTaskPackages(admin, new String[]{});
                }
            }
        } catch (Exception e) {
            Log.w(Const.LOG_TAG, "Failed to unlock kiosk mode", e);
        }
    }

    public static void processConfig(Context context, ServerConfig config) {
        // Stub
    }

    public static void processLocation(Context context, Location location, String provider) {
        // Stub    
    }

    public static String getAppName(Context context) {
        return context.getString(R.string.app_name);
    }

    public static String getCopyright(Context context) {
        return "(c) " + Calendar.getInstance().get(Calendar.YEAR) + " " + context.getString(R.string.vendor);
    }
}
