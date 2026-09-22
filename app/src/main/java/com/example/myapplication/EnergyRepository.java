package com.example.myapplication;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import android.content.SharedPreferences;
import com.example.myapplication.feature.alerts.Alert;

public class EnergyRepository {
    private static final String TAG = "EnergyRepository";
    private static EnergyRepository instance;

    public enum Period { DAY, WEEK, MONTH, YEAR }

    // Live Data
    public final MutableLiveData<Double> voltage = new MutableLiveData<>(0.0);
    public final MutableLiveData<Double> currentAmps = new MutableLiveData<>(0.0);
    public final MutableLiveData<Double> activePower = new MutableLiveData<>(0.0);
    public final MutableLiveData<Double> activeEnergy = new MutableLiveData<>(0.0);
    public final MutableLiveData<Double> frequency = new MutableLiveData<>(50.0);
    public final MutableLiveData<Double> powerFactor = new MutableLiveData<>(0.0);
    public final MutableLiveData<Double> temperature = new MutableLiveData<>(0.0);
    public final MutableLiveData<Integer> humidity = new MutableLiveData<>(0);
    
    // Analytics Data
    public final MutableLiveData<Double> energyToday = new MutableLiveData<>(0.0);
    public final MutableLiveData<Double> billEstimate = new MutableLiveData<>(0.0);
    public final MutableLiveData<Double> predictedBill = new MutableLiveData<>(0.0);
    public final MutableLiveData<Float> billProgress = new MutableLiveData<>(0.0f);
    public final MutableLiveData<String> co2Saved = new MutableLiveData<>("0.0 kg");
    public final MutableLiveData<Double> savingsMonth = new MutableLiveData<>(0.0);
    public final MutableLiveData<String> savingsPercentage = new MutableLiveData<>("0%");
    
    public final MutableLiveData<List<Float>> livePowerHistory = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<List<Float>> usageByTime = new MutableLiveData<>(new ArrayList<>(Arrays.asList(0f, 0f, 0f, 0f, 0f, 0f)));
    public final MutableLiveData<List<Float>> periodData = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<List<String>> periodLabels = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Double> totalKwh = new MutableLiveData<>(0.0);
    public final MutableLiveData<Period> selectedPeriod = new MutableLiveData<>(Period.WEEK);
    public final MutableLiveData<List<Alert>> activeAlerts = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Alert> criticalNotification = new MutableLiveData<>();

    public final MutableLiveData<Boolean> acPower = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> fridgePower = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> lightsPower = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> washerPower = new MutableLiveData<>(false);

    private DatabaseReference rootRef;
    private DatabaseReference sensorRef;
    private DatabaseReference commandsRef;
    private ValueEventListener energyListener;
    private ValueEventListener commandsListener;
    private float monthlyBudget = 2500f;
    private final Context appContext;

    private final Handler staleDataHandler = new Handler(Looper.getMainLooper());
    private final Runnable staleDataRunnable = this::resetLiveReadings;

    public static synchronized EnergyRepository getInstance(Context context) {
        if (instance == null) instance = new EnergyRepository(context);
        return instance;
    }

    private EnergyRepository(Context context) {
        this.appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        monthlyBudget = prefs.getFloat("monthly_budget", 2500f);

        List<Float> initLive = new ArrayList<>();
        for (int i = 0; i < 50; i++) initLive.add(0f);
        livePowerHistory.setValue(initLive);

        updatePeriodData(Period.WEEK);
        setupGlobalFirebase();
        setupCommandsListener();
    }

