package com.ljbyd.launcher3.pip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ljbyd.launcher3.R;
import com.ljbyd.launcher3.adb.AdbCommandProcessor;

/**
 * 完整车控面板（仿迪友桌面）
 *
 * 覆盖：空调（开关/温度/风量/风向/循环/压缩机/除霜/通风/净化）、
 * 座椅（通风/加热/方向盘加热）、天窗/遮阳帘、能量（保电模式/动能回收）、
 * 灯光（日行灯/大灯）、无线充电、后排显示、后备箱、引擎声模拟、车窗。
 * 所有命令走车机 adb 通道（模板可配置），UI 状态本地维护。
 */
public class CarControlPanel {

    private static final String TAG = "CarControlPanel";

    private static CarControlPanel sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final CarControlConfig mConfig;
    private final AdbCommandProcessor mAdb;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private View mRootView;
    private WindowManager.LayoutParams mLayoutParams;
    private boolean mVisible = false;

    // 状态（本地维护，真机可经车机接口回读）
    private boolean acOn = true;
    private int temp = 22;
    private int windLevel = 3;
    private int windMode = 0;         // 0 吹面 1 吹脚 2 吹面+吹脚
    private boolean circulate = false;
    private boolean compressor = true;
    private boolean frontDefrost = false;
    private boolean rearDefrost = false;
    private boolean ventilation = false;
    private boolean airClean = true;
    private boolean autoClean = true;
    private boolean leftVent = false;
    private boolean rightVent = false;
    private boolean leftWarm = false;
    private boolean rightWarm = false;
    private boolean steeringWarm = false;
    private boolean socForce = false;     // false 智能保电 true 强制保电
    private int recovery = 0;             // 0 标准 1 较强 2 最大
    private boolean dayLight = true;
    private boolean headLight = false;
    private boolean wireless = false;
    private boolean rearDisplay = false;
    private boolean engineSound = false;

