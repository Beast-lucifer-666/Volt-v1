package com.energy.volt.feature.alerts;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.energy.volt.R;
import com.energy.volt.feature.dashboard.CustomFloatingNavBar;
import com.energy.volt.feature.dashboard.DashboardActivity;
import java.util.List;
import java.util.Locale;

public class AlertsFragment extends Fragment {

    private AlertsViewModel viewModel;
    private TextView tvAlertsSummary, tvActiveBadge, tvAlertTemp, tvAlertHumidity, tvAlertVoltage, tvAlertCurrent, tvVoltageStatus, tvCurrentStatus;
    private LinearLayout llCriticalAlerts, llWarningAlerts, llInfoAlerts;
    private View tvCriticalLabel, tvWarningLabel;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Permission handled
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alerts, container, false);
        viewModel = new ViewModelProvider(this).get(AlertsViewModel.class);

        createNotificationChannel();
        checkNotificationPermission();

        tvAlertsSummary = view.findViewById(R.id.tvAlertsSummary);
        tvActiveBadge = view.findViewById(R.id.tvActiveBadge);
        tvAlertTemp = view.findViewById(R.id.tvAlertTemp);
        tvAlertHumidity = view.findViewById(R.id.tvAlertHumidity);
        tvAlertVoltage = view.findViewById(R.id.tvAlertVoltage);
        tvAlertCurrent = view.findViewById(R.id.tvAlertCurrent);
        tvVoltageStatus = view.findViewById(R.id.tvVoltageStatus);
        tvCurrentStatus = view.findViewById(R.id.tvCurrentStatus);
        
        llCriticalAlerts = view.findViewById(R.id.llCriticalAlerts);
        llWarningAlerts = view.findViewById(R.id.llWarningAlerts);
        llInfoAlerts = view.findViewById(R.id.llInfoAlerts);
        
        tvCriticalLabel = view.findViewById(R.id.tvCriticalLabel);
        tvWarningLabel = view.findViewById(R.id.tvWarningLabel);

        observeViewModel();

        return view;
    }

    private void observeViewModel() {
        viewModel.getTemperature().observe(getViewLifecycleOwner(), t -> 
            tvAlertTemp.setText(String.format(Locale.getDefault(), "%.1f°C", t)));
            
        viewModel.getHumidity().observe(getViewLifecycleOwner(), h -> 
            tvAlertHumidity.setText(String.format(Locale.getDefault(), "%d%%", h)));

        viewModel.getVoltage().observe(getViewLifecycleOwner(), v -> {
            tvAlertVoltage.setText(String.format(Locale.getDefault(), "%.1f V", v));
            if (v < 100) {
                tvVoltageStatus.setText("CRITICAL");
                tvVoltageStatus.setTextColor(0xFFFF5252);
                tvVoltageStatus.setBackgroundColor(0x1AFF5252);
            } else {
                tvVoltageStatus.setText("SAFE");
                tvVoltageStatus.setTextColor(0xFF00E5CC);
                tvVoltageStatus.setBackgroundColor(0x1A00E5CC);
            }
        });

        viewModel.getCurrentAmps().observe(getViewLifecycleOwner(), a -> 
            tvAlertCurrent.setText(String.format(Locale.getDefault(), "%.2f A", a)));

        viewModel.getActiveAlerts().observe(getViewLifecycleOwner(), this::updateAlertsUI);
        
        viewModel.getCriticalNotification().observe(getViewLifecycleOwner(), alert -> {
            if (alert != null) {
                showNotification(alert);
            }
        });
    }

    private void updateAlertsUI(List<Alert> alerts) {
        llCriticalAlerts.removeAllViews();
        llWarningAlerts.removeAllViews();
        llInfoAlerts.removeAllViews();

        int criticalCount = 0;
        int warningCount = 0;

        for (Alert alert : alerts) {
            View alertView = LayoutInflater.from(getContext()).inflate(R.layout.item_alert, null);
            TextView tvTitle = alertView.findViewById(R.id.tvAlertTitle);
            TextView tvMsg = alertView.findViewById(R.id.tvAlertMessage);
            TextView tvTime = alertView.findViewById(R.id.tvAlertTime);
            TextView btnAction = alertView.findViewById(R.id.btnAlertAction);
            View indicator = alertView.findViewById(R.id.viewIndicator);

            tvTitle.setText(alert.getTitle());
            tvMsg.setText(alert.getMessage());
            tvTime.setText(alert.getTime());
            btnAction.setText(alert.getActionText());

            btnAction.setOnClickListener(v -> {
                String action = alert.getActionText();
                CustomFloatingNavBar nav = getActivity() != null ? getActivity().findViewById(R.id.bottom_navigation) : null;
                if ("Kill All".equals(action)) {
                    viewModel.killAll();
                } else if ("Turn off AC".equals(action)) {
                    viewModel.setAcPower(false);
                } else if ("Go to Relays".equals(action) || "View".equals(action)) {
                    if (nav != null) nav.setSelectedItemId(R.id.nav_relays);
                } else if ("Details".equals(action)) {
                    if (nav != null) nav.setSelectedItemId(R.id.nav_analytics);
                }
            });

            if (alert.getType() == Alert.Type.CRITICAL) {
                indicator.setBackgroundColor(0xFFFF5252);
                llCriticalAlerts.addView(alertView);
                criticalCount++;
            } else if (alert.getType() == Alert.Type.WARNING) {
                indicator.setBackgroundColor(0xFFFFC107);
                llWarningAlerts.addView(alertView);
                warningCount++;
            } else {
                indicator.setBackgroundColor(0xFF00E5CC);
                llInfoAlerts.addView(alertView);
            }
        }

        tvCriticalLabel.setVisibility(criticalCount > 0 ? View.VISIBLE : View.GONE);
        tvWarningLabel.setVisibility(warningCount > 0 ? View.VISIBLE : View.GONE);
        
        int totalActive = criticalCount + warningCount;
        tvAlertsSummary.setText(String.format(Locale.getDefault(), "%d need your attention", totalActive));
        if (totalActive > 0) {
            tvActiveBadge.setVisibility(View.VISIBLE);
            tvActiveBadge.setText(String.format(Locale.getDefault(), "%d Active", totalActive));
        } else {
            tvActiveBadge.setVisibility(View.GONE);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("alerts_channel", "System Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Critical system alerts and warnings");
            NotificationManager manager = requireContext().getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification(Alert alert) {
        Intent intent = new Intent(requireContext(), DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), "alerts_channel")
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle(alert.getTitle())
                .setContentText(alert.getMessage())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireContext());
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
