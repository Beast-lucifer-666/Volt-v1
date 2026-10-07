package com.example.myapplication.feature.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
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

import com.example.myapplication.R;
import com.example.myapplication.MonitoringService;
import com.example.myapplication.feature.dashboard.DashboardActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
            tvRegister.setHighlightColor(android.graphics.Color.TRANSPARENT);
        }

        Button btnSignIn = findViewById(R.id.btnSignIn);
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

                mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                String uid = user.getUid();
                                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
                                userRef.get().addOnCompleteListener(dbTask -> {
                                    String username = user.getDisplayName() != null ? user.getDisplayName() : "User";
                                    String avatarType = "avatar";
                                    String avatarVal = "ic_avatar_1";

                                    if (dbTask.isSuccessful() && dbTask.getResult().exists()) {
                                        DataSnapshot snap = dbTask.getResult();
                                        if (snap.child("username").getValue(String.class) != null) {
                                            username = snap.child("username").getValue(String.class);
                                        }
                                        String profileType = snap.child("profile_image_type").getValue(String.class);
                                        String profileVal = snap.child("profile_image_value").getValue(String.class);
                                        if (profileType != null && !profileType.isEmpty()) {
                                            avatarType = profileType;
                                            avatarVal = profileVal != null ? profileVal : "";
                                        }
                                    } else {
                                        String photoUri = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
                                        if (photoUri.startsWith("avatar://")) {
                                            avatarType = "avatar";
                                            avatarVal = photoUri.replace("avatar://", "");
                                        } else if (!photoUri.isEmpty()) {
                                            avatarType = "uri";
                                            avatarVal = photoUri;
                                        }
                                    }

                                    sharedPreferences.edit()
                                        .putBoolean("isLoggedIn", true)
                                        .putString("email", email)
                                        .putString("username", username)
                                        .putString("profile_image_type", avatarType)
                                        .putString("profile_image_value", avatarVal)
                                        .apply();

                                    startMonitoringService();
                                    Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                                    navigateToNextScreen(sharedPreferences);
                                });
                            }
                        }
else {
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
