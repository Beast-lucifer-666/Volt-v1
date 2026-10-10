package com.energy.volt.feature.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.energy.volt.R;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvLogin;
    private ImageView ivProfilePreview;
    private FrameLayout profileImageContainer;
    private ImageView avatar1, avatar2, avatar3;
    private String selectedAvatarResource = "";
    private Uri selectedImageUri;
    
    private FirebaseAuth mAuth;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    ivProfilePreview.setImageURI(selectedImageUri);
                    selectedAvatarResource = ""; // Clear avatar if image picked
                    try {
                        getContentResolver().takePersistableUriPermission(selectedImageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        ivProfilePreview = findViewById(R.id.ivProfilePreview);
        profileImageContainer = findViewById(R.id.profileImageContainer);
        
        avatar1 = findViewById(R.id.avatar1);
        avatar2 = findViewById(R.id.avatar2);
        avatar3 = findViewById(R.id.avatar3);

        profileImageContainer.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        avatar1.setOnClickListener(v -> selectAvatar("ic_avatar_1", avatar1));
        avatar2.setOnClickListener(v -> selectAvatar("ic_avatar_2", avatar2));
        avatar3.setOnClickListener(v -> selectAvatar("ic_avatar_3", avatar3));

        btnRegister.setOnClickListener(v -> {
            handleRegistration();
        });

        tvLogin.setOnClickListener(v -> finish());
    }

    private void handleRegistration() {
        String email = etEmail.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        // 1. Send Real Verification Email
                        user.sendEmailVerification()
                            .addOnCompleteListener(verifyTask -> {
                                if (verifyTask.isSuccessful()) {
                                    Toast.makeText(RegisterActivity.this, 
                                        "Verification email sent to " + email, Toast.LENGTH_LONG).show();
                                }
                            });

                        // 2. Update Profile with Username and Photo (Convert custom photo to Base64 to persist across reinstalls)
                        String photoUriStr = "";
                        String imgType = "avatar";
                        String imgVal = "ic_avatar_1";

                        if (!selectedAvatarResource.isEmpty()) {
                            photoUriStr = "avatar://" + selectedAvatarResource;
                            imgType = "avatar";
                            imgVal = selectedAvatarResource;
                        } else if (selectedImageUri != null) {
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                                Bitmap resized = scaleBitmap(bitmap, 300);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                resized.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                                String base64 = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                                photoUriStr = base64;
                                imgType = "uri";
                                imgVal = base64;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(username)
                                .setPhotoUri(photoUriStr.isEmpty() ? null : Uri.parse(photoUriStr))
                                .build();

                        String finalImgType = imgType;
                        String finalImgVal = imgVal;
                        user.updateProfile(profileUpdates).addOnCompleteListener(updateTask -> {
                            // 3. Save to Firebase Realtime Database so profile survives app re-install and re-login
                            String uid = user.getUid();
                            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
                            Map<String, Object> map = new HashMap<>();
                            map.put("username", username);
                            map.put("email", email);
                            map.put("profile_image_type", finalImgType);
                            map.put("profile_image_value", finalImgVal);
                            map.put("setup_completed", false);
                            userRef.setValue(map);

                            // 4. Save local preferences
                            saveUserToPrefs(username, email, finalImgType, finalImgVal);

                            Toast.makeText(RegisterActivity.this, "Account created! Please verify your email before logging in.", Toast.LENGTH_LONG).show();
                            finish();
                        });
                    }
                } else {
                    String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    if (error.contains("operation is not allowed")) {
                        error = "Email/Password sign-in is disabled in Firebase Console. Please enable it under Authentication > Sign-in method.";
                    }
                    Toast.makeText(RegisterActivity.this, "Auth Failed: " + error, Toast.LENGTH_LONG).show();
                }
            });
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

    private void saveUserToPrefs(String username, String email, String imgType, String imgVal) {
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("username", username);
        editor.putString("email", email);
        editor.putBoolean("isLoggedIn", true);
        editor.putString("profile_image_type", imgType);
        editor.putString("profile_image_value", imgVal);
        editor.apply();
    }

    private void selectAvatar(String avatarRes, ImageView view) {
        selectedAvatarResource = avatarRes;
        selectedImageUri = null;
        
        // Reset previews
        avatar1.setBackgroundResource(R.drawable.bg_glass_card);
        avatar2.setBackgroundResource(R.drawable.bg_glass_card);
        avatar3.setBackgroundResource(R.drawable.bg_glass_card);
        
        // Highlight selected
        view.setBackgroundResource(R.drawable.glow_outer); 
        
        // Update main preview
        int resId = getResources().getIdentifier(avatarRes, "drawable", getPackageName());
        ivProfilePreview.setImageResource(resId);
    }
}
