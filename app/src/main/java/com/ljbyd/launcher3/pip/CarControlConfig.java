package com.ljbyd.launcher3.pip;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 车控命令模板配置
 *
 * 迪友桌面同款车控的底层命令因车机/固件而异（各家车机私有广播不同），
 * 这里把 4 个车控按钮的命令做成 SharedPreferences 可配置模板，
 * 默认值为常见比亚迪车机广播形式，可在设置/配置文件里按实际车机修改。
 */
public class CarControlConfig {

    private static final String PREFS = "car_control_config";

    public static final String KEY_HALF_WINDOW = "car_cmd_half_window";   // 全车半开窗
    public static final String KEY_CLOSE_WINDOW = "car_cmd_close_window"; // 全车关窗
    public static final String KEY_RECOVERY = "car_cmd_recovery";         // 动能回收切换
    public static final String KEY_BATTERY_MODE = "car_cmd_battery_mode"; // 保电模式切换

    // 默认命令模板（按车机实际命令修改，经 adb 本机通道执行）
    private static final String DEF_HALF_WINDOW = "am broadcast -a com.byd.window.HALF_OPEN";
    private static final String DEF_CLOSE_WINDOW = "am broadcast -a com.byd.window.CLOSE_ALL";
    private static final String DEF_RECOVERY = "am broadcast -a com.byd.energy.RECOVERY_TOGGLE";
    private static final String DEF_BATTERY_MODE = "am broadcast -a com.byd.energy.BATTERY_MODE_TOGGLE";

    private final SharedPreferences sp;

    public CarControlConfig(Context context) {
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getHalfWindow() {
        return sp.getString(KEY_HALF_WINDOW, DEF_HALF_WINDOW);
    }

    public String getCloseWindow() {
        return sp.getString(KEY_CLOSE_WINDOW, DEF_CLOSE_WINDOW);
    }

    public String getRecovery() {
        return sp.getString(KEY_RECOVERY, DEF_RECOVERY);
    }

    public String getBatteryMode() {
        return sp.getString(KEY_BATTERY_MODE, DEF_BATTERY_MODE);
    }

    public void setCommand(String key, String command) {
        sp.edit().putString(key, command).apply();
    }
}
