package com.energy.volt.feature.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.energy.volt.R;
import com.energy.volt.EnergyRepository;
import com.energy.volt.MonitoringService;
import com.energy.volt.feature.dashboard.DashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // Check if user is already logged in
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        if (mAuth.getCurrentUser() != null && sharedPreferences.getBoolean("isLoggedIn", false)) {
            startMonitoringService();
            navigateToNextScreen(sharedPreferences);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        Animation rotateAnim = AnimationUtils.loadAnimation(this, R.anim.rotate_ring);
        Animation swingAnim = AnimationUtils.loadAnimation(this, R.anim.swing_bolt);
        
        ImageView logoRing = findViewById(R.id.logoRing);
        if (logoRing != null) logoRing.startAnimation(rotateAnim);

        ImageView boltIcon = findViewById(R.id.boltIcon);
        if (boltIcon != null) boltIcon.startAnimation(swingAnim);

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> {
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
            });
        }

        TextView tvRegister = findViewById(R.id.tvRegister);
        if (tvRegister != null) {
            String full = getString(R.string.register_prompt);
            SpannableString ss = new SpannableString(full);

            String linkText = getString(R.string.register_link);
            int start = full.indexOf(linkText);
            int end   = start + linkText.length();

            if (start != -1) {
                ss.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.volt_teal)),
                    start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                ss.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            tvRegister.setText(ss);
            tvRegister.setMovementMethod(LinkMovementMethod.getInstance());
            tvRegister.setHighlightColor(Color.TRANSPARENT);
        }

        Button btnSignIn = findViewById(R.id.btnSignIn);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);

        if (btnSignIn != null && etEmail != null && etPassword != null) {
            btnSignIn.setOnClickListener(v -> {
                String email    = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, getString(R.string.enter_email_password), Toast.LENGTH_SHORT).show();
                    return;
                }

                btnSignIn.setEnabled(false);
                btnSignIn.setText("");
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

                Handler authTimeoutHandler = new Handler(Looper.getMainLooper());
                final boolean[] authCompleted = {false};

                Runnable authTimeoutRunnable = () -> {
                    if (!authCompleted[0]) {
                        authCompleted[0] = true;
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        btnSignIn.setText("SIGN IN →");
                        btnSignIn.setEnabled(true);
                        Toast.makeText(LoginActivity.this, "Timeout: Please verify Email/Password is enabled in Firebase Console.", Toast.LENGTH_LONG).show();
                    }
                };

                authTimeoutHandler.postDelayed(authTimeoutRunnable, 12000);

                Log.d("VoltLogin", "Starting sign in with email: " + email);
                mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (authCompleted[0]) return;
                        authCompleted[0] = true;
                        authTimeoutHandler.removeCallbacks(authTimeoutRunnable);

                        Log.d("VoltLogin", "Auth task complete. Success = " + task.isSuccessful());
                        if (!task.isSuccessful()) {
                            String err = task.getException() != null ? task.getException().getMessage() : "unknown";
                            Log.e("VoltLogin", "Auth failed: " + err);
                        }

                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                String uid = user.getUid();
                                Log.d("VoltLogin", "Authenticated user UID: " + uid);
                                String defaultUsername = user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                                        ? user.getDisplayName() : "User";
                                String defaultAvatarType = "avatar";
                                String defaultAvatarVal = "ic_avatar_1";

                                String photoUri = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
                                if (photoUri.startsWith("avatar://")) {
                                    defaultAvatarType = "avatar";
                                    defaultAvatarVal = photoUri.replace("avatar://", "");
                                } else if (!photoUri.isEmpty()) {
                                    defaultAvatarType = "uri";
                                    defaultAvatarVal = photoUri;
                                }

                                final boolean[] isProceeded = {false};
                                Handler timeoutHandler = new Handler(Looper.getMainLooper());

                                Runnable proceedRunnable = () -> {
                                    if (!isProceeded[0]) {
                                        isProceeded[0] = true;
                                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                                        startMonitoringService();
                                        Toast.makeText(LoginActivity.this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                                        navigateToNextScreen(sharedPreferences);
                                    }
                                };

                                // 8 second timeout safeguard for slow network/Firebase connections
                                timeoutHandler.postDelayed(proceedRunnable, 8000);

                                sharedPreferences.edit()
                                    .putBoolean("isLoggedIn", true)
                                    .putString("email", email)
                                    .putString("username", defaultUsername)
                                    .putString("profile_image_type", defaultAvatarType)
                                    .putString("profile_image_value", defaultAvatarVal)
                                    .apply();

                                try {
                                    DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
                                    userRef.get().addOnCompleteListener(dbTask -> {
                                        timeoutHandler.removeCallbacks(proceedRunnable);
                                        if (dbTask.isSuccessful() && dbTask.getResult().exists()) {
                                            DataSnapshot snap = dbTask.getResult();
                                            String fetchedUsername = snap.child("username").getValue(String.class);
                                            String profileType = snap.child("profile_image_type").getValue(String.class);
                                            String profileVal = snap.child("profile_image_value").getValue(String.class);

                                            String r1 = snap.child("relay1_name").getValue(String.class);
                                            String r2 = snap.child("relay2_name").getValue(String.class);
                                            String r3 = snap.child("relay3_name").getValue(String.class);
                                            String r4 = snap.child("relay4_name").getValue(String.class);
                                            String devId = snap.child("device_id").getValue(String.class);
                                            Boolean setupDone = snap.child("setup_completed").getValue(Boolean.class);

                                            Double budgetVal = null;
                                            Object budgetObj = snap.child("monthly_budget").getValue();
                                            if (budgetObj instanceof Double) {
                                                budgetVal = (Double) budgetObj;
                                            } else if (budgetObj instanceof Long) {
                                                budgetVal = ((Long) budgetObj).doubleValue();
                                            }

                                            // Existing users have device_id or setup_completed = true
                                            boolean isSetupDone = (setupDone != null && setupDone) || (devId != null && !devId.isEmpty());

                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            if (fetchedUsername != null && !fetchedUsername.isEmpty()) {
                                                editor.putString("username", fetchedUsername);
                                            }
                                            if (profileType != null && !profileType.isEmpty()) {
                                                editor.putString("profile_image_type", profileType);
                                                editor.putString("profile_image_value", profileVal != null ? profileVal : "");
                                            }
                                            if (r1 != null && !r1.isEmpty()) editor.putString("relay1_name", r1);
                                            if (r2 != null && !r2.isEmpty()) editor.putString("relay2_name", r2);
                                            if (r3 != null && !r3.isEmpty()) editor.putString("relay3_name", r3);
                                            if (r4 != null && !r4.isEmpty()) editor.putString("relay4_name", r4);
                                            if (devId != null && !devId.isEmpty()) editor.putString("device_id", devId);
                                            editor.putBoolean("setup_completed", isSetupDone);

                                            if (budgetVal != null) {
                                                editor.putFloat("monthly_budget", budgetVal.floatValue());
                                                EnergyRepository.getInstance(getApplicationContext()).updateMonthlyBudget(budgetVal.floatValue());
                                            }
                                            editor.apply();
                                        }
                                        proceedRunnable.run();
                                    });
                                } catch (Exception e) {
                                    proceedRunnable.run();
                                }
                            }
                        } else {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            btnSignIn.setText("SIGN IN →");
                            btnSignIn.setEnabled(true);
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(this, "Authentication Failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
            });
        }
    }

    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, MonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void navigateToNextScreen(SharedPreferences prefs) {
        boolean setupDone = prefs.getBoolean("setup_completed", false);
        String deviceId = prefs.getString("device_id", "");
        
        Intent intent;
        if (setupDone && !deviceId.isEmpty()) {
            intent = new Intent(LoginActivity.this, DashboardActivity.class);
        } else {
            // If setup wasn't finished or Device ID is missing, go to Setup
            intent = new Intent(LoginActivity.this, SetupActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