    private void setupCommandsListener() {
        commandsRef = rootRef.child("commands");
        commandsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Boolean r1 = snapshot.child("relay1").getValue(Boolean.class);
                Boolean r2 = snapshot.child("relay2").getValue(Boolean.class);
                Boolean r3 = snapshot.child("relay3").getValue(Boolean.class);
                Boolean r4 = snapshot.child("relay4").getValue(Boolean.class);

                if (r1 != null) acPower.postValue(r1);
                if (r2 != null) fridgePower.postValue(r2);
                if (r3 != null) lightsPower.postValue(r3);
                if (r4 != null) washerPower.postValue(r4);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Commands Error: " + error.getMessage());
            }
        };
        commandsRef.addValueEventListener(commandsListener);
    }

    private void setupGlobalFirebase() {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        rootRef = db.getReference();
        sensorRef = db.getReference("sensordata/simulator_01/latest");
        
        // Listen for relay states from Firebase to keep local LiveData in sync
        DatabaseReference r1Ref = rootRef.child("commands/relay1");
        DatabaseReference r2Ref = rootRef.child("commands/relay2");
        DatabaseReference r3Ref = rootRef.child("commands/relay3");
        DatabaseReference r4Ref = rootRef.child("commands/relay4");

        r1Ref.addValueEventListener(new RelayValueListener(acPower));
        r2Ref.addValueEventListener(new RelayValueListener(fridgePower));
        r3Ref.addValueEventListener(new RelayValueListener(lightsPower));
        r4Ref.addValueEventListener(new RelayValueListener(washerPower));

        energyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                double v = getVal(snapshot, "voltage", "v");
                double i = getVal(snapshot, "current", "i");
                double p = getVal(snapshot, "power", "p");
                double e = getVal(snapshot, "energy", "kwh", "energyKwh");
                double pf = getVal(snapshot, "powerFactor", "pf");
                double freq = getVal(snapshot, "frequency", "f");

                voltage.postValue(v);
                currentAmps.postValue(i);
                powerFactor.postValue(pf > 0 ? pf : 0.94);
                frequency.postValue(freq > 0 ? freq : 50.0);

                if (p <= 0 && v > 0) p = v * i * (pf > 0 ? pf : 0.94);
                activePower.postValue(p);
                updateHistory(p);

                if (e > 0) {
                    activeEnergy.postValue(e);
                    calculateStats(e);
                }

                temperature.postValue(getVal(snapshot, "temperature", "temp"));
                humidity.postValue((int) getVal(snapshot, "humidity", "hum"));

                staleDataHandler.removeCallbacks(staleDataRunnable);
                staleDataHandler.postDelayed(staleDataRunnable, 10000);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase Error: " + error.getMessage());
            }
        };

        sensorRef.addValueEventListener(energyListener);
    }

    private double getVal(DataSnapshot s, String... keys) {
        for (String k : keys) {
            DataSnapshot child = s.child(k);
            if (child.exists()) {
                Object val = child.getValue();
                if (val instanceof Number) return ((Number) val).doubleValue();
                if (val instanceof String) {
                    try { return Double.parseDouble((String) val); } catch (Exception ignored) {}
                }
            }
        }
        return 0.0;
    }

    private void updateHistory(double p) {
        List<Float> history = livePowerHistory.getValue();
        if (history != null) {
            List<Float> updated = new ArrayList<>(history);
            updated.add((float) p);
            if (updated.size() > 50) updated.remove(0);
            livePowerHistory.postValue(updated);
        }
    }

    private void resetLiveReadings() {
        voltage.postValue(0.0);
        currentAmps.postValue(0.0);
        activePower.postValue(0.0);
    }

    private static class RelayValueListener implements ValueEventListener {
        private final MutableLiveData<Boolean> liveData;
        RelayValueListener(MutableLiveData<Boolean> liveData) { this.liveData = liveData; }
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            Boolean val = snapshot.getValue(Boolean.class);
            if (val != null) liveData.postValue(val);
        }
        @Override public void onCancelled(@NonNull DatabaseError error) {}
    }

    public void setPeriod(Period period) {
        selectedPeriod.setValue(period);
        updatePeriodData(period);
        if (period == Period.DAY && energyToday.getValue() != null) {
            totalKwh.setValue(energyToday.getValue());
        }
    }

    private void updatePeriodData(Period period) {
        List<Float> data = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        switch (period) {
            case DAY:
                String[] daySegments = {"12AM", "4AM", "8AM", "12PM", "4PM", "8PM"};
                for (String s : daySegments) { data.add(0f); labels.add(s); }
                break;
            case WEEK:
                String[] weekDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
                for (String day : weekDays) { data.add(0f); labels.add(day); }
                break;
            case MONTH:
                for (int i = 1; i <= 30; i += 5) {
                    data.add(0f);
                    labels.add("D" + i);
                }
                break;
            case YEAR:
                String[] monthLabels = {"Jan", "Mar", "May", "Jul", "Sep", "Nov"};
                for (String m : monthLabels) {
                    data.add(0f);
                    labels.add(m);
                }
                break;
        }
        periodData.setValue(data);
        periodLabels.setValue(labels);
    }

    private void calculateStats(double energy) {
        energyToday.postValue(energy);
        billEstimate.postValue(energy * 7.0);

        Period currentPeriod = selectedPeriod.getValue();
        totalKwh.postValue(energy); // Always show the actual energy for the total

        updateGraphOnData(energy, currentPeriod);

        Calendar cal = Calendar.getInstance();
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            monthlyBudget = prefs.getFloat("monthly_budget", 2500f);
        }

        double predicted;
        if (energy > 0.001) {
            predicted = energy * daysInMonth * 7.0;
        } else {
            predicted = 0.0;
        }

        predictedBill.postValue(predicted);
        if (monthlyBudget > 0) {
            float progress = (float) (predicted / monthlyBudget);
            billProgress.postValue(Math.min(Math.max(progress, 0.0f), 1.0f));
        } else {
            billProgress.postValue(0.0f);
        }

        updateUsageByTime(energy);
    }

    public void updateMonthlyBudget(float newBudget) {
        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            prefs.edit().putFloat("monthly_budget", newBudget).apply();
        }
        this.monthlyBudget = newBudget;
        Double currentPredicted = predictedBill.getValue();
        if (currentPredicted != null && newBudget > 0) {
            float progress = (float) (currentPredicted / newBudget);
            billProgress.postValue(Math.min(Math.max(progress, 0.0f), 1.0f));
        }
    }

    private void updateGraphOnData(double energy, Period currentPeriod) {
        List<Float> graphData = periodData.getValue();
        if (graphData != null) {
            List<Float> updatedGraph = new ArrayList<>(graphData);
            if (currentPeriod == Period.DAY) {
                int seg = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 4;
                if (seg < updatedGraph.size()) {
                    updatedGraph.set(seg, (float)energy);
                    periodData.postValue(updatedGraph);
                }
            } else if (currentPeriod == Period.WEEK) {
                int dayIdx = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7;
                if (dayIdx < updatedGraph.size()) {
                    updatedGraph.set(dayIdx, (float)energy);
                    periodData.postValue(updatedGraph);
                }
            } else if (currentPeriod == Period.MONTH) {
                int day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                int idx = Math.min((day - 1) / 5, updatedGraph.size() - 1);
                updatedGraph.set(idx, (float)energy);
                periodData.postValue(updatedGraph);
            } else if (currentPeriod == Period.YEAR) {
                int month = Calendar.getInstance().get(Calendar.MONTH);
                int idx = Math.min(month / 2, updatedGraph.size() - 1);
                updatedGraph.set(idx, (float)energy);
                periodData.postValue(updatedGraph);
            }
        }
    }

    private void updateUsageByTime(double energy) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int seg = Math.min(hour / 4, 5);
        List<Float> usage = usageByTime.getValue();
        if (usage != null && usage.size() == 6) {
            List<Float> updated = new ArrayList<>(usage);
            updated.set(seg, (float)energy);
            usageByTime.postValue(updated);
        }
    }

    public void setRelay(int relayNum, boolean on) {
        rootRef.child("commands").child("relay" + relayNum).setValue(on);
        
        // Optimistic update for better UI responsiveness
        switch (relayNum) {
            case 1: acPower.setValue(on); break;
            case 2: fridgePower.setValue(on); break;
            case 3: lightsPower.setValue(on); break;
            case 4: washerPower.setValue(on); break;
        }
    }

    public void killAll() {
        rootRef.child("commands").child("killAll").setValue(true);
        for (int i = 1; i <= 4; i++) {
            rootRef.child("commands").child("relay" + i).setValue(false);
        }
        
        // Local update
        acPower.setValue(false);
        fridgePower.setValue(false);
        lightsPower.setValue(false);
        washerPower.setValue(false);
        
        // Reset killAll flag after a short delay so it can be triggered again
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            rootRef.child("commands").child("killAll").setValue(false);
        }, 1000);
    }
}
