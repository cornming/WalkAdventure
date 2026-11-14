package com.cornming.walkadventure;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ = 20;

    // 冒險進度 SharedPreferences 欄位（隨雲端備份還原）。沿用 v0.1.0–v0.3.0 鍵名維持升級無縫接軌。
    private static final String KEY_DAY = "day";
    private static final String KEY_LIFETIME = "lifetime";
    private static final String KEY_COINS = "coins";
    private static final String KEY_CLAIMED_CHEST = "claimed_chest";
    private static final String KEY_EVENT_BUCKET = "event_bucket";
    private static final String KEY_TODAY = "today_steps";

    // RPG 擴充欄位（同存於 walk_adventure）
    private static final String KEY_ADV_BONUS_XP = "adv_bonus_xp";
    private static final String KEY_ADV_BOOTS = "adv_boots";
    private static final String KEY_ADV_CHARM = "adv_charm";
    private static final String KEY_ADV_COMPASS = "adv_compass";
    private static final String KEY_ADV_COMPANION = "adv_companion";
    private static final String KEY_ADV_UNLOCKED_COMPANIONS = "adv_unlocked_companions";
    private static final String KEY_ADV_BIOME_OVERRIDE = "adv_biome_override";
    private static final String KEY_ADV_STREAK = "adv_streak";
    private static final String KEY_ADV_BEST_STREAK = "adv_best_streak";
    private static final String KEY_ADV_STREAK_DAY = "adv_streak_day";
    private static final String KEY_ADV_DAILY_DATE = "adv_daily_date";
    private static final String KEY_ADV_DAILY_MASK = "adv_daily_mask";
    private static final String KEY_ADV_ACHIEVEMENTS = "adv_achievements";
    private static final String KEY_ADV_ENCOUNTERS = "adv_encounters_resolved";
    private static final String KEY_ADV_STARTER = "adv_starter_given";
    private static final String KEY_ADV_PENDING_ENCOUNTER = "adv_pending_enc";
    private static final String KEY_ADV_HISTORY = "adv_history";
    private static final String KEY_ADV_LOG = "adv_log";

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

    private static final int TAB_ADVENTURE = 0;
    private static final int TAB_SHOP = 1;
    private static final int TAB_REPORT = 2;

    private static final int UPDATE_STATE_IDLE = 0;
    private static final int UPDATE_STATE_CHECKING = 1;
    private static final int UPDATE_STATE_DOWNLOADING = 2;
    private static final int UPDATE_STATE_READY = 3;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private SharedPreferences prefs;
    private SharedPreferences devicePrefs;
    private StepProgress progress;
    private AdventureState adventure;
    private Toast activeToast;
    private AlertDialog activeDialog;
    private final Random random = new Random();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private String currentVersion = "0.1.0";
    private AppUpdate.ReleaseInfo readyRelease;
    private boolean updateInFlight = false;
    private boolean pendingInstallAfterSettings = false;

    private int activeTab = TAB_ADVENTURE;
    private String lastNoticeText = "準備出發！每 500 步觸發新奇遇，每 1,000 步開啟步數寶箱。";

    // UI 元件參照
    private ScrollView adventureScroll;
    private ScrollView shopScroll;
    private ScrollView reportScroll;
    private AdventureCanvasView adventureCanvas;
    private LinearLayout biomeNavRow;
    private TextView biomeNavLabel;
    private Button biomePrevButton;
    private Button biomeNextButton;
    private LinearLayout encounterCard;
    private TextView encounterTitle;
    private TextView encounterBody;
    private LinearLayout encounterActionsRow;
    private Button encounterBtnPrimary;
    private Button encounterBtnSecondary;
    private TextView adventureFooterNote;

    private LinearLayout shopListContainer;
    private LinearLayout reportListContainer;

    private Button tabBtnAdventure;
    private Button tabBtnShop;
    private Button tabBtnReport;
    private Button updateButton;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("walk_adventure", MODE_PRIVATE);
        devicePrefs = getSharedPreferences("walk_adventure_device", MODE_PRIVATE);
        progress = loadProgress();
        adventure = loadAdventureState();

        String todayStr = today();
        adventure.syncSnapshot(progress, todayStr);
        boolean starterCreated = adventure.ensureStarterEncounter(todayStr);
        saveProgress();
        if (starterCreated) {
            lastNoticeText = "村長準備了出發補給包！點擊下方按鈕即可領取獎勵。";
        }

        currentVersion = installedVersionName();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;

        buildLayout();

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
        // 狀態列、導覽列與瀏海邊界統一由 root 保留
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

        FrameLayout contentHost = new FrameLayout(this);
        adventureScroll = buildAdventureTab();
        shopScroll = buildScrollListTab(true);
        reportScroll = buildScrollListTab(false);
        contentHost.addView(adventureScroll);
        contentHost.addView(shopScroll);
        contentHost.addView(reportScroll);

        root.addView(contentHost, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        View tabDivider = new View(this);
        tabDivider.setBackgroundColor(Color.rgb(231, 216, 194));
        root.addView(tabDivider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, dp(1))
        ));

        // 底部三分頁切換列
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(dp(12), dp(6), dp(12), dp(4));
        tabBar.setGravity(Gravity.CENTER);

        tabBtnAdventure = makeNavTabButton(R.string.tab_adventure, TAB_ADVENTURE);
        tabBtnShop = makeNavTabButton(R.string.tab_shop, TAB_SHOP);
        tabBtnReport = makeNavTabButton(R.string.tab_report, TAB_REPORT);

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        );
        tabLp.setMargins(dp(4), 0, dp(4), 0);
        tabBar.addView(tabBtnAdventure, tabLp);
        tabBar.addView(tabBtnShop, new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabBtnReport, new LinearLayout.LayoutParams(tabLp));
        root.addView(tabBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 精簡版本與自動更新列
        LinearLayout updateFooter = new LinearLayout(this);
        updateFooter.setOrientation(LinearLayout.HORIZONTAL);
        updateFooter.setGravity(Gravity.CENTER_VERTICAL);
        updateFooter.setPadding(dp(16), dp(2), dp(16), dp(6));

        TextView versionText = new TextView(this);
        versionText.setText(getString(R.string.version_label, currentVersion));
        versionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        versionText.setTextColor(Color.rgb(122, 100, 82));
        updateFooter.addView(versionText, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ));

        updateButton = new Button(this);
        updateButton.setAllCaps(false);
        updateButton.setStateListAnimator(null);
        updateButton.setMinHeight(dp(44));
        updateButton.setMinWidth(dp(88));
        updateButton.setPadding(dp(14), dp(6), dp(14), dp(6));
        updateButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        updateButton.setTypeface(Typeface.create("sans", Typeface.BOLD));
        updateButton.setOnClickListener(v -> onUpdateButtonClicked());
        updateFooter.addView(updateButton, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(updateFooter, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
        renderUpdateButton(UPDATE_STATE_IDLE);
        switchTab(TAB_ADVENTURE);
    }

    private ScrollView buildAdventureTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(10));

        adventureCanvas = new AdventureCanvasView(this);
        adventureCanvas.setOnClickListener(v -> {
            if (!hasStepPermission()) handlePermissionTap();
        });
        col.addView(adventureCanvas, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 區域切換列（解鎖第 2 章以上才顯示）
        biomeNavRow = new LinearLayout(this);
        biomeNavRow.setOrientation(LinearLayout.HORIZONTAL);
        biomeNavRow.setGravity(Gravity.CENTER_VERTICAL);
        biomeNavRow.setPadding(dp(16), dp(2), dp(16), dp(6));

        biomePrevButton = makeSmallPillButton(getString(R.string.biome_prev), Color.rgb(245, 232, 208), getColor(R.color.ink));
        biomePrevButton.setOnClickListener(v -> {
            adventure.shiftDisplayBiome(progress, -1);
            saveProgress();
            refreshAllTabs();
        });

        biomeNavLabel = new TextView(this);
        biomeNavLabel.setGravity(Gravity.CENTER);
        biomeNavLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        biomeNavLabel.setTypeface(Typeface.create("sans", Typeface.BOLD));
        biomeNavLabel.setTextColor(getColor(R.color.ink));

        biomeNextButton = makeSmallPillButton(getString(R.string.biome_next), Color.rgb(245, 232, 208), getColor(R.color.ink));
        biomeNextButton.setOnClickListener(v -> {
            adventure.shiftDisplayBiome(progress, 1);
            saveProgress();
            refreshAllTabs();
        });

        biomeNavRow.addView(biomePrevButton);
        biomeNavRow.addView(biomeNavLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        biomeNavRow.addView(biomeNextButton);
        col.addView(biomeNavRow);

        // 互動奇遇卡片
        encounterCard = new LinearLayout(this);
        encounterCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(dp(16), dp(4), dp(16), dp(6));
        encounterCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        encounterTitle = new TextView(this);
        encounterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        encounterTitle.setTypeface(Typeface.create("sans", Typeface.BOLD));
        encounterTitle.setTextColor(getColor(R.color.ink));
        encounterCard.addView(encounterTitle);

        encounterBody = new TextView(this);
        encounterBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        encounterBody.setTextColor(Color.rgb(92, 72, 56));
        encounterBody.setPadding(0, dp(4), 0, dp(8));
        encounterCard.addView(encounterBody);

        encounterActionsRow = new LinearLayout(this);
        encounterActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        encounterBtnPrimary = makeActionButton(Color.rgb(46, 107, 52), Color.rgb(255, 247, 232));
        encounterBtnPrimary.setOnClickListener(v -> onEncounterChoice(0));
        encounterBtnSecondary = makeActionButton(Color.rgb(255, 225, 170), getColor(R.color.ink));
        encounterBtnSecondary.setOnClickListener(v -> onEncounterChoice(1));

        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        );
        actLp.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams actLpRight = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        );
        actLpRight.setMargins(dp(6), 0, 0, 0);
        encounterActionsRow.addView(encounterBtnPrimary, actLp);
        encounterActionsRow.addView(encounterBtnSecondary, actLpRight);
        encounterCard.addView(encounterActionsRow);

        col.addView(encounterCard, cardLp);

        adventureFooterNote = new TextView(this);
        adventureFooterNote.setPadding(dp(18), dp(2), dp(18), dp(6));
        adventureFooterNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        adventureFooterNote.setTypeface(Typeface.create("sans", Typeface.BOLD));
        adventureFooterNote.setTextColor(Color.rgb(110, 90, 76));
        col.addView(adventureFooterNote);

        scroll.addView(col);
        return scroll;
    }

    private ScrollView buildScrollListTab(boolean isShop) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(12), dp(16), dp(16));
        scroll.addView(list);
        if (isShop) {
            shopListContainer = list;
        } else {
            reportListContainer = list;
        }
        return scroll;
    }

    private Button makeNavTabButton(int stringResId, int tabIndex) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        btn.setMinHeight(dp(48));
        btn.setPadding(dp(8), dp(8), dp(8), dp(8));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(Typeface.create("sans", Typeface.BOLD));
        btn.setText(stringResId);
        btn.setOnClickListener(v -> switchTab(tabIndex));
        return btn;
    }

    private Button makeSmallPillButton(String text, int bgColor, int fgColor) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        btn.setMinHeight(dp(44));
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.create("sans", Typeface.BOLD));
        btn.setTextColor(fgColor);
        btn.setText(text);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(dp(18));
        btn.setBackground(d);
        return btn;
    }

    private Button makeActionButton(int bgColor, int fgColor) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        btn.setMinHeight(dp(48));
        btn.setPadding(dp(10), dp(10), dp(10), dp(10));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.create("sans", Typeface.BOLD));
        btn.setTextColor(fgColor);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(dp(14));
        btn.setBackground(d);
        return btn;
    }

    private void switchTab(int target) {
        activeTab = target;
        adventureScroll.setVisibility(target == TAB_ADVENTURE ? View.VISIBLE : View.GONE);
        shopScroll.setVisibility(target == TAB_SHOP ? View.VISIBLE : View.GONE);
        reportScroll.setVisibility(target == TAB_REPORT ? View.VISIBLE : View.GONE);
        refreshAllTabs();
        if (target == TAB_ADVENTURE) {
            adventureCanvas.startIdleAnimation();
        } else {
            adventureCanvas.stopIdleAnimation();
        }
    }

    private void refreshAllTabs() {
        String todayStr = today();
        refreshTabButtons(todayStr);
        refreshAdventureTab(todayStr);
        if (activeTab == TAB_SHOP) {
            rebuildShopTab(todayStr);
        } else if (activeTab == TAB_REPORT) {
            rebuildReportTab(todayStr);
        }
    }

    private void refreshTabsAfterWalk(String todayStr, StepProgress.Update update, boolean rewardOrEventChanged) {
        refreshTabButtons(todayStr);
        refreshAdventureTab(todayStr);
        if (activeTab == TAB_SHOP && rewardOrEventChanged) {
            rebuildShopTab(todayStr);
        } else if (activeTab == TAB_REPORT && (update.changed() || rewardOrEventChanged)) {
            rebuildReportTab(todayStr);
        }
    }

    private void refreshTabButtons(String todayStr) {
        int claimable = adventure.pendingClaimableDailyCount(progress, todayStr);
        boolean hasEnc = adventure.pendingEncounter != null;
        tabBtnAdventure.setText(getString(hasEnc ? R.string.tab_adventure_alert : R.string.tab_adventure));
        tabBtnShop.setText(getString(R.string.tab_shop));
        tabBtnReport.setText(claimable > 0
            ? getString(R.string.tab_report_badge, claimable)
            : getString(R.string.tab_report));

        styleTabButton(tabBtnAdventure, activeTab == TAB_ADVENTURE);
        styleTabButton(tabBtnShop, activeTab == TAB_SHOP);
        styleTabButton(tabBtnReport, activeTab == TAB_REPORT);
    }

    private void styleTabButton(Button btn, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (selected) {
            bg.setColor(Color.rgb(46, 107, 52));
            btn.setTextColor(Color.rgb(255, 247, 232));
        } else {
            bg.setColor(Color.rgb(245, 232, 208));
            btn.setTextColor(getColor(R.color.ink));
        }
        btn.setBackground(bg);
    }

    private void refreshAdventureTab(String todayStr) {
        String statusText = statusText();
        boolean statusAlert = stepSensor == null || !hasStepPermission();
        adventureCanvas.bind(progress, adventure, todayStr, statusText, statusAlert);

        int maxUnlocked = adventure.highestUnlockedBiomeIndex(progress);
        if (maxUnlocked > 0) {
            biomeNavRow.setVisibility(View.VISIBLE);
            int dispIdx = adventure.activeDisplayBiomeIndex(progress);
            AdventureCatalog.BiomeDef disp = AdventureCatalog.biome(dispIdx);
            biomeNavLabel.setText(String.format(
                Locale.TAIWAN,
                "第 %d / %d 章：%s",
                dispIdx + 1,
                maxUnlocked + 1,
                disp.name
            ));
            boolean canPrev = dispIdx > 0;
            boolean canNext = dispIdx < maxUnlocked;
            biomePrevButton.setEnabled(canPrev);
            biomePrevButton.setAlpha(canPrev ? 1f : 0.45f);
            biomeNextButton.setEnabled(canNext);
            biomeNextButton.setAlpha(canNext ? 1f : 0.45f);
        } else {
            biomeNavRow.setVisibility(View.GONE);
        }

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(18));
        if (adventure.pendingEncounter != null) {
            cardBg.setColor(Color.rgb(255, 240, 194));
            cardBg.setStroke(dp(2), Color.rgb(226, 168, 62));
            encounterCard.setBackground(cardBg);
            encounterTitle.setText(adventure.pendingEncounter.title);
            encounterBody.setText(adventure.pendingEncounter.prompt);
            encounterBtnPrimary.setText(adventure.pendingEncounter.primaryLabel);
            encounterBtnSecondary.setText(adventure.pendingEncounter.secondaryLabel);
            encounterActionsRow.setVisibility(View.VISIBLE);
        } else {
            cardBg.setColor(Color.rgb(248, 236, 214));
            cardBg.setStroke(dp(1), Color.rgb(225, 206, 178));
            encounterCard.setBackground(cardBg);
            long nextEncAt = ((progress.eventBucket + 1L) * StepProgress.STEPS_PER_EVENT);
            long remainSteps = Math.max(0L, nextEncAt - progress.lifetime);
            encounterTitle.setText("冒險日誌速報");
            encounterBody.setText(String.format(
                Locale.TAIWAN,
                "%s\n⏳ 再走 %d 步即可觸發下一次路途奇遇。",
                lastNoticeText,
                remainSteps
            ));
            encounterActionsRow.setVisibility(View.GONE);
        }

        int claimable = adventure.pendingClaimableDailyCount(progress, todayStr);
        if (claimable > 0) {
            adventureFooterNote.setText(String.format(
                Locale.TAIWAN,
                "🎁 今日有 %d 項每日任務達標，可切到「戰報」領取獎勵！",
                claimable
            ));
            adventureFooterNote.setTextColor(Color.rgb(46, 107, 52));
        } else {
            adventureFooterNote.setText("今天走得越多，冒險世界就越遠。");
            adventureFooterNote.setTextColor(Color.rgb(110, 90, 76));
        }
    }

    private void onEncounterChoice(int choiceIndex) {
        String todayStr = today();
        AdventureState.ActionResult res = adventure.resolvePendingEncounter(progress, choiceIndex, todayStr, random);
        if (!res.ok) {
            showShortToast(res.message);
            return;
        }
        lastNoticeText = res.message;
        saveProgress();
        showShortToast(res.message);
        refreshAllTabs();
    }

    private void rebuildShopTab(String todayStr) {
        shopListContainer.removeAllViews();

        // 錢包與加成總覽
        LinearLayout headerCard = makeSectionCard(Color.rgb(255, 234, 188));
        addCardTitle(headerCard, String.format(Locale.TAIWAN, "🪙 持有金幣：%d", progress.coins));
        addCardSubtitle(headerCard, String.format(
            Locale.TAIWAN,
            "目前總加成：走路 XP +%d%% · 寶箱／勝利金幣 +%d%% · 雙倍幸運 %d%%",
            adventure.xpMultiplierPercent(),
            adventure.coinMultiplierPercent(),
            15 + adventure.compassLevel * 12
        ));
        shopListContainer.addView(headerCard, cardMargins());

        // 冒險裝備升級區
        addSectionHeader(shopListContainer, "🎒 冒險裝備升級（永久被動加成）");
        for (int g = 0; g < AdventureCatalog.GEAR_COUNT; g++) {
            final int gearIdx = g;
            int level = adventure.gearLevel(gearIdx);
            int cost = AdventureCatalog.gearUpgradeCost(gearIdx, level);
            LinearLayout card = makeSectionCard(Color.rgb(255, 250, 240));
            addCardTitle(card, AdventureCatalog.gearName(gearIdx));
            addCardSubtitle(card, AdventureCatalog.gearEffectSummary(gearIdx, level));

            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(AdventureCatalog.GEAR_MAX_LEVEL);
            pb.setProgress(level);
            LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            pbLp.setMargins(0, dp(4), 0, dp(8));
            card.addView(pb, pbLp);

            boolean maxed = level >= AdventureCatalog.GEAR_MAX_LEVEL;
            boolean affordable = !maxed && progress.coins >= cost;
            Button buyBtn = makeActionButton(
                maxed ? Color.rgb(225, 212, 190) : (affordable ? Color.rgb(46, 107, 52) : Color.rgb(228, 216, 198)),
                maxed ? Color.rgb(110, 90, 76) : (affordable ? Color.rgb(255, 247, 232) : Color.rgb(120, 102, 86))
            );
            buyBtn.setText(maxed ? "已達最高等級 ★" : String.format(Locale.TAIWAN, "升級裝備（%d 金幣）", cost));
            buyBtn.setEnabled(!maxed);
            buyBtn.setOnClickListener(v -> {
                AdventureState.ActionResult res = adventure.upgradeGear(progress, gearIdx, todayStr);
                showShortToast(res.message);
                if (res.ok) {
                    lastNoticeText = res.message;
                    saveProgress();
                    refreshAllTabs();
                }
            });
            card.addView(buyBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            shopListContainer.addView(card, cardMargins());
        }

        // 同伴招募區
        addSectionHeader(shopListContainer, "🐾 可愛旅伴招募（招募後可隨時切換）");
        for (int c = 0; c < AdventureCatalog.COMPANION_COUNT; c++) {
            final int compId = c;
            AdventureCatalog.CompanionDef def = AdventureCatalog.companion(compId);
            boolean unlocked = adventure.isCompanionUnlocked(compId);
            boolean equipped = adventure.equippedCompanion == compId;
            boolean canAfford = progress.coins >= def.cost;

            LinearLayout card = makeSectionCard(equipped ? Color.rgb(233, 245, 227) : Color.rgb(255, 250, 240));
            addCardTitle(card, String.format(Locale.TAIWAN, "%s · %s", def.name, def.role));
            addCardSubtitle(card, def.perkText);

            Button compBtn;
            if (equipped) {
                compBtn = makeActionButton(Color.rgb(112, 175, 116), Color.rgb(255, 250, 242));
                compBtn.setText("目前同行中 ✓");
                compBtn.setEnabled(false);
            } else if (unlocked) {
                compBtn = makeActionButton(Color.rgb(255, 225, 170), getColor(R.color.ink));
                compBtn.setText("換牠同行");
            } else {
                compBtn = makeActionButton(
                    canAfford ? Color.rgb(46, 107, 52) : Color.rgb(228, 216, 198),
                    canAfford ? Color.rgb(255, 247, 232) : Color.rgb(120, 102, 86)
                );
                compBtn.setText(String.format(Locale.TAIWAN, "招募夥伴（%d 金幣）", def.cost));
            }
            compBtn.setOnClickListener(v -> {
                AdventureState.ActionResult res = adventure.buyOrEquipCompanion(progress, compId, todayStr);
                showShortToast(res.message);
                if (res.ok) {
                    lastNoticeText = res.message;
                    saveProgress();
                    refreshAllTabs();
                }
            });
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            btnLp.setMargins(0, dp(8), 0, 0);
            card.addView(compBtn, btnLp);
            shopListContainer.addView(card, cardMargins());
        }
    }

    private void rebuildReportTab(String todayStr) {
        reportListContainer.removeAllViews();

        // 7 日步數圖表與戰報總覽
        LinearLayout chartCard = makeSectionCard(Color.rgb(255, 250, 240));
        addCardTitle(chartCard, "📊 近 7 天步數與冒險戰報");
        addCardSubtitle(chartCard, String.format(
            Locale.TAIWAN,
            "🔥 連續達標 %d 天（最佳 %d 天） · 累計 %d 步 · 完成 %d 次奇遇",
            adventure.effectiveStreak(todayStr),
            adventure.bestStreakDays,
            progress.lifetime,
            adventure.encountersResolved
        ));

        WeeklyChartView chart = new WeeklyChartView(this);
        chart.setRecords(adventure.lastSevenDays(progress, todayStr));
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        chartLp.setMargins(0, dp(8), 0, dp(8));
        chartCard.addView(chart, chartLp);

        Button copyBtn = makeActionButton(Color.rgb(46, 107, 52), Color.rgb(255, 247, 232));
        copyBtn.setText(R.string.copy_report);
        copyBtn.setOnClickListener(v -> copyDailyReportToClipboard(todayStr));
        chartCard.addView(copyBtn, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        reportListContainer.addView(chartCard, cardMargins());

        // 今日每日任務
        addSectionHeader(reportListContainer, "🎯 今日步數任務");
        long todaySteps = progress.todayStepsOn(todayStr);
        for (AdventureCatalog.DailyQuestDef def : AdventureCatalog.DAILY_QUESTS) {
            boolean reached = todaySteps >= def.targetSteps;
            boolean claimed = adventure.isDailyQuestClaimed(def.index, todayStr);
            LinearLayout card = makeSectionCard(claimed ? Color.rgb(233, 245, 227) : Color.rgb(255, 250, 240));
            addCardTitle(card, String.format(
                Locale.TAIWAN,
                "%s（%d / %d 步）",
                def.title,
                Math.min(todaySteps, def.targetSteps),
                def.targetSteps
            ));
            int previewCoins = adventure.dailyQuestCoinReward(def.index);
            String bonusTag = adventure.equippedCompanion == AdventureCatalog.COMPANION_OWL ? "（波波 +25%）" : "";
            addCardSubtitle(card, String.format(
                Locale.TAIWAN,
                "獎勵：+%d 金幣%s、+%d 冒險 XP",
                previewCoins,
                bonusTag,
                def.xpReward
            ));

            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax((int) def.targetSteps);
            pb.setProgress((int) Math.min(todaySteps, def.targetSteps));
            LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            pbLp.setMargins(0, dp(4), 0, dp(8));
            card.addView(pb, pbLp);

            Button qBtn = makeActionButton(
                claimed ? Color.rgb(214, 226, 208) : (reached ? Color.rgb(46, 107, 52) : Color.rgb(228, 216, 198)),
                claimed ? Color.rgb(70, 96, 66) : (reached ? Color.rgb(255, 247, 232) : Color.rgb(120, 102, 86))
            );
            qBtn.setText(claimed ? "今日已領取 ✓" : (reached ? "領取任務獎勵！" : "繼續散步以達成"));
            qBtn.setEnabled(reached && !claimed);
            qBtn.setOnClickListener(v -> {
                AdventureState.ActionResult res = adventure.claimDailyQuest(progress, def.index, todayStr);
                showShortToast(res.message);
                if (res.ok) {
                    lastNoticeText = res.message;
                    saveProgress();
                    refreshAllTabs();
                }
            });
            card.addView(qBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            reportListContainer.addView(card, cardMargins());
        }

        // 成就徽章圖鑑
        int unlockedBadges = 0;
        for (int i = 0; i < AdventureCatalog.ACHIEVEMENTS.length; i++) {
            if (adventure.isAchievementUnlocked(i)) unlockedBadges++;
        }
        addSectionHeader(reportListContainer, String.format(
            Locale.TAIWAN,
            "🏆 成就稱號徽章（已解鎖 %d / %d）",
            unlockedBadges,
            AdventureCatalog.ACHIEVEMENTS.length
        ));
        for (AdventureCatalog.AchievementDef def : AdventureCatalog.ACHIEVEMENTS) {
            boolean unlocked = adventure.isAchievementUnlocked(def.index);
            LinearLayout card = makeSectionCard(unlocked ? Color.rgb(255, 236, 182) : Color.rgb(241, 230, 212));
            String titleLine = String.format(
                Locale.TAIWAN,
                "%s %s（+%d 金幣）",
                unlocked ? "🏆" : "🔒",
                def.title,
                def.coinReward
            );
            addCardTitle(card, titleLine);
            addCardSubtitle(card, def.conditionText);
            reportListContainer.addView(card, cardMargins());
        }

        // 冒險日誌
        addSectionHeader(reportListContainer, "📜 冒險日誌紀錄");
        if (adventure.adventureLog.isEmpty()) {
            LinearLayout emptyCard = makeSectionCard(Color.rgb(255, 250, 240));
            addCardSubtitle(emptyCard, "目前還沒有冒險日誌，跨出第一步就能紀錄奇遇囉！");
            reportListContainer.addView(emptyCard, cardMargins());
        } else {
            LinearLayout logCard = makeSectionCard(Color.rgb(255, 250, 240));
            for (int i = 0; i < adventure.adventureLog.size(); i++) {
                AdventureState.LogEntry entry = adventure.adventureLog.get(i);
                TextView tv = new TextView(this);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setTextColor(getColor(R.color.ink));
                tv.setPadding(0, dp(3), 0, dp(3));
                String line = entry.date.isEmpty()
                    ? entry.summary
                    : String.format(Locale.TAIWAN, "%s · %s", entry.date, entry.summary);
                tv.setText(line);
                logCard.addView(tv);
            }
            reportListContainer.addView(logCard, cardMargins());
        }
    }

    private void copyDailyReportToClipboard(String todayStr) {
        String report = adventure.formatDailyReport(progress, todayStr);
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("WalkAdventure Report", report));
            showShortToast(getString(R.string.report_copied));
        }
    }

    private LinearLayout makeSectionCard(int bgColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(dp(18));
        d.setStroke(dp(1), Color.rgb(230, 212, 186));
        card.setBackground(d);
        return card;
    }

    private LinearLayout.LayoutParams cardMargins() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.create("sans", Typeface.BOLD));
        tv.setTextColor(getColor(R.color.ink));
        tv.setPadding(dp(2), dp(6), dp(2), dp(6));
        parent.addView(tv);
    }

    private void addCardTitle(LinearLayout card, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.create("sans", Typeface.BOLD));
        tv.setTextColor(getColor(R.color.ink));
        card.addView(tv);
    }

    private void addCardSubtitle(LinearLayout card, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.rgb(104, 82, 66));
        tv.setPadding(0, dp(3), 0, 0);
        card.addView(tv);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            getResources().getDisplayMetrics()
        ));
    }

    private String statusText() {
        if (stepSensor == null) return "此裝置沒有步數感測器，無法取得實際步數。";
        if (isPermissionPermanentlyDenied()) return "尚未開啟「身體活動」權限，點一下畫面前往系統設定允許。";
        if (!hasStepPermission()) return "尚未允許「活動辨識」權限，點一下畫面重新授權計步。";
        return "步數感測器已連線 • 關閉 App 也會保留進度";
    }

    private void renderUpdateButton(int state) {
        if (updateButton == null) return;
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(22));
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
        if (activeTab == TAB_ADVENTURE && adventureCanvas != null) {
            adventureCanvas.startIdleAnimation();
        }
        if (pendingInstallAfterSettings) {
            pendingInstallAfterSettings = false;
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) {
                onInstallRequested();
            }
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (adventureCanvas != null) adventureCanvas.stopIdleAnimation();
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

    private void register() {
        if (sensorManager != null && stepSensor != null && hasStepPermission()) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        refreshAllTabs();
    }

    private String today() {
        return LocalDate.now().toString();
    }

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

    private AdventureState loadAdventureState() {
        AdventureState s = new AdventureState();
        s.bonusXp = prefs.getLong(KEY_ADV_BONUS_XP, 0L);
        s.bootsLevel = prefs.getInt(KEY_ADV_BOOTS, 0);
        s.charmLevel = prefs.getInt(KEY_ADV_CHARM, 0);
        s.compassLevel = prefs.getInt(KEY_ADV_COMPASS, 0);
        s.equippedCompanion = prefs.getInt(KEY_ADV_COMPANION, AdventureCatalog.COMPANION_NONE);
        s.unlockedCompanionsMask = prefs.getInt(KEY_ADV_UNLOCKED_COMPANIONS, 1);
        s.selectedBiomeIndex = prefs.getInt(KEY_ADV_BIOME_OVERRIDE, -1);
        s.streakDays = prefs.getInt(KEY_ADV_STREAK, 0);
        s.bestStreakDays = prefs.getInt(KEY_ADV_BEST_STREAK, 0);
        s.lastStreakDay = prefs.getString(KEY_ADV_STREAK_DAY, null);
        s.claimedDailyDate = prefs.getString(KEY_ADV_DAILY_DATE, null);
        s.claimedDailyMask = prefs.getInt(KEY_ADV_DAILY_MASK, 0);
        s.unlockedAchievementsMask = prefs.getInt(KEY_ADV_ACHIEVEMENTS, 0);
        s.encountersResolved = prefs.getInt(KEY_ADV_ENCOUNTERS, 0);
        s.starterGiven = prefs.getBoolean(KEY_ADV_STARTER, false);
        s.parsePendingEncounter(prefs.getString(KEY_ADV_PENDING_ENCOUNTER, ""));
        s.parseHistory(prefs.getString(KEY_ADV_HISTORY, ""));
        s.parseLog(prefs.getString(KEY_ADV_LOG, ""));
        return s;
    }

    /** 一次寫入冒險進度、RPG 狀態與本機感測器錨點。 */
    private void saveProgress() {
        prefs.edit()
            .putString(KEY_DAY, progress.day)
            .putLong(KEY_TODAY, progress.todaySteps)
            .putLong(KEY_LIFETIME, progress.lifetime)
            .putLong(KEY_COINS, progress.coins)
            .putLong(KEY_CLAIMED_CHEST, progress.claimedChest)
            .putLong(KEY_EVENT_BUCKET, progress.eventBucket)
            .putLong(KEY_ADV_BONUS_XP, adventure.bonusXp)
            .putInt(KEY_ADV_BOOTS, adventure.bootsLevel)
            .putInt(KEY_ADV_CHARM, adventure.charmLevel)
            .putInt(KEY_ADV_COMPASS, adventure.compassLevel)
            .putInt(KEY_ADV_COMPANION, adventure.equippedCompanion)
            .putInt(KEY_ADV_UNLOCKED_COMPANIONS, adventure.unlockedCompanionsMask)
            .putInt(KEY_ADV_BIOME_OVERRIDE, adventure.selectedBiomeIndex)
            .putInt(KEY_ADV_STREAK, adventure.streakDays)
            .putInt(KEY_ADV_BEST_STREAK, adventure.bestStreakDays)
            .putString(KEY_ADV_STREAK_DAY, adventure.lastStreakDay)
            .putString(KEY_ADV_DAILY_DATE, adventure.claimedDailyDate)
            .putInt(KEY_ADV_DAILY_MASK, adventure.claimedDailyMask)
            .putInt(KEY_ADV_ACHIEVEMENTS, adventure.unlockedAchievementsMask)
            .putInt(KEY_ADV_ENCOUNTERS, adventure.encountersResolved)
            .putBoolean(KEY_ADV_STARTER, adventure.starterGiven)
            .putString(KEY_ADV_PENDING_ENCOUNTER, adventure.serializePendingEncounter())
            .putString(KEY_ADV_HISTORY, adventure.serializeHistory())
            .putString(KEY_ADV_LOG, adventure.serializeLog())
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
        String todayStr = today();
        StepProgress.Update update = progress.onSensorReading((long) e.values[0], todayStr, bootCount(), random);
        String rpgNotice = adventure.applyWalkUpdate(progress, update, todayStr, random);
        saveProgress();
        if (rpgNotice != null) {
            lastNoticeText = rpgNotice;
            showShortToast(rpgNotice.replace('\n', ' '));
        }
        refreshTabsAfterWalk(todayStr, update, rpgNotice != null);
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
