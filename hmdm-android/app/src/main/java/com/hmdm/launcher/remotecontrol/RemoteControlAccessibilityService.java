package com.hmdm.launcher.remotecontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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

    public static boolean scroll(float x, float y, int direction) {
        if (instance == null) {
            return false;
        }
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        try {
            AccessibilityNodeInfo node = findScrollableNode(root, (int) x, (int) y);
            if (node == null) {
                node = findScrollableNode(root);
            }
            if (node == null) {
                return false;
            }
            try {
                int action = direction > 0
                        ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                return node.performAction(action);
            } finally {
                node.recycle();
            }
        } finally {
            root.recycle();
        }
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

    private static AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node, int x, int y) {
        if (node == null) {
            return null;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) {
            return null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo scrollable = findScrollableNode(child, x, y);
            if (scrollable != null) {
                return scrollable;
            }
            if (child != null) {
                child.recycle();
            }
        }
        if (node.isScrollable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        return null;
    }

    private static AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isScrollable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo scrollable = findScrollableNode(child);
            if (scrollable != null) {
                return scrollable;
            }
            if (child != null) {
                child.recycle();
            }
        }
        return null;
    }
}
