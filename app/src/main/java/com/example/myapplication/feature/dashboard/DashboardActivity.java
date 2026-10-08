package com.example.myapplication.feature.dashboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.example.myapplication.R;
import com.example.myapplication.feature.analytics.AnalyticsFragment;
import com.example.myapplication.feature.alerts.AlertsFragment;
import com.example.myapplication.feature.relays.RelaysFragment;
import com.example.myapplication.MonitoringService;
import com.example.myapplication.feature.login.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class DashboardActivity extends AppCompatActivity {

    private int previousTabIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);

        setContentView(R.layout.activity_dashboard);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        View navContainer = findViewById(R.id.bottom_navigation_container);

        if (navContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(navContainer, (v, insets) -> {
                Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                int baseMarginBottom = (int) (24 * getResources().getDisplayMetrics().density);
                params.bottomMargin = baseMarginBottom + navBarInsets.bottom;
                v.setLayoutParams(params);
                return insets;
            });
        }

        // Load saved navigation bar opacity
        SharedPreferences prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        int savedOpacity = prefs.getInt("nav_opacity", 85);
        updateNavOpacity(savedOpacity);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int currentIndex;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
                currentIndex = 0;
            } else if (itemId == R.id.nav_relays) {
                selectedFragment = new RelaysFragment();
                currentIndex = 1;
            } else if (itemId == R.id.nav_analytics) {
                selectedFragment = new AnalyticsFragment();
                currentIndex = 2;
            } else {
                selectedFragment = new AlertsFragment();
                currentIndex = 3;
            }

            if (selectedFragment != null) {
                var transaction = getSupportFragmentManager().beginTransaction();
                if (currentIndex > previousTabIndex) {
                    transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
                } else if (currentIndex < previousTabIndex) {
                    transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
                } else {
                    transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
                }
                previousTabIndex = currentIndex;
                transaction.replace(R.id.fragment_container, selectedFragment).commit();
            }
            return true;
        });
    }

    public void updateNavOpacity(int opacityPercent) {
        View navContainer = findViewById(R.id.bottom_navigation_container);
        if (navContainer != null) {
            float alpha = Math.max(0.2f, Math.min(1.0f, opacityPercent / 100f));
            navContainer.setAlpha(alpha);
        }
    }

    public void logout() {
        FirebaseAuth.getInstance().signOut();
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        sharedPreferences.edit()
            .putBoolean("isLoggedIn", false)
            .remove("email")
            .remove("username")
            .apply();

        Intent serviceIntent = new Intent(this, MonitoringService.class);
        stopService(serviceIntent);

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
