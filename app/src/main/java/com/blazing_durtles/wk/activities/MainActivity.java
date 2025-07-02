/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazing_durtles.wk.activities;

import static com.blazing_durtles.wk.util.ObjectSupport.runAsync;
import static com.blazing_durtles.wk.util.ObjectSupport.safe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.blazing_durtles.wk.GlobalSettings;
import com.blazing_durtles.wk.R;
import com.blazing_durtles.wk.api.ApiState;
import com.blazing_durtles.wk.jobs.RetryApiErrorJob;
import com.blazing_durtles.wk.livedata.LiveAlertContext;
import com.blazing_durtles.wk.livedata.LiveApiState;
import com.blazing_durtles.wk.livedata.LiveBurnedItems;
import com.blazing_durtles.wk.livedata.LiveCriticalCondition;
import com.blazing_durtles.wk.livedata.LiveJlptProgress;
import com.blazing_durtles.wk.livedata.LiveJoyoProgress;
import com.blazing_durtles.wk.livedata.LiveLevelDuration;
import com.blazing_durtles.wk.livedata.LiveLevelProgress;
import com.blazing_durtles.wk.livedata.LiveRecentUnlocks;
import com.blazing_durtles.wk.livedata.LiveSrsBreakDown;
import com.blazing_durtles.wk.livedata.LiveTimeLine;
import com.blazing_durtles.wk.model.Session;
import com.blazing_durtles.wk.model.TimeLine;
import com.blazing_durtles.wk.proxy.ViewProxy;
import com.blazing_durtles.wk.services.BackgroundAlarmReceiver;
import com.blazing_durtles.wk.services.BackgroundSyncWorker;
import com.blazing_durtles.wk.services.JobRunnerService;
import com.blazing_durtles.wk.views.AvailableSessionsView;
import com.blazing_durtles.wk.views.FirstTimeSetupView;
import com.blazing_durtles.wk.views.JlptProgressView;
import com.blazing_durtles.wk.views.JoyoProgressView;
import com.blazing_durtles.wk.views.LessonReviewBreakdownView;
import com.blazing_durtles.wk.views.LevelDurationView;
import com.blazing_durtles.wk.views.LevelProgressView;
import com.blazing_durtles.wk.views.LiveBurnedItemsSubjectTableView;
import com.blazing_durtles.wk.views.LiveCriticalConditionSubjectTableView;
import com.blazing_durtles.wk.views.LiveRecentUnlocksSubjectTableView;
import com.blazing_durtles.wk.views.Post60ProgressView;
import com.blazing_durtles.wk.views.SessionButtonsView;
import com.blazing_durtles.wk.views.SrsBreakDownView;
import com.blazing_durtles.wk.views.SyncProgressView;
import com.blazing_durtles.wk.views.TimeLineBarChart;
import com.blazing_durtles.wk.views.UpcomingReviewsView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The dashboard activity.
 *
 * <p>
 *     This has by far the most complex layout. The activity contains a multitude
 *     of views that display database state. It is informed of changes via LiveData
 *     instances.
 * </p>
 */
public final class MainActivity extends AbstractActivity {
    private final ViewProxy apiErrorView = new ViewProxy();
    private final ViewProxy apiKeyRejectedView = new ViewProxy();
    private final ViewProxy keyboardHelpView = new ViewProxy();

    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1001;
    private static final String PREFS_STREAK = "streak_prefs";
    private static final String KEY_LAST_SESSION_DATE = "last_session_date";
    private static final String KEY_STREAK_COUNT = "streak_count";
    private static final String KEY_LONGEST_STREAK = "longest_streak";
    private static final String KEY_STREAK_RESET_DATE = "streak_reset_date";
    private static final String KEY_STREAK_LOGS = "streak_logs";

    /**
     * The constructor.
     */
    public MainActivity() {
        super(R.layout.activity_main, R.menu.main_options_menu);
    }

