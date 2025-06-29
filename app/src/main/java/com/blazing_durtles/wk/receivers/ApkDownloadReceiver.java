package com.blazing_durtles.wk.receivers;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import android.util.Log;

import com.blazing_durtles.wk.BuildConfig;

import java.io.File;

public class ApkDownloadReceiver extends BroadcastReceiver {
    public static final String EXTRA_DOWNLOAD_ID = "com.blazing_durtles.wk.EXTRA_DOWNLOAD_ID";
    public static final String APK_FILE_NAME = "BlazingDurtles-latest.apk";

    @Override
    public void onReceive(Context context, Intent intent) {
        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        Log.d("BlazingDurtlesUpdate", "Receiver triggered for downloadId: " + downloadId);
        if (downloadId == -1) return;
        long expectedId = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE).getLong("update_download_id", -1);
        Log.d("BlazingDurtlesUpdate", "Expected downloadId: " + expectedId);
        if (downloadId != expectedId) return;
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor c = dm.query(query);
        if (c != null && c.moveToFirst()) {
            int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            Log.d("BlazingDurtlesUpdate", "Download status: " + status);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                File apkFile = new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "BlazingDurtles-latest.apk");
                Log.d("BlazingDurtlesUpdate", "APK file path: " + apkFile.getAbsolutePath() + ", exists: " + apkFile.exists());
                Uri apkUri = FileProvider.getUriForFile(context, BuildConfig.FILEPROVIDER_AUTHORITY, apkFile);
                Log.d("BlazingDurtlesUpdate", "APK Uri: " + apkUri);
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    context.startActivity(install);
                    Log.d("BlazingDurtlesUpdate", "Install intent started");
                } catch (Exception e) {
                    Log.e("BlazingDurtlesUpdate", "Failed to start install intent", e);
                }
            }
            c.close();
        }
        // Unregister receiver after handling
        try {
            context.unregisterReceiver(this);
        } catch (Exception ignored) {}
    }
}
