package com.blazing_durtles.wk.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import com.blazing_durtles.wk.R;

public class SessionWidgetConfigActivity extends Activity {
    public static final String PREFS_NAME = "com.blazing_durtles.wk.widgets.SessionWidgetPrefs";
    public static final String PREF_PREFIX_KEY_SHOW_LESSONS = "show_lessons_";
    public static final String PREF_PREFIX_KEY_SHOW_REVIEWS = "show_reviews_";
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.session_appwidget_config);

        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        final CheckBox showLessons = findViewById(R.id.showLessons);
        final CheckBox showReviews = findViewById(R.id.showReviews);
        showLessons.setChecked(loadShowLessonsPref(this, appWidgetId));
        showReviews.setChecked(loadShowReviewsPref(this, appWidgetId));

        final Button saveButton = findViewById(R.id.saveButton);
        final android.widget.TextView invalidSelectionText = findViewById(R.id.invalidSelectionText);

        // Helper to update button and message state
        Runnable updateButtonState = () -> {
            boolean noneChecked = !showLessons.isChecked() && !showReviews.isChecked();
            saveButton.setEnabled(!noneChecked);
            if (noneChecked) {
                saveButton.setAlpha(0.5f);
                invalidSelectionText.setVisibility(View.VISIBLE);
            } else {
                saveButton.setAlpha(1.0f);
                invalidSelectionText.setVisibility(View.GONE);
            }
        };

        // Initial state
        updateButtonState.run();

        showLessons.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateButtonState.run();
        });
        showReviews.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateButtonState.run();
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveShowLessonsPref(SessionWidgetConfigActivity.this, appWidgetId, showLessons.isChecked());
                saveShowReviewsPref(SessionWidgetConfigActivity.this, appWidgetId, showReviews.isChecked());
                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                setResult(RESULT_OK, resultValue);
                // Now send the broadcast after setResult, right before finish
                Intent updateIntent = new Intent(SessionWidgetConfigActivity.this, com.blazing_durtles.wk.services.SessionWidgetProvider.class);
                updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
                sendBroadcast(updateIntent);
                finish();
            }
        });
    }

    static void saveShowLessonsPref(Context context, int appWidgetId, boolean showLessons) {
        android.util.Log.d("SessionWidgetConfig", "saveShowLessonsPref: id=" + appWidgetId + ", showLessons=" + showLessons);
        Context appContext = context.getApplicationContext();
        SharedPreferences.Editor prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS).edit();
        prefs.putBoolean(PREF_PREFIX_KEY_SHOW_LESSONS + appWidgetId, showLessons);
        prefs.commit(); // Use commit() to ensure the value is written before update
    }

    static boolean loadShowLessonsPref(Context context, int appWidgetId) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
        boolean value = prefs.getBoolean(PREF_PREFIX_KEY_SHOW_LESSONS + appWidgetId, false);
        android.util.Log.d("SessionWidgetConfig", "loadShowLessonsPref: id=" + appWidgetId + ", showLessons=" + value);
        return value;
    }

    static void saveShowReviewsPref(Context context, int appWidgetId, boolean showReviews) {
        Context appContext = context.getApplicationContext();
        SharedPreferences.Editor prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS).edit();
        prefs.putBoolean(PREF_PREFIX_KEY_SHOW_REVIEWS + appWidgetId, showReviews);
        prefs.commit();
    }

    static boolean loadShowReviewsPref(Context context, int appWidgetId) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
        return prefs.getBoolean(PREF_PREFIX_KEY_SHOW_REVIEWS + appWidgetId, false);
    }
}
