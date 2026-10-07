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
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
    private TextView tvTotalKwhLabel, tvTotalKwhSubtitle, tvTotalKwhUnit;
    private ProgressBar billProgressBar;

    // Buttons
    private TextView btnDay, btnWeek, btnMonth, btnYear;
    private Button btnPrint;

    // Usage bar views
    private View[] usageBarViews = new View[7];

    private static final String[] DAY_SLOT_LABELS = { "12–6 AM", "6–9 AM", "9 AM–12", "12–3 PM", "3–6 PM", "6 PM–12" };
    private static final String[] WEEK_SLOT_LABELS = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };
    private static final String[] MONTH_SLOT_LABELS = { "D1–D5", "D6–D10", "D11–D15", "D16–D20", "D21–D25", "D26–End" };
    private static final String[] YEAR_SLOT_LABELS = { "Jan–Feb", "Mar–Apr", "May–Jun", "Jul–Aug", "Sep–Oct", "Nov–Dec" };

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

    @Override
    public void onResume() {
        super.onResume();
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
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
        tvTotalKwhUnit = view.findViewById(R.id.tvTotalKwhUnit);
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
            String periodPrefix;
            if (dayOfMonth <= 10) {
                periodPrefix = "Beginning of";
            } else if (dayOfMonth <= 20) {
                periodPrefix = "Middle of";
            } else {
                periodPrefix = "End of";
            }
            tvPredictedSubtitle.setText(String.format(Locale.US, "%s %s - ₹7/unit", periodPrefix, monthYearStr));
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
            
            if (v.getId() == R.id.btnDay) {
                selectedPeriod = EnergyRepository.Period.DAY;
            } else if (v.getId() == R.id.btnWeek) {
                selectedPeriod = EnergyRepository.Period.WEEK;
            } else if (v.getId() == R.id.btnMonth) {
                selectedPeriod = EnergyRepository.Period.MONTH;
            } else {
                selectedPeriod = EnergyRepository.Period.YEAR;
            }

            // Trigger Data Update
            viewModel.setPeriod(selectedPeriod);
        };

        btnDay.setOnClickListener(listener);
        btnWeek.setOnClickListener(listener);
        btnMonth.setOnClickListener(listener);
        btnYear.setOnClickListener(listener);

        EnergyRepository.Period currentPeriod = viewModel.getSelectedPeriod().getValue();
        if (currentPeriod == null) {
            btnDay.post(() -> btnDay.performClick());
        }
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

        final int[] points = { 60, 180, 300, 600 };
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EnergyRepository repo = EnergyRepository.getInstance(requireContext());
                repo.realtimeMaxHistory = points[position];
                
                if (repo.realtimeVoltageHistory.size() > repo.realtimeMaxHistory) {
                    List<Float> subList = new ArrayList<>(repo.realtimeVoltageHistory.subList(repo.realtimeVoltageHistory.size() - repo.realtimeMaxHistory, repo.realtimeVoltageHistory.size()));
                    repo.realtimeVoltageHistory.clear();
                    repo.realtimeVoltageHistory.addAll(subList);
                }
                if (repo.realtimeCurrentHistory.size() > repo.realtimeMaxHistory) {
                    List<Float> subList = new ArrayList<>(repo.realtimeCurrentHistory.subList(repo.realtimeCurrentHistory.size() - repo.realtimeMaxHistory, repo.realtimeCurrentHistory.size()));
                    repo.realtimeCurrentHistory.clear();
                    repo.realtimeCurrentHistory.addAll(subList);
                }
                
                if (realtimeChart != null) {
                    realtimeChart.setMaxPoints(repo.realtimeMaxHistory);
                    realtimeChart.setData(repo.realtimeVoltageHistory, repo.realtimeCurrentHistory, null);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
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
        int[] barIds = { R.id.usageBar0, R.id.usageBar1, R.id.usageBar2, R.id.usageBar3, R.id.usageBar4, R.id.usageBar5, R.id.usageBar6 };

        for (int i = 0; i < 7; i++) {
            usageBarViews[i] = view.findViewById(barIds[i]);
        }
    }

    private void updateUsageBars(List<Float> values) {
        if (values == null) return;

        EnergyRepository.Period period = viewModel.getSelectedPeriod().getValue();
        if (period == null) period = EnergyRepository.Period.DAY;

        String[] labels;
        switch (period) {
            case WEEK: labels = WEEK_SLOT_LABELS; break;
            case MONTH: labels = MONTH_SLOT_LABELS; break;
            case YEAR: labels = YEAR_SLOT_LABELS; break;
            default: labels = DAY_SLOT_LABELS; break;
        }

        int count = Math.min(values.size(), labels.length);

        float maxVal = 0;
        for (int i = 0; i < count; i++) {
            float v = values.get(i);
            if (v > maxVal) maxVal = v;
        }
        if (maxVal == 0) maxVal = 1.0f;

        for (int i = 0; i < 7; i++) {
            if (usageBarViews[i] == null) continue;

            if (i < count) {
                usageBarViews[i].setVisibility(View.VISIBLE);

                TextView labelText = usageBarViews[i].findViewById(R.id.tvUsageLabel);
                TextView valueText = usageBarViews[i].findViewById(R.id.tvUsageValue);
                View barFill = usageBarViews[i].findViewById(R.id.usageBarFill);

                if (labelText != null) labelText.setText(labels[i]);
                if (valueText != null) valueText.setText(String.format(Locale.getDefault(), "%.3f", values.get(i)));

                if (barFill != null) {
                    float val = values.get(i);
                    float fraction = (val <= 0f) ? 0f : (val / maxVal);

                    int colorIdx = i % BAR_COLORS.length;
                    GradientDrawable gradient = new GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT, BAR_COLORS[colorIdx]);
                    gradient.setCornerRadius(dpToPx(6));
                    barFill.setBackground(gradient);

                    final float fFrac = fraction;
                    barFill.post(() -> {
                        ViewGroup parent = (ViewGroup) barFill.getParent();
                        if (parent != null) {
                            int parentWidth = parent.getWidth();
                            ViewGroup.LayoutParams lp = barFill.getLayoutParams();
                            lp.width = (int) (parentWidth * fFrac);
                            barFill.setLayoutParams(lp);
                        }
                    });
                }
            } else {
                usageBarViews[i].setVisibility(View.GONE);
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

        // Real-time chart is now updated via timer in onResume/onPause
        // We still need to observe other data

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

        viewModel.getSelectedPeriod().observe(getViewLifecycleOwner(), period -> {
            if (period == null) return;
            
            int activeId = R.id.btnDay;
            String label = "Total kWh (Today)";
            String subtitle = "Whole home · Today";
            String unitLabel = "kWh today";

            if (period == EnergyRepository.Period.WEEK) {
                activeId = R.id.btnWeek;
                label = "Total kWh (Weekly)";
                subtitle = "Whole home · This week";
                unitLabel = "kWh this week";
            } else if (period == EnergyRepository.Period.MONTH) {
                activeId = R.id.btnMonth;
                label = "Total kWh (Monthly)";
                subtitle = "Whole home · This month";
                unitLabel = "kWh this month";
            } else if (period == EnergyRepository.Period.YEAR) {
                activeId = R.id.btnYear;
                label = "Total kWh (Yearly)";
                subtitle = "Whole home · This year";
                unitLabel = "kWh this year";
            }

            updatePeriodButtons(activeId);
            if (tvTotalKwhLabel != null) tvTotalKwhLabel.setText(label);
            if (tvTotalKwhSubtitle != null) tvTotalKwhSubtitle.setText(subtitle);
            if (tvTotalKwhUnit != null) tvTotalKwhUnit.setText(unitLabel);
        });

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
        input.setTextColor(0xFFFFFFFF); // Keep text white

        // Center the input text
        input.setGravity(Gravity.CENTER);

        // Add proper margins using a container
        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setTitle("Monthly Budget Target")
                .setMessage("Set your target monthly electricity budget (₹):")
                .setView(container)
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

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateRealtimeChart();
            timerHandler.postDelayed(this, 1000);
        }
    };

    private void updateRealtimeChart() {
        if (realtimeChart == null || viewModel == null) return;
        
        Double v = viewModel.getVoltage().getValue();
        Double a = viewModel.getCurrentAmps().getValue();
        
        EnergyRepository repo = EnergyRepository.getInstance(requireContext());
        
        if (v != null) {
            repo.realtimeVoltageHistory.add(v.floatValue());
            if (repo.realtimeVoltageHistory.size() > repo.realtimeMaxHistory) {
                repo.realtimeVoltageHistory.remove(0);
            }
        }
        if (a != null) {
            repo.realtimeCurrentHistory.add(a.floatValue());
            if (repo.realtimeCurrentHistory.size() > repo.realtimeMaxHistory) {
                repo.realtimeCurrentHistory.remove(0);
            }
        }
        
        realtimeChart.setData(repo.realtimeVoltageHistory, repo.realtimeCurrentHistory, null);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}