package com.ljbyd.launcher3.pip;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * 车控命令模板配置（仿迪友完整车控）
 *
 * 所有车控按钮的命令均为 SharedPreferences 可配置模板，
 * 默认值为常见比亚迪车机私有广播形式（经 adb 本机通道执行），
 * 真机可按键型 + 参数在设置里按实际车机修改。
 */
public class CarControlConfig {

    private static final String PREFS = "car_control_config";
    private static final String PREFIX = "car_cmd_";

    // ============ 空调 ============
    public static final String KEY_AC_TOGGLE = PREFIX + "ac_toggle";
    public static final String KEY_AC_TEMP_UP = PREFIX + "ac_temp_up";
    public static final String KEY_AC_TEMP_DOWN = PREFIX + "ac_temp_down";
    public static final String KEY_AC_WIND_UP = PREFIX + "ac_wind_up";
    public static final String KEY_AC_WIND_DOWN = PREFIX + "ac_wind_down";
    public static final String KEY_AC_MODE_CYCLE = PREFIX + "ac_mode_cycle";
    public static final String KEY_AC_CIRCULATE = PREFIX + "ac_circulate";
    public static final String KEY_AC_COMPRESSOR = PREFIX + "ac_compressor";
    public static final String KEY_AC_FRONT_DEFROST = PREFIX + "ac_front_defrost";
    public static final String KEY_AC_REAR_DEFROST = PREFIX + "ac_rear_defrost";
    public static final String KEY_AC_VENTILATION = PREFIX + "ac_ventilation";
    public static final String KEY_AC_AIR_CLEAN = PREFIX + "ac_air_clean";
    public static final String KEY_AC_AUTO_CLEAN = PREFIX + "ac_auto_clean";
    // ============ 座椅 ============
    public static final String KEY_SEAT_LEFT_VENT = PREFIX + "seat_left_vent";
    public static final String KEY_SEAT_RIGHT_VENT = PREFIX + "seat_right_vent";
    public static final String KEY_SEAT_LEFT_WARM = PREFIX + "seat_left_warm";
    public static final String KEY_SEAT_RIGHT_WARM = PREFIX + "seat_right_warm";
    // ============ 车辆 ============
    public static final String KEY_SUNSHADE = PREFIX + "sunshade";
    public static final String KEY_SKYLIGHT = PREFIX + "skylight";
    public static final String KEY_SOC_MODE = PREFIX + "soc_mode";
    public static final String KEY_RECOVERY = PREFIX + "recovery";
    public static final String KEY_DAY_LIGHT = PREFIX + "day_light";
    public static final String KEY_HEAD_LIGHT = PREFIX + "head_light";
    public static final String KEY_WIRELESS = PREFIX + "wireless";
    public static final String KEY_REAR_DISPLAY = PREFIX + "rear_display";
    public static final String KEY_BACK_DOOR = PREFIX + "back_door";
    public static final String KEY_STEERING_WARM = PREFIX + "steering_warm";
    public static final String KEY_ENGINE_SOUND = PREFIX + "engine_sound";
    // ============ 快捷（原 4 键） ============
    public static final String KEY_HALF_WINDOW = PREFIX + "half_window";
    public static final String KEY_CLOSE_WINDOW = PREFIX + "close_window";

