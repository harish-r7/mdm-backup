package com.hmdm.launcher.remotecontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.hmdm.launcher.Const;
import com.hmdm.launcher.R;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.server.ServerService;
import com.hmdm.launcher.server.ServerServiceKeeper;
import com.hmdm.launcher.util.RemoteLogger;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class RemoteControlService extends Service {
    public static final String EXTRA_TOKEN = "remote_control_token";
    public static final String EXTRA_RESULT_CODE = "remote_control_result_code";
    public static final String EXTRA_RESULT_DATA = "remote_control_result_data";

    private static final String CHANNEL_ID = "remote_control";
    private static final int NOTIFICATION_ID = 7721;
    private static final int FRAME_INTERVAL_MS = 500;
    private static final int COMMAND_INTERVAL_MS = 180;
    private static final int MAX_FRAME_WIDTH = 540;
    private static final int JPEG_QUALITY = 42;

    private HandlerThread workerThread;
    private Handler workerHandler;
    private HandlerThread commandThread;
    private Handler commandHandler;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private String token;
    private String project;
    private String deviceId;
    private int width;
    private int height;
    private int density;

    private final Runnable frameLoop = new Runnable() {
        @Override
        public void run() {
            captureAndPostFrame();
            if (workerHandler != null) {
                workerHandler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    private final Runnable commandLoop = new Runnable() {
        @Override
        public void run() {
            pollCommand();
            if (commandHandler != null) {
                commandHandler.postDelayed(this, COMMAND_INTERVAL_MS);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            stopSelf();
            return START_NOT_STICKY;
        }

        token = intent.getStringExtra(EXTRA_TOKEN);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        if (token == null || resultData == null || resultCode == 0) {
            stopSelf();
            return START_NOT_STICKY;
        }

        SettingsHelper settings = SettingsHelper.getInstance(this);
        project = settings.getServerProject();
        deviceId = settings.getDeviceId();

        startForeground(NOTIFICATION_ID, buildNotification());
        startWorker();

        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = projectionManager.getMediaProjection(resultCode, resultData);
        startCapture();
        RemoteLogger.log(this, Const.LOG_INFO, "Remote control session started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopCapture();
        notifyStopped();
        RemoteLogger.log(this, Const.LOG_INFO, "Remote control session stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startWorker() {
        workerThread = new HandlerThread("RemoteControl");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        commandThread = new HandlerThread("RemoteControlCommands");
        commandThread.start();
        commandHandler = new Handler(commandThread.getLooper());
        workerHandler.post(frameLoop);
        commandHandler.post(commandLoop);
    }

    private void startCapture() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        density = metrics.densityDpi;
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay(
                "HMDM Remote Control",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                workerHandler
        );
    }

    private void stopCapture() {
        if (workerHandler != null) {
            workerHandler.removeCallbacksAndMessages(null);
        }
        if (commandHandler != null) {
            commandHandler.removeCallbacksAndMessages(null);
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
            workerHandler = null;
        }
        if (commandThread != null) {
            commandThread.quitSafely();
            commandThread = null;
            commandHandler = null;
        }
    }

    private void captureAndPostFrame() {
        if (imageReader == null) {
            return;
        }
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                return;
            }
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            bitmap.recycle();

            int outWidth = width;
            int outHeight = height;
            Bitmap output = cropped;
            if (width > MAX_FRAME_WIDTH) {
                outWidth = MAX_FRAME_WIDTH;
                outHeight = Math.max(1, height * MAX_FRAME_WIDTH / width);
                output = Bitmap.createScaledBitmap(cropped, outWidth, outHeight, true);
                cropped.recycle();
            }

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream);
            output.recycle();
            String frame = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
            ServerService service = ServerServiceKeeper.getServerServiceInstance(this);
            service.postRemoteControlFrame(project, deviceId,
                    new RemoteControlFrame(token, frame, width, height)).execute();
        } catch (Exception e) {
            RemoteLogger.log(this, Const.LOG_WARN, "Remote control frame failed: " + e.getMessage());
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void pollCommand() {
        try {
            ServerService service = ServerServiceKeeper.getServerServiceInstance(this);
            Call<ResponseBody> call = service.pollRemoteControlCommand(project, deviceId, token);
            Response<ResponseBody> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                return;
            }
            JSONObject root = new JSONObject(response.body().string());
            if (!root.has("data") || root.isNull("data")) {
                return;
            }
            JSONObject data = root.getJSONObject("data");
            handleCommand(data);
        } catch (Exception e) {
            RemoteLogger.log(this, Const.LOG_WARN, "Remote control command failed: " + e.getMessage());
        }
    }

    private void handleCommand(JSONObject command) {
        String type = command.optString("type", "");
        float x = (float) command.optDouble("x", 0);
        float y = (float) command.optDouble("y", 0);
        float x2 = (float) command.optDouble("x2", 0);
        float y2 = (float) command.optDouble("y2", 0);
        long duration = command.optLong("duration", 150);
        int direction = command.optInt("direction", command.optInt("text", 0));
        if ("tap".equals(type)) {
            RemoteControlAccessibilityService.tap(x, y);
        } else if ("swipe".equals(type)) {
            RemoteControlAccessibilityService.swipe(x, y, x2, y2, duration);
        } else if ("scroll".equals(type)) {
            if (!RemoteControlAccessibilityService.scroll(x, y, direction)) {
                RemoteControlAccessibilityService.swipe(x, y, x2, y2, duration);
            }
        } else if ("back".equals(type)) {
            RemoteControlAccessibilityService.back();
        } else if ("home".equals(type)) {
            RemoteControlAccessibilityService.home();
        } else if ("stop".equals(type)) {
            stopSelf();
        }
    }

    private void notifyStopped() {
        if (token == null || deviceId == null) {
            return;
        }
        try {
            ServerServiceKeeper.getServerServiceInstance(this)
                    .stopRemoteControl(project, deviceId, token)
                    .execute();
        } catch (Exception e) {
        }
    }

    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Remote Control",
                    NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Remote control active")
                    .setContentText("Your MDM administrator can view and control this screen.")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("Remote control active")
                .setContentText("Your MDM administrator can view and control this screen.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }
}
