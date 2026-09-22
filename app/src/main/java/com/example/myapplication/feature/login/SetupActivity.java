package com.example.myapplication.feature.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.EnergyRepository;
import com.example.myapplication.R;
import com.example.myapplication.feature.dashboard.DashboardActivity;

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

            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("relay1_name", spinner1.getSelectedItem().toString());
            editor.putString("relay2_name", spinner2.getSelectedItem().toString());
            editor.putString("relay3_name", spinner3.getSelectedItem().toString());
            editor.putString("relay4_name", spinner4.getSelectedItem().toString());
            editor.putString("device_id", deviceId);
            
            String budgetStr = etMonthlyBudget.getText().toString().trim();
            float budget = budgetStr.isEmpty() ? 2500f : Float.parseFloat(budgetStr);
            editor.putFloat("monthly_budget", budget);

            editor.putBoolean("setup_completed", true);
            editor.apply();

            EnergyRepository.getInstance(getApplicationContext()).updateMonthlyBudget(budget);

            startActivity(new Intent(SetupActivity.this, DashboardActivity.class));
            finish();
        });
    }
}
