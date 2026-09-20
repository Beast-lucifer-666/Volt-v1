package com.example.myapplication.feature.relays;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.myapplication.R;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.Locale;

public class RelaysFragment extends Fragment {

    private TextView tvLiveWatts;
    private MaterialSwitch switchRelay1, switchRelay2, switchRelay3, switchRelay4;
    private TextView tvStatusRelay1, tvStatusRelay2, tvStatusRelay3, tvStatusRelay4;
    private TextView tvRelay1Title, tvRelay2Title, tvRelay3Title, tvRelay4Title;
    private ImageView ivRelay1, ivRelay2, ivRelay3, ivRelay4;
    private ImageView ivKillSwitch;
    private RelaysViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_relays, container, false);
        viewModel = new ViewModelProvider(this).get(RelaysViewModel.class);

        initializeViews(view);
        loadRelayNames();
        setupSwitches();
        setupKillSwitch();
        observeViewModel();

        return view;
    }

    private void initializeViews(View view) {
        tvLiveWatts = view.findViewById(R.id.tvLiveWatts);
        ivKillSwitch = view.findViewById(R.id.ivEye); 
        
        switchRelay1 = view.findViewById(R.id.switchRelay1);
        switchRelay2 = view.findViewById(R.id.switchRelay2);
        switchRelay3 = view.findViewById(R.id.switchRelay3);
        switchRelay4 = view.findViewById(R.id.switchRelay4);

        tvStatusRelay1 = view.findViewById(R.id.tvStatusRelay1);
        tvStatusRelay2 = view.findViewById(R.id.tvStatusRelay2);
        tvStatusRelay3 = view.findViewById(R.id.tvStatusRelay3);
        tvStatusRelay4 = view.findViewById(R.id.tvStatusRelay4);

        tvRelay1Title = view.findViewById(R.id.tvRelay1Title);
        tvRelay2Title = view.findViewById(R.id.tvRelay2Title);
        tvRelay3Title = view.findViewById(R.id.tvRelay3Title);
        tvRelay4Title = view.findViewById(R.id.tvRelay4Title);

        ivRelay1 = view.findViewById(R.id.ivRelay1);
        ivRelay2 = view.findViewById(R.id.ivRelay2);
        ivRelay3 = view.findViewById(R.id.ivRelay3);
        ivRelay4 = view.findViewById(R.id.ivRelay4);
    }

    private void loadRelayNames() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        tvRelay1Title.setText(prefs.getString("relay1_name", "Relay 1"));
        tvRelay2Title.setText(prefs.getString("relay2_name", "Relay 2"));
        tvRelay3Title.setText(prefs.getString("relay3_name", "Relay 3"));
        tvRelay4Title.setText(prefs.getString("relay4_name", "Relay 4"));

        updateIcon(ivRelay1, tvRelay1Title.getText().toString());
        updateIcon(ivRelay2, tvRelay2Title.getText().toString());
        updateIcon(ivRelay3, tvRelay3Title.getText().toString());
        updateIcon(ivRelay4, tvRelay4Title.getText().toString());
    }

    private void updateIcon(ImageView iv, String name) {
        if (name.contains("AC")) iv.setImageResource(R.drawable.ic_ac);
        else if (name.contains("Light")) iv.setImageResource(R.drawable.ic_light);
        else if (name.contains("Washer") || name.contains("Bolt")) iv.setImageResource(R.drawable.ic_bolt);
        else iv.setImageResource(R.drawable.ic_humidity);
    }

    private void observeViewModel() {
        viewModel.getAcPower().observe(getViewLifecycleOwner(), isOn -> {
            switchRelay1.setChecked(isOn);
            updateStatus(tvStatusRelay1, switchRelay1, isOn);
        });
        viewModel.getFridgePower().observe(getViewLifecycleOwner(), isOn -> {
            switchRelay2.setChecked(isOn);
            updateStatus(tvStatusRelay2, switchRelay2, isOn);
        });
        viewModel.getLightsPower().observe(getViewLifecycleOwner(), isOn -> {
            switchRelay3.setChecked(isOn);
            updateStatus(tvStatusRelay3, switchRelay3, isOn);
        });
        viewModel.getWasherPower().observe(getViewLifecycleOwner(), isOn -> {
            switchRelay4.setChecked(isOn);
            updateStatus(tvStatusRelay4, switchRelay4, isOn);
        });
        viewModel.getCurrentPower().observe(getViewLifecycleOwner(), power -> 
            tvLiveWatts.setText(String.format(Locale.getDefault(), "%.1fkW", power))
        );
    }

    private void setupSwitches() {
        switchRelay1.setOnClickListener(v -> viewModel.setAcPower(switchRelay1.isChecked()));
        switchRelay2.setOnClickListener(v -> viewModel.setFridgePower(switchRelay2.isChecked()));
        switchRelay3.setOnClickListener(v -> viewModel.setLightsPower(switchRelay3.isChecked()));
        switchRelay4.setOnClickListener(v -> viewModel.setWasherPower(switchRelay4.isChecked()));
    }

    private void setupKillSwitch() {
        if (ivKillSwitch != null) {
            ivKillSwitch.setOnClickListener(v -> viewModel.killAll());
        }
    }

    private void updateStatus(TextView tv, MaterialSwitch sw, boolean isOn) {
        tv.setText(isOn ? "ON" : "OFF");
        int color = isOn ? 0xFF00E5CC : 0xFFFF5252;
        tv.setTextColor(color);
        sw.setThumbTintList(ColorStateList.valueOf(color));
        sw.setTrackTintList(ColorStateList.valueOf(isOn ? 0x3300E5CC : 0x33FF5252));
    }
}
