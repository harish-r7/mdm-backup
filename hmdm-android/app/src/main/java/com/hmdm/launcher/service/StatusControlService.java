package com.hmdm.launcher.service;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.UserManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.AdminReceiver;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.policy.LauncherProtectionPolicy;
import com.hmdm.launcher.util.Utils;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StatusControlService extends Service {

    private SettingsHelper settingsHelper;
    private ScheduledThreadPoolExecutor threadPoolExecutor = new ScheduledThreadPoolExecutor( 1 );
    private boolean controlDisabled = false;
    private Timer disableControlTimer;

    private final long ENABLE_CONTROL_DELAY = 60;

    private final long STATUS_CHECK_INTERVAL_MS = 10000;
    private final long LAUNCHER_LOCK_CHECK_INTERVAL_MS = 2000;
    private final long LOCATION_LOCK_CHECK_INTERVAL_MS = 2000;

    private static class PackageInfo {
        public String packageName;
        public String className;

        public PackageInfo(String packageName, String className) {
            this.packageName = packageName;
            this.className = className;
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive( Context context, Intent intent ) {
            switch ( intent.getAction() ) {
                case Const.ACTION_SERVICE_STOP:
                    stopSelf();
                    break;
                case Const.ACTION_STOP_CONTROL:
                    disableControl();
                    break;
            }
        }
    };

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance( this ).unregisterReceiver( receiver );

        threadPoolExecutor.shutdownNow();
        threadPoolExecutor = new ScheduledThreadPoolExecutor( 1 );

        Log.i(Const.LOG_TAG, "StatusControlService: service stopped");

        super.onDestroy();
    }

    @Override
    public int onStartCommand( Intent intent, int flags, int startId) {
        settingsHelper = SettingsHelper.getInstance(this);

        Log.i(Const.LOG_TAG, "StatusControlService: service started.");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);

        IntentFilter intentFilter = new IntentFilter(Const.ACTION_SERVICE_STOP);
        intentFilter.addAction(Const.ACTION_STOP_CONTROL);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);

        threadPoolExecutor.shutdownNow();

        threadPoolExecutor = new ScheduledThreadPoolExecutor(1);
        threadPoolExecutor.scheduleWithFixedDelay(() -> controlStatus(),
                STATUS_CHECK_INTERVAL_MS, STATUS_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        threadPoolExecutor.scheduleWithFixedDelay(() -> controlLauncherLock(),
                0, LAUNCHER_LOCK_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        threadPoolExecutor.scheduleWithFixedDelay(() -> controlLocationLock(),
                0, LOCATION_LOCK_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return Service.START_STICKY;
    }


    private void disableControl() {
        Log.i(Const.LOG_TAG, "StatusControlService: request to disable control");

        if (disableControlTimer != null) {
            try {
                disableControlTimer.cancel();
            } catch (Exception e) {
            }
            disableControlTimer = null;
        }
        controlDisabled = true;
        disableControlTimer = new Timer();
        disableControlTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                controlDisabled = false;
                Log.i(Const.LOG_TAG, "StatusControlService: control enabled");
            }
        }, ENABLE_CONTROL_DELAY * 1000);
        Log.i(Const.LOG_TAG, "StatusControlService: control disabled for 60 sec");
    }

    @SuppressLint("MissingPermission")
    private void controlStatus() {
        ServerConfig config = settingsHelper.getConfig();
        if (config == null || controlDisabled) {
            return;
        }

        if (config.getBluetooth() != null) {
            try {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null) {
                    boolean enabled = bluetoothAdapter.isEnabled();
                    if (config.getBluetooth() && !enabled) {
                        bluetoothAdapter.enable();
                    } else if (!config.getBluetooth() && enabled) {
                        bluetoothAdapter.disable();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Note: SecurityException here on Mediatek
        // Looks like com.mediatek.permission.CTA_ENABLE_WIFI needs to be explicitly granted
        // or even available to system apps only
        // By now, let's just ignore this issue
        if (config.getWifi() != null) {
            try {
                WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    boolean enabled = wifiManager.isWifiEnabled();
                    if (config.getWifi() && !enabled) {
                        wifiManager.setWifiEnabled(true);
                    } else if (!config.getWifi() && enabled) {
                        wifiManager.setWifiEnabled(false);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (config.getGps() != null) {
            LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (config.getGps() && !enabled) {
                    notifyStatusViolation(Const.GPS_ON_REQUIRED);
                    return;
                } else if (!config.getGps() && enabled) {
                    notifyStatusViolation(Const.GPS_OFF_REQUIRED);
                    return;
                }
            }
        }

        if (config.getMobileData() != null) {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                try {
                    boolean enabled = Utils.isMobileDataEnabled(this);
                    if (config.getMobileData() && !enabled) {
                        notifyStatusViolation(Const.MOBILE_DATA_ON_REQUIRED);
                    } else if (!config.getMobileData() && enabled) {
                        notifyStatusViolation(Const.MOBILE_DATA_OFF_REQUIRED);
                    }
                } catch (Exception e) {
                    // Some problem access private API
                }
            }
        }
    }

    private void controlLauncherLock() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        SharedPreferences customPolicyPrefs = getSharedPreferences("custom_policy", MODE_PRIVATE);
        boolean lockDefaultLauncher = customPolicyPrefs.getBoolean("lock_default_launcher", false);

        if (config != null) {
            lockDefaultLauncher = config.isLockDefaultLauncher();
        }

        if (!lockDefaultLauncher) {
            return;
        }

        if (!Utils.isDeviceOwner(this)) {
            Log.d("LOCK_TEST", "StatusControlService: app is not Device Owner, launcher lock cannot be enforced");
            return;
        }

        String defaultLauncher = Utils.getDefaultLauncher(this);
        if (!getPackageName().equalsIgnoreCase(defaultLauncher)) {
            Log.d("LOCK_TEST", "StatusControlService: default launcher changed to "
                    + defaultLauncher + ", restoring Headwind MDM");
        }
        LauncherProtectionPolicy.apply(this);
    }

    private void controlLocationLock() {
        ServerConfig config = settingsHelper != null ? settingsHelper.getConfig() : null;
        if (config == null) {
            return;
        }

        boolean lockLocationOn = Boolean.TRUE.equals(config.getGps()) ||
                "gps".equalsIgnoreCase(config.getRequestUpdates());

        if (lockLocationOn) {
            applyLocationOnPolicy();
        } else {
            removeLocationOnPolicy();
        }
    }

    private void applyLocationOnPolicy() {
        if (!Utils.isDeviceOwner(this)) {
            Log.d("LOCK_TEST", "StatusControlService: app is not Device Owner, location lock cannot be enforced");
            return;
        }

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null) {
            return;
        }

        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_LOCATION);
                Log.d("LOCK_TEST", "DISALLOW_CONFIG_LOCATION applied");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setLocationEnabled(admin, true);
                Log.d("LOCK_TEST", "Location enabled by Device Owner");
            }
        } catch (Exception e) {
            Log.e("LOCK_TEST", "Error while applying location lock", e);
        }
    }

    private void removeLocationOnPolicy() {
        if (!Utils.isDeviceOwner(this)) {
            return;
        }

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }

        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_LOCATION);
            Log.d("LOCK_TEST", "DISALLOW_CONFIG_LOCATION cleared");
        } catch (Exception e) {
            Log.e("LOCK_TEST", "Error while removing location lock", e);
        }
    }

    private void notifyStatusViolation(int cause) {
        Intent intent = new Intent(Const.ACTION_POLICY_VIOLATION);
        intent.putExtra(Const.POLICY_VIOLATION_CAUSE, cause);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
