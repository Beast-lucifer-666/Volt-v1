package com.example.myapplication.feature.dashboard;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.myapplication.EnergyRepository;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {
    private final EnergyRepository repository;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repository = EnergyRepository.getInstance(application);
    }

    public LiveData<Double> getVoltage() { return repository.voltage; }
    public LiveData<Double> getCurrentAmps() { return repository.currentAmps; }
    public LiveData<Double> getActivePower() { return repository.activePower; }
    public LiveData<Double> getEnergyToday() { return repository.energyToday; }
    public LiveData<Double> getTotalKwh() { return repository.totalKwh; }
    public LiveData<List<Float>> getGraphData() { return repository.periodData; }
    public LiveData<List<String>> getGraphLabels() { return repository.periodLabels; }
    public LiveData<EnergyRepository.Period> getSelectedPeriod() { return repository.selectedPeriod; }

    
    public LiveData<Double> getTemperature() { return repository.temperature; }
    public LiveData<Integer> getHumidity() { return repository.humidity; }
    
    public LiveData<Double> getSavingsMonth() { return repository.savingsMonth; }
    public LiveData<String> getSavingsPercentage() { return repository.savingsPercentage; }

    public LiveData<Boolean> getAcPower() { return repository.acPower; }
    public LiveData<Boolean> getFridgePower() { return repository.fridgePower; }

    public void setAcPower(boolean on) { repository.setRelay(1, on); }
    public void setFridgePower(boolean on) { repository.setRelay(2, on); }
}