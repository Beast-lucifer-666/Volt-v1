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
        // Voltage - Greenish
        voltagePaint.setColor(0xFF00E676);
        voltagePaint.setStyle(Paint.Style.STROKE);
        voltagePaint.setStrokeWidth(3f);
        voltagePaint.setStrokeJoin(Paint.Join.ROUND);

        // Current - Blue
        currentPaint.setColor(0xFF4488FF);
        currentPaint.setStyle(Paint.Style.STROKE);
        currentPaint.setStrokeWidth(3f);
        currentPaint.setStrokeJoin(Paint.Join.ROUND);

        // Grid lines
        gridPaint.setColor(0x1AFFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        // Text Paint
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(24f);
    }

    public void setData(List<Float> voltage, List<Float> current, List<Float> placeholder) {
        // We adapt the existing call signature but focus on voltage and current
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

        // Draw grid
        for (int i = 1; i < 6; i++) {
            float y = height * i / 6f;
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // Draw Legend
        textPaint.setColor(0xFF00E676);
        canvas.drawText("— Voltage (V)", 20, 40, textPaint);
        textPaint.setColor(0xFF4488FF);
        canvas.drawText("— Current (A)", 20, 80, textPaint);

        // Draw Voltage line
        if (!voltageData.isEmpty()) {
            drawLine(canvas, voltageData, voltagePaint, width, height, getMin(voltageData), getMax(voltageData));
        }

        // Draw Current line
        if (!currentData.isEmpty()) {
            drawLine(canvas, currentData, currentPaint, width, height, getMin(currentData), getMax(currentData));
        }
    }

    private float getMin(List<Float> data) {
        float min = Float.MAX_VALUE;
        for (float f : data) if (f < min) min = f;
        return min;
    }

    private float getMax(List<Float> data) {
        float max = -Float.MAX_VALUE;
        for (float f : data) if (f > max) max = f;
        if (max == getMin(data)) max += 1.0f;
        return max;
    }

    private void drawLine(Canvas canvas, List<Float> data, Paint paint, float width, float height, float minVal, float maxVal) {
        if (data.size() < 2) return;

        path.reset();
        float xStep = width / (maxPoints - 1);
        int size = data.size();
        float range = maxVal - minVal;
        if (range == 0) range = 1.0f;

        for (int i = 0; i < size; i++) {
            float x = width - ((size - 1 - i) * xStep);
            float normalizedVal = (data.get(i) - minVal) / range;
            float y = height - (normalizedVal * height);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
    }
}
