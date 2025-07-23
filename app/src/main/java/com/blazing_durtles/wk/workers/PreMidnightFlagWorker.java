package com.blazing_durtles.wk.workers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.blazing_durtles.wk.GlobalSettings;

public class PreMidnightFlagWorker extends Worker {
    public PreMidnightFlagWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Check if any lessons or reviews were done today and store the flag
        boolean hadProgress = GlobalSettings.getDailyLessonCount() > 0 || GlobalSettings.getDailyReviewCount() > 0;
        GlobalSettings.setHadProgressYesterday(hadProgress);
        return Result.success();
    }
}
