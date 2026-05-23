package com.hmdm.launcher.remotecontrol;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.util.RemoteLogger;

public class RemoteControlPermissionActivity extends Activity {
    private static final int REQUEST_CAPTURE = 7712;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getIntent().getStringExtra(RemoteControlService.EXTRA_TOKEN);
        if (token == null || token.length() == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            RemoteLogger.log(this, Const.LOG_WARN, "Remote control cannot start on this Android version");
            finish();
            return;
        }

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent serviceIntent = new Intent(this, RemoteControlService.class);
            serviceIntent.putExtra(RemoteControlService.EXTRA_TOKEN, token);
            serviceIntent.putExtra(RemoteControlService.EXTRA_RESULT_CODE, resultCode);
            serviceIntent.putExtra(RemoteControlService.EXTRA_RESULT_DATA, data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } else {
            RemoteLogger.log(this, Const.LOG_WARN, "Remote control screen capture permission was not granted");
        }
        finish();
    }
}