    @Override
    protected void onCreateLocal(final @Nullable Bundle savedInstanceState) {
        // Modern immersive/fullscreen handling (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getInsetsController().show(android.view.WindowInsets.Type.statusBars());
            getWindow().setDecorFitsSystemWindows(true);
        } else {
            // Avoid displaying under the cutout
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
                getWindow().setAttributes(lp);
            }
        }

        apiErrorView.setDelegate(this, R.id.apiErrorView);
        apiKeyRejectedView.setDelegate(this, R.id.apiKeyRejectedView);
        keyboardHelpView.setDelegate(this, R.id.keyboardHelpView);

        final ViewProxy retryApiErrorButton1 = new ViewProxy(this, R.id.retryApiErrorButton1);
        final ViewProxy retryApiErrorButton2 = new ViewProxy(this, R.id.retryApiErrorButton2);
        final ViewProxy goToSettingsButton = new ViewProxy(this, R.id.goToSettingsButton);
        final ViewProxy viewKeyboardHelpButton = new ViewProxy(this, R.id.viewKeyboardHelpButton);
        final ViewProxy dismissKeyboardHelpButton = new ViewProxy(this, R.id.dismissKeyboardHelpButton);
        final ViewProxy startLessonsButton = new ViewProxy(this, R.id.startLessonsButton);
        final ViewProxy startReviewsButton = new ViewProxy(this, R.id.startReviewsButton);
        final ViewProxy resumeButton = new ViewProxy(this, R.id.resumeButton);
        final ViewProxy reviewCounterText = new ViewProxy(this, R.id.reviewCounterText);
        final ViewProxy lessonCounterText = new ViewProxy(this, R.id.lessonCounterText);

        retryApiErrorButton1.setOnClickListener(v -> retryApiError());
        retryApiErrorButton2.setOnClickListener(v -> retryApiError());
        goToSettingsButton.setOnClickListener(v -> goToSettings());
        viewKeyboardHelpButton.setOnClickListener(v -> viewKeyboardHelp());
        dismissKeyboardHelpButton.setOnClickListener(v -> dismissKeyboardHelp());
        startLessonsButton.setOnClickListener(v -> startLessonSession());
        startReviewsButton.setOnClickListener(v -> startReviewSession());
        resumeButton.setOnClickListener(v -> resumeSession());

        LiveApiState.getInstance().observe(this, t -> safe(() -> {
            apiErrorView.setVisibility(t == ApiState.ERROR);
            apiKeyRejectedView.setVisibility(t == ApiState.API_KEY_REJECTED);
        }));

        final @Nullable AvailableSessionsView availableSessionsView = findViewById(R.id.availableSessionsView);
        if (availableSessionsView != null) {
            availableSessionsView.setLifecycleOwner(this);
        }

        final @Nullable LessonReviewBreakdownView lessonReviewBreakdownView = findViewById(R.id.lessonReviewBreakdownView);
        if (lessonReviewBreakdownView != null) {
            lessonReviewBreakdownView.setLifecycleOwner(this);
        }

        final @Nullable FirstTimeSetupView firstTimeSetupView = findViewById(R.id.firstTimeSetupView);
        if (firstTimeSetupView != null) {
            firstTimeSetupView.setLifecycleOwner(this);
        }

        final @Nullable LevelDurationView levelDurationView = findViewById(R.id.levelDurationView);
        if (levelDurationView != null) {
            levelDurationView.setLifecycleOwner(this);
        }

        final @Nullable LevelProgressView levelProgressView = findViewById(R.id.levelProgressView);
        if (levelProgressView != null) {
            levelProgressView.setLifecycleOwner(this);
        }

        final @Nullable Post60ProgressView post60ProgressView = findViewById(R.id.post60ProgressView);
        if (post60ProgressView != null) {
            post60ProgressView.setLifecycleOwner(this);
        }

        final @Nullable JoyoProgressView joyoProgressView = findViewById(R.id.joyoProgressView);
        if (joyoProgressView != null) {
            joyoProgressView.setLifecycleOwner(this);
        }

        final @Nullable JlptProgressView jlptProgressView = findViewById(R.id.jlptProgressView);
        if (jlptProgressView != null) {
            jlptProgressView.setLifecycleOwner(this);
        }

        final @Nullable LiveRecentUnlocksSubjectTableView recentUnlocksView = findViewById(R.id.recentUnlocksView);
        if (recentUnlocksView != null) {
            recentUnlocksView.setLifecycleOwner(this);
        }

        final @Nullable LiveCriticalConditionSubjectTableView criticalConditionView = findViewById(R.id.criticalConditionView);
        if (criticalConditionView != null) {
            criticalConditionView.setLifecycleOwner(this);
        }

        final @Nullable LiveBurnedItemsSubjectTableView burnedItemsView = findViewById(R.id.burnedItemsView);
        if (burnedItemsView != null) {
            burnedItemsView.setLifecycleOwner(this);
        }

        final @Nullable SessionButtonsView sessionButtonsView = findViewById(R.id.sessionButtonsView);
        if (sessionButtonsView != null) {
            sessionButtonsView.setLifecycleOwner(this);
        }

        final @Nullable SrsBreakDownView srsBreakDownView = findViewById(R.id.srsBreakDownView);
        if (srsBreakDownView != null) {
            srsBreakDownView.setLifecycleOwner(this);
        }

        final @Nullable SyncProgressView syncProgressView = findViewById(R.id.syncProgressView);
        if (syncProgressView != null) {
            syncProgressView.setLifecycleOwner(this);
        }

        final @Nullable UpcomingReviewsView upcomingReviewsView = findViewById(R.id.upcomingReviewsView);
        if (upcomingReviewsView != null) {
            upcomingReviewsView.setLifecycleOwner(this);
        }

        final @Nullable TimeLineBarChart timeLineBarChart = findViewById(R.id.timeLineBarChart);
        if (timeLineBarChart != null) {
            timeLineBarChart.setLifecycleOwner(this);
        }        int reviewCount = GlobalSettings.getDailyReviewCount();
        reviewCounterText.setText("Reviews Completed Today: " + reviewCount);
        boolean showReviewCounter = GlobalSettings.Dashboard.getShowDailyReviewCounter();
        reviewCounterText.setVisibility(showReviewCounter ? View.VISIBLE : View.GONE);

        int lessonCount = GlobalSettings.getDailyLessonCount();
        lessonCounterText.setText("Lessons Completed Today: " + lessonCount);
        boolean showLessonCounter = GlobalSettings.Dashboard.getShowDailyLessonCounter();
        lessonCounterText.setVisibility(showLessonCounter ? View.VISIBLE : View.GONE);

        // Show notification enable dialog on first app open if not dismissed
        if (!GlobalSettings.Tutorials.getNotificationPromptDismissed()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enable Notifications?")
                .setMessage("To show notifications for new reviews, please allow notification permissions...")
                .setCancelable(false)
                .setNegativeButton("NO THANKS", (dialog, which) -> {
                    GlobalSettings.Tutorials.setNotificationPromptDismissed(true);
                    dialog.dismiss();
                })
                .setPositiveButton("ENABLE", (dialog, which) -> {
                    GlobalSettings.Tutorials.setNotificationPromptDismissed(true);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
                        } else {
                            GlobalSettings.Other.setEnableNotifications(true);
                        }
                    } else {
                        GlobalSettings.Other.setEnableNotifications(true);
                    }
                    dialog.dismiss();
                })                .show();
        }
        
        checkAndUpdateStreakOnAppOpen();
        updateStreakView();
    }

    @Override    protected void onResumeLocal() {
        BackgroundAlarmReceiver.scheduleOrCancelAlarm();
        BackgroundSyncWorker.scheduleOrCancelWork();
        
        // Check for streak reset in case the app was suspended across multiple days
        boolean streakWasReset = checkAndUpdateStreakOnAppOpen();
        
        // Update streak view in case progress was made since last view
        updateStreakView();
        
        // If streak was reset, force another UI update to ensure the reset value is displayed
        if (streakWasReset) {
            // Post to UI thread to ensure the reset is reflected immediately
            runOnUiThread(this::updateStreakView);
        }

        runAsync(() -> {
            LiveBurnedItems.getInstance().forceUpdate();
            LiveCriticalCondition.getInstance().forceUpdate();
            LiveLevelDuration.getInstance().forceUpdate();
            LiveLevelProgress.getInstance().forceUpdate();
            LiveRecentUnlocks.getInstance().forceUpdate();
            LiveSrsBreakDown.getInstance().forceUpdate();
            LiveTimeLine.getInstance().forceUpdate();
            LiveJoyoProgress.getInstance().forceUpdate();
            LiveJlptProgress.getInstance().forceUpdate();
            LiveAlertContext.getInstance().forceUpdate();
        });        // Update the review counter display every time the activity resumes
        final ViewProxy reviewCounterText = new ViewProxy(this, R.id.reviewCounterText);
        int reviewCount = GlobalSettings.getDailyReviewCount();
        reviewCounterText.setText("Reviews Completed Today: " + reviewCount);

        // Update the lesson counter display every time the activity resumes
        final ViewProxy lessonCounterText = new ViewProxy(this, R.id.lessonCounterText);
        int lessonCount = GlobalSettings.getDailyLessonCount();
        lessonCounterText.setText("Lessons Completed Today: " + lessonCount);

        keyboardHelpView.setVisibility(!GlobalSettings.Tutorials.getKeyboardHelpDismissed());

        collapseSearchBox();

        boolean showReviewCounter = GlobalSettings.Dashboard.getShowDailyReviewCounter();
        reviewCounterText.setVisibility(showReviewCounter ? View.VISIBLE : View.GONE);

        boolean showLessonCounter = GlobalSettings.Dashboard.getShowDailyLessonCounter();
        lessonCounterText.setVisibility(showLessonCounter ? View.VISIBLE : View.GONE);

        // Daily update check: only once per day if enabled + 1 hour cooldown to check again (if offline)
        if (com.blazing_durtles.wk.GlobalSettings.getEnableDailyUpdateCheck()) {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            com.blazing_durtles.wk.enums.OnlineStatus onlineStatus = com.blazing_durtles.wk.WkApplication.getInstance().getOnlineStatus();
            android.content.SharedPreferences prefs = getSharedPreferences("update_check_prefs", Context.MODE_PRIVATE);
            String lastCheck = prefs.getString("last_update_check", "");
            long lastOfflineAttempt = prefs.getLong("last_offline_attempt", 0L);
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(calendar.getTime());
            long now = System.currentTimeMillis();
            if (onlineStatus != com.blazing_durtles.wk.enums.OnlineStatus.NO_CONNECTION) {
                if (!today.equals(lastCheck)) {
                    prefs.edit().putString("last_update_check", today).apply();
                    String currentVersion = com.blazing_durtles.wk.BuildConfig.VERSION_NAME;
                    com.blazing_durtles.wk.util.UpdateChecker.checkForUpdate(this, currentVersion);
                }
            } else {
                // If offline, only try again if at least 1 hour has passed since last attempt
                if (now - lastOfflineAttempt > 60 * 60 * 1000) {
                    prefs.edit().putLong("last_offline_attempt", now).apply();
                    // (No update check, just record the attempt)
                }
            }
        }
    }

    @Override
    protected void onPauseLocal() {
        //
    }

    @Override
    protected void enableInteractionLocal() {
        final @Nullable SessionButtonsView view = findViewById(R.id.sessionButtonsView);
        if (view != null) {
            view.enableInteraction();
        }
    }

    @Override
    protected void disableInteractionLocal() {
        final @Nullable SessionButtonsView view = findViewById(R.id.sessionButtonsView);
        if (view != null) {
            view.disableInteraction();
        }
    }

    @Override
    protected boolean showWithoutApiKey() {
        return false;
    }

    /**
     * Handler for the API error retry button.
     */
    @SuppressWarnings("MethodMayBeStatic")
    private void retryApiError() {
        safe(() -> JobRunnerService.schedule(RetryApiErrorJob.class, ""));
    }

    /**
     * Handler for the API error settings button.
     */
    private void goToSettings() {
        safe(() -> goToPreferencesActivity("api_settings"));
    }

    /**
     * Handler for the keyboard help button.
     */
    private void viewKeyboardHelp() {
        safe(() -> goToActivity(KeyboardHelpActivity.class));
    }

    /**
     * Handler for dismissing the keyboard help view.
     */
    private void dismissKeyboardHelp() {
        safe(() -> {
            GlobalSettings.Tutorials.setKeyboardHelpDismissed(true);
            keyboardHelpView.setVisibility(false);
        });
    }

    /**
     * Handler for the start lessons button.
     */
    private void startLessonSession() {
        safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            final TimeLine timeLine = LiveTimeLine.getInstance().get();
            if (timeLine.hasAvailableLessons()) {
                runAsync(this, () -> {
                    Session.getInstance().startNewLessonSession(timeLine.getAvailableLessons());
                    return null;
                }, result -> goToActivity(SessionActivity.class));
            }
            else {
                enableInteraction();
            }
        });
    }

    /**
     * Handler for the start reviews button.
     */
    private void startReviewSession() {
        safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            final TimeLine timeLine = LiveTimeLine.getInstance().get();
            if (timeLine.hasAvailableReviews()) {
                runAsync(this, () -> {
                    Session.getInstance().startNewReviewSession(timeLine.getAvailableReviews());
                    return null;
                }, result -> goToActivity(SessionActivity.class));
            }
            else {
                enableInteraction();
            }
        });
    }

    /**
     * Handler for the resume session button.
     */
    private void resumeSession() {
        safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            if (Session.getInstance().isInactive()) {
                enableInteraction();
            }
            else {
                goToActivity(SessionActivity.class);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GlobalSettings.Other.setEnableNotifications(true);
            }
        }
    }    private void updateStreakView() {
        View streakView = findViewById(R.id.streakView);
        if (streakView == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_STREAK, Context.MODE_PRIVATE);
        int streak = prefs.getInt(KEY_STREAK_COUNT, 0);
        int longest = prefs.getInt(KEY_LONGEST_STREAK, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());
        
        // Check if progress was made today by looking at session logs
        Set<String> logs = prefs.getStringSet(KEY_STREAK_LOGS, new HashSet<>());
        boolean progressToday = false;
        if (logs != null) {
            for (String log : logs) {
                if (log.startsWith(today + " - Session Completed")) {
                    progressToday = true;
                    break;
                }
            }
        }
        
        // Also check current daily counters in case session hasn't been logged yet
        int dailyReviews = GlobalSettings.getDailyReviewCount();
        int dailyLessons = GlobalSettings.getDailyLessonCount();
        boolean hasProgress = progressToday || dailyReviews > 0 || dailyLessons > 0;
        
        ImageView flame = streakView.findViewById(R.id.streakFlameIcon);
        TextView value = streakView.findViewById(R.id.streakValue);
        value.setText(String.valueOf(streak));
        if (hasProgress) {
            flame.setImageResource(R.drawable.ic_flame);
            value.setTextColor(Color.parseColor("#4CAF50")); // green
        } else {
            flame.setImageResource(R.drawable.ic_flame_dim);
            value.setTextColor(Color.parseColor("#FF0000")); // red
        }
        // Show/hide based on setting
        boolean show = GlobalSettings.Dashboard.getShowDailyStreak();
        streakView.setVisibility(show ? View.VISIBLE : View.GONE);    }    private boolean checkAndUpdateStreakOnAppOpen() {
        SharedPreferences prefs = getSharedPreferences(PREFS_STREAK, Context.MODE_PRIVATE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());
        String lastSessionDate = prefs.getString(KEY_LAST_SESSION_DATE, "");
        int streak = prefs.getInt(KEY_STREAK_COUNT, 0);
        int longest = prefs.getInt(KEY_LONGEST_STREAK, 0);
        String lastResetDate = prefs.getString(KEY_STREAK_RESET_DATE, "");
        
        // Only check for reset once per day
        if (!today.equals(lastResetDate)) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            String yesterday = sdf.format(cal.getTime());
            
            // Reset streak if there's a gap: lastSessionDate is not yesterday and not today
            if (!lastSessionDate.equals("") && !lastSessionDate.equals(yesterday) && !lastSessionDate.equals(today)) {
                // Missed one or more days, reset streak
                streak = 0;
                prefs.edit().putInt(KEY_STREAK_COUNT, streak)
                    .putInt(KEY_LONGEST_STREAK, longest)
                    .putString(KEY_STREAK_RESET_DATE, today)
                    .apply();
                // Update widget
                com.blazing_durtles.wk.GlobalSettings.updateDailyProgressWidget(com.blazing_durtles.wk.WkApplication.getInstance());
                return true; // Indicate that a reset occurred
            } else {
                // No reset needed, just update reset date to prevent multiple checks per day
                prefs.edit().putString(KEY_STREAK_RESET_DATE, today).apply();
            }
        }
        return false; // No reset occurred
    }

    // Helper to prune logs to last 7 days
    private static void pruneStreakLogs(SharedPreferences prefs) {
        Set<String> logs = prefs.getStringSet(KEY_STREAK_LOGS, new HashSet<>());
        if (logs == null || logs.isEmpty()) return;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -7);
        String sevenDaysAgo = sdf.format(cal.getTime());
        Set<String> pruned = new HashSet<>();
        for (String log : logs) {
            // Assume log format: "yyyy-MM-dd - ..."
            if (log.length() >= 10 && log.substring(0, 10).compareTo(sevenDaysAgo) >= 0) {
                pruned.add(log);
            }
        }        prefs.edit().putStringSet(KEY_STREAK_LOGS, pruned).apply();
    }    /**
     * Log session completion and update streak based purely on consecutive progress days.
     * 
     * This method ensures that streaks only count days where actual progress was made,
     * completely ignoring sessions without progress.
     * 
     * Streak Logic:
     * - Only processes streak if current day has progress (dailyReviews > 0 OR dailyLessons > 0)
     * - Only increments streak if this is the first session with progress today
     * - Streak resets are handled separately in checkAndUpdateStreakOnAppOpen()
     * - Simple increment: completed session with progress today = streak++
     * 
     * BUG FIX: 
     * - No longer gives "free" streak increments for sessions without progress
     * - Only increments when actual progress is made on a new day
     * - Reset logic is separate from increment logic for clarity
     * 
     * @param context The context for accessing SharedPreferences
     */
    public static void logSessionCompleted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_STREAK, Context.MODE_PRIVATE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());
        String lastSessionDate = prefs.getString(KEY_LAST_SESSION_DATE, "");
        int streak = prefs.getInt(KEY_STREAK_COUNT, 0);
        int longest = prefs.getInt(KEY_LONGEST_STREAK, 0);
        
        // Check if user made actual progress today
        int dailyReviews = GlobalSettings.getDailyReviewCount();
        int dailyLessons = GlobalSettings.getDailyLessonCount();
        boolean hasProgress = dailyReviews > 0 || dailyLessons > 0;
        
        // Log the session, only one per date
        Set<String> logs = prefs.getStringSet(KEY_STREAK_LOGS, new HashSet<>());
        if (logs == null) logs = new HashSet<>();
        boolean alreadyLoggedToday = false;
        for (String log : logs) {
            if (log.startsWith(today + " - ")) {
                alreadyLoggedToday = true;
                break;
            }
        }
        
        if (!alreadyLoggedToday && hasProgress) {
            // Only log if user made actual progress (completed items)
            logs.add(today + " - Session Completed (R:" + dailyReviews + " L:" + dailyLessons + ")");
            prefs.edit().putStringSet(KEY_STREAK_LOGS, logs).apply();
            pruneStreakLogs(prefs);
        }        if (!today.equals(lastSessionDate) && hasProgress) {
            // Simple increment: if this is the first session with progress today, increment streak
            streak++;
            if (streak > longest) longest = streak;
            
            // Update last session date only if progress was made
            prefs.edit().putString(KEY_LAST_SESSION_DATE, today).putInt(KEY_STREAK_COUNT, streak).putInt(KEY_LONGEST_STREAK, longest).apply();
            // Update widget
            com.blazing_durtles.wk.GlobalSettings.updateDailyProgressWidget(com.blazing_durtles.wk.WkApplication.getInstance());
        }    }

    public static int getCurrentStreakValue() {
        android.content.SharedPreferences prefs = com.blazing_durtles.wk.WkApplication.getInstance().getSharedPreferences(PREFS_STREAK, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_STREAK_COUNT, 0);
    }
}
