package com.blazing_durtles.wk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.blazing_durtles.wk.workers.PreMidnightFlagWorker;
import com.blazing_durtles.wk.util.MidnightAlarmHelper;

public class PreMidnightFlagReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Enqueue the flag worker
        OneTimeWorkRequest flagRequest = new OneTimeWorkRequest.Builder(PreMidnightFlagWorker.class).build();
        WorkManager.getInstance(context).enqueue(flagRequest);
        // Reschedule the alarm for the next day
        MidnightAlarmHelper.schedulePreMidnightFlagAlarm(context);
    }
}
