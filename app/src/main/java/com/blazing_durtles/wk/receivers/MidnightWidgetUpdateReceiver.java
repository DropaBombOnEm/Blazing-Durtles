package com.blazing_durtles.wk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.blazing_durtles.wk.GlobalSettings;
import com.blazing_durtles.wk.util.MidnightAlarmHelper;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.blazing_durtles.wk.workers.MidnightSyncWorker;

public class MidnightWidgetUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Enqueue a background sync before updating the widget
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(MidnightSyncWorker.class).build();
        WorkManager.getInstance(context).enqueue(syncRequest);
        // Reschedule the alarm for the next midnight
        MidnightAlarmHelper.scheduleMidnightAlarm(context);
    }
}
