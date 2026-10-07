package com.ljbyd.launcher3.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 画中画镜像前台服务
 * Android 10+ 上 MediaProjection 需配合前台服务保持稳定；
 * MediaProjection 由 PipWindowManager 持有，本服务仅做前台保活。
 */
public class PipProjectionService extends Service {

    private static final String CHANNEL_ID = "ljbyd_pip";
    private static final int NOTIFY_ID = 1002;

    public static void start(Context context) {
        Intent intent = new Intent(context, PipProjectionService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PipProjectionService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "画中画镜像", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("保持画中画镜像稳定运行");
            nm.createNotificationChannel(channel);
        }
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("垃圾比亚迪")
                .setContentText("画中画镜像运行中，三指下滑可关闭")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true);
        startForeground(NOTIFY_ID, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
