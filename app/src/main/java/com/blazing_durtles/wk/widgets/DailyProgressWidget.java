package com.blazing_durtles.wk.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.blazing_durtles.wk.GlobalSettings;
import com.blazing_durtles.wk.R;
import com.blazing_durtles.wk.activities.MainActivity;

public class DailyProgressWidget extends AppWidgetProvider {
    private static final String TAG = "BD_Widget";
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        Log.d(TAG, "onAppWidgetOptionsChanged for widgetId: " + appWidgetId + ", newOptions: " + newOptions);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateWidgetWithSize(context, appWidgetManager, appWidgetId, newOptions);
    }

    private void updateWidgetWithSize(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle options) {
        boolean showStreak = DailyProgressWidgetConfigActivity.getShowStreak(context, appWidgetId);
        boolean showLessons = DailyProgressWidgetConfigActivity.getShowLessons(context, appWidgetId);
        boolean showReviews = DailyProgressWidgetConfigActivity.getShowReviews(context, appWidgetId);

        int minWidth = 0;
        int minHeight = 0;
        if (options != null) {
            minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 100);
            minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40);
        }

        int layoutId;
        // Use compact layout if only streak is selected OR if widget is small and streak is selected
        if (showStreak && !showLessons && !showReviews && (minWidth < 100 || minHeight < 60)) {
            layoutId = R.layout.widget_daily_progress_compact;
        } else {
            layoutId = R.layout.widget_daily_progress_normal;
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        int lessonsDone = GlobalSettings.getDailyLessonCount();
        int reviewsDone = GlobalSettings.getDailyReviewCount();
        int streak = com.blazing_durtles.wk.activities.MainActivity.getCurrentStreakValue();

        // Set text for both layouts
        if (layoutId == R.layout.widget_daily_progress_normal) {
            views.setTextViewText(R.id.widgetLessonsDone, "Lessons Done: " + lessonsDone);
            views.setTextViewText(R.id.widgetReviewsDone, "Reviews Done: " + reviewsDone);
        }
        views.setTextViewText(R.id.widgetStreakValue, String.valueOf(streak));
        int flameRes = (streak > 0 && (lessonsDone > 0 || reviewsDone > 0)) ? R.drawable.ic_flame : R.drawable.ic_flame_dim;
        views.setImageViewResource(R.id.widgetStreakFlameIcon, flameRes);
        boolean didSomethingToday = (lessonsDone > 0 || reviewsDone > 0);
        int color = didSomethingToday ? 0xFF4CAF50 : 0xFFF44336;
        views.setTextColor(R.id.widgetStreakValue, color);

        // Show/hide elements based on config (for normal layout)
        if (layoutId == R.layout.widget_daily_progress_normal) {
            views.setViewVisibility(R.id.widgetStreakFlameIcon, showStreak ? android.view.View.VISIBLE : android.view.View.GONE);
            views.setViewVisibility(R.id.widgetStreakValue, showStreak ? android.view.View.VISIBLE : android.view.View.GONE);
            views.setViewVisibility(R.id.widgetLessonsDone, showLessons ? android.view.View.VISIBLE : android.view.View.GONE);
            views.setViewVisibility(R.id.widgetReviewsDone, showReviews ? android.view.View.VISIBLE : android.view.View.GONE);
        } else {
            // For compact, always show streak icon and value
            views.setViewVisibility(R.id.widgetStreakFlameIcon, android.view.View.VISIBLE);
            views.setViewVisibility(R.id.widgetStreakValue, android.view.View.VISIBLE);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetStreakFlameIcon, pendingIntent);
        views.setOnClickPendingIntent(R.id.widgetStreakValue, pendingIntent);
        if (layoutId == R.layout.widget_daily_progress_normal) {
            views.setOnClickPendingIntent(R.id.widgetLessonsDone, pendingIntent);
            views.setOnClickPendingIntent(R.id.widgetReviewsDone, pendingIntent);
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called for appWidgetIds: " + java.util.Arrays.toString(appWidgetIds));
        for (int appWidgetId : appWidgetIds) {
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            Log.d(TAG, "Updating widgetId: " + appWidgetId + ", options: " + options);
            updateWidgetWithSize(context, appWidgetManager, appWidgetId, options);
        }
    }
}
