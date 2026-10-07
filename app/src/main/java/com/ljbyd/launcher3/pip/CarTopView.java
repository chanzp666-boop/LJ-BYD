package com.ljbyd.launcher3.pip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 俯视小车模（像素级还原参考图左侧小车）
 * 车身 + 四轮 + 四个胎压角标（左前/右前/左后/右后）
 */
public class CarTopView extends View {

    /** 轮胎位置索引 */
    public static final int TIRE_FL = 0;  // 左前
    public static final int TIRE_FR = 1;  // 右前
    public static final int TIRE_RL = 2;  // 左后
    public static final int TIRE_RR = 3;  // 右后

    private static final int[] TIRE_IDS = {TIRE_FL, TIRE_FR, TIRE_RL, TIRE_RR};

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int[] pressures = {0, 0, 0, 0};   // 左前/右前/左后/右后 (kPa)

    public CarTopView(Context context) {
        super(context);
        init();
    }

    public CarTopView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bodyPaint.setColor(0xFF1E2A3A);
        bodyPaint.setStyle(Paint.Style.FILL);

        glassPaint.setColor(0xFF3B5269);
        glassPaint.setStyle(Paint.Style.FILL);

        wheelPaint.setColor(0xFF10151C);
        wheelPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(sp(11));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        labelPaint.setColor(0xFF8FA3B8);
        labelPaint.setTextSize(sp(9));
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    /** 设置四轮胎压 kPa */
    public void setTirePressures(int fl, int fr, int rl, int rr) {
        pressures[0] = fl;
        pressures[1] = fr;
        pressures[2] = rl;
        pressures[3] = rr;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float bodyTop = h * 0.14f;
        float bodyBottom = h * 0.86f;
        float bodyW = w * 0.56f;
        float bodyLeft = cx - bodyW / 2f;
        float bodyRight = cx + bodyW / 2f;
        float radius = w * 0.05f;

        // 车身
        RectF body = new RectF(bodyLeft, bodyTop, bodyRight, bodyBottom);
        canvas.drawRoundRect(body, radius, radius, bodyPaint);

        // 前挡风玻璃（车头在上）
        RectF frontGlass = new RectF(bodyLeft + bodyW * 0.10f, bodyTop + h * 0.06f,
                bodyRight - bodyW * 0.10f, bodyTop + h * 0.30f);
        canvas.drawRoundRect(frontGlass, w * 0.03f, w * 0.03f, glassPaint);

        // 后挡风玻璃
        RectF rearGlass = new RectF(bodyLeft + bodyW * 0.10f, bodyBottom - h * 0.30f,
                bodyRight - bodyW * 0.10f, bodyBottom - h * 0.06f);
        canvas.drawRoundRect(rearGlass, w * 0.03f, w * 0.03f, glassPaint);

        // 四个车轮（轮眉，位置对齐轮胎压角标）
        float wheelW = bodyW * 0.16f;
        float wheelH = h * 0.16f;
        float[] wheelX = {bodyLeft + bodyW * 0.06f, bodyRight - bodyW * 0.06f - wheelW,
                bodyLeft + bodyW * 0.06f, bodyRight - bodyW * 0.06f - wheelW};
        float[] wheelY = {bodyTop + h * 0.36f, bodyTop + h * 0.36f,
                bodyBottom - h * 0.36f - wheelH, bodyBottom - h * 0.36f - wheelH};
        for (int i = 0; i < 4; i++) {
            RectF wheel = new RectF(wheelX[i], wheelY[i], wheelX[i] + wheelW, wheelY[i] + wheelH);
            canvas.drawRoundRect(wheel, wheelW * 0.2f, wheelW * 0.2f, wheelPaint);
        }

        // 胎压角标（四轮外侧）
        String[] labels = {"左前", "右前", "左后", "右后"};
        float textY = h * 0.03f;
        float topRowY = wheelY[0] + wheelH / 2f;
        float bottomRowY = wheelY[2] + wheelH / 2f;
        float[] pressureY = {topRowY, topRowY, bottomRowY, bottomRowY};
        for (int i = 0; i < 4; i++) {
            float px = wheelX[i] + wheelW / 2f;
            canvas.drawText(pressures[i] + "kPa", px, pressureY[i], textPaint);
            canvas.drawText(labels[i], px, pressureY[i] + sp(13), labelPaint);
        }
    }
}
