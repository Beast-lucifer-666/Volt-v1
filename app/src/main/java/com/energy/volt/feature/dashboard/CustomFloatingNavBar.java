package com.energy.volt.feature.dashboard;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.energy.volt.R;

import java.util.ArrayList;
import java.util.List;

public class CustomFloatingNavBar extends FrameLayout {

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
        int currentTextColor = COLOR_INACTIVE;

        TabItem(int id, String title, int iconRes) {
            this.id = id;
            this.title = title;
            this.iconRes = iconRes;
        }
    }

    private final List<TabItem> tabs = new ArrayList<>();
    private int selectedItemId = R.id.nav_home;
    private OnItemSelectedListener listener;

    private View indicatorView;
    private LinearLayout tabsContainer;

    private ValueAnimator indicatorXAnimator;
    private ValueAnimator indicatorWAnimator;

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
        setBackgroundResource(R.drawable.bg_capsule_dock);

        int paddingPx = dpToPx(context, 6);
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        indicatorView = new View(context);
        indicatorView.setBackgroundResource(R.drawable.bg_active_tab_pill);
        indicatorView.setVisibility(View.INVISIBLE);
        addView(indicatorView, new LayoutParams(0, 0));

        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setGravity(Gravity.CENTER_VERTICAL);
        addView(tabsContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        tabs.add(new TabItem(R.id.nav_home, "Home", R.drawable.ic_nav_home));
        tabs.add(new TabItem(R.id.nav_relays, "Relays", R.drawable.ic_nav_lock));
        tabs.add(new TabItem(R.id.nav_analytics, "Analytics", R.drawable.ic_nav_email));
        tabs.add(new TabItem(R.id.nav_alerts, "Alerts", R.drawable.ic_nav_bolt));

        buildTabs(context);

        post(() -> updateTabSelection(false));
    }

    private void buildTabs(Context context) {
        tabsContainer.removeAllViews();

        for (TabItem item : tabs) {
            LinearLayout tabLayout = new LinearLayout(context);
            tabLayout.setOrientation(LinearLayout.VERTICAL);
            tabLayout.setGravity(Gravity.CENTER);
            tabLayout.setClickable(true);
            tabLayout.setFocusable(true);

            int vPadding = dpToPx(context, 7);
            int hPadding = dpToPx(context, 10);
            tabLayout.setPadding(hPadding, vPadding, hPadding, vPadding);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tabLayout.setLayoutParams(params);

            ImageView iconView = new ImageView(context);
            int iconSize = dpToPx(context, 22);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconView.setLayoutParams(iconParams);
            iconView.setImageResource(item.iconRes);

            TextView textView = new TextView(context);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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

            tabsContainer.addView(tabLayout);
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
        TabItem selectedTab = null;

        for (TabItem item : tabs) {
            boolean isSelected = (item.id == selectedItemId);

            if (isSelected) {
                selectedTab = item;
                animateTabItem(item, COLOR_ACTIVE, 1.08f, Typeface.DEFAULT_BOLD, animate);
            } else {
                animateTabItem(item, COLOR_INACTIVE, 1.0f, Typeface.DEFAULT, animate);
            }
        }

        if (selectedTab != null && selectedTab.view != null) {
            TabItem targetTab = selectedTab;
            targetTab.view.post(() -> animateIndicatorTo(targetTab.view, animate));
        }
    }

    private void animateTabItem(TabItem item, int targetColor, float targetScale, Typeface typeface, boolean animate) {
        item.textView.setTypeface(typeface);

        if (!animate) {
            item.textView.setTextColor(targetColor);
            item.iconView.setColorFilter(targetColor);
            item.iconView.setScaleX(targetScale);
            item.iconView.setScaleY(targetScale);
            item.currentTextColor = targetColor;
            return;
        }

        int startColor = item.currentTextColor;
        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, targetColor);
        colorAnim.setDuration(220);
        colorAnim.setInterpolator(new FastOutSlowInInterpolator());
        colorAnim.addUpdateListener(anim -> {
            int c = (int) anim.getAnimatedValue();
            item.textView.setTextColor(c);
            item.iconView.setColorFilter(c);
            item.currentTextColor = c;
        });
        colorAnim.start();

        item.iconView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(220)
                .setInterpolator(new FastOutSlowInInterpolator())
                .start();
    }

    private void animateIndicatorTo(View targetView, boolean animate) {
        int targetX = targetView.getLeft();
        int targetY = targetView.getTop();
        int targetW = targetView.getWidth();
        int targetH = targetView.getHeight();

        if (targetW <= 0 || targetH <= 0) return;

        int currentX = (indicatorView.getLayoutParams() instanceof LayoutParams)
                ? ((LayoutParams) indicatorView.getLayoutParams()).leftMargin : targetX;
        int currentW = indicatorView.getWidth();

        if (!animate || indicatorView.getVisibility() != View.VISIBLE || currentW <= 0) {
            LayoutParams params = new LayoutParams(targetW, targetH);
            params.leftMargin = targetX;
            params.topMargin = targetY;
            indicatorView.setLayoutParams(params);
            indicatorView.setVisibility(View.VISIBLE);
            return;
        }

        if (indicatorXAnimator != null) indicatorXAnimator.cancel();
        if (indicatorWAnimator != null) indicatorWAnimator.cancel();

        indicatorXAnimator = ValueAnimator.ofInt(currentX, targetX);
        indicatorXAnimator.setDuration(260);
        indicatorXAnimator.setInterpolator(new FastOutSlowInInterpolator());
        indicatorXAnimator.addUpdateListener(anim -> {
            int newX = (int) anim.getAnimatedValue();
            LayoutParams p = (LayoutParams) indicatorView.getLayoutParams();
            p.leftMargin = newX;
            indicatorView.setLayoutParams(p);
        });

        indicatorWAnimator = ValueAnimator.ofInt(currentW, targetW);
        indicatorWAnimator.setDuration(260);
        indicatorWAnimator.setInterpolator(new FastOutSlowInInterpolator());
        indicatorWAnimator.addUpdateListener(anim -> {
            int newW = (int) anim.getAnimatedValue();
            LayoutParams p = (LayoutParams) indicatorView.getLayoutParams();
            p.width = newW;
            p.height = targetH;
            p.topMargin = targetY;
            indicatorView.setLayoutParams(p);
        });

        indicatorXAnimator.start();
        indicatorWAnimator.start();
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
