package com.example.myapplication.feature.analytics;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.myapplication.EnergyRepository;
import com.example.myapplication.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AnalyticsFragment extends Fragment {

    private AnalyticsViewModel viewModel;
    private RealtimeLineChartView realtimeChart;
    private WeeklyChartView weeklyChart;

    // 6 Main Stats
    private TextView tvVoltageVal, tvCurrentVal, tvActivePowerVal, tvActiveEnergyVal, tvFrequencyVal, tvPowerFactorVal;
    
    // Bottom Stats
    private TextView tvTotalKwhValue, tvKwhTodayVal, tvBillEstVal, tvCo2Val;
    private TextView tvPredictedBillVal, tvPredictedSubtitle, tvBillStart, tvBillToday, tvBillEnd;
    private TextView tvTotalKwhLabel, tvTotalKwhSubtitle;
    private ProgressBar billProgressBar;

    // Buttons
    private TextView btnDay, btnWeek, btnMonth, btnYear;
    private Button btnPrint;

    // Usage bar views
    private View[] usageBarViews = new View[6];

    private static final String[] TIME_LABELS = { "12–6 AM", "6–9 AM", "9 AM–12", "12–3 PM", "3–6 PM", "6 PM–12" };
    private static final int[][] BAR_COLORS = {
            { 0xFF00B4D8, 0xFF4488FF }, // Teal → Blue
            { 0xFF00B4D8, 0xFF00E5CC }, // Teal → Cyan
            { 0xFF00C853, 0xFF00E5CC }, // Green → Cyan
            { 0xFFFF6B1A, 0xFFFF4444 }, // Orange → Red
            { 0xFF00C853, 0xFF00E5CC }, // Green → Cyan
            { 0xFF00B4D8, 0xFF4488FF }, // Teal → Blue
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_analytics, container, false);
        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);

        initViews(view);
        setupPeriodSelector();
        setupScaleSpinner(view);
        setupCharts(view);
        setupUsageBars(view);
        observeViewModel();

        return view;
    }

    private void initViews(View view) {
        // 6 Grid Stats
        tvVoltageVal = view.findViewById(R.id.tvVoltageVal);
        tvCurrentVal = view.findViewById(R.id.tvCurrentVal);
        tvActivePowerVal = view.findViewById(R.id.tvActivePowerVal);
        tvActiveEnergyVal = view.findViewById(R.id.tvActiveEnergyVal);
        tvFrequencyVal = view.findViewById(R.id.tvFrequencyVal);
        tvPowerFactorVal = view.findViewById(R.id.tvPowerFactorVal);

        // Summary Stats
        tvTotalKwhValue = view.findViewById(R.id.tvTotalKwhValue);
        tvKwhTodayVal = view.findViewById(R.id.tvKwhTodayVal);
        tvBillEstVal = view.findViewById(R.id.tvBillEstVal);
        tvCo2Val = view.findViewById(R.id.tvCo2Val);
        
        // Total kWh dynamic labels
        tvTotalKwhLabel = view.findViewById(R.id.tvTotalKwhLabel);
        tvTotalKwhSubtitle = view.findViewById(R.id.tvTotalKwhSubtitle);
        if (tvTotalKwhSubtitle == null) {
            // If ID not found, find by position or just ignore. In typical layouts it's the 2nd child of card
            View card = view.findViewById(R.id.cardTotalKwh);
            if (card instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) card;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof TextView && child != tvTotalKwhLabel && child != tvTotalKwhValue) {
                        tvTotalKwhSubtitle = (TextView) child;
                        break;
                    }
                }
            }
        }

        // Predicted Bill
        tvPredictedBillVal = view.findViewById(R.id.tvPredictedBillVal);
        tvPredictedSubtitle = view.findViewById(R.id.tvPredictedSubtitle);
        tvBillStart = view.findViewById(R.id.tvBillStart);
        tvBillToday = view.findViewById(R.id.tvBillToday);
        tvBillEnd = view.findViewById(R.id.tvBillEnd);
        billProgressBar = view.findViewById(R.id.billProgressBar);

        View cardBill = view.findViewById(R.id.cardBill);
        if (cardBill != null) {
            cardBill.setOnClickListener(v -> showBudgetDialog());
        }

        // Navigation/Action Buttons
        btnDay = view.findViewById(R.id.btnDay);
        btnWeek = view.findViewById(R.id.btnWeek);
        btnMonth = view.findViewById(R.id.btnMonth);
        btnYear = view.findViewById(R.id.btnYear);
        btnPrint = view.findViewById(R.id.btnPrint);

        if (btnPrint != null) {
            btnPrint.setOnClickListener(v -> captureAndSaveAnalytics());
        }

        // Set real-world / device date values in bill card
        updateBillCardDates();
        
        Double currentBill = viewModel.getPredictedBill().getValue();
        updateBillProgress(currentBill != null ? currentBill : 0.0);
    }

    private void updateBillCardDates() {
        Calendar cal = Calendar.getInstance();
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        SimpleDateFormat fullMonthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        String monthYearStr = fullMonthYearFormat.format(cal.getTime());

        SimpleDateFormat shortMonthFormat = new SimpleDateFormat("MMM", Locale.US);
        String shortMonthStr = shortMonthFormat.format(cal.getTime());

        if (tvPredictedSubtitle != null) {
            tvPredictedSubtitle.setText(String.format(Locale.US, "End of %s - ₹7/unit", monthYearStr));
        }

        if (tvBillStart != null) {
            tvBillStart.setText(String.format(Locale.US, "%s 1", shortMonthStr));
        }

        if (tvBillToday != null) {
            tvBillToday.setText(String.format(Locale.US, "Today · Day %d", dayOfMonth));
        }

        if (tvBillEnd != null) {
            tvBillEnd.setText(String.format(Locale.US, "%s %d", shortMonthStr, maxDaysInMonth));
        }
    }

    private void captureAndSaveAnalytics() {
        if (realtimeChart == null) return;

        // Create a bitmap that includes the chart and the data below it
        int width = realtimeChart.getWidth();
        int chartHeight = realtimeChart.getHeight();
        int dataHeight = 400; // Extra space for text data
        int totalHeight = chartHeight + dataHeight;

        Bitmap bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Draw background
        canvas.drawColor(0xFF111D1C); // bg_dark color

        // Draw the chart
        realtimeChart.draw(canvas);

        // Draw the data below the chart
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(36f);
        paint.setFakeBoldText(true);

        float startY = chartHeight + 60;
        float spacing = 50;

        canvas.drawText("Current Analytics Report", 40, startY, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(30f);
        
        startY += 60;
        canvas.drawText("Voltage: " + (tvVoltageVal != null ? tvVoltageVal.getText() : ""), 40, startY, paint);
        startY += spacing;
        canvas.drawText("Current: " + (tvCurrentVal != null ? tvCurrentVal.getText() : ""), 40, startY, paint);
        startY += spacing;
        canvas.drawText("Active Power: " + (tvActivePowerVal != null ? tvActivePowerVal.getText() : ""), 40, startY, paint);
        startY += spacing;
        canvas.drawText("Active Energy: " + (tvActiveEnergyVal != null ? tvActiveEnergyVal.getText() : ""), 40, startY, paint);
        startY += spacing;
        canvas.drawText("Frequency: " + (tvFrequencyVal != null ? tvFrequencyVal.getText() : ""), 40, startY, paint);
        startY += spacing;
        canvas.drawText("Power Factor: " + (tvPowerFactorVal != null ? tvPowerFactorVal.getText() : ""), 40, startY, paint);

        // Add Timestamp
        paint.setTextSize(24f);
        paint.setColor(0xFF7AABAA);
        canvas.drawText("Generated on: " + Calendar.getInstance().getTime().toString(), 40, startY + 60, paint);

        saveBitmapToGallery(bitmap);
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String filename = "Analytics_" + System.currentTimeMillis() + ".png";
        OutputStream fos;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Analytics");

                Uri imageUri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                fos = requireContext().getContentResolver().openOutputStream(imageUri);
            } else {
                String imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
                File file = new File(imagesDir, filename);
                fos = new FileOutputStream(file);
            }

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            if (fos != null) fos.close();

            Toast.makeText(getContext(), "Report saved to Gallery", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error saving report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupPeriodSelector() {
        View.OnClickListener listener = v -> {
            EnergyRepository.Period selectedPeriod;
            String label;
            String subtitle;
            
            if (v.getId() == R.id.btnDay) {
                selectedPeriod = EnergyRepository.Period.DAY;
                label = "Total kWh (Today)";
                subtitle = "Whole home · Today";
            } else if (v.getId() == R.id.btnWeek) {
                selectedPeriod = EnergyRepository.Period.WEEK;
                label = "Total kWh (Weekly)";
                subtitle = "Whole home · This week";
            } else if (v.getId() == R.id.btnMonth) {
                selectedPeriod = EnergyRepository.Period.MONTH;
                label = "Total kWh (Monthly)";
                subtitle = "Whole home · This month";
            } else {
                selectedPeriod = EnergyRepository.Period.YEAR;
                label = "Total kWh (Yearly)";
                subtitle = "Whole home · This year";
            }

            // Update UI
            updatePeriodButtons(v.getId());
            if (tvTotalKwhLabel != null) tvTotalKwhLabel.setText(label);
            if (tvTotalKwhSubtitle != null) tvTotalKwhSubtitle.setText(subtitle);
            
            // Trigger Data Update
            viewModel.setPeriod(selectedPeriod);
        };

        btnDay.setOnClickListener(listener);
        btnWeek.setOnClickListener(listener);
        btnMonth.setOnClickListener(listener);
        btnYear.setOnClickListener(listener);
    }

    private void updatePeriodButtons(int activeId) {
        btnDay.setBackgroundResource(activeId == R.id.btnDay ? R.drawable.bg_period_active : R.drawable.bg_period_inactive);
        btnDay.setTextColor(activeId == R.id.btnDay ? 0xFFFFFFFF : 0xFF7AABAA);
        
        btnWeek.setBackgroundResource(activeId == R.id.btnWeek ? R.drawable.bg_period_active : R.drawable.bg_period_inactive);
        btnWeek.setTextColor(activeId == R.id.btnWeek ? 0xFFFFFFFF : 0xFF7AABAA);
        
        btnMonth.setBackgroundResource(activeId == R.id.btnMonth ? R.drawable.bg_period_active : R.drawable.bg_period_inactive);
        btnMonth.setTextColor(activeId == R.id.btnMonth ? 0xFFFFFFFF : 0xFF7AABAA);
        
        btnYear.setBackgroundResource(activeId == R.id.btnYear ? R.drawable.bg_period_active : R.drawable.bg_period_inactive);
        btnYear.setTextColor(activeId == R.id.btnYear ? 0xFFFFFFFF : 0xFF7AABAA);
    }

    private void setupScaleSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.spinnerScale);
        if (spinner == null) return;

        String[] scales = { "1 min", "3 mins", "5 mins", "10 mins" };

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_spinner_item, scales) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView) v).setTextColor(0xFFFFFFFF);
                ((TextView) v).setTextSize(12);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                ((TextView) v).setTextColor(0xFFFFFFFF);
                v.setBackgroundColor(0xFF111D1C);
                v.setPadding(24, 16, 24, 16);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupCharts(View view) {
        // Real-time chart
        realtimeChart = new RealtimeLineChartView(requireContext());
        FrameLayout realtimeContainer = view.findViewById(R.id.realtimeChartContainer);
        if (realtimeContainer != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            realtimeChart.setLayoutParams(params);
            realtimeContainer.addView(realtimeChart);
        }

        // Weekly chart
        weeklyChart = new WeeklyChartView(requireContext());
        FrameLayout weeklyContainer = view.findViewById(R.id.weeklyChartContainer);
        if (weeklyContainer != null) {
            FrameLayout.LayoutParams weeklyParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            weeklyChart.setLayoutParams(weeklyParams);
            weeklyContainer.addView(weeklyChart);
        }
    }

    private void setupUsageBars(View view) {
        int[] barIds = { R.id.usageBar0, R.id.usageBar1, R.id.usageBar2, R.id.usageBar3, R.id.usageBar4,
                R.id.usageBar5 };

        for (int i = 0; i < 6; i++) {
            usageBarViews[i] = view.findViewById(barIds[i]);
            if (usageBarViews[i] != null) {
                TextView label = usageBarViews[i].findViewById(R.id.tvUsageLabel);
                if (label != null) label.setText(TIME_LABELS[i]);
            }
        }
    }

    private void updateUsageBars(List<Float> values) {
        if (values == null || values.size() < 6) return;

        float maxVal = 0;
        for (float v : values) { if (v > maxVal) maxVal = v; }
        if (maxVal == 0) maxVal = 1;

        for (int i = 0; i < 6; i++) {
            if (usageBarViews[i] == null) continue;

            TextView valueText = usageBarViews[i].findViewById(R.id.tvUsageValue);
            View barFill = usageBarViews[i].findViewById(R.id.usageBarFill);

            if (valueText != null) valueText.setText(String.format(Locale.getDefault(), "%.3f", values.get(i)));

            if (barFill != null) {
                float fraction = values.get(i) / maxVal;
                GradientDrawable gradient = new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT, BAR_COLORS[i]);
                gradient.setCornerRadius(dpToPx(6));
                barFill.setBackground(gradient);

                barFill.post(() -> {
                    ViewGroup parent = (ViewGroup) barFill.getParent();
                    int parentWidth = parent.getWidth();
                    ViewGroup.LayoutParams lp = barFill.getLayoutParams();
                    lp.width = (int) (parentWidth * fraction);
                    barFill.setLayoutParams(lp);
                });
            }
        }
    }

    private void observeViewModel() {
        // Main 6 Grid Stats
        viewModel.getVoltage().observe(getViewLifecycleOwner(), 
            v -> { if (tvVoltageVal != null) tvVoltageVal.setText(String.format(Locale.getDefault(), "%.1f V", v)); });
        
        viewModel.getCurrentAmps().observe(getViewLifecycleOwner(), 
            a -> { if (tvCurrentVal != null) tvCurrentVal.setText(String.format(Locale.getDefault(), "%.2f A", a)); });
            
        viewModel.getActivePower().observe(getViewLifecycleOwner(), 
            p -> { if (tvActivePowerVal != null) tvActivePowerVal.setText(String.format(Locale.getDefault(), "%.1f W", p)); });
            
        viewModel.getActiveEnergy().observe(getViewLifecycleOwner(), 
            e -> { if (tvActiveEnergyVal != null) tvActiveEnergyVal.setText(String.format(Locale.getDefault(), "%.3f kWh", e)); });
            
        viewModel.getFrequency().observe(getViewLifecycleOwner(), 
            f -> { if (tvFrequencyVal != null) tvFrequencyVal.setText(String.format(Locale.getDefault(), "%.2f Hz", f)); });
            
        viewModel.getPowerFactor().observe(getViewLifecycleOwner(), 
            pf -> { if (tvPowerFactorVal != null) tvPowerFactorVal.setText(String.format(Locale.getDefault(), "%.2f", pf)); });

        // Real-time chart
        viewModel.getVoltage().observe(getViewLifecycleOwner(), v -> {
            updateRealtimeChart();
        });
        viewModel.getCurrentAmps().observe(getViewLifecycleOwner(), a -> {
            updateRealtimeChart();
        });

        // Weekly chart
        viewModel.getPeriodData().observe(getViewLifecycleOwner(), data -> {
            if (weeklyChart != null) {
                viewModel.getPeriodLabels().observe(getViewLifecycleOwner(), labels -> {
                    weeklyChart.setData(data, labels);
                });
            }
        });

        viewModel.getTotalKwh().observe(getViewLifecycleOwner(),
                total -> { if (tvTotalKwhValue != null) tvTotalKwhValue.setText(String.format(Locale.getDefault(), "%.3f", total)); });

        // Summary stats
        viewModel.getEnergyToday().observe(getViewLifecycleOwner(),
                energy -> { if (tvKwhTodayVal != null) tvKwhTodayVal.setText(String.format(Locale.getDefault(), "%.3f", energy)); });

        viewModel.getBillEstimate().observe(getViewLifecycleOwner(),
                bill -> { if (tvBillEstVal != null) tvBillEstVal.setText(String.format(Locale.getDefault(), "₹%,.0f", bill)); });

        viewModel.getCo2Saved().observe(getViewLifecycleOwner(), co2 -> {
            if (tvCo2Val != null) tvCo2Val.setText(co2);
        });

        // Usage by time
        viewModel.getUsageByTime().observe(getViewLifecycleOwner(), this::updateUsageBars);

        // Predicted bill
        viewModel.getPredictedBill().observe(getViewLifecycleOwner(), bill -> {
            if (bill != null) {
                if (tvPredictedBillVal != null) {
                    tvPredictedBillVal.setText(String.format(Locale.getDefault(), "₹%,.0f", bill));
                }
                updateBillProgress(bill);
            }
        });

        viewModel.getBillProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                Double currentBill = viewModel.getPredictedBill().getValue();
                if (currentBill != null) {
                    updateBillProgress(currentBill);
                } else if (billProgressBar != null) {
                    billProgressBar.setProgress(Math.round(progress * 100));
                }
            }
        });
    }

    private void updateBillProgress(double predictedBill) {
        if (billProgressBar == null || getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        float budget = prefs.getFloat("monthly_budget", 2500f);
        if (budget > 0) {
            float ratio = (float) (predictedBill / budget);
            int progressPercent = Math.min(Math.max(Math.round(ratio * 100), 0), 100);
            if (ratio > 0 && progressPercent == 0) {
                progressPercent = 1;
            }
            billProgressBar.setProgress(progressPercent);
        } else {
            billProgressBar.setProgress(0);
        }
    }

    private void showBudgetDialog() {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        float currentBudget = prefs.getFloat("monthly_budget", 2500f);

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.0f", currentBudget));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(requireContext())
                .setTitle("Monthly Budget Target")
                .setMessage("Set your target monthly electricity budget (₹):")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String str = input.getText().toString().trim();
                    if (!str.isEmpty()) {
                        try {
                            float newBudget = Float.parseFloat(str);
                            if (newBudget > 0) {
                                prefs.edit().putFloat("monthly_budget", newBudget).apply();
                                EnergyRepository.getInstance(requireContext()).updateMonthlyBudget(newBudget);
                                Double currentBill = viewModel.getPredictedBill().getValue();
                                updateBillProgress(currentBill != null ? currentBill : 0.0);
                                Toast.makeText(getContext(), "Monthly budget target set to ₹" + Math.round(newBudget), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<Float> voltageHistory = new ArrayList<>();
    private List<Float> currentHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 50;

    private void updateRealtimeChart() {
        if (realtimeChart == null) return;
        
        Double v = viewModel.getVoltage().getValue();
        Double a = viewModel.getCurrentAmps().getValue();
        
        if (v != null) {
            voltageHistory.add(v.floatValue());
            if (voltageHistory.size() > MAX_HISTORY) voltageHistory.remove(0);
        }
        if (a != null) {
            currentHistory.add(a.floatValue());
            if (currentHistory.size() > MAX_HISTORY) currentHistory.remove(0);
        }
        
        realtimeChart.setData(voltageHistory, currentHistory, null);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}