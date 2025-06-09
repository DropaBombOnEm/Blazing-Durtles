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

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

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

    /**
     * The constructor.
     */
    public MainActivity() {
        super(R.layout.activity_main, R.menu.main_options_menu);
    }

    @Override
    protected void onCreateLocal(final @Nullable Bundle savedInstanceState) {
        // Always show the notification bar
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Avoid displaying under the cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            getWindow().setAttributes(lp);
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
        }

        int reviewCount = GlobalSettings.DailyReviewCounter.getCount();
        reviewCounterText.setText("Reviews Completed Today: " + reviewCount);
        reviewCounterText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResumeLocal() {
        BackgroundAlarmReceiver.scheduleOrCancelAlarm();
        BackgroundSyncWorker.scheduleOrCancelWork();

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
        });

        // Update the review counter display every time the activity resumes
        final ViewProxy reviewCounterText = new ViewProxy(this, R.id.reviewCounterText);
        int reviewCount = GlobalSettings.DailyReviewCounter.getCount();
        reviewCounterText.setText("Reviews Completed Today: " + reviewCount);
        reviewCounterText.setVisibility(android.view.View.VISIBLE);

        keyboardHelpView.setVisibility(!GlobalSettings.Tutorials.getKeyboardHelpDismissed());

        collapseSearchBox();
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
}
