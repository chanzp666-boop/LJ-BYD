package com.ljbyd.launcher3.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 音乐可视化柱状条自定义 View
 * 对应原 WebView 前端 music.js 中的可视化频谱条
 */
public class MusicVisualizerView extends View {
    private static final int BAR_COUNT = 16;
    private final Paint barPaint = new Paint();
    private float[] barLevels = new float[BAR_COUNT];
    private int[] barColors = { 0xFF4285F4, 0xFF34A853, 0xFFFBBC04, 0xFFEA4335 };

    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setAntiAlias(true);
    }

    /**
     * 设置频谱数据（0-255 原始数据），内部归一化
     */
    public void setFrequencyData(byte[] frequency) {
        if (frequency == null || frequency.length == 0) {
            return;
        }
        int step = Math.max(1, frequency.length / BAR_COUNT);
        for (int i = 0; i < BAR_COUNT; i++) {
            int idx = Math.min(i * step, frequency.length - 1);
            int value = (frequency[idx] & 0xFF);
            // 平滑过渡，避免跳动
            float target = Math.min(1f, value / 255f);
            barLevels[i] = barLevels[i] * 0.7f + target * 0.3f;
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float barWidth = (float) getWidth() / (BAR_COUNT * 1.5f);
        float gap = barWidth * 0.5f;
        float totalBarWidth = barWidth * BAR_COUNT + gap * (BAR_COUNT - 1);
        float startX = (getWidth() - totalBarWidth) / 2f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float barHeight = Math.max(2f, barLevels[i] * getHeight());
            float left = startX + i * (barWidth + gap);
            float top = getHeight() - barHeight;
            barPaint.setColor(barColors[i % barColors.length]);
            canvas.drawRoundRect(left, top, left + barWidth, getHeight(), 2f, 2f, barPaint);
        }
    }
}
