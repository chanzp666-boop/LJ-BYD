package com.ljbyd.launcher3;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 二级画中画窗口（仿迪友 DualCardDualPip 双画中画）
 *
 * 复用主画中画的 MediaProjection（Android 同一时间仅一个授权实例），
 * 额外创建一个可拖动的悬浮窗 + VirtualDisplay，实现双应用同时镜像显示。
 */
public class SecondaryPipWindow {

    private static final String TAG = "SecondaryPipWindow";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private MediaProjection mMediaProjection;

    private LinearLayout mRootView;
    private WindowManager.LayoutParams mLayoutParams;
    private TextView mAppNameText;
    private ImageView mCloseBtn;
    private TextureView mTextureView;
    private VirtualDisplay mVirtualDisplay;

    private boolean mVisible = false;
    private boolean mMirroring = false;
    private String mCurrentPackage = null;

    private float mTouchStartX, mTouchStartY;
    private int mStartX, mStartY;
    private boolean mDragging = false;

    private static final int WINDOW_W_DP = 520;
    private static final int WINDOW_H_DP = 330;

    public SecondaryPipWindow(Context context, MediaProjection mediaProjection) {
        mContext = context.getApplicationContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mMediaProjection = mediaProjection;
    }

    /** 更新 MediaProjection（主窗授权/重建后调用） */
    public void setMediaProjection(MediaProjection projection) {
        mMediaProjection = projection;
        // 已有镜像则重建 VirtualDisplay
        if (mMirroring && mTextureView != null && projection != null) {
            restartVirtualDisplay();
        }
    }

    public boolean isVisible() {
        return mVisible;
    }

    public boolean isMirroring() {
        return mMirroring;
    }

    public String getCurrentPackage() {
        return mCurrentPackage;
    }

    @SuppressLint("WrongConstant")
    public void show() {
        if (mVisible) return;
        try {
            if (mRootView == null) {
                createView();
            }
            mWindowManager.addView(mRootView, mLayoutParams);
            mVisible = true;
            Log.i(TAG, "二级画中画窗口已显示");
        } catch (Exception e) {
            Log.e(TAG, "二级画中画显示失败: " + e.getMessage(), e);
        }
    }

    public void hide() {
        if (!mVisible) return;
        try {
            mWindowManager.removeView(mRootView);
            mVisible = false;
        } catch (Exception e) {
            Log.e(TAG, "二级画中画隐藏失败: " + e.getMessage(), e);
        }
    }

    @SuppressLint("WrongConstant")
    private void createView() {
        mRootView = new LinearLayout(mContext);
        mRootView.setOrientation(LinearLayout.VERTICAL);
        mRootView.setBackgroundColor(Color.rgb(20, 24, 34));
        mRootView.setElevation(8f);

        // 顶部栏
        LinearLayout topBar = new LinearLayout(mContext);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.rgb(17, 17, 17));
        topBar.setPadding(dip(10), 0, dip(6), 0);

        TextView title = new TextView(mContext);
        title.setText("画中画 2");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setPadding(0, 0, dip(10), 0);
        topBar.addView(title);

        mAppNameText = new TextView(mContext);
        mAppNameText.setTextColor(Color.argb(136, 255, 255, 255));
        mAppNameText.setTextSize(12);
        mAppNameText.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        mAppNameText.setGravity(Gravity.CENTER_VERTICAL);
        topBar.addView(mAppNameText);

        mCloseBtn = new ImageView(mContext);
        mCloseBtn.setImageResource(R.drawable.ic_close);
        mCloseBtn.setPadding(dip(9), dip(9), dip(9), dip(9));
        mCloseBtn.setOnClickListener(v -> close());
        topBar.addView(mCloseBtn);

        mRootView.addView(topBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dip(40)));

        // 内容区 TextureView
        mTextureView = new TextureView(mContext);
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
        mRootView.addView(mTextureView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // 拖动
        mRootView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchStartX = event.getRawX();
                    mTouchStartY = event.getRawY();
                    mStartX = mLayoutParams.x;
                    mStartY = mLayoutParams.y;
                    mDragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - mTouchStartX);
                    int dy = (int) (event.getRawY() - mTouchStartY);
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

        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mLayoutParams.width = Math.min(dip(WINDOW_W_DP), (int) (screenW * 0.5f));
        mLayoutParams.height = dip(WINDOW_H_DP);
        mLayoutParams.x = (int) (screenW * 0.45f);
        mLayoutParams.y = dip(200);
    }

    private void clampWindowPosition() {
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        mLayoutParams.x = Math.max(0, Math.min(screenW - mLayoutParams.width, mLayoutParams.x));
        mLayoutParams.y = Math.max(0, Math.min(screenH - mLayoutParams.height, mLayoutParams.y));
    }

    /**
     * 在二级画中画打开软件（复用主窗 MediaProjection）
     */
    public void startMirror(String packageName, String label) {
        if (mMediaProjection == null) {
            Toast.makeText(mContext, "请先开启主画中画（录屏授权后）", Toast.LENGTH_LONG).show();
            return;
        }
        stopVirtualDisplay();
        mCurrentPackage = packageName;
        mAppNameText.setText(label);
        show();

        // 启动软件
        try {
            Intent launchIntent = mContext.getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(launchIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "启动应用失败: " + packageName, e);
        }

        mMirroring = true;
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture != null) {
            startVirtualDisplay(surfaceTexture);
        } else {
            Log.i(TAG, "等待 TextureView 可用后创建镜像");
        }
    }

    @SuppressLint("WrongConstant")
    private void startVirtualDisplay(SurfaceTexture surfaceTexture) {
        if (mMediaProjection == null || surfaceTexture == null) return;
        try {
            Surface surface = new Surface(surfaceTexture);
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "ljbyd_pip2", mLayoutParams.width,
                    (int) (mLayoutParams.height * 0.72f),
                    mContext.getResources().getDisplayMetrics().densityDpi,
                    0, surface, null, null);
            Log.i(TAG, "二级 VirtualDisplay 创建成功");
        } catch (Exception e) {
            Log.e(TAG, "二级 VirtualDisplay 失败: " + e.getMessage(), e);
        }
    }

    private void restartVirtualDisplay() {
        stopVirtualDisplay();
        if (mTextureView != null && mTextureView.getSurfaceTexture() != null) {
            startVirtualDisplay(mTextureView.getSurfaceTexture());
        }
    }

    private void stopVirtualDisplay() {
        if (mVirtualDisplay != null) {
            try {
                mVirtualDisplay.release();
            } catch (Exception ignored) {
            }
            mVirtualDisplay = null;
        }
    }

    /** 关闭二级画中画 */
    public void close() {
        stopVirtualDisplay();
        mMirroring = false;
        mCurrentPackage = null;
        hide();
    }

    private int dip(int v) {
        return (int) (v * mContext.getResources().getDisplayMetrics().density);
    }
}
