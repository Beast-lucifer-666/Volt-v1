package com.example.myapplication.feature.analytics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class WeeklyChartView extends View {

    private List<Float> dataPoints = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private String[] labels = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };

    public WeeklyChartView(Context context) {
        super(context);
        init();
    }

    private void init() {
        linePaint.setColor(0xFF00E5CC);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint.setColor(0xFF00E5CC);
        dotPaint.setStyle(Paint.Style.FILL);

        dotOuterPaint.setColor(0x4000E5CC);
        dotOuterPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(0xFF7AABAA); // Matches active period color
        labelPaint.setTextSize(26f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<Float> data) {
        setData(data, null);
    }

    public void setData(List<Float> data, List<String> labels) {
        this.dataPoints = data != null ? data : new ArrayList<>();
        if (labels != null) {
            this.labels = labels.toArray(new String[0]);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataPoints.isEmpty())
            return;

        float width = getWidth();
        float height = getHeight();
        float chartBottom = height - 50f; // Increased space for labels
        float chartTop = 30f;
        float chartHeight = chartBottom - chartTop;

        int size = dataPoints.size();
        float xStep = size > 1 ? width / (size - 1) : width;

        // Find max for normalization
        float maxVal = 0;
        for (float val : dataPoints) {
            if (val > maxVal)
                maxVal = val;
        }
        if (maxVal == 0)
            maxVal = 1;

        // Calculate points
        float[] xPoints = new float[size];
        float[] yPoints = new float[size];
        for (int i = 0; i < size; i++) {
            xPoints[i] = i * xStep;
            yPoints[i] = chartBottom - (dataPoints.get(i) / maxVal * chartHeight);
        }

        if (size >= 2) {
            // Build line path with smooth curves
            linePath.reset();
            fillPath.reset();

            linePath.moveTo(xPoints[0], yPoints[0]);
            fillPath.moveTo(xPoints[0], chartBottom);
            fillPath.lineTo(xPoints[0], yPoints[0]);

            for (int i = 1; i < size; i++) {
                float cx = (xPoints[i - 1] + xPoints[i]) / 2f;
                linePath.cubicTo(cx, yPoints[i - 1], cx, yPoints[i], xPoints[i], yPoints[i]);
                fillPath.cubicTo(cx, yPoints[i - 1], cx, yPoints[i], xPoints[i], yPoints[i]);
            }

            fillPath.lineTo(xPoints[size - 1], chartBottom);
            fillPath.close();

            // Draw fill gradient
            Shader fillShader = new LinearGradient(0, chartTop, 0, chartBottom,
                    new int[]{0x6600E5CC, 0x0000E5CC}, null, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillShader);
            canvas.drawPath(fillPath, fillPaint);

            // Draw line
            canvas.drawPath(linePath, linePaint);
        }

        // Draw dots
        for (int i = 0; i < size; i++) {
            canvas.drawCircle(xPoints[i], yPoints[i], 10f, dotOuterPaint);
            canvas.drawCircle(xPoints[i], yPoints[i], 5f, dotPaint);
        }

        // Draw labels
        int labelCount = Math.min(labels.length, size);
        for (int i = 0; i < labelCount; i++) {
            if (labels[i] != null && !labels[i].isEmpty()) {
                canvas.drawText(labels[i], xPoints[i], height - 10f, labelPaint);
            }
        }
    }
}
