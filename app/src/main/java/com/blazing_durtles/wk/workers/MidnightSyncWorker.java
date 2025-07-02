package com.blazing_durtles.wk.workers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.blazing_durtles.wk.GlobalSettings;
import com.blazing_durtles.wk.WkApplication;
import com.blazing_durtles.wk.db.AppDatabase;
import com.blazing_durtles.wk.services.ApiTaskService;

public class MidnightSyncWorker extends Worker {
    public MidnightSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Reset daily review and lesson counters to 0 at midnight
        GlobalSettings.setDailyReviewCount(0);
        GlobalSettings.setDailyLessonCount(0);
        // Enqueue summary and user sync tasks (if not already queued)
        AppDatabase db = WkApplication.getDatabase();
        db.assertGetUserTask();
        db.assertGetSummaryTask();
        // Trigger the API task runner to process the queue
        ApiTaskService.schedule();
        // Update the widget after (will reflect reset values)
        GlobalSettings.updateDailyProgressWidget(getApplicationContext());
        return Result.success();
    }
}
