package com.ljbyd.launcher3.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍自愈保活服务（对齐迪友桌面 AutoService 机制）
 *
 * 职责：
 * 1. 系统级保活：无障碍服务被系统强保活；进程被杀后系统会自动重启本服务，
 *    服务（重）连接时自动拉起桌面 MainActivity 回桌面（自愈）。
 * 2. 全局三指下滑手势：任意界面三指下滑 → 切换画中画窗口开/关。
 * 3. 兼容多任务按钮：ACTION_OPEN_RECENTS → performGlobalAction 打开最近任务。
 * 4. ready 广播：服务连接后发 READY 广播，通知存活组件。
 */
public class AutoService extends AccessibilityService {

    private static final String TAG = "AutoService";

    /** 三指下滑切换画中画 */
    public static final String ACTION_TOGGLE_PIP = "com.ljbyd.launcher3.action.TOGGLE_PIP";
    /** 打开最近任务 */
    public static final String ACTION_OPEN_RECENTS = "com.ljbyd.launcher3.action.OPEN_RECENTS";
    /** 重启桌面 */
    public static final String ACTION_RESTART_DESKTOP = "com.ljbyd.launcher3.action.RESTART_DESKTOP";
    /** 就绪广播 */
    public static final String ACTION_READY = "com.ljbyd.launcher3.READY";

    private static AutoService sInstance = null;

    /** 三指下滑手势常量值（反射读取；Android 12+ 有效，低版本为 -1） */
    private static int GESTURE_SWIPE_DOWN_3_VAL = -1;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /** 开机/连接后自动回桌面延迟（毫秒） */
    private static final long RESTART_DELAY_MS = 800;

    /**
     * 处理 ACTION_TOGGLE_PIP / ACTION_OPEN_RECENTS 命令的接收器
     * （服务可被 startService(intent) 唤醒，也可通过广播转发）
     */
    private final BroadcastReceiver mCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_TOGGLE_PIP.equals(action)) {
                // 由 MainActivity 处理开/关画中画
                Intent toDesktop = new Intent(context, com.ljbyd.launcher3.MainActivity.class);
                toDesktop.setAction(ACTION_TOGGLE_PIP);
                toDesktop.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(toDesktop);
            } else if (ACTION_OPEN_RECENTS.equals(action)) {
                performGlobalAction(GLOBAL_ACTION_RECENTS);
            }
        }
    };

    public static AutoService getInstance() {
        return sInstance;
    }

    public static boolean isConnected() {
        return sInstance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.i(TAG, "无障碍服务已连接（自愈拉起桌面）");
        registerReceiver(mCommandReceiver,
                new IntentFilter(ACTION_TOGGLE_PIP));
        // 注册三指下滑全局手势：
        // - Android 12+ 系统支持三指手势（GESTURE_SWIPE_DOWN_3 常量是 API 31 加入，用反射避免编译失败）
        // - Android 10/11 系统仅支持单/双指手势，由 onGesture 兜底处理双指下滑
        trySetThreeFingerGesture();
        // 服务连接 = 进程被系统拉起，自动回桌面完成自愈
        mHandler.postDelayed(this::bringDesktopToFront, RESTART_DELAY_MS);
        sendBroadcast(new Intent(ACTION_READY));
    }

    /** 反射注册三指下滑手势（Android 12+ 生效，低版本忽略） */
    private void trySetThreeFingerGesture() {
        try {
            Class<?> infoCls = Class.forName("android.accessibilityservice.AccessibilityServiceInfo");
            java.lang.reflect.Field gestureTypes = infoCls.getField("gestureTypes");
            java.lang.reflect.Field down3 = AccessibilityService.class.getField("GESTURE_SWIPE_DOWN_3");
            GESTURE_SWIPE_DOWN_3_VAL = down3.getInt(null);
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                gestureTypes.setInt(info, GESTURE_SWIPE_DOWN_3_VAL);
                setServiceInfo(info);
                Log.i(TAG, "已注册三指下滑手势（系统支持时生效）");
            }
        } catch (Exception e) {
            Log.i(TAG, "系统不支持三指手势，使用双指下滑兜底: " + e.getMessage());
        }
    }

    /**
     * 自愈：拉起桌面主界面
     */
    private void bringDesktopToFront() {
        try {
            Intent intent = new Intent(this, com.ljbyd.launcher3.MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "自愈拉起桌面失败: " + e.getMessage());
        }
    }

    /**
     * 全局手势：三指下滑（Android 12+）或双指下滑（Android 10/11 兜底）→ 切换画中画
     */
    @Override
    public boolean onGesture(int gestureId) {
        if (gestureId == GESTURE_SWIPE_DOWN_3_VAL
                || gestureId == AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN) {
            Log.i(TAG, "全局手势触发画中画切换 gestureId=" + gestureId);
            try {
                sendBroadcast(new Intent(ACTION_TOGGLE_PIP));
            } catch (Exception e) {
                Log.e(TAG, "手势广播失败: " + e.getMessage());
            }
        }
        return true;
    }

    /**
     * 兼容：外部通过 startService 发送命令
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_OPEN_RECENTS.equals(action)) {
                performGlobalAction(GLOBAL_ACTION_RECENTS);
            } else if (ACTION_RESTART_DESKTOP.equals(action)) {
                bringDesktopToFront();
            } else if (ACTION_TOGGLE_PIP.equals(action)) {
                sendBroadcast(new Intent(ACTION_TOGGLE_PIP));
            }
        }
        return START_STICKY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 保活服务无需处理事件内容
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "无障碍服务中断");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "无障碍服务解绑");
        try {
            unregisterReceiver(mCommandReceiver);
        } catch (Exception ignored) {
        }
        sInstance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mCommandReceiver);
        } catch (Exception ignored) {
        }
        sInstance = null;
        super.onDestroy();
    }
}
