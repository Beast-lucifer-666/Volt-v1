package com.energy.volt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.energy.volt.R;
import com.energy.volt.feature.dashboard.DashboardActivity;
import androidx.lifecycle.Observer;
import java.util.Locale;

public class MonitoringService extends Service {

    private static final String CHANNEL_ID = "MonitoringServiceChannel";
    private static final String CRITICAL_CHANNEL_ID = "CriticalAlertsChannel";
    private boolean isVoltageDip = false;
    private EnergyRepository repository;
    private final Observer<Double> voltageObserver = this::checkVoltage;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        repository = EnergyRepository.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isUserLoggedIn()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Home Monitoring Active")
                .setContentText("Monitoring power safety in background")
                .setSmallIcon(R.drawable.ic_bolt)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
        
        // Use EnergyRepository as the source of truth for voltage
        repository.voltage.observeForever(voltageObserver);

        return START_STICKY;
    }

    private boolean isUserLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        return prefs.getBoolean("isLoggedIn", false);
    }

    private void checkVoltage(Double currentVoltage) {
        if (currentVoltage == null) return;
        
        if (currentVoltage > 10 && currentVoltage < 100) {
            if (!isVoltageDip) {
                isVoltageDip = true;
                showCriticalNotification("Critical Low Voltage", 
                    "Voltage dropped to " + String.format(Locale.getDefault(), "%.0fV", currentVoltage) + "! Protect your appliances.");
            }
        } else if (currentVoltage <= 10) {
            // Power is likely killed or device is offline
            if (isVoltageDip) isVoltageDip = false; 
        } else {
            isVoltageDip = false;
        }
    }

    private void showCriticalNotification(String title, String message) {
        if (!isUserLoggedIn()) return;

        Intent intent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CRITICAL_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_bolt)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(2, notification);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            
            NotificationChannel criticalChannel = new NotificationChannel(
                    CRITICAL_CHANNEL_ID,
                    "Critical Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            criticalChannel.setDescription("Important alerts regarding voltage and power safety");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(criticalChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.voltage.removeObserver(voltageObserver);
        }
    }
}
