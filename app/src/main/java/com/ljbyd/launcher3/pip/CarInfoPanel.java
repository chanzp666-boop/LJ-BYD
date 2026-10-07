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
 * 左侧 1/4 车辆信息区悬浮窗（像素级还原参考图左侧）
 *
 * 内容：挡位 / 小车模（四轮胎压）/ 综合油耗 / 行驶-里程-能耗
 *       4 个车控快捷按钮（半开窗、关窗、动能回收、保电模式）
 * 车控命令走 CarControlConfig 可配置模板 + AdbCommandProcessor 车机通道执行
 */
public class CarInfoPanel {

    private static final String TAG = "CarInfoPanel";

    private static CarInfoPanel sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private View mRootView;

    private TextView mGearText;
    private TextView mBatteryModeText;
    private CarTopView mCarTopView;
    private TextView mFuelText;
    private TextView mDriveTimeText;
    private TextView mMileageText;
    private TextView mEnergyText;
    private TextView mRecoveryStateText;
    private TextView mBatteryStateText;

    private AdbCommandProcessor mAdbProcessor;
    private CarControlConfig mCarConfig;
    private boolean mVisible = false;

    /** 本地 UI 状态（真实状态需车机接口，可配置接入） */
    private boolean mRecoveryStandard = true;  // true=标准  false=较大
    private boolean mForceBattery = false;     // true=强制保电 false=智能保电

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public static synchronized CarInfoPanel getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CarInfoPanel(context.getApplicationContext());
        }
        return sInstance;
    }

    private CarInfoPanel(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mAdbProcessor = new AdbCommandProcessor(context);
        mCarConfig = new CarControlConfig(context);
    }

    public boolean isVisible() {
        return mVisible;
    }

    /** 显示车辆信息区（左 1/4） */
    @SuppressLint("WrongConstant")
    public void show() {
        if (mVisible) return;
        try {
            if (mRootView == null) {
                mRootView = LayoutInflater.from(mContext)
                        .inflate(R.layout.car_info_panel_layout, null);
                initViews();
                initWindowParams();
                mWindowManager.addView(mRootView, mLayoutParams);
            } else {
                mWindowManager.addView(mRootView, mLayoutParams);
            }
            mVisible = true;
            Log.i(TAG, "车辆信息区已显示");
        } catch (Exception e) {
            Log.e(TAG, "显示车辆信息区失败: " + e.getMessage(), e);
        }
    }

    /** 隐藏车辆信息区 */
    public void hide() {
        if (!mVisible) return;
        try {
            mWindowManager.removeView(mRootView);
            mVisible = false;
            Log.i(TAG, "车辆信息区已隐藏");
        } catch (Exception e) {
            Log.e(TAG, "隐藏车辆信息区失败: " + e.getMessage(), e);
        }
    }

    private void initWindowParams() {
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int topOffset = getStatusBarHeight() + dip(90);

        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mLayoutParams.gravity = Gravity.TOP | Gravity.START;
        // 左 1/4，右侧 3/4 留给画中画窗口；顶部下移与画中画窗口对齐
        mLayoutParams.width = (int) (screenW * 0.25f);
        mLayoutParams.height = screenH - topOffset;
        mLayoutParams.x = 0;
        mLayoutParams.y = topOffset;
    }

    /** 屏幕旋转/尺寸变化时重新布局（与画中画窗口同步自适应） */
    public void onDisplayMetricsChanged() {
        if (mLayoutParams == null || mRootView == null) return;
        try {
            boolean wasVisible = mVisible;
            if (wasVisible) {
                mWindowManager.removeView(mRootView);
            }
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;
            int topOffset = getStatusBarHeight() + dip(90);
            mLayoutParams.width = (int) (screenW * 0.25f);
            mLayoutParams.height = screenH - topOffset;
            mLayoutParams.x = 0;
            mLayoutParams.y = topOffset;
            if (wasVisible) {
                mWindowManager.addView(mRootView, mLayoutParams);
            }
            Log.i(TAG, "车辆信息区布局已按新屏幕调整 " + screenW + "x" + screenH);
        } catch (Exception e) {
            Log.e(TAG, "调整车辆信息区布局失败: " + e.getMessage(), e);
        }
    }

    private int getStatusBarHeight() {
        try {
            int resId = mContext.getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
            if (resId > 0) {
                return mContext.getResources().getDimensionPixelSize(resId);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int dip(int v) {
        return (int) (v * mContext.getResources().getDisplayMetrics().density);
    }

    private void initViews() {
        mGearText = mRootView.findViewById(R.id.gearText);
        mBatteryModeText = mRootView.findViewById(R.id.batteryModeText);
        mCarTopView = mRootView.findViewById(R.id.carTopView);
        mFuelText = mRootView.findViewById(R.id.fuelText);
        mDriveTimeText = mRootView.findViewById(R.id.driveTimeText);
        mMileageText = mRootView.findViewById(R.id.mileageText);
        mEnergyText = mRootView.findViewById(R.id.energyText);
        mRecoveryStateText = mRootView.findViewById(R.id.recoveryStateText);
        mBatteryStateText = mRootView.findViewById(R.id.batteryStateText);

        Button btnHalfWindow = mRootView.findViewById(R.id.btnHalfWindow);
        Button btnCloseWindow = mRootView.findViewById(R.id.btnCloseWindow);
        Button btnRecovery = mRootView.findViewById(R.id.btnRecovery);
        Button btnBatteryMode = mRootView.findViewById(R.id.btnBatteryMode);

        // 默认数据（参考图状态：未连接时胎压 0kPa / 能耗 0.0）
        mCarTopView.setTirePressures(0, 0, 0, 0);
        refreshStates();

        btnHalfWindow.setOnClickListener(v ->
                runCarCommand(mCarConfig.getHalfWindow(), "全车半开窗"));
        btnCloseWindow.setOnClickListener(v ->
                runCarCommand(mCarConfig.getCloseWindow(), "全车关窗"));
        btnRecovery.setOnClickListener(v -> {
            // 动能回收切换（标准 ↔ 较大），同时发送车控命令
            mRecoveryStandard = !mRecoveryStandard;
            refreshStates();
            runCarCommand(mCarConfig.getRecovery(),
                    mRecoveryStandard ? "动能回收:标准" : "动能回收:较大");
        });
        btnBatteryMode.setOnClickListener(v -> {
            // 强制保电 ↔ 智能保电
            mForceBattery = !mForceBattery;
            refreshStates();
            runCarCommand(mCarConfig.getBatteryMode(),
                    mForceBattery ? "已切换强制保电" : "已切换智能保电");
        });

        // 完整车控面板入口（仿迪友全功能车控）
        mRootView.findViewById(R.id.btnFullControl).setOnClickListener(v -> {
            CarControlPanel.getInstance(mContext).toggle();
        });
    }

    /** 刷新保电/回收 UI 状态 */
    private void refreshStates() {
        mBatteryModeText.setText(mForceBattery ? "强制 100%" : "强制 0%");
        mBatteryStateText.setText(mForceBattery ? "保电:强制" : "保电:智能");
        mRecoveryStateText.setText(mRecoveryStandard ? "回收:标准" : "回收:较大");
    }

    /** 执行车控命令（车机 adb 通道，失败提示） */
    private void runCarCommand(String command, String label) {
        Toast.makeText(mContext, label + " 执行中…", Toast.LENGTH_SHORT).show();
        final String cmd = command;
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = mAdbProcessor.executeCommand(cmd);
            } catch (Exception e) {
                Log.e(TAG, "车控命令异常: " + e.getMessage(), e);
            }
            final boolean success = ok;
            mHandler.post(() -> Toast.makeText(mContext,
                    success ? label + " 已发送" : label + " 失败（检查车机ADB连接/命令配置）",
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    /** 更新四轮胎压（外部数据源可调用） */
    public void updateTirePressure(int fl, int fr, int rl, int rr) {
        mCarTopView.setTirePressures(fl, fr, rl, rr);
    }

    /** 更新综合油耗（外部数据源可调用） */
    public void updateFuel(float fuelPer100km) {
        if (mFuelText != null) {
            mFuelText.setText(String.format(java.util.Locale.CHINA, "%.1fL/100km", fuelPer100km));
        }
    }
}
