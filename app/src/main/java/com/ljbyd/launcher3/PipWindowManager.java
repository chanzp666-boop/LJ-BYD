package com.ljbyd.launcher3;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.ljbyd.launcher3.pip.CarInfoPanel;
import com.ljbyd.launcher3.service.PipProjectionService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 画中画悬浮窗管理器（对齐迪友桌面自由窗口画中画）
 *
 * 布局：悬浮窗占屏幕右侧 3/4（左侧 1/4 为桌面车辆信息区）
 * 顶部 ⋮ 三点入口 → 应用选择弹窗（本机所有已装应用，单选打勾）→ 确定
 * → 启动所选软件到前台 + MediaProjection 录屏镜像到画中画 TextureView
 * 默认显示网页地图（高德），左侧桌面同屏可见（像素级还原参考图）
 */
public class PipWindowManager {

    private static final String TAG = "PipWindowManager";
    /** 默认底图：腾讯地图网页版（参考图同款导航界面，无需申请 key） */
    private static final String MAP_URL = "https://map.qq.com/";

    private static PipWindowManager sInstance;

    private Context mContext;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private View mRootView;
    private WebView mWebView;
    private TextureView mTextureView;
    private TextView mAppNameText;
    private TextView mStatusText;
    private ImageView mSettingsBtn;
    private ImageView mCloseBtn;
    private TextView mAddWindowBtn;

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private String mCurrentPackage = null;

    // 二级画中画（仿迪友双画中画）
    private SecondaryPipWindow mSecondaryWindow;
    private boolean mDualPipEnabled = false;

    /** 左侧 1/4 车辆信息区（与画中画同步显示） */
    private CarInfoPanel mCarInfoPanel;

    private boolean mVisible = false;
    private boolean mMirroring = false;

    private float mTouchStartX, mTouchStartY;
    private int mStartX, mStartY;
    private boolean mDragging = false;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public static synchronized PipWindowManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PipWindowManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private PipWindowManager(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mCarInfoPanel = CarInfoPanel.getInstance(context);
    }

    /** 三指下滑/指令切换开-关 */
    public void toggle() {
        if (mVisible) {
            closePip();
        } else {
            show();
        }
    }

    /** 关闭画中画：释放镜像资源并隐藏窗口 */
    public void closePip() {
        stopMirror();
        hide();
    }

    public boolean isVisible() {
        return mVisible;
    }

    public boolean isMirroring() {
        return mMirroring;
    }

    /** 显示画中画悬浮窗（右侧 3/4） */
    @SuppressLint("WrongConstant")
    public void show() {
        if (mVisible) return;
        try {
            if (mRootView == null) {
                mRootView = LayoutInflater.from(mContext)
                        .inflate(R.layout.pip_window_layout, null);
                initViews();
                initWindowParams();
                mWindowManager.addView(mRootView, mLayoutParams);
                loadMapWebView();
            } else {
                mWindowManager.addView(mRootView, mLayoutParams);
            }
            mVisible = true;
            mCarInfoPanel.show();
            Log.i(TAG, "画中画已显示");
        } catch (Exception e) {
            Log.e(TAG, "显示画中画失败: " + e.getMessage(), e);
        }
    }

    /** 隐藏画中画悬浮窗 */
    public void hide() {
        if (!mVisible) return;
        try {
            mWindowManager.removeView(mRootView);
            mVisible = false;
            mCarInfoPanel.hide();
            Log.i(TAG, "画中画已隐藏");
        } catch (Exception e) {
            Log.e(TAG, "隐藏画中画失败: " + e.getMessage(), e);
        }
    }

