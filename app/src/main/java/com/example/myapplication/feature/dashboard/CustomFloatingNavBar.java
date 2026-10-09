package com.example.myapplication.feature.dashboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class CustomFloatingNavBar extends LinearLayout {

    public interface OnItemSelectedListener {
        boolean onItemSelected(int itemId);
    }

    private static class TabItem {
        int id;
        String title;
        int iconRes;
        LinearLayout view;
        ImageView iconView;
        TextView textView;

        TabItem(int id, String title, int iconRes) {
            this.id = id;
            this.title = title;
            this.iconRes = iconRes;
        }
    }

    private final List<TabItem> tabs = new ArrayList<>();
    private int selectedItemId = R.id.nav_home;
    private OnItemSelectedListener listener;

    private static final int COLOR_ACTIVE = Color.parseColor("#00E5CC");
    private static final int COLOR_INACTIVE = Color.parseColor("#7AABAA");

    public CustomFloatingNavBar(@NonNull Context context) {
        super(context);
        init(context);
    }

    public CustomFloatingNavBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CustomFloatingNavBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_capsule_dock);

        int paddingPx = dpToPx(context, 6);
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        tabs.add(new TabItem(R.id.nav_home, "Home", R.drawable.ic_nav_home));
        tabs.add(new TabItem(R.id.nav_relays, "Relays", R.drawable.ic_nav_lock));
        tabs.add(new TabItem(R.id.nav_analytics, "Analytics", R.drawable.ic_nav_email));
        tabs.add(new TabItem(R.id.nav_alerts, "Alerts", R.drawable.ic_nav_bolt));

        buildTabs(context);
        updateTabSelection(false);
    }

    private void buildTabs(Context context) {
        removeAllViews();

        for (TabItem item : tabs) {
            LinearLayout tabLayout = new LinearLayout(context);
            tabLayout.setOrientation(VERTICAL);
            tabLayout.setGravity(Gravity.CENTER);
            tabLayout.setClickable(true);
            tabLayout.setFocusable(true);

            int vPadding = dpToPx(context, 7);
            int hPadding = dpToPx(context, 10);
            tabLayout.setPadding(hPadding, vPadding, hPadding, vPadding);

            LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
            tabLayout.setLayoutParams(params);

            ImageView iconView = new ImageView(context);
            int iconSize = dpToPx(context, 22);
            LayoutParams iconParams = new LayoutParams(iconSize, iconSize);
            iconView.setLayoutParams(iconParams);
            iconView.setImageResource(item.iconRes);

            TextView textView = new TextView(context);
            LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            textParams.topMargin = dpToPx(context, 2);
            textView.setLayoutParams(textParams);
            textView.setText(item.title);
            textView.setTextSize(11f);
            textView.setGravity(Gravity.CENTER);

            tabLayout.addView(iconView);
            tabLayout.addView(textView);

            item.view = tabLayout;
            item.iconView = iconView;
            item.textView = textView;

            tabLayout.setOnClickListener(v -> {
                if (selectedItemId != item.id) {
                    setSelectedItemId(item.id, true);
                }
            });

            addView(tabLayout);
        }
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    public void setSelectedItemId(int itemId) {
        setSelectedItemId(itemId, true);
    }

    public void setSelectedItemId(int itemId, boolean userTriggered) {
        if (selectedItemId == itemId && !userTriggered) {
            return;
        }

        if (userTriggered && listener != null) {
            boolean handled = listener.onItemSelected(itemId);
            if (!handled) {
                return;
            }
        }

        this.selectedItemId = itemId;
        updateTabSelection(true);
    }

    public int getSelectedItemId() {
        return selectedItemId;
    }

    private void updateTabSelection(boolean animate) {
        for (TabItem item : tabs) {
            boolean isSelected = (item.id == selectedItemId);

            if (isSelected) {
                item.view.setBackgroundResource(R.drawable.bg_active_tab_pill);
                item.iconView.setColorFilter(COLOR_ACTIVE);
                item.textView.setTextColor(COLOR_ACTIVE);
                item.textView.setTypeface(Typeface.DEFAULT_BOLD);

                if (animate) {
                    item.view.setScaleX(0.92f);
                    item.view.setScaleY(0.92f);
                    item.view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180)
                            .start();
                }
            } else {
                item.view.setBackground(null);
                item.iconView.setColorFilter(COLOR_INACTIVE);
                item.textView.setTextColor(COLOR_INACTIVE);
                item.textView.setTypeface(Typeface.DEFAULT);
                item.view.setScaleX(1.0f);
                item.view.setScaleY(1.0f);
            }
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
