package com.blazing_durtles.wk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.blazing_durtles.wk.util.MidnightAlarmHelper;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            MidnightAlarmHelper.scheduleMidnightAlarm(context);
        }
    }
}
