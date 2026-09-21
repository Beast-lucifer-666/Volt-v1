package com.example.myapplication.feature.dashboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private TextView tvPowerValue, tvVoltage, tvCurrent, tvEnergyToday, tvTemperature, tvHumidity, tvSavings, tvSavingsPerc, tvTodayKwhVal;
    private TextView tvControlsSummary;
    private MaterialSwitch switchRelay1, switchRelay2;
    private TextView tvStatusRelay1, tvStatusRelay2;
    private TextView tvRelay1Name, tvRelay2Name;
    private ImageView ivControl1, ivControl2;
    private FrameLayout graphContainer;
    private LiveGraphView liveGraphView;
    private HomeViewModel viewModel;
    private ImageView ivProfile;

    // Side Panel Views
    private DrawerLayout drawerLayout;
    private ImageView ivPanelProfilePic;
    private TextView tvPanelUsername, tvPanelEmail;
    private ImageView ivPanelAvatar1, ivPanelAvatar2, ivPanelAvatar3;
    private View btnUploadPhoto;
    private EditText etRelay1Name, etRelay2Name, etRelay3Name, etRelay4Name;
    private Button btnSaveProfile;
    private ImageButton btnClosePanel;

    private String selectedAvatar = "";
    private Uri selectedImageUri = null;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    selectedAvatar = "";
                    if (selectedImageUri != null && ivPanelProfilePic != null) {
                        ivPanelProfilePic.setImageURI(selectedImageUri);
                        resetAvatarHighlights();
                        try {
                            requireActivity().getContentResolver().takePersistableUriPermission(
                                    selectedImageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        initializeViews(view);
        setupUserGreeting(view);
        setupLogout(view);
        setupSwitches();
        setupGraph(view);
        setupSidePanel();
        observeViewModel();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfilePicture();
        loadCustomApplianceNames();
    }

    private void initializeViews(View view) {
        tvPowerValue = view.findViewById(R.id.tvPowerValue);
        tvVoltage = view.findViewById(R.id.tvVoltage);
        tvCurrent = view.findViewById(R.id.tvCurrent);
        tvEnergyToday = view.findViewById(R.id.tvEnergyToday);
        tvTodayKwhVal = view.findViewById(R.id.tvTodayKwhVal);
        tvTemperature = view.findViewById(R.id.tvTemperature);
        tvHumidity = view.findViewById(R.id.tvHumidity);
        tvControlsSummary = view.findViewById(R.id.tvControlsSummary);
        tvSavings = view.findViewById(R.id.tvSavings);
        tvSavingsPerc = view.findViewById(R.id.tvSavingsPerc);
        ivProfile = view.findViewById(R.id.ivProfile);

        switchRelay1 = view.findViewById(R.id.switchRelay1);
        switchRelay2 = view.findViewById(R.id.switchRelay2);
        tvStatusRelay1 = view.findViewById(R.id.tvStatusRelay1);
        tvStatusRelay2 = view.findViewById(R.id.tvStatusRelay2);
        tvRelay1Name = view.findViewById(R.id.tvRelay1Name);
        tvRelay2Name = view.findViewById(R.id.tvRelay2Name);
        ivControl1 = view.findViewById(R.id.ivControl1);
        ivControl2 = view.findViewById(R.id.ivControl2);

        graphContainer = view.findViewById(R.id.graphContainer);

        // Side Panel
        drawerLayout = view.findViewById(R.id.drawerLayout);
        ivPanelProfilePic = view.findViewById(R.id.ivPanelProfilePic);
        tvPanelUsername = view.findViewById(R.id.tvPanelUsername);
        tvPanelEmail = view.findViewById(R.id.tvPanelEmail);
        ivPanelAvatar1 = view.findViewById(R.id.ivPanelAvatar1);
        ivPanelAvatar2 = view.findViewById(R.id.ivPanelAvatar2);
        ivPanelAvatar3 = view.findViewById(R.id.ivPanelAvatar3);
        btnUploadPhoto = view.findViewById(R.id.btnUploadPhoto);
        etRelay1Name = view.findViewById(R.id.etRelay1Name);
        etRelay2Name = view.findViewById(R.id.etRelay2Name);
        etRelay3Name = view.findViewById(R.id.etRelay3Name);
        etRelay4Name = view.findViewById(R.id.etRelay4Name);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        btnClosePanel = view.findViewById(R.id.btnClosePanel);

        loadProfilePicture();
        loadCustomApplianceNames();
    }

    private void setupSidePanel() {
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> openSidePanel());
        }

        if (btnClosePanel != null) {
            btnClosePanel.setOnClickListener(v -> closeSidePanel());
        }

        if (ivPanelAvatar1 != null) {
            ivPanelAvatar1.setOnClickListener(v -> selectPanelAvatar("ic_avatar_1", ivPanelAvatar1));
        }

        if (ivPanelAvatar2 != null) {
            ivPanelAvatar2.setOnClickListener(v -> selectPanelAvatar("ic_avatar_2", ivPanelAvatar2));
        }

        if (ivPanelAvatar3 != null) {
            ivPanelAvatar3.setOnClickListener(v -> selectPanelAvatar("ic_avatar_3", ivPanelAvatar3));
        }

        if (btnUploadPhoto != null) {
            btnUploadPhoto.setOnClickListener(v -> openImagePicker());
        }

        if (btnSaveProfile != null) {
            btnSaveProfile.setOnClickListener(v -> saveProfileAndRelayChanges());
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END);
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void openSidePanel() {
        populateSidePanelData();
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.END);
        }
    }

    private void closeSidePanel() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(GravityCompat.END);
        }
    }

    private void populateSidePanelData() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("username", "User");
        String email = prefs.getString("email", "");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                username = user.getDisplayName();
            }
            if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                email = user.getEmail();
            }
        }

        if (tvPanelUsername != null) tvPanelUsername.setText(username);
        if (tvPanelEmail != null) tvPanelEmail.setText(email.isEmpty() ? "user@example.com" : email);

        if (etRelay1Name != null) etRelay1Name.setText(prefs.getString("relay1_name", "AC Unit"));
        if (etRelay2Name != null) etRelay2Name.setText(prefs.getString("relay2_name", "Fridge"));
        if (etRelay3Name != null) etRelay3Name.setText(prefs.getString("relay3_name", "Lights"));
        if (etRelay4Name != null) etRelay4Name.setText(prefs.getString("relay4_name", "Washer"));

        String type = prefs.getString("profile_image_type", "");
        String value = prefs.getString("profile_image_value", "");
        selectedAvatar = "";
        selectedImageUri = null;
        resetAvatarHighlights();

        if ("avatar".equals(type) && !value.isEmpty()) {
            selectedAvatar = value;
            int resId = getResources().getIdentifier(value, "drawable", requireActivity().getPackageName());
            if (resId != 0 && ivPanelProfilePic != null) {
                ivPanelProfilePic.setImageResource(resId);
            }
            if ("ic_avatar_1".equals(value) && ivPanelAvatar1 != null) {
                ivPanelAvatar1.setBackgroundResource(R.drawable.glow_outer);
            } else if ("ic_avatar_2".equals(value) && ivPanelAvatar2 != null) {
                ivPanelAvatar2.setBackgroundResource(R.drawable.glow_outer);
            } else if ("ic_avatar_3".equals(value) && ivPanelAvatar3 != null) {
                ivPanelAvatar3.setBackgroundResource(R.drawable.glow_outer);
            }
        } else if ("uri".equals(type) && !value.isEmpty()) {
            try {
                selectedImageUri = Uri.parse(value);
                if (ivPanelProfilePic != null) {
                    ivPanelProfilePic.setImageURI(selectedImageUri);
                }
            } catch (Exception e) {
                if (ivPanelProfilePic != null) {
                    ivPanelProfilePic.setImageResource(R.drawable.circle_inner);
                }
            }
        } else {
            if (ivPanelProfilePic != null) {
                ivPanelProfilePic.setImageResource(R.drawable.circle_inner);
            }
        }
    }

    private void selectPanelAvatar(String resName, ImageView selectedView) {
        selectedAvatar = resName;
        selectedImageUri = null;
        resetAvatarHighlights();
        if (selectedView != null) {
            selectedView.setBackgroundResource(R.drawable.glow_outer);
        }
        int resId = getResources().getIdentifier(resName, "drawable", requireActivity().getPackageName());
        if (resId != 0 && ivPanelProfilePic != null) {
            ivPanelProfilePic.setImageResource(resId);
        }
    }

    private void resetAvatarHighlights() {
        if (ivPanelAvatar1 != null) ivPanelAvatar1.setBackgroundResource(R.drawable.bg_glass_card);
        if (ivPanelAvatar2 != null) ivPanelAvatar2.setBackgroundResource(R.drawable.bg_glass_card);
        if (ivPanelAvatar3 != null) ivPanelAvatar3.setBackgroundResource(R.drawable.bg_glass_card);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    private void saveProfileAndRelayChanges() {
        String r1 = etRelay1Name != null ? etRelay1Name.getText().toString().trim() : "";
        String r2 = etRelay2Name != null ? etRelay2Name.getText().toString().trim() : "";
        String r3 = etRelay3Name != null ? etRelay3Name.getText().toString().trim() : "";
        String r4 = etRelay4Name != null ? etRelay4Name.getText().toString().trim() : "";

        if (r1.isEmpty()) r1 = "AC Unit";
        if (r2.isEmpty()) r2 = "Fridge";
        if (r3.isEmpty()) r3 = "Lights";
        if (r4.isEmpty()) r4 = "Washer";

        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("relay1_name", r1);
        editor.putString("relay2_name", r2);
        editor.putString("relay3_name", r3);
        editor.putString("relay4_name", r4);

        if (!selectedAvatar.isEmpty()) {
            editor.putString("profile_image_type", "avatar");
            editor.putString("profile_image_value", selectedAvatar);
        } else if (selectedImageUri != null) {
            editor.putString("profile_image_type", "uri");
            editor.putString("profile_image_value", selectedImageUri.toString());
        }
        editor.apply();

        loadProfilePicture();
        loadCustomApplianceNames();
        closeSidePanel();
        Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
    }

    private void loadCustomApplianceNames() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String name1 = prefs.getString("relay1_name", "AC Unit");
        String name2 = prefs.getString("relay2_name", "Fridge");

        if (tvRelay1Name != null) tvRelay1Name.setText(name1);
        if (tvRelay2Name != null) tvRelay2Name.setText(name2);

        if (ivControl1 != null) updateApplianceIcon(ivControl1, name1);
        if (ivControl2 != null) updateApplianceIcon(ivControl2, name2);
    }

    private void updateApplianceIcon(ImageView iv, String name) {
        if (name.contains("Fridge")) iv.setImageResource(R.drawable.ic_humidity);
        else if (name.contains("AC")) iv.setImageResource(R.drawable.ic_ac);
        else if (name.contains("Light")) iv.setImageResource(R.drawable.ic_light);
        else if (name.contains("Washer")) iv.setImageResource(R.drawable.ic_bolt);
        else iv.setImageResource(R.drawable.ic_bolt);
    }

    private void loadProfilePicture() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String type = prefs.getString("profile_image_type", "");
        String value = prefs.getString("profile_image_value", "");

        if (ivProfile == null) return;

        if ("avatar".equals(type)) {
            int resId = getResources().getIdentifier(value, "drawable", requireActivity().getPackageName());
            if (resId != 0) ivProfile.setImageResource(resId);
        } else if ("uri".equals(type) && !value.isEmpty()) {
            try {
                ivProfile.setImageURI(Uri.parse(value));
            } catch (Exception e) {
                ivProfile.setImageResource(R.drawable.circle_inner);
            }
        }
    }

    private void observeViewModel() {
        viewModel.getAcPower().observe(getViewLifecycleOwner(), isOn -> {
            updateRelayUI(switchRelay1, tvStatusRelay1, isOn);
            updateControlsSummary();
        });

        viewModel.getFridgePower().observe(getViewLifecycleOwner(), isOn -> {
            updateRelayUI(switchRelay2, tvStatusRelay2, isOn);
            updateControlsSummary();
        });

        viewModel.getActivePower().observe(getViewLifecycleOwner(), power ->
            tvPowerValue.setText(String.format(Locale.getDefault(), "%.1f", power)));

        viewModel.getGraphData().observe(getViewLifecycleOwner(), data -> {
            if (liveGraphView != null) liveGraphView.setData(data);
        });

        viewModel.getVoltage().observe(getViewLifecycleOwner(), v ->
            tvVoltage.setText(String.format(Locale.getDefault(), "%.0f V", v)));

        viewModel.getCurrentAmps().observe(getViewLifecycleOwner(), a ->
            tvCurrent.setText(String.format(Locale.getDefault(), "%.1f A", a)));

        viewModel.getTemperature().observe(getViewLifecycleOwner(), t ->
            tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°", t)));

        viewModel.getHumidity().observe(getViewLifecycleOwner(), h ->
            tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", h)));

        viewModel.getEnergyToday().observe(getViewLifecycleOwner(), energy -> {
            String energyStr = String.format(Locale.getDefault(), "%.3f kWh", energy);
            tvEnergyToday.setText(energyStr);
            tvTodayKwhVal.setText(energyStr);
        });

        viewModel.getSavingsMonth().observe(getViewLifecycleOwner(), savings ->
            tvSavings.setText(String.format(Locale.getDefault(), "₹%.0f", savings))
        );

        viewModel.getSavingsPercentage().observe(getViewLifecycleOwner(), perc ->
            tvSavingsPerc.setText(perc)
        );
    }

    private void updateRelayUI(MaterialSwitch sw, TextView tv, boolean isOn) {
        if (sw != null) sw.setChecked(isOn);
        if (tv != null) {
            tv.setText(isOn ? R.string.status_on : R.string.status_off);
            tv.setTextColor(isOn ? getResources().getColor(R.color.volt_teal) : 0xFFFF5252);
        }
    }

    private void setupGraph(View view) {
        liveGraphView = new LiveGraphView(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(100));
        liveGraphView.setLayoutParams(params);

        if (graphContainer != null) {
             graphContainer.addView(liveGraphView);
        }
    }

    private void setupUserGreeting(View view) {
        TextView tvGreeting = view.findViewById(R.id.tvGreeting);
        TextView tvHomeName = view.findViewById(R.id.tvHomeName);
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("username", "User");
        tvGreeting.setText(getGreeting());
        tvHomeName.setText(getString(R.string.home_title_format, username));
    }

    private void setupLogout(View view) {
        ImageButton btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).logout();
            }
        });
    }

    private void setupSwitches() {
        if (switchRelay1 != null) {
            switchRelay1.setOnClickListener(v -> viewModel.setAcPower(switchRelay1.isChecked()));
        }
        if (switchRelay2 != null) {
            switchRelay2.setOnClickListener(v -> viewModel.setFridgePower(switchRelay2.isChecked()));
        }
    }

    private void updateControlsSummary() {
        if (tvControlsSummary == null) return;
        int onCount = 0;
        if (switchRelay1 != null && switchRelay1.isChecked()) onCount++;
        if (switchRelay2 != null && switchRelay2.isChecked()) onCount++;
        tvControlsSummary.setText(String.format(Locale.getDefault(), "%d ON · %d OFF", onCount, 2 - onCount));
    }

    private String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.greeting_morning);
        if (hour < 17) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    public static class LiveGraphView extends View {
        private List<Float> dataPoints = new ArrayList<>();
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path fillPath = new Path();

        public LiveGraphView(Context context) {
            super(context);
            init();
        }

        private void init() {
            linePaint.setColor(0xFF00E5CC);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(6f);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            fillPaint.setStyle(Paint.Style.FILL);
        }

        public void setData(List<Float> data) {
            this.dataPoints = data;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (dataPoints.size() < 2) return;

            float width = getWidth();
            float height = getHeight();
            int maxPoints = 30;
            float xStep = width / (maxPoints - 1);
            float maxVal = 5.0f;

            path.reset();
            fillPath.reset();

            int size = dataPoints.size();
            for (int i = 0; i < size; i++) {
                float x = width - ((size - 1 - i) * xStep);
                float y = height - (dataPoints.get(i) / maxVal * (height - 20)) - 10;

                if (i == 0) {
                    path.moveTo(x, y);
                    fillPath.moveTo(x, height);
                    fillPath.lineTo(x, y);
                } else {
                    path.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
            }

            fillPath.lineTo(width, height);
            fillPath.close();

            Shader fillShader = new LinearGradient(0, 0, 0, height,
                    new int[]{0x8000E5CC, 0x0000E5CC}, null, Shader.TileMode.CLAMP);
            fillPaint.setShader(fillShader);

            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(path, linePaint);
        }
    }
}
