package com.energy.volt.feature.dashboard;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.energy.volt.BuildConfig;
import com.energy.volt.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String GITHUB_RELEASE_URL = "https://api.github.com/repos/Beast-lucifer-666/Volt-v1/releases/latest";
    private static File pendingApkFile = null;
    private static boolean isDownloading = false;
    private static volatile boolean cancelDownloadRequested = false;

    public interface UpdateCheckCallback {
        void onCheckCompleted(boolean hasUpdate, String latestVersion);
    }

    public static void checkForUpdates(Context context, boolean showProgressDialog, UpdateCheckCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        AlertDialog progressDialog = null;
        if (showProgressDialog) {
            progressDialog = new AlertDialog.Builder(context)
                    .setMessage("Checking for updates...")
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        }

        final AlertDialog finalProgressDialog = progressDialog;

        executor.execute(() -> {
            String remoteVersion = "";
            String releaseBody = "";
            String apkUrl = "";

            try {
                URL url = new URL(GITHUB_RELEASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String tagName = json.optString("tag_name", "1.0.0");
                    remoteVersion = tagName.replaceAll("[^0-9.]", "");
                    releaseBody = json.optString("body", "");

                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }

                    if (apkUrl.isEmpty()) {
                        apkUrl = json.optString("html_url", "https://github.com/Beast-lucifer-666/Volt-v1/releases");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final String finalVersion = remoteVersion;
            final String finalReleaseBody = releaseBody;
            final String finalApkUrl = apkUrl;
            final boolean hasUpdate = isVersionNewer(finalVersion, BuildConfig.VERSION_NAME);

            mainHandler.post(() -> {
                if (finalProgressDialog != null && finalProgressDialog.isShowing()) {
                    finalProgressDialog.dismiss();
                }

                if (hasUpdate) {
                    showUpdateDialog(context, finalVersion, finalReleaseBody, finalApkUrl);
                } else if (showProgressDialog) {
                    new AlertDialog.Builder(context)
                            .setTitle("Up to Date")
                            .setMessage("You are running the latest version of Volt (v" + BuildConfig.VERSION_NAME + ").")
                            .setPositiveButton("OK", null)
                            .show();
                }

                if (callback != null) {
                    callback.onCheckCompleted(hasUpdate, finalVersion);
                }
            });
        });
    }

    public static void showUpdateDialog(Context context, String version, String releaseBody, String apkUrl) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_software_update, null);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setCancelable(true);

        TextView tvUpdateTitle = dialogView.findViewById(R.id.tvUpdateTitle);
        TextView tvUpdateSubtitle = dialogView.findViewById(R.id.tvUpdateSubtitle);
        LinearLayout layoutProgressContainer = dialogView.findViewById(R.id.layoutProgressContainer);
        ProgressBar pbDownload = dialogView.findViewById(R.id.pbDownload);
        TextView tvProgressPercent = dialogView.findViewById(R.id.tvProgressPercent);
        TextView tvReleaseNotes = dialogView.findViewById(R.id.tvReleaseNotes);

        Button btnDownloadNow = dialogView.findViewById(R.id.btnDownloadNow);
        Button btnRemindLater = dialogView.findViewById(R.id.btnRemindLater);
        Button btnCancelDownload = dialogView.findViewById(R.id.btnCancelDownload);

        String displayVersion = version.startsWith("v") ? version : "v" + version;
        tvUpdateTitle.setText("Software Update");
        tvUpdateSubtitle.setText("Volt " + displayVersion + " is available to download.");

        if (releaseBody != null && !releaseBody.trim().isEmpty()) {
            tvReleaseNotes.setText(formatReleaseNotes(releaseBody));
        } else {
            tvReleaseNotes.setText("• In-app automatic update downloader\n• Improved energy monitoring & real-time telemetry\n• UI performance enhancements & stability fixes");
        }

        btnRemindLater.setOnClickListener(v -> dialog.dismiss());

        btnDownloadNow.setOnClickListener(v -> {
            // Transition UI to Downloading State
            cancelDownloadRequested = false;
            isDownloading = true;

            tvUpdateSubtitle.setText("Downloading Volt " + displayVersion + "...");
            layoutProgressContainer.setVisibility(View.VISIBLE);
            pbDownload.setIndeterminate(true);
            tvProgressPercent.setText("Starting download...");

            btnDownloadNow.setVisibility(View.GONE);
            btnRemindLater.setVisibility(View.GONE);
            btnCancelDownload.setVisibility(View.VISIBLE);
            dialog.setCancelable(false);

            startDownload(context, apkUrl, displayVersion, dialog, tvUpdateSubtitle, pbDownload, tvProgressPercent, btnDownloadNow, btnRemindLater, btnCancelDownload, layoutProgressContainer);
        });

        btnCancelDownload.setOnClickListener(v -> {
            cancelDownloadRequested = true;
            isDownloading = false;

            tvUpdateSubtitle.setText("Volt " + displayVersion + " is available to download.");
            layoutProgressContainer.setVisibility(View.GONE);
            btnDownloadNow.setVisibility(View.VISIBLE);
            btnRemindLater.setVisibility(View.VISIBLE);
            btnCancelDownload.setVisibility(View.GONE);
            dialog.setCancelable(true);

            Toast.makeText(context, "Download cancelled.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private static void startDownload(Context context, String apkUrl, String version, Dialog dialog,
                                      TextView tvUpdateSubtitle, ProgressBar pbDownload, TextView tvProgressPercent,
                                      Button btnDownloadNow, Button btnRemindLater, Button btnCancelDownload,
                                      LinearLayout layoutProgressContainer) {

        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null) {
                downloadsDir = context.getCacheDir();
            }
            File apkFile = new File(downloadsDir, "Volt_Update_" + version.replace(".", "_") + ".apk");

            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            boolean success = false;

            try {
                URL url = new URL(apkUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage());
                }

                int fileLength = connection.getContentLength();
                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;

                while ((count = inputStream.read(data)) != -1) {
                    if (cancelDownloadRequested) {
                        break;
                    }

                    total += count;
                    outputStream.write(data, 0, count);

                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        final long currentTotal = total;
                        final int totalLength = fileLength;

                        mainHandler.post(() -> {
                            if (!cancelDownloadRequested) {
                                pbDownload.setIndeterminate(false);
                                pbDownload.setProgress(progress);
                                String currentMB = String.format(Locale.US, "%.1f", currentTotal / (1024.0 * 1024.0));
                                String totalMB = String.format(Locale.US, "%.1f", totalLength / (1024.0 * 1024.0));
                                tvProgressPercent.setText(progress + "% (" + currentMB + " MB / " + totalMB + " MB)");
                            }
                        });
                    }
                }

                outputStream.flush();
                if (!cancelDownloadRequested) {
                    success = true;
                } else {
                    if (apkFile.exists()) {
                        apkFile.delete();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (outputStream != null) outputStream.close();
                    if (inputStream != null) inputStream.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }

            final boolean finalSuccess = success;
            mainHandler.post(() -> {
                isDownloading = false;
                if (finalSuccess) {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    pendingApkFile = apkFile;
                    installApk(context, apkFile);
                } else if (!cancelDownloadRequested) {
                    tvUpdateSubtitle.setText("Volt " + version + " is available to download.");
                    layoutProgressContainer.setVisibility(View.GONE);
                    btnDownloadNow.setVisibility(View.VISIBLE);
                    btnRemindLater.setVisibility(View.VISIBLE);
                    btnCancelDownload.setVisibility(View.GONE);
                    dialog.setCancelable(true);

                    Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    public static void installApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "Update file not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                pendingApkFile = apkFile;
                new AlertDialog.Builder(context)
                        .setTitle("Permission Required")
                        .setMessage("Volt needs permission to install updates from this source. Please enable 'Allow from this source' in the settings.")
                        .setPositiveButton("Settings", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            intent.setData(Uri.parse("package:" + context.getPackageName()));
                            context.startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void checkPendingInstall(Context context) {
        if (pendingApkFile != null && pendingApkFile.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.getPackageManager().canRequestPackageInstalls()) {
                    File fileToInstall = pendingApkFile;
                    pendingApkFile = null;
                    installApk(context, fileToInstall);
                }
            }
        }
    }

    private static String formatReleaseNotes(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) return "";
        String[] lines = rawBody.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (!trimmed.startsWith("•") && !trimmed.startsWith("-") && !trimmed.startsWith("*")) {
                    sb.append("• ").append(trimmed).append("\n");
                } else {
                    if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                        trimmed = "•" + trimmed.substring(1);
                    }
                    sb.append(trimmed).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    public static boolean isVersionNewer(String remoteVer, String currentVer) {
        if (remoteVer == null || currentVer == null || remoteVer.isEmpty() || currentVer.isEmpty()) return false;
        String[] rParts = remoteVer.replaceAll("[^0-9.]", "").split("\\.");
        String[] cParts = currentVer.replaceAll("[^0-9.]", "").split("\\.");
        int length = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < length; i++) {
            int rVal = (i < rParts.length && !rParts[i].isEmpty()) ? Integer.parseInt(rParts[i]) : 0;
            int cVal = (i < cParts.length && !cParts[i].isEmpty()) ? Integer.parseInt(cParts[i]) : 0;
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return false;
    }
}
