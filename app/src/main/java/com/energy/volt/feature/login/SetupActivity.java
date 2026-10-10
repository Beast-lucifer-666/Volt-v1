package com.energy.volt.feature.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

import com.energy.volt.EnergyRepository;
import com.energy.volt.R;
import com.energy.volt.feature.dashboard.DashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SetupActivity extends AppCompatActivity {

    private Spinner spinner1, spinner2, spinner3, spinner4;
    private EditText etMonthlyBudget, etDeviceId;
    private Button btnFinish;
    private String[] appliances = {"AC Unit", "Fridge", "Lights", "Washer", "Microwave", "Geyser", "TV", "Fan"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        spinner1 = findViewById(R.id.spinnerRelay1);
        spinner2 = findViewById(R.id.spinnerRelay2);
        spinner3 = findViewById(R.id.spinnerRelay3);
        spinner4 = findViewById(R.id.spinnerRelay4);
        etMonthlyBudget = findViewById(R.id.etMonthlyBudget);
        etDeviceId = findViewById(R.id.etDeviceId);
        btnFinish = findViewById(R.id.btnCompleteSetup);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, appliances);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinner1.setAdapter(adapter);
        spinner2.setAdapter(adapter);
        spinner3.setAdapter(adapter);
        spinner4.setAdapter(adapter);
        
        // Default selections
        spinner1.setSelection(0); // AC
        spinner2.setSelection(1); // Fridge
        spinner3.setSelection(2); // Lights
        spinner4.setSelection(3); // Washer

        btnFinish.setOnClickListener(v -> {
            String deviceId = etDeviceId.getText().toString().trim();
            if (deviceId.isEmpty()) {
                etDeviceId.setError("Device ID is required");
                return;
            }

            String r1 = spinner1.getSelectedItem().toString();
            String r2 = spinner2.getSelectedItem().toString();
            String r3 = spinner3.getSelectedItem().toString();
            String r4 = spinner4.getSelectedItem().toString();

            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("relay1_name", r1);
            editor.putString("relay2_name", r2);
            editor.putString("relay3_name", r3);
            editor.putString("relay4_name", r4);
            editor.putString("device_id", deviceId);
            
            String budgetStr = etMonthlyBudget.getText().toString().trim();
            float budget = budgetStr.isEmpty() ? 2500f : Float.parseFloat(budgetStr);
            editor.putFloat("monthly_budget", budget);

            editor.putBoolean("setup_completed", true);
            editor.apply();

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
                Map<String, Object> map = new HashMap<>();
                map.put("relay1_name", r1);
                map.put("relay2_name", r2);
                map.put("relay3_name", r3);
                map.put("relay4_name", r4);
                map.put("device_id", deviceId);
                map.put("monthly_budget", budget);
                map.put("setup_completed", true);
                userRef.updateChildren(map);
            }

            EnergyRepository.getInstance(getApplicationContext()).updateMonthlyBudget(budget);

            startActivity(new Intent(SetupActivity.this, DashboardActivity.class));
            finish();
        });
    }
}
