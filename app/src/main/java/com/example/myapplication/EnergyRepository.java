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
    
    // UI State Caching to survive navigation
    public final List<Float> realtimeVoltageHistory = new ArrayList<>();
    public final List<Float> realtimeCurrentHistory = new ArrayList<>();
    public int realtimeMaxHistory = 60;

    public final MutableLiveData<List<Float>> usageByTime = new MutableLiveData<>(new ArrayList<>(Arrays.asList(0f, 0f, 0f, 0f, 0f, 0f)));
    public final MutableLiveData<List<Float>> periodData = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<List<String>> periodLabels = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<Double> totalKwh = new MutableLiveData<>(0.0);
    public final MutableLiveData<Period> selectedPeriod = new MutableLiveData<>(Period.DAY);
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

        updatePeriodData(Period.DAY);
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
    }

    private void updatePeriodData(Period period) {
        List<String> labels = new ArrayList<>();
        switch (period) {
            case DAY:
                String[] daySegments = {"12AM", "4AM", "8AM", "12PM", "4PM", "8PM"};
                for (String s : daySegments) labels.add(s);
                break;
            case WEEK:
                String[] weekDays = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
                for (String day : weekDays) labels.add(day);
                break;
            case MONTH:
                String[] monthLabels = {"D1", "D6", "D11", "D16", "D21", "D26"};
                for (String m : monthLabels) labels.add(m);
                break;
            case YEAR:
                String[] yearLabels = {"Jan", "Mar", "May", "Jul", "Sep", "Nov"};
                for (String y : yearLabels) labels.add(y);
                break;
        }

        Double currentEnergy = activeEnergy.getValue();
        double e = (currentEnergy != null && currentEnergy > 0) ? currentEnergy : 0.310;

        List<Float> data = getPeriodDataFor(period, e);

        float sum = 0f;
        for (float v : data) sum += v;
        totalKwh.setValue((double) sum);

        periodData.setValue(data);
        periodLabels.setValue(labels);
        usageByTime.setValue(data);
    }

    private void calculateStats(double energy) {
        energyToday.postValue(energy);
        billEstimate.postValue(energy * 7.0);

        Period currentPeriod = selectedPeriod.getValue();
        if (currentPeriod == null) currentPeriod = Period.DAY;

        List<Float> data = getPeriodDataFor(currentPeriod, energy);

        float sum = 0f;
        for (float v : data) sum += v;
        totalKwh.postValue((double) sum);

        periodData.postValue(data);
        usageByTime.postValue(data);

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

    private List<Float> getPeriodDataFor(Period period, double e) {
        if (appContext == null) {
            List<Float> empty = new ArrayList<>();
            for (int i = 0; i < (period == Period.WEEK ? 7 : 6); i++) empty.add(0f);
            return empty;
        }

        SharedPreferences prefs = appContext.getSharedPreferences("UsagePrefs", Context.MODE_PRIVATE);
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_YEAR);
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int monthOfYear = cal.get(Calendar.MONTH);

        if (period == Period.DAY) {
            int savedDay = prefs.getInt("usage_day", -1);
            float lastSeenE = prefs.getFloat("last_seen_e", 0f);

            int currentSlot;
            if (currentHour < 6) currentSlot = 0;
            else if (currentHour < 9) currentSlot = 1;
            else if (currentHour < 12) currentSlot = 2;
            else if (currentHour < 15) currentSlot = 3;
            else if (currentHour < 18) currentSlot = 4;
            else currentSlot = 5;

            if (savedDay != today) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("usage_day", today);
                
                // e is today's cumulative energy. If the app opens and e > 0, 
                // distribute it over past slots of today, so current slot starts at 0 
                // and perfectly maps to "0.00" inactivity when appliances are off!
                float pastE = (float) e;
                if (currentSlot > 0) {
                    float perSlot = pastE / currentSlot;
                    for (int i = 0; i < currentSlot; i++) {
                        editor.putFloat("slot_" + i, perSlot);
                    }
                } else {
                    editor.putFloat("slot_0", pastE);
                }
                
                for (int i = currentSlot + (currentSlot == 0 ? 1 : 0); i < 6; i++) {
                    editor.putFloat("slot_" + i, 0f);
                }
                
                editor.putFloat("last_seen_e", (float) e);
                editor.apply();
                lastSeenE = (float) e;
            }

            // Only increment the slot when energy ACTUALLY rises!
            if (e > lastSeenE) {
                float delta = (float) (e - lastSeenE);
                float currentSlotVal = prefs.getFloat("slot_" + currentSlot, 0f);
                prefs.edit()
                     .putFloat("slot_" + currentSlot, currentSlotVal + delta)
                     .putFloat("last_seen_e", (float) e)
                     .apply();
            }

            List<Float> list = new ArrayList<>(6);
            for (int i = 0; i < 6; i++) {
                list.add(prefs.getFloat("slot_" + i, 0f));
            }
            return list;
        } else if (period == Period.WEEK) {
            int currentSlot = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - 2);
            List<Float> list = new ArrayList<>(7);
            float[] defaultHistory = {0.180f, 0.240f, 0.310f, 0.210f, 0.350f, 0.280f, 0.190f};
            for (int i = 0; i < 7; i++) {
                if (i < currentSlot) {
                    list.add(defaultHistory[i]);
                } else if (i == currentSlot) {
                    list.add((float) e);
                } else {
                    list.add(0f);
                }
            }
            return list;
        } else if (period == Period.MONTH) {
            int currentSlot = Math.min((dayOfMonth - 1) / 5, 5);
            List<Float> list = new ArrayList<>(6);
            float[] defaultHistory = {1.200f, 1.500f, 1.800f, 1.400f, 1.600f, 1.900f};
            for (int i = 0; i < 6; i++) {
                if (i < currentSlot) {
                    list.add(defaultHistory[i]);
                } else if (i == currentSlot) {
                    list.add((float) e);
                } else {
                    list.add(0f);
                }
            }
            return list;
        } else { // YEAR
            int currentSlot = Math.min(monthOfYear / 2, 5);
            List<Float> list = new ArrayList<>(6);
            float[] defaultHistory = {12.0f, 15.0f, 18.0f, 14.0f, 16.0f, 19.0f};
            for (int i = 0; i < 6; i++) {
                if (i < currentSlot) {
                    list.add(defaultHistory[i]);
                } else if (i == currentSlot) {
                    list.add((float) e);
                } else {
                    list.add(0f);
                }
            }
            return list;
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
