package com.cornming.walkadventure;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ = 20;

    // 冒險進度 SharedPreferences 欄位（隨雲端備份還原）。前幾個鍵名沿用 v0.1.0，升級後可無縫接軌。
    private static final String KEY_DAY = "day";
    private static final String KEY_LIFETIME = "lifetime";
    private static final String KEY_COINS = "coins";
    private static final String KEY_CLAIMED_CHEST = "claimed_chest";
    private static final String KEY_EVENT_BUCKET = "event_bucket";
    private static final String KEY_TODAY = "today_steps";

    // 與這台手機硬體／開機／更新快取綁定的狀態（不應跨機備份還原，另存於 walk_adventure_device）
    private static final String KEY_LAST_SENSOR = "last_sensor_steps";
    private static final String KEY_BOOT_COUNT = "boot_count";
    private static final String KEY_PERMISSION_REQUESTED = "permission_requested";
    private static final String KEY_LEGACY_BASELINE = "baseline";
    private static final String KEY_UPDATE_ETAG = "update_etag";
    private static final String KEY_UPDATE_LAST_CHECK = "update_last_check_ms";
    private static final String KEY_CACHED_TAG = "update_cached_tag";
    private static final String KEY_CACHED_HTML_URL = "update_cached_html_url";
    private static final String KEY_CACHED_APK_NAME = "update_cached_apk_name";
    private static final String KEY_CACHED_APK_URL = "update_cached_apk_url";
    private static final String KEY_CACHED_APK_SIZE = "update_cached_apk_size";
    private static final String KEY_CACHED_APK_SHA256 = "update_cached_apk_sha256";
    private static final String KEY_DOWNLOADED_TAG = "update_downloaded_tag";
    private static final String KEY_DISMISSED_TAG = "update_dismissed_tag";

    private static final int UPDATE_STATE_IDLE = 0;
    private static final int UPDATE_STATE_CHECKING = 1;
    private static final int UPDATE_STATE_DOWNLOADING = 2;
    private static final int UPDATE_STATE_READY = 3;

    private static final String[] EVENTS = {
        "★ 遇見旅行 NPC！送你一份祝福",
        "★ 小怪獸出現了！你用腳步擊退牠",
        "★ 撿到幸運道具！",
        "★ 神秘光芒照亮前方道路"
    };

    private GameView view;
    private Button updateButton;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private SharedPreferences prefs;
    private SharedPreferences devicePrefs;
    private StepProgress progress;
    private Toast activeToast;
    private AlertDialog activeDialog;
    private final Random random = new Random();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private String currentVersion = "0.1.0";
    private AppUpdate.ReleaseInfo readyRelease;
    private boolean updateInFlight = false;
    private boolean pendingInstallAfterSettings = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("walk_adventure", MODE_PRIVATE);
        devicePrefs = getSharedPreferences("walk_adventure_device", MODE_PRIVATE);
        progress = loadProgress();
        currentVersion = installedVersionName();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;

        buildLayout();
        // 權限被拒絕時，點一下遊戲畫面可再次授權或前往系統設定
        view.setOnClickListener(v -> {
            if (!hasStepPermission()) handlePermissionTap();
        });
        if (stepSensor != null && !hasStepPermission() && !devicePrefs.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
            requestStepPermission();
        }

        restoreOrCleanCachedUpdate();
        long lastCheckMs = devicePrefs.getLong(KEY_UPDATE_LAST_CHECK, 0L);
        if (AppUpdate.shouldAutoCheck(lastCheckMs, System.currentTimeMillis())) {
            checkForUpdates(false);
        }
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        // 狀態列、導覽列與瀏海邊界統一由 root 保留，底部的更新列就不會與導覽鍵重疊
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            }
            v.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom()
            );
            return insets.consumeSystemWindowInsets();
        });

        view = new GameView(this);
        root.addView(view, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(231, 216, 194));
        root.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, dp(1))
        ));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView versionText = new TextView(this);
        versionText.setText(getString(R.string.version_label, currentVersion));
        versionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        versionText.setTextColor(Color.rgb(110, 90, 76));
        footer.addView(versionText, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ));

        updateButton = new Button(this);
        updateButton.setAllCaps(false);
        updateButton.setStateListAnimator(null);
        updateButton.setMinHeight(dp(48));
        updateButton.setMinWidth(dp(96));
        updateButton.setPadding(dp(16), dp(10), dp(16), dp(10));
        updateButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        updateButton.setTypeface(Typeface.create("sans", Typeface.BOLD));
        updateButton.setOnClickListener(v -> onUpdateButtonClicked());
        footer.addView(updateButton, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(footer, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setContentView(root);
        renderUpdateButton(UPDATE_STATE_IDLE);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }

    private void renderUpdateButton(int state) {
        if (updateButton == null) return;
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(24));
        if (state == UPDATE_STATE_READY && readyRelease != null) {
            pill.setColor(Color.rgb(46, 107, 52));
            updateButton.setBackground(pill);
            updateButton.setTextColor(Color.rgb(255, 247, 232));
            updateButton.setText(getString(R.string.update_ready_button, readyRelease.versionName));
            updateButton.setEnabled(true);
        } else if (state == UPDATE_STATE_CHECKING || state == UPDATE_STATE_DOWNLOADING) {
            pill.setColor(Color.rgb(239, 226, 202));
            updateButton.setBackground(pill);
            updateButton.setTextColor(Color.rgb(110, 90, 76));
            updateButton.setText(getString(
                state == UPDATE_STATE_CHECKING ? R.string.update_checking : R.string.update_downloading
            ));
            updateButton.setEnabled(false);
        } else {
            pill.setColor(Color.rgb(255, 225, 170));
            updateButton.setBackground(pill);
            updateButton.setTextColor(getColor(R.color.ink));
            updateButton.setText(getString(R.string.update_check));
            updateButton.setEnabled(true);
        }
        view.updateAccessibility();
    }

    private String installedVersionName() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                info = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            return info.versionName != null ? info.versionName : "0.1.0";
        } catch (PackageManager.NameNotFoundException ignored) {
            return "0.1.0";
        }
    }

    private void restoreOrCleanCachedUpdate() {
        File cachedApk = ApkFileProvider.updateApkFile(this);
        String downloadedTag = devicePrefs.getString(KEY_DOWNLOADED_TAG, null);
        AppUpdate.ReleaseInfo cachedInfo = loadCachedReleaseInfo();
        if (downloadedTag != null
            && cachedInfo != null
            && downloadedTag.equals(cachedInfo.tagName)
            && AppUpdate.isRemoteNewer(cachedInfo.versionName, currentVersion)
            && AppUpdate.verifyDownloadedApk(cachedApk, cachedInfo.apkSize, cachedInfo.apkSha256)) {
            readyRelease = cachedInfo;
            renderUpdateButton(UPDATE_STATE_READY);
            return;
        }
        clearStaleDownloadedApk(cachedApk);
    }

    private void clearStaleDownloadedApk(File cachedApk) {
        if (cachedApk.exists()) {
            boolean ignored = cachedApk.delete();
        }
        readyRelease = null;
        devicePrefs.edit()
            .remove(KEY_DOWNLOADED_TAG)
            .remove(KEY_DISMISSED_TAG)
            .apply();
        renderUpdateButton(UPDATE_STATE_IDLE);
    }

    private AppUpdate.ReleaseInfo loadCachedReleaseInfo() {
        String tag = devicePrefs.getString(KEY_CACHED_TAG, null);
        String apkUrl = devicePrefs.getString(KEY_CACHED_APK_URL, null);
        if (tag == null || !AppUpdate.isTrustedAssetUrl(apkUrl)) return null;
        String version = AppUpdate.normalizeVersion(tag);
        if (!AppUpdate.isValidSemVer(version)) return null;
        String htmlUrl = devicePrefs.getString(KEY_CACHED_HTML_URL, AppUpdate.RELEASES_PAGE_URL);
        if (!AppUpdate.isTrustedPageUrl(htmlUrl)) htmlUrl = AppUpdate.RELEASES_PAGE_URL;
        String apkName = devicePrefs.getString(KEY_CACHED_APK_NAME, "WalkAdventure-" + tag + ".apk");
        long apkSize = devicePrefs.getLong(KEY_CACHED_APK_SIZE, 0L);
        String apkSha256 = devicePrefs.getString(KEY_CACHED_APK_SHA256, "");
        return new AppUpdate.ReleaseInfo(tag, version, htmlUrl, apkName, apkUrl, apkSize, apkSha256);
    }

    private void saveReleaseMetadata(String etag, AppUpdate.ReleaseInfo info, long checkedAtMs) {
        SharedPreferences.Editor editor = devicePrefs.edit()
            .putLong(KEY_UPDATE_LAST_CHECK, checkedAtMs);
        if (etag != null && !etag.trim().isEmpty()) {
            editor.putString(KEY_UPDATE_ETAG, etag.trim());
        }
        if (info != null) {
            editor.putString(KEY_CACHED_TAG, info.tagName)
                .putString(KEY_CACHED_HTML_URL, info.htmlUrl)
                .putString(KEY_CACHED_APK_NAME, info.apkName)
                .putString(KEY_CACHED_APK_URL, info.apkDownloadUrl)
                .putLong(KEY_CACHED_APK_SIZE, info.apkSize)
                .putString(KEY_CACHED_APK_SHA256, info.apkSha256);
        }
        editor.apply();
    }

    private void onUpdateButtonClicked() {
        if (readyRelease != null) {
            onInstallRequested();
            return;
        }
        checkForUpdates(true);
    }

    private void checkForUpdates(boolean manual) {
        if (updateInFlight) return;
        updateInFlight = true;
        if (manual) renderUpdateButton(UPDATE_STATE_CHECKING);

        final String etag = devicePrefs.getString(KEY_UPDATE_ETAG, null);
        final AppUpdate.ReleaseInfo cachedInfo = loadCachedReleaseInfo();
        final File targetApkFile = ApkFileProvider.updateApkFile(this);

        updateExecutor.execute(() -> {
            AppUpdate.CheckOutcome outcome = AppUpdate.fetchLatestRelease(etag, cachedInfo);
            long checkedAtMs = System.currentTimeMillis();
            if (outcome.status == AppUpdate.CheckOutcome.STATUS_OK
                || outcome.status == AppUpdate.CheckOutcome.STATUS_NOT_MODIFIED) {
                saveReleaseMetadata(outcome.etag, outcome.release, checkedAtMs);
            }

            if (outcome.status != AppUpdate.CheckOutcome.STATUS_OK
                && outcome.status != AppUpdate.CheckOutcome.STATUS_NOT_MODIFIED) {
                finishUpdateCheckOnUi(manual, outcome.status, null, false);
                return;
            }

            AppUpdate.ReleaseInfo release = outcome.release;
            if (release == null || !AppUpdate.isRemoteNewer(release.versionName, currentVersion)) {
                finishUpdateCheckOnUi(manual, AppUpdate.CheckOutcome.STATUS_NO_RELEASE, null, true);
                return;
            }

            boolean alreadyDownloaded = AppUpdate.verifyDownloadedApk(
                targetApkFile,
                release.apkSize,
                release.apkSha256
            );
            if (!alreadyDownloaded) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        renderUpdateButton(UPDATE_STATE_DOWNLOADING);
                    }
                });
                alreadyDownloaded = AppUpdate.downloadVerifiedApk(release, targetApkFile);
            }

            if (alreadyDownloaded) {
                devicePrefs.edit().putString(KEY_DOWNLOADED_TAG, release.tagName).apply();
                finishUpdateCheckOnUi(manual, AppUpdate.CheckOutcome.STATUS_OK, release, true);
            } else {
                finishUpdateCheckOnUi(manual, AppUpdate.CheckOutcome.STATUS_ERROR, release, false);
            }
        });
    }

    private void finishUpdateCheckOnUi(
        boolean manual,
        int status,
        AppUpdate.ReleaseInfo downloadedRelease,
        boolean reachedServer
    ) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            updateInFlight = false;

            if (downloadedRelease != null) {
                readyRelease = downloadedRelease;
                renderUpdateButton(UPDATE_STATE_READY);
                String dismissedTag = devicePrefs.getString(KEY_DISMISSED_TAG, null);
                if (manual || !downloadedRelease.tagName.equals(dismissedTag)) {
                    showUpdateReadyDialog(downloadedRelease);
                }
                return;
            }

            if (reachedServer && readyRelease != null
                && !AppUpdate.isRemoteNewer(readyRelease.versionName, currentVersion)) {
                clearStaleDownloadedApk(ApkFileProvider.updateApkFile(this));
            } else {
                renderUpdateButton(readyRelease != null ? UPDATE_STATE_READY : UPDATE_STATE_IDLE);
            }

            if (!manual) return;
            if (status == AppUpdate.CheckOutcome.STATUS_NO_RELEASE) {
                showShortToast(getString(R.string.update_up_to_date, currentVersion));
            } else if (status == AppUpdate.CheckOutcome.STATUS_RATE_LIMITED) {
                showShortToast(getString(R.string.update_rate_limited));
            } else {
                showShortToast(getString(
                    downloadedRelease != null ? R.string.update_download_failed : R.string.update_check_failed
                ));
            }
        });
    }

    private void showUpdateReadyDialog(AppUpdate.ReleaseInfo release) {
        dismissActiveDialog();
        activeDialog = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_dialog_title, release.versionName))
            .setMessage(getString(R.string.update_dialog_message, currentVersion))
            .setPositiveButton(R.string.update_install_now, (d, which) -> onInstallRequested())
            .setNeutralButton(R.string.update_view_release, (d, which) -> openReleasePage(release.htmlUrl))
            .setNegativeButton(R.string.update_later, (d, which) ->
                devicePrefs.edit().putString(KEY_DISMISSED_TAG, release.tagName).apply()
            )
            .create();
        activeDialog.show();
    }

    private void onInstallRequested() {
        if (readyRelease == null) return;
        File apkFile = ApkFileProvider.updateApkFile(this);
        if (!AppUpdate.verifyDownloadedApk(apkFile, readyRelease.apkSize, readyRelease.apkSha256)) {
            clearStaleDownloadedApk(apkFile);
            checkForUpdates(true);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            dismissActiveDialog();
            activeDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.update_permission_title)
                .setMessage(R.string.update_permission_message)
                .setPositiveButton(R.string.update_open_settings, (d, which) -> openUnknownSourcesSettings())
                .setNegativeButton(R.string.update_cancel, null)
                .create();
            activeDialog.show();
            return;
        }
        launchApkInstaller();
    }

    private void openUnknownSourcesSettings() {
        pendingInstallAfterSettings = true;
        Intent intent = new Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", getPackageName(), null)
        );
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            pendingInstallAfterSettings = false;
            Intent appSettings = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
            );
            try {
                startActivity(appSettings);
            } catch (ActivityNotFoundException err) {
                openReleasePage(readyRelease != null ? readyRelease.htmlUrl : AppUpdate.RELEASES_PAGE_URL);
            }
        }
    }

    private void launchApkInstaller() {
        Uri apkUri = ApkFileProvider.uriForUpdateApk(this);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, ApkFileProvider.MIME_APK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            showShortToast(getString(R.string.update_installer_unavailable));
            openReleasePage(readyRelease != null ? readyRelease.htmlUrl : AppUpdate.RELEASES_PAGE_URL);
        }
    }

    private void openReleasePage(String rawUrl) {
        String safeUrl = AppUpdate.isTrustedPageUrl(rawUrl) ? rawUrl : AppUpdate.RELEASES_PAGE_URL;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)));
        } catch (ActivityNotFoundException ignored) {
            // 裝置沒有瀏覽器時靜默略過
        }
    }

    private void dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }
        activeDialog = null;
    }

    private void showShortToast(String text) {
        if (activeToast != null) activeToast.cancel();
        activeToast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        activeToast.show();
    }

    @Override protected void onResume() {
        super.onResume();
        register();
        if (pendingInstallAfterSettings) {
            pendingInstallAfterSettings = false;
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) {
                onInstallRequested();
            }
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override protected void onDestroy() {
        dismissActiveDialog();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ) return;
        register();
    }

    private boolean hasStepPermission() {
        return Build.VERSION.SDK_INT < 29
            || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isPermissionPermanentlyDenied() {
        if (Build.VERSION.SDK_INT < 29 || hasStepPermission()) return false;
        return devicePrefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
            && !shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION);
    }

    private void handlePermissionTap() {
        if (isPermissionPermanentlyDenied()) {
            Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
            );
            startActivity(intent);
            return;
        }
        requestStepPermission();
    }

    private void requestStepPermission() {
        if (Build.VERSION.SDK_INT < 29) return;
        devicePrefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply();
        requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ);
    }

    // 權限對話框或系統設定關閉後 Activity 會回到 onResume，這裡會重新檢查並註冊。
    private void register() {
        view.sensorMissing = stepSensor == null;
        view.permissionMissing = stepSensor != null && !hasStepPermission();
        view.permissionPermanentlyDenied = view.permissionMissing && isPermissionPermanentlyDenied();
        if (sensorManager != null && !view.sensorMissing && !view.permissionMissing) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        view.updateAccessibility();
        view.invalidate();
    }

    private String today() {
        return LocalDate.now().toString();
    }

    /** 開機次數：感測器的累計步數在重開機後會歸零，靠它分辨。讀不到時回傳 UNKNOWN。 */
    private long bootCount() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, (int) StepProgress.UNKNOWN);
    }

    private StepProgress loadProgress() {
        StepProgress p = new StepProgress();
        p.lifetime = prefs.getLong(KEY_LIFETIME, 0);
        p.coins = prefs.getLong(KEY_COINS, 0);
        p.claimedChest = prefs.getLong(KEY_CLAIMED_CHEST, 0);
        p.eventBucket = prefs.getLong(KEY_EVENT_BUCKET, 0);
        p.lastSensorSteps = devicePrefs.contains(KEY_LAST_SENSOR)
            ? devicePrefs.getLong(KEY_LAST_SENSOR, StepProgress.UNKNOWN)
            : prefs.getLong(KEY_LAST_SENSOR, StepProgress.UNKNOWN);
        p.lastBootCount = devicePrefs.contains(KEY_BOOT_COUNT)
            ? devicePrefs.getLong(KEY_BOOT_COUNT, StepProgress.UNKNOWN)
            : prefs.getLong(KEY_BOOT_COUNT, StepProgress.UNKNOWN);
        p.day = prefs.getString(KEY_DAY, null);
        p.todaySteps = prefs.contains(KEY_TODAY)
            ? prefs.getLong(KEY_TODAY, 0)
            : StepProgress.todayStepsFromLegacy(p.day, today(), prefs.getFloat(KEY_LEGACY_BASELINE, -1), p.lastSensorSteps);
        return p;
    }

    /** 一次讀數的所有變更分別原子寫入冒險進度與本機感測器錨點。 */
    private void saveProgress() {
        prefs.edit()
            .putString(KEY_DAY, progress.day)
            .putLong(KEY_TODAY, progress.todaySteps)
            .putLong(KEY_LIFETIME, progress.lifetime)
            .putLong(KEY_COINS, progress.coins)
            .putLong(KEY_CLAIMED_CHEST, progress.claimedChest)
            .putLong(KEY_EVENT_BUCKET, progress.eventBucket)
            .remove(KEY_LEGACY_BASELINE)
            .remove(KEY_LAST_SENSOR)
            .remove(KEY_BOOT_COUNT)
            .apply();
        devicePrefs.edit()
            .putLong(KEY_LAST_SENSOR, progress.lastSensorSteps)
            .putLong(KEY_BOOT_COUNT, progress.lastBootCount)
            .apply();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e == null || e.sensor == null || e.sensor.getType() != Sensor.TYPE_STEP_COUNTER
            || e.values == null || e.values.length == 0) {
            return;
        }
        StepProgress.Update update = progress.onSensorReading((long) e.values[0], today(), bootCount(), random);
        saveProgress();
        String eventMessage = update.event ? EVENTS[random.nextInt(EVENTS.length)] : null;
        String announcement = StepProgress.buildAnnouncement(update, eventMessage);
        if (announcement != null) {
            announce(announcement);
        }
        view.updateAccessibility();
        view.invalidate();
    }

    private void announce(String message) {
        view.message = message;
        showShortToast(message.replace('\n', ' '));
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    class GameView extends View {
        // 畫面以 540 寬的設計座標排版，再依螢幕寬度等比例放大，
        // 不同解析度與像素密度的手機看到的比例才會一樣。
        private static final float DESIGN_WIDTH = 540f;
        private static final float DESIGN_HEIGHT = 690f;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
        private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
        private final int bg;
        private final int ink;
        String message = "準備出發！每一步都是冒險進度。";
        boolean sensorMissing = false;
        boolean permissionMissing = false;
        boolean permissionPermanentlyDenied = false;

        GameView(Context c) {
            super(c);
            bg = c.getColor(R.color.bg);
            ink = c.getColor(R.color.ink);
            setBackgroundColor(bg);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            updateAccessibility();
        }

        private String statusText() {
            if (sensorMissing) return "此裝置沒有步數感測器，無法取得實際步數。";
            if (permissionPermanentlyDenied) return "尚未開啟「身體活動」權限，點一下前往系統設定允許。";
            if (permissionMissing) return "尚未允許「活動辨識」權限，無法計步。點一下畫面重新授權。";
            return "步數感測器已連線 • 關閉 App 也會保留進度";
        }

        void updateAccessibility() {
            if (progress == null) return;
            long todaySteps = progress.todayStepsOn(today());
            String updateNotice = readyRelease != null
                ? "。已下載新版本 v" + readyRelease.versionName + "，可點擊底部按鈕安裝"
                : "";
            setContentDescription(
                "走走冒險，Lv." + progress.level()
                    + "，金幣 " + progress.coins
                    + "，今日步數 " + todaySteps
                    + "，已探索 " + progress.exploredCells() + " 格"
                    + "，下一個寶箱在累積 " + progress.nextChestAt() + " 步"
                    + "。" + message.replace('\n', ' ')
                    + "。" + statusText()
                    + updateNotice
            );
        }

        void text(Canvas c, String s, float x, float y, float size, boolean isBold) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(size);
            p.setColor(ink);
            p.setTypeface(isBold ? bold : regular);
            c.drawText(s, x, y, p);
        }

        void drawCenteredText(Canvas c, String s, float cx, float cy, float size, boolean isBold) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size);
            p.setColor(ink);
            p.setTypeface(isBold ? bold : regular);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(s, cx, cy - (fm.ascent + fm.descent) * 0.5f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        List<String> wrapLines(String raw, float maxWidth, float size, boolean isBold) {
            p.setTextSize(size);
            p.setTypeface(isBold ? bold : regular);
            List<String> lines = new ArrayList<>();
            for (String paragraph : raw.split("\n")) {
                int start = 0;
                int len = paragraph.length();
                if (len == 0) continue;
                while (start < len) {
                    int count = p.breakText(paragraph, start, len, true, maxWidth, null);
                    if (count <= 0) break;
                    lines.add(paragraph.substring(start, start + count));
                    start += count;
                }
            }
            return lines;
        }

        void drawCoinIcon(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(235, 178, 42));
            c.drawCircle(cx, cy, r, p);
            p.setColor(Color.rgb(255, 224, 112));
            c.drawCircle(cx, cy, r * 0.72f, p);
            p.setColor(Color.rgb(184, 126, 18));
            rect.set(cx - r * 0.14f, cy - r * 0.36f, cx + r * 0.14f, cy + r * 0.36f);
            c.drawRoundRect(rect, 2, 2, p);
        }

        void drawExplorerBadge(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255, 245, 224));
            c.drawCircle(cx, cy, r, p);
            p.setColor(Color.rgb(112, 175, 116));
            c.drawCircle(cx, cy, r * 0.78f, p);
            // 簡單的探險小帽與帽沿
            p.setColor(Color.rgb(92, 64, 48));
            rect.set(cx - r * 0.42f, cy - r * 0.44f, cx + r * 0.42f, cy + r * 0.04f);
            c.drawRoundRect(rect, 8, 8, p);
            rect.set(cx - r * 0.54f, cy - r * 0.06f, cx + r * 0.54f, cy + r * 0.1f);
            c.drawRoundRect(rect, 4, 4, p);
            // 臉部剪影
            p.setColor(Color.rgb(255, 232, 198));
            c.drawCircle(cx, cy + r * 0.2f, r * 0.3f, p);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float availableWidth = getWidth();
            float availableHeight = getHeight();
            float scale = Math.min(availableWidth / DESIGN_WIDTH, availableHeight / DESIGN_HEIGHT);
            if (scale <= 0) return;
            c.save();
            c.scale(scale, scale);
            // 以下座標都是設計座標；w 是設計座標下的可用寬度
            float w = availableWidth / scale;

            long xp = progress.xp();
            float xpRatio = Math.max(0f, Math.min(1f, xp / (float) StepProgress.STEPS_PER_CHEST));
            int lit = progress.litCells();

            text(c, "走走冒險", 28, 55, 30, true);
            String coinText = String.valueOf(progress.coins);
            p.setTextSize(20);
            p.setTypeface(bold);
            float coinTextWidth = p.measureText(coinText);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(ink);
            c.drawText(coinText, w - 28, 55, p);
            p.setTextAlign(Paint.Align.LEFT);
            drawCoinIcon(c, w - 28 - coinTextWidth - 18, 48, 11);

            // 角色卡
            p.setColor(Color.rgb(255, 225, 170));
            c.drawRoundRect(20, 78, w - 20, 190, 28, 28, p);
            drawExplorerBadge(c, 62, 134, 28);
            text(c, "Lv." + progress.level(), 105, 118, 24, true);
            text(c, "冒險者", 105, 151, 17, false);
            p.setColor(Color.rgb(230, 210, 180));
            c.drawRoundRect(105, 164, w - 45, 181, 10, 10, p);
            if (xpRatio > 0f) {
                p.setColor(Color.rgb(116, 181, 120));
                c.drawRoundRect(105, 164, 105 + (w - 150) * xpRatio, 181, 10, 10, p);
            }
            text(c, xp + " / " + StepProgress.STEPS_PER_CHEST + " XP", 105, 207, 14, false);

            text(c, "今日步數", 25, 250, 18, true);
            text(c, "" + progress.todayStepsOn(today()), 25, 300, 48, true);
            text(c, "每 " + StepProgress.STEPS_PER_CHEST + " 步探索一格地圖", 25, 326, 15, false);

            // 冒險地圖
            text(c, "冒險地圖", 25, 370, 20, true);
            float y = 405;
            float stepX = (w - 90f) / (StepProgress.MAP_CELLS - 1);
            for (int i = 0; i < StepProgress.MAP_CELLS; i++) {
                float x = 45f + i * stepX;
                p.setColor(i < lit ? Color.rgb(112, 175, 116) : Color.rgb(220, 205, 180));
                c.drawCircle(x, y, 23, p);
                drawCenteredText(c, i < lit ? "★" : "·", x, y, 22, true);
                if (i < StepProgress.MAP_CELLS - 1) {
                    p.setColor(Color.rgb(190, 170, 140));
                    c.drawRect(x + 23, y - 3, x + stepX - 23, y + 3, p);
                }
            }
            text(c, "目前探索：" + progress.exploredCells() + " 格", 25, 455, 16, false);
            text(c, "🎁 下一個寶箱：" + progress.nextChestAt() + " 步", 25, 485, 16, false);

            p.setColor(Color.rgb(255, 239, 190));
            c.drawRoundRect(20, 508, w - 20, 588, 20, 20, p);
            List<String> msgLines = wrapLines(message, w - 76, 15, true);
            int maxLines = Math.min(2, msgLines.size());
            float lineHeight = 22f;
            float blockStartY = 548f - ((maxLines - 1) * lineHeight) * 0.5f;
            for (int i = 0; i < maxLines; i++) {
                text(c, msgLines.get(i), 38, blockStartY + i * lineHeight, 15, true);
            }

            String statusText = statusText();
            boolean statusBold = sensorMissing || permissionMissing || permissionPermanentlyDenied;
            List<String> statusLines = wrapLines(statusText, w - 50, statusBold ? 14 : 13, statusBold);
            float statusY = 622f;
            for (int i = 0; i < Math.min(2, statusLines.size()); i++) {
                text(c, statusLines.get(i), 25, statusY + i * 18f, statusBold ? 14 : 13, statusBold);
            }
            text(c, "今天走得越多，冒險世界就越遠。", 25, 668, 16, true);
            c.restore();
        }
    }
}
