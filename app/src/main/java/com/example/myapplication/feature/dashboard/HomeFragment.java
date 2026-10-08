package com.example.myapplication.feature.dashboard;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.BuildConfig;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView tvPowerValue, tvVoltage, tvCurrent, tvEnergyToday, tvTemperature, tvHumidity, tvSavings, tvSavingsPerc, tvTodayKwhVal;
    private TextView tvControlsSummary;
    private TextView tvTodayKwhTitle, tvGraphLabelStart, tvGraphLabelMid, tvGraphLabelEnd;
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
    private EditText etPanelUsername;
    private EditText etRelay1Name, etRelay2Name, etRelay3Name, etRelay4Name;
    private Button btnSaveProfile;
    private ImageButton btnClosePanel;
    private SeekBar sbNavOpacity;
    private TextView tvNavOpacityVal, tvAppVersionCode;
    private View btnCheckUpdates, btnAboutApp;

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
        
        tvTodayKwhTitle = view.findViewById(R.id.tvTodayKwhTitle);
        tvGraphLabelStart = view.findViewById(R.id.tvGraphLabelStart);
        tvGraphLabelMid = view.findViewById(R.id.tvGraphLabelMid);
        tvGraphLabelEnd = view.findViewById(R.id.tvGraphLabelEnd);

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
        etPanelUsername = view.findViewById(R.id.etPanelUsername);
        etRelay1Name = view.findViewById(R.id.etRelay1Name);
        etRelay2Name = view.findViewById(R.id.etRelay2Name);
        etRelay3Name = view.findViewById(R.id.etRelay3Name);
        etRelay4Name = view.findViewById(R.id.etRelay4Name);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        btnClosePanel = view.findViewById(R.id.btnClosePanel);
        sbNavOpacity = view.findViewById(R.id.sbNavOpacity);
        tvNavOpacityVal = view.findViewById(R.id.tvNavOpacityVal);
        tvAppVersionCode = view.findViewById(R.id.tvAppVersionCode);
        btnCheckUpdates = view.findViewById(R.id.btnCheckUpdates);
        btnAboutApp = view.findViewById(R.id.btnAboutApp);

        loadProfilePicture();
        loadCustomApplianceNames();
    }

    private void setupSidePanel() {
        if (drawerLayout != null) {
            drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    if (getActivity() == null) return;
                    View fragContainer = getActivity().findViewById(R.id.fragment_container);
                    if (fragContainer != null) {
                        if (slideOffset > 0) {
                            fragContainer.setTranslationZ(20f * getResources().getDisplayMetrics().density);
                        } else {
                            fragContainer.setTranslationZ(0f);
                        }
                    }
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    if (getActivity() == null) return;
                    View fragContainer = getActivity().findViewById(R.id.fragment_container);
                    if (fragContainer != null) {
                        fragContainer.setTranslationZ(0f);
                    }
                }
            });
        }

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

        if (sbNavOpacity != null) {
            sbNavOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        if (tvNavOpacityVal != null) tvNavOpacityVal.setText(progress + "%");
                        if (getActivity() instanceof DashboardActivity) {
                            ((DashboardActivity) getActivity()).updateNavOpacity(progress);
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                    prefs.edit().putInt("nav_opacity", progress).apply();
                }
            });
        }

        if (btnCheckUpdates != null) {
            btnCheckUpdates.setOnClickListener(v -> {
                @SuppressWarnings("deprecation")
                ProgressDialog progressDialog = new ProgressDialog(requireContext());
                progressDialog.setMessage("Checking for updates...");
                progressDialog.setCancelable(false);
                progressDialog.show();

                new Thread(() -> {
                    try {
                        URL url = new URL("https://api.github.com/repos/Beast-lucifer-666/Volt-v1/releases/latest");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);

                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        String tagName = json.optString("tag_name", "1.0.0"); // e.g. "v1.0.51" or "1.0.51"
                        String cleanTagName = tagName.replaceAll("[^0-9.]", "");

                        String apkUrl = "";
                        JSONArray assets = json.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject asset = assets.getJSONObject(i);
                                String name = asset.optString("name", "");
                                if (name.endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url", "");
                                    break;
                                }
                            }
                        }

                        if (apkUrl.isEmpty()) {
                            apkUrl = json.optString("html_url", "https://github.com/Beast-lucifer-666/Volt-v1/releases");
                        }

                        final String finalRemoteName = cleanTagName.isEmpty() ? "Latest" : cleanTagName;
                        final String finalApkUrl = apkUrl;
                        final boolean hasNewerVersion = isVersionNewer(cleanTagName, BuildConfig.VERSION_NAME);

                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            if (hasNewerVersion) {
                                new AlertDialog.Builder(requireContext())
                                    .setTitle("Update Available (v" + finalRemoteName + ")")
                                    .setMessage("A new version of Volt (v" + finalRemoteName + ") is available on GitHub Releases. Current version is v" + BuildConfig.VERSION_NAME + ".\n\nWould you like to open the release page or download it?")
                                    .setPositiveButton("Download / View", (dialog, which) -> {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalApkUrl));
                                        startActivity(intent);
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            } else {
                                new AlertDialog.Builder(requireContext())
                                    .setTitle("Check for Updates")
                                    .setMessage("You are running the latest version of Volt (v" + BuildConfig.VERSION_NAME + "). All systems are up to date!")
                                    .setPositiveButton("OK", null)
                                    .show();
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(requireContext())
                                .setTitle("Check for Updates")
                                .setMessage("Could not connect to GitHub Releases. Would you like to open the releases page directly?")
                                .setPositiveButton("Open GitHub", (dialog, which) -> {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Beast-lucifer-666/Volt-v1/releases"));
                                    startActivity(intent);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        });
                    }
                }).start();
            });
        }

        if (btnAboutApp != null) {
            btnAboutApp.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("About Volt")
                        .setMessage("Volt v" + BuildConfig.VERSION_NAME + " (Build " + BuildConfig.VERSION_CODE + ")\n\nSmart Home Energy Monitoring & Appliance Automation Platform.\n\n• Real-time voltage & power telemetry\n• Liquid glassmorphic navigation\n• Firebase Cloud Sync & Secure Authentication\n\nDeveloped for Advanced Android Environments.")
                        .setPositiveButton("Close", null)
                        .show();
            });
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
        if (etPanelUsername != null) etPanelUsername.setText(username);
        if (tvPanelEmail != null) tvPanelEmail.setText(email.isEmpty() ? "user@example.com" : email);

        int opacity = prefs.getInt("nav_opacity", 85);
        if (sbNavOpacity != null) sbNavOpacity.setProgress(opacity);
        if (tvNavOpacityVal != null) tvNavOpacityVal.setText(opacity + "%");

        if (tvAppVersionCode != null) {
            tvAppVersionCode.setText(String.format("v%s (Build %d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        }

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
                if (value.startsWith("data:image/")) {
                    String base64Data = value.substring(value.indexOf(",") + 1);
                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    if (bitmap != null && ivPanelProfilePic != null) {
                        ivPanelProfilePic.setImageBitmap(bitmap);
                    } else if (ivPanelProfilePic != null) {
                        ivPanelProfilePic.setImageResource(R.drawable.circle_inner);
                    }
                } else {
                    selectedImageUri = Uri.parse(value);
                    if (ivPanelProfilePic != null) {
                        ivPanelProfilePic.setImageURI(selectedImageUri);
                    }
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
        String newUsername = etPanelUsername != null ? etPanelUsername.getText().toString().trim() : "";
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

        String photoUriStr = "";
        String imgType = prefs.getString("profile_image_type", "avatar");
        String imgVal = prefs.getString("profile_image_value", "ic_avatar_1");

        if (!selectedAvatar.isEmpty()) {
            photoUriStr = "avatar://" + selectedAvatar;
            imgType = "avatar";
            imgVal = selectedAvatar;
            editor.putString("profile_image_type", "avatar");
            editor.putString("profile_image_value", selectedAvatar);
        } else if (selectedImageUri != null) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(requireActivity().getContentResolver(), selectedImageUri);
                Bitmap bitmap = ImageDecoder.decodeBitmap(source);
                Bitmap resized = scaleBitmap(bitmap, 300);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String base64 = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                photoUriStr = base64;
                imgType = "uri";
                imgVal = base64;
                editor.putString("profile_image_type", "uri");
                editor.putString("profile_image_value", base64);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!newUsername.isEmpty()) {
            editor.putString("username", newUsername);
            if (tvPanelUsername != null) tvPanelUsername.setText(newUsername);
        }

        editor.apply();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            UserProfileChangeRequest.Builder profileBuilder = new UserProfileChangeRequest.Builder();
            if (!newUsername.isEmpty()) {
                profileBuilder.setDisplayName(newUsername);
            }
            if (!photoUriStr.isEmpty()) {
                profileBuilder.setPhotoUri(Uri.parse(photoUriStr));
            }

            String finalImgType = imgType;
            String finalImgVal = imgVal;
            String finalUsername = newUsername.isEmpty() ? prefs.getString("username", "User") : newUsername;

            user.updateProfile(profileBuilder.build()).addOnCompleteListener(task -> {
                String uid = user.getUid();
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
                Map<String, Object> map = new HashMap<>();
                map.put("username", finalUsername);
                map.put("email", user.getEmail() != null ? user.getEmail() : "");
                map.put("profile_image_type", finalImgType);
                map.put("profile_image_value", finalImgVal);
                userRef.updateChildren(map);
            });
        }

        loadProfilePicture();
        loadCustomApplianceNames();
        if (getView() != null) {
            setupUserGreeting(getView());
        }
        closeSidePanel();
        Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
    }

    private Bitmap scaleBitmap(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxDimension && height <= maxDimension) return bitmap;
        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxDimension;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxDimension;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
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
            else ivProfile.setImageResource(R.drawable.circle_inner);
        } else if ("uri".equals(type) && !value.isEmpty()) {
            try {
                if (value.startsWith("data:image/")) {
                    String base64Data = value.substring(value.indexOf(",") + 1);
                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                    if (bitmap != null) {
                        ivProfile.setImageBitmap(bitmap);
                    } else {
                        ivProfile.setImageResource(R.drawable.circle_inner);
                    }
                } else {
                    ivProfile.setImageURI(Uri.parse(value));
                }
            } catch (Exception e) {
                ivProfile.setImageResource(R.drawable.circle_inner);
            }
        } else {
            ivProfile.setImageResource(R.drawable.circle_inner);
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

        viewModel.getGraphLabels().observe(getViewLifecycleOwner(), labels -> {
            if (labels != null && labels.size() >= 3) {
                if (tvGraphLabelStart != null) tvGraphLabelStart.setText(labels.get(0));
                if (tvGraphLabelMid != null) tvGraphLabelMid.setText(labels.get(labels.size() / 2));
                if (tvGraphLabelEnd != null) tvGraphLabelEnd.setText(labels.get(labels.size() - 1));
            }
        });

        viewModel.getSelectedPeriod().observe(getViewLifecycleOwner(), period -> {
            if (tvTodayKwhTitle != null) {
                switch (period) {
                    case DAY: tvTodayKwhTitle.setText(R.string.todays_kwh); break;
                    case WEEK: tvTodayKwhTitle.setText("This Week's kWh"); break;
                    case MONTH: tvTodayKwhTitle.setText("This Month's kWh"); break;
                    case YEAR: tvTodayKwhTitle.setText("This Year's kWh"); break;
                }
            }
        });

        viewModel.getVoltage().observe(getViewLifecycleOwner(), v ->
            tvVoltage.setText(String.format(Locale.getDefault(), "%.1f V", v)));

        viewModel.getCurrentAmps().observe(getViewLifecycleOwner(), a ->
            tvCurrent.setText(String.format(Locale.getDefault(), "%.2f A", a)));

        viewModel.getTemperature().observe(getViewLifecycleOwner(), t ->
            tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°", t)));

        viewModel.getHumidity().observe(getViewLifecycleOwner(), h ->
            tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", h)));

        viewModel.getEnergyToday().observe(getViewLifecycleOwner(), energy -> {
            String energyStr = String.format(Locale.getDefault(), "%.3f kWh", energy);
            tvEnergyToday.setText(energyStr);
        });

        viewModel.getTotalKwh().observe(getViewLifecycleOwner(), total -> {
            String totalStr = String.format(Locale.getDefault(), "%.3f kWh", total);
            if (tvTodayKwhVal != null) tvTodayKwhVal.setText(totalStr);
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
            tv.setTextColor(isOn ? ContextCompat.getColor(requireContext(), R.color.volt_teal) : 0xFFFF5252);
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

    @SuppressWarnings("deprecation")
    private void downloadAndInstallApk(String urlStr, String versionName) {
        ProgressDialog downloadDialog = new ProgressDialog(requireContext());
        downloadDialog.setMessage("Downloading update v" + versionName + "...");
        downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadDialog.setCancelable(false);
        downloadDialog.show();

        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setDoOutput(true);
                c.connect();

                int fileLength = c.getContentLength();
                File outputFile = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "volt_v" + versionName + ".apk");
                if (outputFile.exists()) outputFile.delete();

                FileOutputStream fos = new FileOutputStream(outputFile);
                InputStream is = c.getInputStream();

                byte[] buffer = new byte[1024];
                int total = 0;
                int count;
                while ((count = is.read(buffer)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        requireActivity().runOnUiThread(() -> downloadDialog.setProgress(progress));
                    }
                    fos.write(buffer, 0, count);
                }
                fos.close();
                is.close();

                requireActivity().runOnUiThread(() -> {
                    downloadDialog.dismiss();
                    installApk(outputFile);
                });

            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    downloadDialog.dismiss();
                    Toast.makeText(requireContext(), "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void installApk(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Installation failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

    private boolean isVersionNewer(String remoteVer, String currentVer) {
        if (remoteVer == null || currentVer == null || remoteVer.isEmpty() || currentVer.isEmpty()) return false;
        String[] rParts = remoteVer.replaceAll("[^0-9.]", "").split("\\.");
        String[] cParts = currentVer.replaceAll("[^0-9.]", "").split("\\.");
        int length = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < length; i++) {
            int rVal = (i < rParts.length && !rParts[i].isEmpty()) ? Integer.parseInt(rParts[i]) : 0;
            int cVal = (i < cParts.length && !cParts[i].isEmpty()) ? Integer.parseInt(cParts[i]) : 0;
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return false;
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

            int size = dataPoints.size();
            float xStep = size > 1 ? width / (size - 1) : width;
            
            float maxVal = 0;
            for (float val : dataPoints) {
                if (val > maxVal) maxVal = val;
            }
            if (maxVal == 0) maxVal = 1;

            float[] xPoints = new float[size];
            float[] yPoints = new float[size];
            for (int i = 0; i < size; i++) {
                xPoints[i] = i * xStep;
                yPoints[i] = height - (dataPoints.get(i) / maxVal * (height - 20)) - 10;
            }

            path.reset();
            fillPath.reset();

            path.moveTo(xPoints[0], yPoints[0]);
            fillPath.moveTo(xPoints[0], height);
            fillPath.lineTo(xPoints[0], yPoints[0]);

            for (int i = 1; i < size; i++) {
                float cx = (xPoints[i - 1] + xPoints[i]) / 2f;
                path.cubicTo(cx, yPoints[i - 1], cx, yPoints[i], xPoints[i], yPoints[i]);
                fillPath.cubicTo(cx, yPoints[i - 1], cx, yPoints[i], xPoints[i], yPoints[i]);
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