    /** 车控面板打开时隐藏左车况区，关闭时恢复（避免 z 序重叠） */
    public void setCarInfoVisible(boolean visible) {
        try {
            if (mCarInfoPanel != null) {
                if (visible) {
                    if (mVisible) mCarInfoPanel.show();
                } else {
                    mCarInfoPanel.hide();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "切换车况区显示失败", e);
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
        // 右侧 3/4，左侧 1/4 留给车辆信息区；顶部下移避开状态栏/系统手势触摸区
        mLayoutParams.width = (int) (screenW * 0.75f);
        mLayoutParams.height = screenH - topOffset;
        mLayoutParams.x = (int) (screenW * 0.25f);
        mLayoutParams.y = topOffset;
    }

    /**
     * 屏幕旋转/尺寸变化时重新布局（车载横屏场景，兼容模拟器横竖屏切换）
     */
    public void onDisplayMetricsChanged() {
        if (mLayoutParams == null || mRootView == null) return;
        try {
            boolean wasVisible = mVisible;
            if (wasVisible) {
                mWindowManager.removeView(mRootView);
            }
            // 重新按当前屏幕尺寸计算位置尺寸
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;
            int topOffset = getStatusBarHeight() + dip(90);
            mLayoutParams.width = (int) (screenW * 0.75f);
            mLayoutParams.height = screenH - topOffset;
            mLayoutParams.x = (int) (screenW * 0.25f);
            mLayoutParams.y = topOffset;
            if (wasVisible) {
                mWindowManager.addView(mRootView, mLayoutParams);
            }
            // 左侧车辆信息区同步自适应
            mCarInfoPanel.onDisplayMetricsChanged();
            Log.i(TAG, "画中画布局已按新屏幕调整 " + screenW + "x" + screenH);
        } catch (Exception e) {
            Log.e(TAG, "调整画中画布局失败: " + e.getMessage(), e);
        }
    }

    private int dip(int v) {
        return (int) (v * mContext.getResources().getDisplayMetrics().density);
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

    @SuppressLint("SetJavaScriptEnabled")
    private void initViews() {
        mWebView = mRootView.findViewById(R.id.pipWebView);
        mTextureView = mRootView.findViewById(R.id.pipTextureView);
        mAppNameText = mRootView.findViewById(R.id.pipAppName);
        mStatusText = mRootView.findViewById(R.id.pipStatusText);
        mSettingsBtn = mRootView.findViewById(R.id.pipSettingsBtn);
        mCloseBtn = mRootView.findViewById(R.id.pipCloseBtn);

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        mWebView.setWebViewClient(new WebViewClient());

        // ⋮ 三点 → 应用选择弹窗
        mSettingsBtn.setOnClickListener(v -> {
            Log.i(TAG, "三点设置按钮被点击");
            showAppPicker();
        });
        // ＋画中画（二级窗口入口，双画中画模式开启时显示）
        TextView addWindowBtn = mRootView.findViewById(R.id.pipAddWindowBtn);
        mAddWindowBtn = addWindowBtn;
        addWindowBtn.setOnClickListener(v -> {
            Log.i(TAG, "二级画中画入口被点击");
            if (mSecondaryWindow != null && mSecondaryWindow.isVisible()) {
                // 已在显示：重新选择应用
                showSecondaryAppPicker();
            } else {
                showSecondaryAppPicker();
            }
        });
        // 关闭按钮
        mCloseBtn.setOnClickListener(v -> {
            Log.i(TAG, "关闭按钮被点击");
            closePip();
        });

        // 窗口拖动（整窗拖动；DOWN 不拦截，保证子 View 点击正常）
        mRootView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchStartX = event.getRawX();
                    mTouchStartY = event.getRawY();
                    mStartX = mLayoutParams.x;
                    mStartY = mLayoutParams.y;
                    mDragging = false;
                    return false; // 不消费 DOWN，子 View（关闭/三点）可正常点击
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - mTouchStartX);
                    int dy = (int) (event.getRawY() - mTouchStartY);
                    // 位移超过阈值才开始拖动（避免与点击冲突）
                    if (!mDragging && (Math.abs(dx) > 20 || Math.abs(dy) > 20)) {
                        mDragging = true;
                    }
                    if (mDragging) {
                        mLayoutParams.x = mStartX + dx;
                        mLayoutParams.y = mStartY + dy;
                        clampWindowPosition();
                        try {
                            mWindowManager.updateViewLayout(mRootView, mLayoutParams);
                        } catch (Exception ignored) {
                        }
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    return false;
            }
            return false;
        });

        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                if (mMirroring) {
                    startVirtualDisplay(surfaceTexture);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });
    }

    private void clampWindowPosition() {
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        mLayoutParams.x = Math.max(0, Math.min(screenW - mLayoutParams.width, mLayoutParams.x));
        mLayoutParams.y = Math.max(0, Math.min(screenH - mLayoutParams.height, mLayoutParams.y));
    }

    /** 加载默认网页地图（高德） */
    private void loadMapWebView() {
        mWebView.loadUrl(MAP_URL);
    }

    /** 应用选择弹窗（⋮）：本机所有应用，单选打勾，确定后镜像打开 */
    public void showAppPicker() {
        // 弹窗属于 MainActivity 层，z 低于悬浮窗；打开前暂藏悬浮窗避免遮挡
        boolean wasVisible = mVisible;
        if (wasVisible) {
            hide();
        }
        List<ResolveInfo> apps = queryLaunchableApps();
        if (apps.isEmpty()) {
            Toast.makeText(mContext, "未获取到应用列表", Toast.LENGTH_SHORT).show();
            if (wasVisible) show();
            return;
        }

        List<Map<String, Object>> data = new ArrayList<>();
        PackageManager pm = mContext.getPackageManager();
        for (ResolveInfo info : apps) {
            Map<String, Object> row = new HashMap<>();
            row.put("icon", info.activityInfo.loadIcon(pm));
            row.put("name", info.loadLabel(pm).toString());
            row.put("pkg", info.activityInfo.packageName);
            row.put("checked", Boolean.FALSE);
            data.add(row);
        }

        final int[] selected = {0};
        SimpleAdapter adapter = new SimpleAdapter(
                mContext, data, R.layout.item_pip_app_picker,
                new String[]{"icon", "name"},
                new int[]{R.id.pipPickerIcon, R.id.pipPickerName}) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                ImageView check = v.findViewById(R.id.pipPickerCheck);
                boolean isSel = position == selected[0];
                check.setVisibility(isSel ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
                check.setImageResource(isSel ? R.drawable.ic_check_circle : 0);
                return v;
            }
        };

        ListView listView = new ListView(mContext);
        listView.setAdapter(adapter);
        listView.setDividerHeight(1);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selected[0] = position;
            adapter.notifyDataSetChanged();
        });

        // 弹窗需要 Activity context（悬浮窗 Manager 持有的是 applicationContext）
        Context dialogContext = MainActivity.instance != null ? MainActivity.instance : mContext;
        final boolean restoreAfter = wasVisible;
        new AlertDialog.Builder(dialogContext)
                .setTitle("选择在画中画显示的软件")
                .setView(listView)
                .setPositiveButton("确定", (dialog, which) -> {
                    ResolveInfo chosen = apps.get(selected[0]);
                    String pkg = chosen.activityInfo.packageName;
                    String label = chosen.loadLabel(mContext.getPackageManager()).toString();
                    // 关闭弹窗后恢复悬浮窗，再启动镜像流程
                    if (restoreAfter) {
                        show();
                    }
                    startMirror(pkg, label);
                })
                .setNegativeButton("取消", null)
                .setOnDismissListener(d -> {
                    if (restoreAfter) {
                        show();
                    }
                })
                .show();
    }

    /** 查询本机所有可启动应用 */
    private List<ResolveInfo> queryLaunchableApps() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = mContext.getPackageManager();
        return pm.queryIntentActivities(mainIntent, 0);
    }

    /**
     * 设置双画中画模式（true：画中画窗口显示 ＋画中画 入口）
     */
    public void setDualPipEnabled(boolean on) {
        mDualPipEnabled = on;
        if (mAddWindowBtn != null) {
            mAddWindowBtn.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    public boolean isDualPipEnabled() {
        return mDualPipEnabled;
    }

    /** 二级画中画：应用选择弹窗（与主窗 ⋮ 同款） */
    private void showSecondaryAppPicker() {
        // 主窗镜像未授权时二级窗不可用
        if (mMediaProjection == null) {
            Toast.makeText(mContext, "请先在主画中画完成录屏授权", Toast.LENGTH_LONG).show();
            return;
        }
        if (mSecondaryWindow == null) {
            mSecondaryWindow = new SecondaryPipWindow(mContext, mMediaProjection);
        }
        List<ResolveInfo> apps = queryLaunchableApps();
        if (apps.isEmpty()) {
            Toast.makeText(mContext, "未获取到应用列表", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Map<String, Object>> data = new ArrayList<>();
        PackageManager pm = mContext.getPackageManager();
        for (ResolveInfo info : apps) {
            Map<String, Object> row = new HashMap<>();
            row.put("icon", info.activityInfo.loadIcon(pm));
            row.put("name", info.loadLabel(pm).toString());
            row.put("pkg", info.activityInfo.packageName);
            row.put("checked", Boolean.FALSE);
            data.add(row);
        }

        final int[] selected = {0};
        SimpleAdapter adapter = new SimpleAdapter(
                mContext, data, R.layout.item_pip_app_picker,
                new String[]{"icon", "name"},
                new int[]{R.id.pipPickerIcon, R.id.pipPickerName}) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                ImageView check = v.findViewById(R.id.pipPickerCheck);
                boolean isSel = position == selected[0];
                check.setVisibility(isSel ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
                check.setImageResource(isSel ? R.drawable.ic_check_circle : 0);
                return v;
            }
        };

        ListView listView = new ListView(mContext);
        listView.setAdapter(adapter);
        listView.setDividerHeight(1);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selected[0] = position;
            adapter.notifyDataSetChanged();
        });

        Context dialogContext = MainActivity.instance != null ? MainActivity.instance : mContext;
        new AlertDialog.Builder(dialogContext)
                .setTitle("选择在二级画中画显示的软件")
                .setView(listView)
                .setPositiveButton("确定", (dialog, which) -> {
                    ResolveInfo chosen = apps.get(selected[0]);
                    String pkg = chosen.activityInfo.packageName;
                    String label = chosen.loadLabel(mContext.getPackageManager()).toString();
                    mSecondaryWindow.startMirror(pkg, label);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 在画中画窗口打开所选软件（镜像方案）：
     * 1. 先启动前台服务保活（避免授权流程中进程被杀）
     * 2. 发起 MediaProjection 录屏授权（MainActivity.onActivityResult 回调）
     * 3. 授权完成后启动软件到前台 + VirtualDisplay 镜像到 TextureView
     */
    public void startMirror(String packageName, String label) {
        // 释放上一次的镜像（如有），避免重复授权/重复 VirtualDisplay
        stopMirror();

        mCurrentPackage = packageName;
        mAppNameText.setText(label);
        mStatusText.setText("正在启动 " + label + " …");

        // 前台服务保活：授权/镜像期间进程保持前台，避免被系统回收
        PipProjectionService.start(mContext);

        requestProjectionPermission();
    }

    /**
     * 请求录屏授权（由 MainActivity 发起 startActivityForResult）
     */
    private void requestProjectionPermission() {
        Intent request = new Intent(mContext, MainActivity.class);
        request.setAction(MainActivity.ACTION_REQUEST_PROJECTION);
        request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(request);
    }

    /**
     * MainActivity.onActivityResult 回调：授权完成后创建镜像
     */
    public void onProjectionReady(int resultCode, Intent data) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            Log.w(TAG, "录屏授权被拒绝");
            mStatusText.setText("录屏授权被拒绝，点击 ⋮ 重新选择");
            PipProjectionService.stop(mContext);
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager)
                mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mMediaProjection == null) {
            Log.e(TAG, "获取 MediaProjection 失败");
            mStatusText.setText("镜像创建失败");
            PipProjectionService.stop(mContext);
            return;
        }

        // 前台服务保持镜像稳定
        PipProjectionService.start(mContext);

        // 同步 MediaProjection 给二级画中画
        if (mSecondaryWindow != null) {
            mSecondaryWindow.setMediaProjection(mMediaProjection);
        }

        // 启动所选软件到前台（授权完成后，避免抢占授权页导致进程被回收）
        if (mCurrentPackage != null) {
            Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(mCurrentPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(launchIntent);
            }
        }

        // 切换视图：显示 TextureView 隐藏 WebView
        mWebView.setVisibility(View.GONE);
        mTextureView.setVisibility(View.VISIBLE);
        mMirroring = true;
        mStatusText.setText("正在显示软件画面…");

        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture != null) {
            startVirtualDisplay(surfaceTexture);
        }
        Log.i(TAG, "镜像启动: " + mCurrentPackage);
    }

    /** 创建 VirtualDisplay 镜像到 TextureView */
    @SuppressLint("WrongConstant")
    private void startVirtualDisplay(SurfaceTexture surfaceTexture) {
        if (mMediaProjection == null) return;
        try {
            DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
            int w = mLayoutParams.width;
            int h = (int) (mLayoutParams.height * 0.7f);
            Surface surface = new Surface(surfaceTexture);
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "ljbyd_pip", w, h, dm.densityDpi,
                    0 /* VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR */, surface, null, null);
            mStatusText.setText("画中画显示: " + mAppNameText.getText());
            Log.i(TAG, "VirtualDisplay 创建成功 " + w + "x" + h);
        } catch (Exception e) {
            Log.e(TAG, "创建 VirtualDisplay 失败: " + e.getMessage(), e);
            mStatusText.setText("镜像失败");
        }
    }

    /** 停止镜像，恢复地图视图 */
    public void stopMirror() {
        // 关闭二级画中画（如有）
        if (mSecondaryWindow != null) {
            mSecondaryWindow.close();
        }
        if (mVirtualDisplay != null) {
            try {
                mVirtualDisplay.release();
            } catch (Exception ignored) {
            }
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            try {
                mMediaProjection.stop();
            } catch (Exception ignored) {
            }
            mMediaProjection = null;
        }
        mMirroring = false;
        mCurrentPackage = null;
        PipProjectionService.stop(mContext);

        mWebView.setVisibility(View.VISIBLE);
        mTextureView.setVisibility(View.GONE);
        mAppNameText.setText("");
        mStatusText.setText("点击 ⋮ 选择在画中画显示的软件");
    }

    /** 应用选择弹窗单项布局 */
    public static class PickerViewHolder {
    }
}
