package com.example.myapplication.feature.relays;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.myapplication.EnergyRepository;

public class RelaysViewModel extends AndroidViewModel {
    private final EnergyRepository repository;

    public RelaysViewModel(@NonNull Application application) {
        super(application);
        repository = EnergyRepository.getInstance(application);
    }

    public LiveData<Boolean> getAcPower() { return repository.acPower; }
    public LiveData<Boolean> getFridgePower() { return repository.fridgePower; }
    public LiveData<Boolean> getLightsPower() { return repository.lightsPower; }
    public LiveData<Boolean> getWasherPower() { return repository.washerPower; }
    public LiveData<Double> getCurrentPower() { return repository.activePower; }

    public void setAcPower(boolean on) { repository.setRelay(1, on); }
    public void setFridgePower(boolean on) { repository.setRelay(2, on); }
    public void setLightsPower(boolean on) { repository.setRelay(3, on); }
    public void setWasherPower(boolean on) { repository.setRelay(4, on); }
    
    public void killAll() {
        repository.killAll();
    }
}
