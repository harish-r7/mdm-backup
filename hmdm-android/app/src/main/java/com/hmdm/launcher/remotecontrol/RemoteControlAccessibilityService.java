package com.hmdm.launcher.remotecontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class RemoteControlAccessibilityService extends AccessibilityService {
    private static RemoteControlAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    public static boolean tap(float x, float y) {
        return gesture(x, y, x, y, 1);
    }

    public static boolean swipe(float x1, float y1, float x2, float y2, long duration) {
        return gesture(x1, y1, x2, y2, Math.max(100, duration));
    }

    public static boolean back() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean home() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private static boolean gesture(float x1, float y1, float x2, float y2, long duration) {
        if (instance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        return instance.dispatchGesture(gesture, null, null);
    }
}
