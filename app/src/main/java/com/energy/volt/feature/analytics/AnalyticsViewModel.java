package com.energy.volt.feature.analytics;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.energy.volt.EnergyRepository;
import java.util.List;

public class AnalyticsViewModel extends AndroidViewModel {
    private final EnergyRepository repository;

    public AnalyticsViewModel(@NonNull Application application) {
        super(application);
        repository = EnergyRepository.getInstance(application);
    }

    public LiveData<Double> getVoltage() { return repository.voltage; }
    public LiveData<Double> getCurrentAmps() { return repository.currentAmps; }
    public LiveData<Double> getActivePower() { return repository.activePower; }
    public LiveData<Double> getActiveEnergy() { return repository.activeEnergy; }
    public LiveData<Double> getFrequency() { return repository.frequency; }
    public LiveData<Double> getPowerFactor() { return repository.powerFactor; }

    public LiveData<Double> getEnergyToday() { return repository.energyToday; }
    public LiveData<Double> getBillEstimate() { return repository.billEstimate; }
    public LiveData<Double> getPredictedBill() { return repository.predictedBill; }
    public LiveData<Float> getBillProgress() { return repository.billProgress; }
    public LiveData<String> getCo2Saved() { return repository.co2Saved; }

    public LiveData<List<Float>> getUsageByTime() { return repository.usageByTime; }
    public LiveData<List<Float>> getPeriodData() { return repository.periodData; }
    public LiveData<List<String>> getPeriodLabels() { return repository.periodLabels; }
    public LiveData<Double> getTotalKwh() { return repository.totalKwh; }
    public LiveData<EnergyRepository.Period> getSelectedPeriod() { return repository.selectedPeriod; }

    public void setPeriod(EnergyRepository.Period period) {
        repository.setPeriod(period);
    }
}
