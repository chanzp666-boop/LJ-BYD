package com.ljbyd.launcher3.customkey;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 自定义方向盘按键映射（仿迪友 CustomKeyBean）
 *
 * 车机方向盘物理按键在 Android 上以标准 KeyEvent 上报，
 * 这里把 语音键/左键/右键 映射到用户选择的应用；开机任务键在启动时执行。
 */
public class CustomKeyManager {

    private static final String PREFS = "ljbyd_custom_keys";

    // 映射槽位
    public static final String SLOT_VOICE = "voice";   // KEYCODE_VOICE_ASSIST 231
    public static final String SLOT_LEFT = "left";     // KEYCODE_DPAD_LEFT 21
    public static final String SLOT_RIGHT = "right";   // KEYCODE_DPAD_RIGHT 22
    public static final String SLOT_BOOT = "boot";     // 开机任务（非按键）

    private static final String[] SLOTS = {SLOT_VOICE, SLOT_LEFT, SLOT_RIGHT, SLOT_BOOT};

    public static String slotName(Context ctx, String slot) {
        switch (slot) {
            case SLOT_VOICE: return "语音键";
            case SLOT_LEFT: return "方向盘左键";
            case SLOT_RIGHT: return "方向盘右键";
            case SLOT_BOOT: return "开机任务键";
            default: return slot;
        }
    }

    public static String[] allSlots() {
        return SLOTS;
    }

    public static String get(Context ctx, String slot) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(slot, null);
    }

    public static void set(Context ctx, String slot, String packageName) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(slot, packageName).apply();
    }

    /** 按键码 → 映射包名（无映射返回 null） */
    public static String getByKeyCode(Context ctx, int keyCode) {
        String slot = null;
        if (keyCode == 231) {          // KEYCODE_VOICE_ASSIST
            slot = SLOT_VOICE;
        } else if (keyCode == 21) {    // KEYCODE_DPAD_LEFT
            slot = SLOT_LEFT;
        } else if (keyCode == 22) {    // KEYCODE_DPAD_RIGHT
            slot = SLOT_RIGHT;
        }
        if (slot == null) return null;
        return get(ctx, slot);
    }
}
