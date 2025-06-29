package com.blazing_durtles.wk.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.blazing_durtles.wk.GlobalSettings;

public class MidnightWidgetUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Update the Daily Progress widget at midnight
        GlobalSettings.updateDailyProgressWidget(context);
    }
}
