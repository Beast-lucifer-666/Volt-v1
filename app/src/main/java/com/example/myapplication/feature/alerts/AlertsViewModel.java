package com.example.myapplication.feature.alerts;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.myapplication.EnergyRepository;
import java.util.List;

public class AlertsViewModel extends AndroidViewModel {
    private final EnergyRepository repository;

    public AlertsViewModel(@NonNull Application application) {
        super(application);
        repository = EnergyRepository.getInstance(application);
    }

    public LiveData<Double> getVoltage() { return repository.voltage; }
    public LiveData<Double> getCurrentAmps() { return repository.currentAmps; }
    public LiveData<Double> getTemperature() { return repository.temperature; }
    public LiveData<Integer> getHumidity() { return repository.humidity; }
    
    public LiveData<List<Alert>> getActiveAlerts() { return repository.activeAlerts; }
    public LiveData<Alert> getCriticalNotification() { return repository.criticalNotification; }

    public void setAcPower(boolean on) { repository.setRelay(1, on); }
    public void killAll() {
        repository.killAll();
    }
}
