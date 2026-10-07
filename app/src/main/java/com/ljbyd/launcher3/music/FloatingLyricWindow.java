package com.ljbyd.launcher3.music;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 悬浮歌词窗口（仿迪友 localMusicFloatLyricWindowState）
 *
 * 桌面顶部悬浮显示当前播放歌曲名 + 歌词行，可开关、可拖动。
 */
public class FloatingLyricWindow {

    private static final String TAG = "FloatingLyricWindow";
    private static final String PREFS = "lyric_window";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private LinearLayout mRootView;
    private WindowManager.LayoutParams mLayoutParams;
    private TextView mSongTitle;
    private TextView mLyricText;
    private boolean mVisible = false;

    public FloatingLyricWindow(Context context) {
        mContext = context.getApplicationContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isVisible() {
        return mVisible;
    }

    public void setEnabled(boolean on) {
        mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", on).apply();
        if (on) {
            show();
        } else {
            hide();
        }
    }

    public boolean isEnabled() {
        return mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
    }

    @SuppressLint("WrongConstant")
    public void show() {
        if (mVisible) return;
        try {
            if (mRootView == null) {
                mRootView = new LinearLayout(mContext);
                mRootView.setOrientation(LinearLayout.VERTICAL);
                mRootView.setBackgroundColor(Color.argb(140, 10, 15, 25));
                mRootView.setPadding(24, 10, 24, 10);

                mSongTitle = new TextView(mContext);
                mSongTitle.setTextColor(Color.WHITE);
                mSongTitle.setTextSize(13);
                mSongTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                mSongTitle.setSingleLine(true);
                mRootView.addView(mSongTitle);

                mLyricText = new TextView(mContext);
                mLyricText.setTextColor(Color.rgb(85, 214, 255));
                mLyricText.setTextSize(15);
                mLyricText.setSingleLine(true);
                mLyricText.setPadding(0, 6, 0, 0);
                mRootView.addView(mLyricText);

                mLayoutParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                mLayoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
                mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                mLayoutParams.y = 160;   // 顶部状态栏下方
            }
            mWindowManager.addView(mRootView, mLayoutParams);
            mVisible = true;
            Log.i(TAG, "悬浮歌词已显示");
        } catch (Exception e) {
            Log.e(TAG, "悬浮歌词显示失败: " + e.getMessage(), e);
        }
    }

    public void hide() {
        if (!mVisible) return;
        try {
            mWindowManager.removeView(mRootView);
            mVisible = false;
            Log.i(TAG, "悬浮歌词已隐藏");
        } catch (Exception e) {
            Log.e(TAG, "悬浮歌词隐藏失败: " + e.getMessage(), e);
        }
    }

    public void updateSong(String title, String artist) {
        if (!mVisible || mSongTitle == null) return;
        String text = (title == null || title.isEmpty()) ? "未在播放" : title;
        if (artist != null && !artist.isEmpty()) {
            text += " - " + artist;
        }
        mSongTitle.setText(text);
    }

    public void updateLyric(String line) {
        if (!mVisible || mLyricText == null) return;
        mLyricText.setText(line == null || line.isEmpty() ? "♪" : line);
    }
}
