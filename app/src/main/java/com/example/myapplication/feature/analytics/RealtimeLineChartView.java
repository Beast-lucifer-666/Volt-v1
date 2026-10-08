package com.example.myapplication.feature.analytics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class RealtimeLineChartView extends View {

    private List<Float> voltageData = new ArrayList<>();
    private List<Float> currentData = new ArrayList<>();

    private final Paint voltagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int maxPoints = 60; // Default 1 min (60 seconds)

    public RealtimeLineChartView(Context context) {
        super(context);
        init();
    }

    private void init() {
        // Voltage - Greenish (#00E676)
        voltagePaint.setColor(0xFF00E676);
        voltagePaint.setStyle(Paint.Style.STROKE);
        voltagePaint.setStrokeWidth(4f);
        voltagePaint.setStrokeJoin(Paint.Join.ROUND);
        voltagePaint.setStrokeCap(Paint.Cap.ROUND);

        // Current - Blue (#4488FF)
        currentPaint.setColor(0xFF4488FF);
        currentPaint.setStyle(Paint.Style.STROKE);
        currentPaint.setStrokeWidth(4f);
        currentPaint.setStrokeJoin(Paint.Join.ROUND);
        currentPaint.setStrokeCap(Paint.Cap.ROUND);

        // Grid lines
        gridPaint.setColor(0x1AFFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.5f);

        // Text Paint
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(26f);
    }

    public void setData(List<Float> voltage, List<Float> current, List<Float> placeholder) {
        this.voltageData = voltage != null ? new ArrayList<>(voltage) : new ArrayList<>();
        this.currentData = current != null ? new ArrayList<>(current) : new ArrayList<>();
        invalidate();
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();

        float topPadding = 60f; // Space for legend
        float bottomPadding = 30f;
        float usableHeight = height - topPadding - bottomPadding;

        if (usableHeight <= 0) return;

        // Draw Grid Lines
        for (int i = 0; i <= 4; i++) {
            float y = topPadding + (usableHeight * i / 4f);
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // Draw Legend
        textPaint.setColor(0xFF00E676);
        canvas.drawText("— Voltage (V)", 20, 38, textPaint);
        textPaint.setColor(0xFF4488FF);
        canvas.drawText("— Current (A)", 220, 38, textPaint);

        // Draw Voltage line
        if (!voltageData.isEmpty()) {
            float minV = getMin(voltageData);
            float maxV = getMax(voltageData);
            if (minV > 100f) {
                minV = Math.min(minV, 180f);
                maxV = Math.max(maxV, 260f);
            } else {
                minV = 0f;
                maxV = Math.max(maxV, 260f);
            }
            drawLine(canvas, voltageData, voltagePaint, width, topPadding, usableHeight, minV, maxV);
        }

        // Draw Current line
        if (!currentData.isEmpty()) {
            float minI = 0f;
            float maxI = Math.max(getMax(currentData), 2.0f);
            drawLine(canvas, currentData, currentPaint, width, topPadding, usableHeight, minI, maxI);
        }
    }

    private float getMin(List<Float> data) {
        float min = Float.MAX_VALUE;
        for (float f : data) if (f < min) min = f;
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private float getMax(List<Float> data) {
        float max = -Float.MAX_VALUE;
        for (float f : data) if (f > max) max = f;
        return max == -Float.MAX_VALUE ? 1f : max;
    }

    private void drawLine(Canvas canvas, List<Float> data, Paint paint, float width, float topPadding, float usableHeight, float minVal, float maxVal) {
        if (data.size() < 1) return;

        path.reset();
        float xStep = width / (maxPoints > 1 ? (maxPoints - 1) : 1);
        int size = data.size();
        float range = maxVal - minVal;
        if (range <= 0) range = 1.0f;

        for (int i = 0; i < size; i++) {
            float x = width - ((size - 1 - i) * xStep);
            float normalizedVal = (data.get(i) - minVal) / range;
            normalizedVal = Math.max(0f, Math.min(1f, normalizedVal));
            float y = topPadding + usableHeight - (normalizedVal * usableHeight);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
    }
}