    public static synchronized CarControlPanel getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CarControlPanel(context.getApplicationContext());
        }
        return sInstance;
    }

    private CarControlPanel(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mConfig = new CarControlConfig(context);
        mAdb = new AdbCommandProcessor(context);
    }

    public boolean isVisible() {
        return mVisible;
    }

    @SuppressLint("WrongConstant")
    public void show() {
        if (mVisible) return;
        try {
            if (mRootView == null) {
                mRootView = LayoutInflater.from(mContext)
                        .inflate(R.layout.car_control_panel_layout, null);
                initViews();
                initWindowParams();
                mWindowManager.addView(mRootView, mLayoutParams);
            } else {
                mWindowManager.addView(mRootView, mLayoutParams);
            }
            mVisible = true;
            // 全屏车控面板打开时，隐藏左车况区避免重叠
            com.ljbyd.launcher3.PipWindowManager.getInstance(mContext).setCarInfoVisible(false);
            refreshAll();
            Log.i(TAG, "车控面板已显示");
        } catch (Exception e) {
            Log.e(TAG, "显示车控面板失败: " + e.getMessage(), e);
        }
    }

    public void hide() {
        if (!mVisible) return;
        try {
            mWindowManager.removeView(mRootView);
            mVisible = false;
            // 关闭车控面板后恢复左车况区
            com.ljbyd.launcher3.PipWindowManager.getInstance(mContext).setCarInfoVisible(true);
            Log.i(TAG, "车控面板已隐藏");
        } catch (Exception e) {
            Log.e(TAG, "隐藏车控面板失败: " + e.getMessage(), e);
        }
    }

    public void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    @SuppressLint("WrongConstant")
    private void initWindowParams() {
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mLayoutParams.width = dm.widthPixels;
        mLayoutParams.height = dm.heightPixels;
        mLayoutParams.x = 0;
        mLayoutParams.y = 0;
    }

    private void initViews() {
        mRootView.findViewById(R.id.ccpCloseBtn).setOnClickListener(v -> hide());

        // ============ 空调 ============
        bindToggle(R.id.ccpAcToggle, () -> { acOn = !acOn; exec(mConfig.get(CarControlConfig.KEY_AC_TOGGLE)); refreshAc(); });
        mRootView.findViewById(R.id.ccpTempUp).setOnClickListener(v -> {
            temp = Math.min(33, temp + 1);
            exec(mConfig.get(CarControlConfig.KEY_AC_TEMP_UP));
            refreshAc();
        });
        mRootView.findViewById(R.id.ccpTempDown).setOnClickListener(v -> {
            temp = Math.max(17, temp - 1);
            exec(mConfig.get(CarControlConfig.KEY_AC_TEMP_DOWN));
            refreshAc();
        });
        mRootView.findViewById(R.id.ccpWindUp).setOnClickListener(v -> {
            windLevel = Math.min(7, windLevel + 1);
            exec(mConfig.get(CarControlConfig.KEY_AC_WIND_UP));
            refreshAc();
        });
        mRootView.findViewById(R.id.ccpWindDown).setOnClickListener(v -> {
            windLevel = Math.max(1, windLevel - 1);
            exec(mConfig.get(CarControlConfig.KEY_AC_WIND_DOWN));
            refreshAc();
        });
        mRootView.findViewById(R.id.ccpModeCycle).setOnClickListener(v -> {
            windMode = (windMode + 1) % 3;
            exec(mConfig.get(CarControlConfig.KEY_AC_MODE_CYCLE));
            refreshAc();
        });
        bindToggle(R.id.ccpCirculate, () -> { circulate = !circulate; exec(mConfig.get(CarControlConfig.KEY_AC_CIRCULATE)); refreshAc(); });
        bindToggle(R.id.ccpCompressor, () -> { compressor = !compressor; exec(mConfig.get(CarControlConfig.KEY_AC_COMPRESSOR)); refreshAc(); });
        bindToggle(R.id.ccpFrontDefrost, () -> { frontDefrost = !frontDefrost; exec(mConfig.get(CarControlConfig.KEY_AC_FRONT_DEFROST)); refreshAc(); });
        bindToggle(R.id.ccpRearDefrost, () -> { rearDefrost = !rearDefrost; exec(mConfig.get(CarControlConfig.KEY_AC_REAR_DEFROST)); refreshAc(); });
        bindToggle(R.id.ccpVentilation, () -> { ventilation = !ventilation; exec(mConfig.get(CarControlConfig.KEY_AC_VENTILATION)); refreshAc(); });
        bindToggle(R.id.ccpAirClean, () -> { airClean = !airClean; exec(mConfig.get(CarControlConfig.KEY_AC_AIR_CLEAN)); refreshAc(); });
        bindToggle(R.id.ccpAutoClean, () -> { autoClean = !autoClean; exec(mConfig.get(CarControlConfig.KEY_AC_AUTO_CLEAN)); refreshAc(); });

        // ============ 座椅 ============
        bindToggle(R.id.ccpLeftVent, () -> { leftVent = !leftVent; exec(mConfig.get(CarControlConfig.KEY_SEAT_LEFT_VENT)); refreshSeat(); });
        bindToggle(R.id.ccpRightVent, () -> { rightVent = !rightVent; exec(mConfig.get(CarControlConfig.KEY_SEAT_RIGHT_VENT)); refreshSeat(); });
        bindToggle(R.id.ccpLeftWarm, () -> { leftWarm = !leftWarm; exec(mConfig.get(CarControlConfig.KEY_SEAT_LEFT_WARM)); refreshSeat(); });
        bindToggle(R.id.ccpRightWarm, () -> { rightWarm = !rightWarm; exec(mConfig.get(CarControlConfig.KEY_SEAT_RIGHT_WARM)); refreshSeat(); });
        bindToggle(R.id.ccpSteeringWarm, () -> { steeringWarm = !steeringWarm; exec(mConfig.get(CarControlConfig.KEY_STEERING_WARM)); refreshSeat(); });

        // ============ 天窗/遮阳帘 ============
        bindToggle(R.id.ccpSunshade, () -> exec(mConfig.get(CarControlConfig.KEY_SUNSHADE)));
        bindToggle(R.id.ccpSkylight, () -> exec(mConfig.get(CarControlConfig.KEY_SKYLIGHT)));

        // ============ 能量 ============
        bindToggle(R.id.ccpSocMode, () -> {
            socForce = !socForce;
            exec(mConfig.get(CarControlConfig.KEY_SOC_MODE));
            refreshEnergy();
        });
        bindToggle(R.id.ccpRecovery, () -> {
            recovery = (recovery + 1) % 3;
            exec(mConfig.get(CarControlConfig.KEY_RECOVERY));
            refreshEnergy();
        });
        mRootView.findViewById(R.id.ccpHalfWindow).setOnClickListener(v -> exec(mConfig.get(CarControlConfig.KEY_HALF_WINDOW)));
        mRootView.findViewById(R.id.ccpCloseWindow).setOnClickListener(v -> exec(mConfig.get(CarControlConfig.KEY_CLOSE_WINDOW)));

        // ============ 灯光/其他 ============
        bindToggle(R.id.ccpDayLight, () -> { dayLight = !dayLight; exec(mConfig.get(CarControlConfig.KEY_DAY_LIGHT)); refreshOther(); });
        bindToggle(R.id.ccpHeadLight, () -> { headLight = !headLight; exec(mConfig.get(CarControlConfig.KEY_HEAD_LIGHT)); refreshOther(); });
        bindToggle(R.id.ccpWireless, () -> { wireless = !wireless; exec(mConfig.get(CarControlConfig.KEY_WIRELESS)); refreshOther(); });
        bindToggle(R.id.ccpRearDisplay, () -> { rearDisplay = !rearDisplay; exec(mConfig.get(CarControlConfig.KEY_REAR_DISPLAY)); refreshOther(); });
        bindToggle(R.id.ccpBackDoor, () -> exec(mConfig.get(CarControlConfig.KEY_BACK_DOOR)));
        bindToggle(R.id.ccpEngineSound, () -> { engineSound = !engineSound; exec(mConfig.get(CarControlConfig.KEY_ENGINE_SOUND)); refreshOther(); });
    }

    private void bindToggle(int id, Runnable action) {
        mRootView.findViewById(id).setOnClickListener(v -> action.run());
    }

    /** 执行车机命令（adb 通道；模拟器无车机时提示） */
    private void exec(String cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        try {
            mAdb.executeCommand(cmd);
        } catch (Exception e) {
            Log.e(TAG, "车控命令执行失败: " + cmd + " " + e.getMessage());
        }
    }

    // ============ 刷新 UI ============
    private void refreshAll() {
        refreshAc();
        refreshSeat();
        refreshEnergy();
        refreshOther();
    }

    private void refreshAc() {
        setText(R.id.ccpAcToggle, acOn ? "开" : "关");
        setText(R.id.ccpTempText, temp + "°C");
        setText(R.id.ccpWindText, windLevel + " 档");
        setText(R.id.ccpModeCycle, windMode == 0 ? "吹面" : windMode == 1 ? "吹脚" : "吹面+吹脚");
        setText(R.id.ccpCirculate, circulate ? "外循环" : "内循环");
        setText(R.id.ccpCompressor, compressor ? "开" : "关");
        setText(R.id.ccpFrontDefrost, frontDefrost ? "前·开" : "前");
        setText(R.id.ccpRearDefrost, rearDefrost ? "后·开" : "后");
        setText(R.id.ccpVentilation, ventilation ? "通风·开" : "通风");
        setText(R.id.ccpAirClean, airClean ? "净化·开" : "净化");
        setText(R.id.ccpAutoClean, autoClean ? "自动·开" : "自动");
    }

    private void refreshSeat() {
        setText(R.id.ccpLeftVent, leftVent ? "开" : "关");
        setText(R.id.ccpRightVent, rightVent ? "开" : "关");
        setText(R.id.ccpLeftWarm, leftWarm ? "开" : "关");
        setText(R.id.ccpRightWarm, rightWarm ? "开" : "关");
        setText(R.id.ccpSteeringWarm, steeringWarm ? "开" : "关");
    }

    private void refreshEnergy() {
        setText(R.id.ccpSocMode, socForce ? "强制保电" : "智能保电");
        setText(R.id.ccpRecovery, recovery == 0 ? "标准" : recovery == 1 ? "较强" : "最大");
    }

    private void refreshOther() {
        setText(R.id.ccpDayLight, dayLight ? "开" : "关");
        setText(R.id.ccpHeadLight, headLight ? "开" : "关");
        setText(R.id.ccpWireless, wireless ? "开" : "关");
        setText(R.id.ccpRearDisplay, rearDisplay ? "开" : "关");
        setText(R.id.ccpEngineSound, engineSound ? "开" : "关");
    }

    private void setText(int id, String text) {
        if (mRootView == null) return;
        View v = mRootView.findViewById(id);
        if (v instanceof TextView) {
            ((TextView) v).setText(text);
        } else if (v instanceof Button) {
            ((Button) v).setText(text);
        }
    }
}
