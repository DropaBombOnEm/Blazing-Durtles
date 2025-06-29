package com.blazing_durtles.wk.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import com.blazing_durtles.wk.BuildConfig;
import io.noties.markwon.Markwon;
import android.provider.Settings;
import android.app.PendingIntent;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import java.io.IOException;

public class UpdateChecker {
    private static final String RELEASES_URL = "https://api.github.com/repos/DropaBombOnEm/Blazing-Durtles/releases";
    private static final String APK_SUFFIX = ".apk";

    public static void checkForUpdate(Activity activity, String currentVersion) {
        new AsyncTask<Void, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackground(Void... voids) {
                try {
                    URI uri = URI.create(RELEASES_URL);
                    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONArray releases = new JSONArray(sb.toString());
                    if (releases.length() > 0) {
                        return releases.getJSONObject(0); // latest release
                    }
                } catch (IOException e) {
                    // Network error (offline)
                    return new JSONObject(); // Special marker for offline
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
            @Override
            protected void onPostExecute(JSONObject release) {
                if (release == null) {
                    Toast.makeText(activity, "No Updates Needed", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Special marker for offline
                if (release.length() == 0) {
                    Toast.makeText(activity, "You're Currently Offline", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    String latestVersion = release.getString("tag_name").replace("v", "");
                    if (compareVersions(latestVersion, currentVersion) > 0) {
                        String changelog = release.optString("body", "");
                        JSONArray assets = release.getJSONArray("assets");
                        String apkUrl = null;
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.getString("name");
                            if (name.endsWith(APK_SUFFIX)) {
                                apkUrl = asset.getString("browser_download_url");
                                break;
                            }
                        }
                        showUpdateDialog(activity, latestVersion, changelog, apkUrl);
                    } else {
                        Toast.makeText(activity, "No Updates Needed", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(activity, "No Updates Needed", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static void showUpdateDialog(Activity activity, String version, String changelog, String apkUrl) {
        Markwon markwon = Markwon.create(activity);
        android.widget.TextView changelogView = new android.widget.TextView(activity);
        changelogView.setPadding(32, 32, 32, 32);
        changelogView.setTextIsSelectable(true);
        markwon.setMarkdown(changelogView, changelog);
        new androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("New Update Available: v" + version)
            .setView(changelogView)
            .setNegativeButton("Not Now", null)
            .setPositiveButton("Update", (dialog, which) -> downloadAndPromptInstall(activity, apkUrl))
            .show();
    }

    private static void downloadAndPromptInstall(Activity activity, String apkUrl) {
        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... voids) {
                try {
                    URI uri = URI.create(apkUrl);
                    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                    conn.connect();
                    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                    InputStream is = conn.getInputStream();
                    File apkFile = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "BlazingDurtles-latest.apk");
                    FileOutputStream fos = new FileOutputStream(apkFile);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                    fos.close();
                    is.close();
                    return apkFile;
                } catch (Exception e) {
                    Log.e("BlazingDurtlesUpdate", "Manual download failed", e);
                    return null;
                }
            }
            @Override
            protected void onPostExecute(File apkFile) {
                if (apkFile == null || !apkFile.exists()) {
                    Toast.makeText(activity, "Download failed.", Toast.LENGTH_SHORT).show();
                    Log.e("BlazingDurtlesUpdate", "APK file missing after download");
                    return;
                }
                Log.d("BlazingDurtlesUpdate", "APK file downloaded: " + apkFile.getAbsolutePath());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    boolean canInstall = activity.getPackageManager().canRequestPackageInstalls();
                    Log.d("BlazingDurtlesUpdate", "Can request package installs: " + canInstall);
                    if (!canInstall) {
                        Toast.makeText(activity, "Please allow 'Install unknown apps' for updates.", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                        showInstallNotification(activity, apkFile);
                        return;
                    }
                }
                try {
                    Uri apkUri = androidx.core.content.FileProvider.getUriForFile(activity, BuildConfig.FILEPROVIDER_AUTHORITY, apkFile);
                    Log.d("BlazingDurtlesUpdate", "APK Uri: " + apkUri);
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(install);
                    Log.d("BlazingDurtlesUpdate", "Install intent started");
                } catch (Exception e) {
                    Log.e("BlazingDurtlesUpdate", "Failed to start install intent", e);
                    Toast.makeText(activity, "Install prompt failed: " + e, Toast.LENGTH_LONG).show();
                    showInstallNotification(activity, apkFile);
                }
            }
        }.execute();
    }

    private static void showInstallNotification(Context context, File apkFile) {
        Log.d("BlazingDurtlesUpdate", "Showing fallback install notification");
        Uri apkUri = androidx.core.content.FileProvider.getUriForFile(context, BuildConfig.FILEPROVIDER_AUTHORITY, apkFile);
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, installIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "update_install_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Update Install", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setContentTitle("Blazing Durtles Update Ready")
            .setContentText("Tap to install the update.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
        nm.notify(1001, builder.build());
    }

    // Returns 1 if v1 > v2, -1 if v1 < v2, 0 if equal
    private static int compareVersions(String v1, String v2) {
        String[] a1 = v1.split("\\.");
        String[] a2 = v2.split("\\.");
        int len = Math.max(a1.length, a2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < a1.length ? Integer.parseInt(a1[i]) : 0;
            int n2 = i < a2.length ? Integer.parseInt(a2[i]) : 0;
            if (n1 != n2) return n1 > n2 ? 1 : -1;
        }
        return 0;
    }
}