    private static final Map<String, String> DEF_CMDS = new HashMap<>();
    static {
        DEF_CMDS.put(KEY_AC_TOGGLE, "am broadcast -a com.byd.ac.TOGGLE");
        DEF_CMDS.put(KEY_AC_TEMP_UP, "am broadcast -a com.byd.ac.TEMP_UP");
        DEF_CMDS.put(KEY_AC_TEMP_DOWN, "am broadcast -a com.byd.ac.TEMP_DOWN");
        DEF_CMDS.put(KEY_AC_WIND_UP, "am broadcast -a com.byd.ac.WIND_UP");
        DEF_CMDS.put(KEY_AC_WIND_DOWN, "am broadcast -a com.byd.ac.WIND_DOWN");
        DEF_CMDS.put(KEY_AC_MODE_CYCLE, "am broadcast -a com.byd.ac.MODE_CYCLE");
        DEF_CMDS.put(KEY_AC_CIRCULATE, "am broadcast -a com.byd.ac.CIRCULATE_TOGGLE");
        DEF_CMDS.put(KEY_AC_COMPRESSOR, "am broadcast -a com.byd.ac.COMPRESSOR_TOGGLE");
        DEF_CMDS.put(KEY_AC_FRONT_DEFROST, "am broadcast -a com.byd.ac.FRONT_DEFROST");
        DEF_CMDS.put(KEY_AC_REAR_DEFROST, "am broadcast -a com.byd.ac.REAR_DEFROST");
        DEF_CMDS.put(KEY_AC_VENTILATION, "am broadcast -a com.byd.ac.VENTILATION_TOGGLE");
        DEF_CMDS.put(KEY_AC_AIR_CLEAN, "am broadcast -a com.byd.ac.AIR_CLEAN_TOGGLE");
        DEF_CMDS.put(KEY_AC_AUTO_CLEAN, "am broadcast -a com.byd.ac.AUTO_CLEAN_TOGGLE");
        DEF_CMDS.put(KEY_SEAT_LEFT_VENT, "am broadcast -a com.byd.seat.LEFT_VENT_TOGGLE");
        DEF_CMDS.put(KEY_SEAT_RIGHT_VENT, "am broadcast -a com.byd.seat.RIGHT_VENT_TOGGLE");
        DEF_CMDS.put(KEY_SEAT_LEFT_WARM, "am broadcast -a com.byd.seat.LEFT_WARM_TOGGLE");
        DEF_CMDS.put(KEY_SEAT_RIGHT_WARM, "am broadcast -a com.byd.seat.RIGHT_WARM_TOGGLE");
        DEF_CMDS.put(KEY_SUNSHADE, "am broadcast -a com.byd.window.SUNSHADE_TOGGLE");
        DEF_CMDS.put(KEY_SKYLIGHT, "am broadcast -a com.byd.window.SKYLIGHT_TOGGLE");
        DEF_CMDS.put(KEY_SOC_MODE, "am broadcast -a com.byd.energy.BATTERY_MODE_TOGGLE");
        DEF_CMDS.put(KEY_RECOVERY, "am broadcast -a com.byd.energy.RECOVERY_TOGGLE");
        DEF_CMDS.put(KEY_DAY_LIGHT, "am broadcast -a com.byd.light.DAY_LIGHT_TOGGLE");
        DEF_CMDS.put(KEY_HEAD_LIGHT, "am broadcast -a com.byd.light.HEAD_LIGHT_TOGGLE");
        DEF_CMDS.put(KEY_WIRELESS, "am broadcast -a com.byd.charge.WIRELESS_TOGGLE");
        DEF_CMDS.put(KEY_REAR_DISPLAY, "am broadcast -a com.byd.display.REAR_TOGGLE");
        DEF_CMDS.put(KEY_BACK_DOOR, "am broadcast -a com.byd.door.BACK_TOGGLE");
        DEF_CMDS.put(KEY_STEERING_WARM, "am broadcast -a com.byd.seat.STEERING_WARM_TOGGLE");
        DEF_CMDS.put(KEY_ENGINE_SOUND, "am broadcast -a com.byd.engine.SOUND_TOGGLE");
        DEF_CMDS.put(KEY_HALF_WINDOW, "am broadcast -a com.byd.window.HALF_OPEN");
        DEF_CMDS.put(KEY_CLOSE_WINDOW, "am broadcast -a com.byd.window.CLOSE_ALL");
    }

    private final SharedPreferences sp;

    public CarControlConfig(Context context) {
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String get(String key) {
        return sp.getString(key, DEF_CMDS.get(key));
    }

    public String getHalfWindow() {
        return get(KEY_HALF_WINDOW);
    }

    public String getCloseWindow() {
        return get(KEY_CLOSE_WINDOW);
    }

    public String getRecovery() {
        return get(KEY_RECOVERY);
    }

    public String getBatteryMode() {
        return get(KEY_SOC_MODE);
    }

    public void setCommand(String key, String command) {
        sp.edit().putString(key, command).apply();
    }
}
