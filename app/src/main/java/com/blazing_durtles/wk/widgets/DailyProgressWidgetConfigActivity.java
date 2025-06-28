package com.blazing_durtles.wk.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import com.blazing_durtles.wk.R;

public class DailyProgressWidgetConfigActivity extends Activity {
    private static final String TAG = "BD_WidgetConfig";
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private CheckBox showStreak, showLessons, showReviews;
    public static final String PREFS_NAME = "com.blazing_durtles.wk.widgets.DailyProgressWidgetPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.widget_daily_progress_config);

        // Set config background to match user's theme
        View root = findViewById(android.R.id.content);
        int bgColor = getThemeBackgroundColor();
        root.setBackgroundColor(bgColor);

        // Set navigation bar color to match widget background
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.wkNordWindowBackground));
        }

        setResult(RESULT_CANCELED);
        showStreak = findViewById(R.id.checkboxShowStreak);
        showLessons = findViewById(R.id.checkboxShowLessons);
        showReviews = findViewById(R.id.checkboxShowReviews);
        final android.widget.TextView invalidSelectionText = findViewById(R.id.invalidSelectionText);
        Button addButton = findViewById(R.id.add_widget_button);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            Log.d(TAG, "Received appWidgetId: " + appWidgetId);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Invalid appWidgetId, finishing config activity");
            finish();
            return;
        }

        Runnable updateButtonState = () -> {
            boolean noneChecked = !showStreak.isChecked() && !showLessons.isChecked() && !showReviews.isChecked();
            addButton.setEnabled(!noneChecked);
            if (noneChecked) {
                addButton.setAlpha(0.5f);
                invalidSelectionText.setVisibility(View.VISIBLE);
            } else {
                addButton.setAlpha(1.0f);
                invalidSelectionText.setVisibility(View.GONE);
            }
        };
        // Initial state
        updateButtonState.run();
        showStreak.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState.run());
        showLessons.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState.run());
        showReviews.setOnCheckedChangeListener((buttonView, isChecked) -> updateButtonState.run());

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Saving prefs: streak=" + showStreak.isChecked() + ", lessons=" + showLessons.isChecked() + ", reviews=" + showReviews.isChecked());
                savePrefs(DailyProgressWidgetConfigActivity.this, appWidgetId, showStreak.isChecked(), showLessons.isChecked(), showReviews.isChecked());
                // Trigger widget update immediately
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(DailyProgressWidgetConfigActivity.this);
                Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
                new DailyProgressWidget().onUpdate(DailyProgressWidgetConfigActivity.this, appWidgetManager, new int[]{appWidgetId});
                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                setResult(RESULT_OK, resultValue);
                Log.d(TAG, "Config finished, returning RESULT_OK");
                finish();
            }
        });
    }

    private int getThemeBackgroundColor() {
        // Try to resolve the window background from the current theme
        int[] attrs = new int[] { android.R.attr.windowBackground };
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        int color = ta.getColor(0, Color.WHITE);
        ta.recycle();
        return color;
    }

    static void savePrefs(Context context, int appWidgetId, boolean showStreak, boolean showLessons, boolean showReviews) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putBoolean("showStreak_" + appWidgetId, showStreak);
        prefs.putBoolean("showLessons_" + appWidgetId, showLessons);
        prefs.putBoolean("showReviews_" + appWidgetId, showReviews);
        prefs.apply();
    }

    public static boolean getShowStreak(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS_NAME, 0).getBoolean("showStreak_" + appWidgetId, true);
    }
    public static boolean getShowLessons(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS_NAME, 0).getBoolean("showLessons_" + appWidgetId, true);
    }
    public static boolean getShowReviews(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS_NAME, 0).getBoolean("showReviews_" + appWidgetId, true);
    }
}
