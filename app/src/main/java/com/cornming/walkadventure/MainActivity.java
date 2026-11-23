package com.cornming.walkadventure;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
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
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ = 20;
    private static final String V2_SAVE_FILENAME = "walk_adventure_v2.json";

    // 冒險進度 SharedPreferences 欄位（隨雲端備份還原）。沿用 v0.1.0–v0.4.0 鍵名維持升級無縫接軌。
    private static final String KEY_DAY = "day";
    private static final String KEY_LIFETIME = "lifetime";
    private static final String KEY_COINS = "coins";
    private static final String KEY_CLAIMED_CHEST = "claimed_chest";
    private static final String KEY_EVENT_BUCKET = "event_bucket";
    private static final String KEY_TODAY = "today_steps";

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
    private static final String KEY_PENDING_HUNT_ID = "phase2_pending_hunt_id";
    private static final String KEY_PENDING_MEMORY_ID = "phase2_pending_memory_id";

    private static final int REQ_AUDIO = 21;
    private static final int REQ_LOCATION = 22;
    private static final int REQ_CAMERA_CAPTURE = 30;
    private static final int REQ_PICK_PHOTO = 31;

    private static final int TAB_ADVENTURE = 0;
    private static final int TAB_CAMP = 1;
    private static final int TAB_JOURNAL = 2;
    private static final int TAB_SQUAD = 3;

    private static final int UPDATE_STATE_IDLE = 0;
    private static final int UPDATE_STATE_CHECKING = 1;
    private static final int UPDATE_STATE_DOWNLOADING = 2;
    private static final int UPDATE_STATE_READY = 3;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private Sensor lightSensor;
    private NfcAdapter nfcAdapter;
    private SharedPreferences prefs;
    private SharedPreferences devicePrefs;
    private StepProgress progress;
    private AdventureState adventure;
    private MemoryMediaStore mediaStore;
    private PostcardWorkshopController workshopController;
    private TreasureAndFootprintController treasureController;
    private int pendingHuntId = -1;
    private String pendingMemoryId = "";
    private Runnable pendingAudioPermissionCallback;
    private Runnable pendingLocationPermissionCallback;
    private long lastLightProcessMs = 0L;
    private Toast activeToast;
    private AlertDialog activeDialog;
    private final Random random = new Random();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private String currentVersion = "1.0.0";
    private AppUpdate.ReleaseInfo readyRelease;
    private boolean updateInFlight = false;
    private boolean pendingInstallAfterSettings = false;

    private int activeTab = TAB_ADVENTURE;
    private String lastNoticeText = "準備出發！平日累積進營火盒，假日一起開箱、孵蛋與相機尋寶！";

    // 頂部模式與口袋防低頭遮罩
    private Button modeToggleBtn;
    private Button pocketBtn;
    private Button helpBtn;
    private TextView topCurrencyText;
    private LinearLayout pocketOverlay;
    private TextView pocketStepsLabel;
    private CelebrationOverlayView celebrationOverlay;

    // 4 分頁容器與控制器
    private ScrollView adventureScroll;
    private ScrollView campScroll;
    private ScrollView journalScroll;
    private ScrollView squadScroll;
    private LinearLayout campListContainer;
    private LinearLayout journalListContainer;
    private LinearLayout squadListContainer;
    private CampTabController campController;
    private JournalTabController journalController;
    private SquadTabController squadController;
    private GuideDialogController guideController;

    // 探險分頁動態區塊
    private AdventureCanvasView adventureCanvas;
    private LinearLayout adventureTipSlot;
    private LinearLayout biomeNavRow;
    private TextView biomeNavLabel;
    private Button biomePrevButton;
    private Button biomeNextButton;
    private LinearLayout vaultBannerCard;
    private TextView vaultBannerTitle;
    private LinearLayout cameraHuntCard;
    private TextView cameraHuntTitle;
    private TextView cameraHuntBody;
    private Button cameraRecommendedBtn;
    private LinearLayout bossCard;
    private TextView bossTitle;
    private TextView bossBody;
    private Button bossActionBtn;
    private LinearLayout encounterCard;
    private TextView encounterTitle;
    private TextView encounterBody;
    private LinearLayout encounterActionsRow;
    private Button encounterBtnPrimary;
    private Button encounterBtnSecondary;
    private LinearLayout lookUpContainer;
    private TextView adventureFooterNote;

    // 底部 4 分頁與自動更新列
    private Button tabBtnAdventure;
    private Button tabBtnCamp;
    private Button tabBtnJournal;
    private Button tabBtnSquad;
    private Button updateButton;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("walk_adventure", MODE_PRIVATE);
        devicePrefs = getSharedPreferences("walk_adventure_device", MODE_PRIVATE);
        progress = loadProgress();
        adventure = loadAdventureState();

        WorldClock.Snapshot clock = WorldClock.now();
        String todayStr = clock.isoDate;
        adventure.syncSnapshot(progress, todayStr);
        boolean hadV2File = loadV2StateFile(adventure);
        SaveCodec.MigrationResult migration = SaveCodec.migrateIfNeeded(progress, adventure, hadV2File, todayStr);

        boolean starterCreated = adventure.ensureStarterEncounter(todayStr);
        String envNotice = adventure.syncV2DailyEnvironment(progress, clock, random);
        saveProgress();

        if (migration.migrated && !migration.welcomeMessage.isEmpty()) {
            lastNoticeText = migration.welcomeMessage;
        } else if (envNotice != null) {
            lastNoticeText = envNotice;
        } else if (starterCreated) {
            lastNoticeText = "村長準備了出發補給包！點擊下方按鈕即可領取獎勵。";
        }

        currentVersion = installedVersionName();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) : null;
        lightSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        initControllers();
        buildLayout();
        handleNfcIntent(getIntent());

        if (migration.migrated && migration.wasVeteran) {
            showMigrationWelcomeDialog(migration.welcomeMessage);
        } else if (adventure.guide.shouldShowOnboarding()) {
            guideController.showOnboardingDialog(adventure);
        }

        if (stepSensor != null && !hasStepPermission() && !devicePrefs.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
            requestStepPermission();
        }

        restoreOrCleanCachedUpdate();
        long lastCheckMs = devicePrefs.getLong(KEY_UPDATE_LAST_CHECK, 0L);
        if (AppUpdate.shouldAutoCheck(lastCheckMs, System.currentTimeMillis())) {
            checkForUpdates(false);
        }
    }

    private void initControllers() {
        mediaStore = new MemoryMediaStore(this);
        guideController = new GuideDialogController(this, new GuideDialogController.Host() {
            @Override public void onOnboardingFinished(boolean jumpToSquad) {
                saveProgress();
                refreshAllTabs();
                triggerCelebration(
                    CelebrationOverlayView.STYLE_HARVEST,
                    "🎉 準備好一起冒險囉！",
                    "隨時可點右上角「❓」重看說明與導覽"
                );
                if (jumpToSquad) {
                    switchTab(TAB_SQUAD);
                }
            }

            @Override public void onJumpToTab(int tabIndex) {
                switchTab(tabIndex);
            }
        });

        treasureController = new TreasureAndFootprintController(this, new TreasureAndFootprintController.Host() {
            @Override public void onStateChanged(String toastMessage) {
                commitAndRefresh(toastMessage);
            }

            @Override public void onCelebrate(int style, String title, String subtitle) {
                triggerCelebration(style, title, subtitle);
            }

            @Override public void onVibrateSuccess() {
                vibrateSuccess();
            }

            @Override public boolean isNfcAvailable() {
                return nfcAdapter != null && nfcAdapter.isEnabled();
            }

            @Override public boolean hasCoarseLocationPermission() {
                return MainActivity.this.hasCoarseLocationPermission();
            }

            @Override public void onRequestCoarseLocationPermission(Runnable onGranted) {
                requestCoarseLocationPermission(onGranted);
            }

            @Override public void onRefreshFootprintLocation() {
                refreshCoarseFootprintLocation(true);
            }
        }, mediaStore);

        workshopController = new PostcardWorkshopController(this, new PostcardWorkshopController.Host() {
            @Override public void onLaunchSystemCamera(int huntId) {
                launchSystemCamera(huntId);
            }

            @Override public void onLaunchPhotoPicker(int huntId) {
                launchPhotoPicker(huntId);
            }

            @Override public boolean hasRecordAudioPermission() {
                return MainActivity.this.hasRecordAudioPermission();
            }

            @Override public void onRequestRecordAudioPermission(Runnable onGranted) {
                requestRecordAudioPermission(onGranted);
            }

            @Override public void onStateChanged(String toastMessage) {
                commitAndRefresh(toastMessage);
                if (toastMessage != null && toastMessage.startsWith("📮")) {
                    if (toastMessage.contains("彩虹七色")) {
                        triggerCelebration(
                            CelebrationOverlayView.STYLE_EVOLVE,
                            "🌈 彩虹七色全制霸！",
                            "獲得花瓣奇蹟神祕蛋與豐厚獎勵！"
                        );
                    } else if (toastMessage.contains("自然百科全書")) {
                        triggerCelebration(
                            CelebrationOverlayView.STYLE_EVOLVE,
                            "🔬 自然百科全書制霸！",
                            "集滿 8 大自然生態，獲贈星光奇蹟蛋！"
                        );
                    } else {
                        triggerCelebration(
                            CelebrationOverlayView.STYLE_HARVEST,
                            "📮 冒險明信片誕生！",
                            "已珍藏進繪本手札，隨時回味與分享"
                        );
                    }
                }
            }
        }, mediaStore);

        campController = new CampTabController(this, new CampTabController.Host() {
            @Override public void onStateChanged(String toastMessage) {
                commitAndRefresh(toastMessage);
                if (toastMessage != null) {
                    if (toastMessage.contains("進化")) {
                        triggerCelebration(CelebrationOverlayView.STYLE_EVOLVE, "✨ 旅伴華麗進化！", toastMessage);
                    } else if (toastMessage.contains("落成") || toastMessage.contains("升級") || toastMessage.contains("升至")) {
                        triggerCelebration(CelebrationOverlayView.STYLE_EVOLVE, "⛺ 營地設施升級！", toastMessage);
                    }
                }
            }

            @Override public void onOpenCampfireDialog() {
                showCampfireUnboxDialog();
            }

            @Override public void onHatchEggRequested(int eggListIndex) {
                CompanionSystem.HatchResult res = adventure.hatchEgg(progress, eggListIndex, WorldClock.now().isoDate, random);
                commitAndRefresh(res.message);
                if (res.ok) {
                    triggerCelebration(
                        CelebrationOverlayView.STYLE_HATCH,
                        res.isShiny ? "✨ 閃亮異色破殼！" : "🐣 新旅伴誕生！",
                        res.message
                    );
                }
            }

            @Override public void onHarvestGardenRequested(int slot) {
                CampState.HarvestResult res = adventure.harvestGardenPlot(progress, slot, WorldClock.now().isoDate, random);
                commitAndRefresh(res.message);
                if (res.ok) {
                    triggerCelebration(CelebrationOverlayView.STYLE_HARVEST, "🌻 奇幻菜園大豐收！", res.message);
                }
            }

            @Override public void onCompanionPetted(int companionId) {
                AdventureState.ActionResult res = adventure.petCompanionInCamp(progress, companionId, WorldClock.now().isoDate);
                commitAndRefresh(res.message);
            }
        });

        journalController = new JournalTabController(this, new JournalTabController.Host() {
            @Override public void onStateChanged(String toastMessage) {
                commitAndRefresh(toastMessage);
            }

            @Override public void onStartPhotoHunt(int huntId) {
                workshopController.showCaptureSourceDialog(huntId);
            }

            @Override public void onOpenHuntPicker() {
                workshopController.showHuntPickerDialog(adventure);
            }

            @Override public void onViewPostcard(StoryCatalog.MemoryEntry entry) {
                workshopController.showPostcardViewerDialog(entry, adventure);
            }

            @Override public void onOpenTreasureHub() {
                treasureController.showTreasureHubDialog(progress, adventure, WorldClock.now().isoDate);
            }

            @Override public void onOpenYearbook() {
                treasureController.showYearbookDialog(progress, adventure, WorldClock.now());
            }

            @Override public void onOpenNatureCategory(NatureVisionEngine.CategoryDef cat) {
                treasureController.showNatureCategoryTriviaDialog(cat, adventure);
            }
        }, mediaStore);

        squadController = new SquadTabController(this, new SquadTabController.Host() {
            @Override public void onStateChanged(String toastMessage) {
                commitAndRefresh(toastMessage);
            }

            @Override public void onCopyReportRequested() {
                copyDailyReportToClipboard(WorldClock.now().isoDate);
            }

            @Override public void onBackupRestored(String rawJson) {
                AdventureState candidate = loadAdventureState();
                if (!SaveCodec.deserializeInto(rawJson, candidate)) {
                    showShortToast("備份格式無效，未變更現有存檔");
                    return;
                }
                SaveCodec.deserializeInto(rawJson, adventure);
                commitAndRefresh("已成功還原 2.0 營地備份資料！");
            }

            @Override public void onOpenFamilyGiftExchange() {
                treasureController.showFamilyGiftExchangeDialog(progress, adventure, WorldClock.now().isoDate);
            }

            @Override public void onOpenFootprintMap() {
                treasureController.showFootprintMapDialog(progress, adventure, WorldClock.now().isoDate);
            }
        });
    }

    private void commitAndRefresh(String message) {
        if (message != null && !message.isEmpty()) {
            lastNoticeText = message;
            showShortToast(message);
        }
        saveProgress();
        refreshAllTabs();
    }

    private void triggerCelebration(int style, String title, String subtitle) {
        if (celebrationOverlay != null) {
            celebrationOverlay.triggerCelebration(style, title, subtitle);
        }
    }

    private void buildLayout() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(getColor(R.color.bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
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

        // 頂部雙模式切換與雙幣狀態列
        root.addView(buildTopModeBar(), UiKit.matchWrap());

        // 中間 4 分頁主內容區
        FrameLayout contentHost = new FrameLayout(this);
        adventureScroll = buildAdventureTab();
        campScroll = buildListScroll(TAB_CAMP);
        journalScroll = buildListScroll(TAB_JOURNAL);
        squadScroll = buildListScroll(TAB_SQUAD);
        contentHost.addView(adventureScroll);
        contentHost.addView(campScroll);
        contentHost.addView(journalScroll);
        contentHost.addView(squadScroll);
        root.addView(contentHost, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        View tabDivider = new View(this);
        tabDivider.setBackgroundColor(Color.rgb(231, 216, 194));
        root.addView(tabDivider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dp(this, 1))
        ));

        // 底部 4 分頁導覽列
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 6), UiKit.dp(this, 8), UiKit.dp(this, 4));
        tabBar.setGravity(Gravity.CENTER);

        tabBtnAdventure = makeNavTabButton(R.string.tab_adventure, TAB_ADVENTURE);
        tabBtnCamp = makeNavTabButton(R.string.tab_camp, TAB_CAMP);
        tabBtnJournal = makeNavTabButton(R.string.tab_journal, TAB_JOURNAL);
        tabBtnSquad = makeNavTabButton(R.string.tab_squad, TAB_SQUAD);

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabLp.setMargins(UiKit.dp(this, 3), 0, UiKit.dp(this, 3), 0);
        tabBar.addView(tabBtnAdventure, tabLp);
        tabBar.addView(tabBtnCamp, new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabBtnJournal, new LinearLayout.LayoutParams(tabLp));
        tabBar.addView(tabBtnSquad, new LinearLayout.LayoutParams(tabLp));
        root.addView(tabBar, UiKit.matchWrap());

        // 精簡版本與自動更新列
        LinearLayout updateFooter = new LinearLayout(this);
        updateFooter.setOrientation(LinearLayout.HORIZONTAL);
        updateFooter.setGravity(Gravity.CENTER_VERTICAL);
        updateFooter.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 2), UiKit.dp(this, 16), UiKit.dp(this, 6));

        TextView versionText = new TextView(this);
        versionText.setText(getString(R.string.version_label, currentVersion));
        versionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        versionText.setTextColor(Color.rgb(122, 100, 82));
        updateFooter.addView(versionText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        updateButton = new Button(this);
        updateButton.setAllCaps(false);
        updateButton.setStateListAnimator(null);
        updateButton.setMinHeight(UiKit.dp(this, 40));
        updateButton.setMinWidth(UiKit.dp(this, 84));
        updateButton.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 5), UiKit.dp(this, 12), UiKit.dp(this, 5));
        updateButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        updateButton.setTypeface(Typeface.create("sans", Typeface.BOLD));
        updateButton.setOnClickListener(v -> onUpdateButtonClicked());
        updateFooter.addView(updateButton, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(updateFooter, UiKit.matchWrap());

        frame.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 口袋探險模式防低頭全螢幕遮罩
        pocketOverlay = buildPocketOverlay();
        frame.addView(pocketOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 穿透式全螢幕慶祝動畫覆蓋層（不攔截任何觸控）
        celebrationOverlay = new CelebrationOverlayView(this);
        frame.addView(celebrationOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(frame);
        renderUpdateButton(UPDATE_STATE_IDLE);
        switchTab(TAB_ADVENTURE, false);
    }

    private LinearLayout buildTopModeBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 6), UiKit.dp(this, 10), UiKit.dp(this, 6));
        bar.setBackgroundColor(Color.rgb(252, 240, 218));

        modeToggleBtn = UiKit.makeSmallPillButton(this, "☀️ 平日模式", UiKit.COLOR_WARM_BTN, getColor(R.color.ink));
        modeToggleBtn.setOnClickListener(v -> {
            UiKit.animatePopBounce(this, modeToggleBtn);
            adventure.family.toggleMode();
            adventure.evaluateExtraAchievements(progress, WorldClock.now().isoDate);
            boolean fam = adventure.family.isFamilyMode();
            String msg = fam
                ? "🎈 已切換「親子出任務模式」！營火開箱享額外加成，快看今日抬頭任務！"
                : "☀️ 已切換「平日累積模式」，寶箱可自動存進營火盒留給晚上！";
            commitAndRefresh(msg);
            if (fam) {
                triggerCelebration(
                    CelebrationOverlayView.STYLE_HARVEST,
                    "🎈 親子出任務啟動！",
                    "營火開箱享加成，全家一起看世界！"
                );
            }
        });
        bar.addView(modeToggleBtn);

        pocketBtn = UiKit.makeSmallPillButton(this, "🎒 口袋", UiKit.COLOR_CARD_BG, getColor(R.color.ink));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pLp.setMargins(UiKit.dp(this, 5), 0, UiKit.dp(this, 5), 0);
        pocketBtn.setOnClickListener(v -> {
            UiKit.animatePopBounce(this, pocketBtn);
            adventure.family.startPocketMode();
            saveProgress();
            refreshPocketOverlay();
            UiKit.animateFadeSlideIn(this, pocketOverlay);
        });
        bar.addView(pocketBtn, pLp);

        topCurrencyText = new TextView(this);
        topCurrencyText.setGravity(Gravity.END);
        topCurrencyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        topCurrencyText.setTypeface(Typeface.create("sans", Typeface.BOLD));
        topCurrencyText.setTextColor(getColor(R.color.ink));
        bar.addView(topCurrencyText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        helpBtn = UiKit.makeSmallPillButton(this, "❓", UiKit.COLOR_CARD_BG, getColor(R.color.ink));
        helpBtn.setContentDescription("開啟走走冒險 2.0 說明指南與新手導覽");
        helpBtn.setMinWidth(UiKit.dp(this, 40));
        helpBtn.setPadding(UiKit.dp(this, 9), UiKit.dp(this, 5), UiKit.dp(this, 9), UiKit.dp(this, 5));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hLp.setMargins(UiKit.dp(this, 5), 0, 0, 0);
        helpBtn.setOnClickListener(v -> {
            UiKit.animatePopBounce(this, helpBtn);
            guideController.showHelpManualDialog(adventure);
        });
        bar.addView(helpBtn, hLp);
        return bar;
    }

    private LinearLayout buildPocketOverlay() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Color.argb(244, 42, 54, 44));
        overlay.setPadding(UiKit.dp(this, 28), UiKit.dp(this, 36), UiKit.dp(this, 28), UiKit.dp(this, 36));
        overlay.setClickable(true);

        TextView icon = new TextView(this);
        icon.setText("🎒🌿");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        icon.setGravity(Gravity.CENTER);
        overlay.addView(icon);

        TextView title = new TextView(this);
        title.setText("口袋探險模式運作中");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        title.setTextColor(Color.rgb(255, 247, 232));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 8));
        overlay.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("現在把手機安心收進口袋，抬頭牽手看風景！\n路上所有步數、孵蛋、澆水與寶箱都會自動收進背包。");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(Color.rgb(220, 234, 218));
        subtitle.setGravity(Gravity.CENTER);
        overlay.addView(subtitle);

        pocketStepsLabel = new TextView(this);
        pocketStepsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        pocketStepsLabel.setTypeface(Typeface.create("sans", Typeface.BOLD));
        pocketStepsLabel.setTextColor(Color.rgb(255, 224, 120));
        pocketStepsLabel.setGravity(Gravity.CENTER);
        pocketStepsLabel.setPadding(0, UiKit.dp(this, 20), 0, UiKit.dp(this, 24));
        overlay.addView(pocketStepsLabel);

        Button exitBtn = UiKit.makeActionButton(this, Color.rgb(245, 191, 66), getColor(R.color.ink));
        exitBtn.setText("✨ 抵達休息點！打開口袋看收穫");
        exitBtn.setOnClickListener(v -> {
            long walked = adventure.family.finishPocketMode();
            String msg = walked > 0
                ? String.format(Locale.TAIWAN, "🎉 剛剛口袋散步一共累積 %d 步！看看收穫吧！", walked)
                : "已結束口袋探險模式";
            commitAndRefresh(msg);
            if (walked > 0) {
                triggerCelebration(
                    CelebrationOverlayView.STYLE_HARVEST,
                    "🎒 口袋散步大收穫！",
                    String.format(Locale.TAIWAN, "剛才安心散步累積了 %d 步！", walked)
                );
            }
        });
        overlay.addView(exitBtn, UiKit.matchWrap());
        overlay.setVisibility(View.GONE);
        return overlay;
    }

    private ScrollView buildAdventureTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, UiKit.dp(this, 12));

        adventureCanvas = new AdventureCanvasView(this);
        adventureCanvas.setOnClickListener(v -> {
            if (!hasStepPermission()) handlePermissionTap();
        });
        col.addView(adventureCanvas, UiKit.matchWrap());

        // 探險分頁首次造訪情境提示卡插槽
        adventureTipSlot = new LinearLayout(this);
        adventureTipSlot.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tipSlotLp = UiKit.matchWrap();
        tipSlotLp.setMargins(UiKit.dp(this, 16), UiKit.dp(this, 4), UiKit.dp(this, 16), 0);
        col.addView(adventureTipSlot, tipSlotLp);

        // 區域切換列
        biomeNavRow = new LinearLayout(this);
        biomeNavRow.setOrientation(LinearLayout.HORIZONTAL);
        biomeNavRow.setGravity(Gravity.CENTER_VERTICAL);
        biomeNavRow.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 2), UiKit.dp(this, 16), UiKit.dp(this, 6));

        biomePrevButton = UiKit.makeSmallPillButton(this, getString(R.string.biome_prev), Color.rgb(245, 232, 208), getColor(R.color.ink));
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
        biomeNextButton = UiKit.makeSmallPillButton(this, getString(R.string.biome_next), Color.rgb(245, 232, 208), getColor(R.color.ink));
        biomeNextButton.setOnClickListener(v -> {
            adventure.shiftDisplayBiome(progress, 1);
            saveProgress();
            refreshAllTabs();
        });
        biomeNavRow.addView(biomePrevButton);
        biomeNavRow.addView(biomeNavLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        biomeNavRow.addView(biomeNextButton);
        col.addView(biomeNavRow);

        // 營火開箱提醒橫幅
        vaultBannerCard = UiKit.makeSectionCard(this, Color.rgb(255, 232, 208));
        vaultBannerTitle = UiKit.addCardTitle(this, vaultBannerCard, "");
        Button openVaultBtn = UiKit.makeActionButton(this, UiKit.COLOR_ACCENT_FIRE, UiKit.COLOR_PRIMARY_TEXT);
        openVaultBtn.setText("🔥 點燃營火・和家人一起開箱！");
        openVaultBtn.setOnClickListener(v -> showCampfireUnboxDialog());
        LinearLayout.LayoutParams vBtnLp = UiKit.matchWrap();
        vBtnLp.setMargins(0, UiKit.dp(this, 6), 0, 0);
        vaultBannerCard.addView(openVaultBtn, vBtnLp);
        LinearLayout.LayoutParams hCardLp = UiKit.matchWrap();
        hCardLp.setMargins(UiKit.dp(this, 16), UiKit.dp(this, 4), UiKit.dp(this, 16), UiKit.dp(this, 6));
        col.addView(vaultBannerCard, hCardLp);

        // Phase 2 實境相機尋寶快捷卡
        cameraHuntCard = UiKit.makeSectionCard(this, Color.rgb(235, 246, 255));
        cameraHuntTitle = UiKit.addCardTitle(this, cameraHuntCard, "");
        cameraHuntBody = UiKit.addCardSubtitle(this, cameraHuntCard, "");
        cameraRecommendedBtn = UiKit.makeActionButton(this, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        Button pickAllHuntsBtn = UiKit.makeActionButton(this, UiKit.COLOR_WARM_BTN, getColor(R.color.ink));
        pickAllHuntsBtn.setText("🌈 全部 12 題");
        pickAllHuntsBtn.setOnClickListener(v -> workshopController.showHuntPickerDialog(adventure));
        LinearLayout camRow = UiKit.makeButtonRow(this, cameraRecommendedBtn, pickAllHuntsBtn);
        LinearLayout.LayoutParams camLp = UiKit.matchWrap();
        camLp.setMargins(0, UiKit.dp(this, 8), 0, 0);
        cameraHuntCard.addView(camRow, camLp);
        col.addView(cameraHuntCard, new LinearLayout.LayoutParams(hCardLp));

        // 島嶼守護巨獸試煉卡
        bossCard = UiKit.makeSectionCard(this, Color.rgb(240, 236, 255));
        bossTitle = UiKit.addCardTitle(this, bossCard, "");
        bossBody = UiKit.addCardSubtitle(this, bossCard, "");
        bossActionBtn = UiKit.makeActionButton(this, UiKit.COLOR_ACCENT_STAR, UiKit.COLOR_PRIMARY_TEXT);
        bossActionBtn.setOnClickListener(v -> {
            int biomeIdx = adventure.frontierBiomeIndex(progress);
            AdventureState.ActionResult r = adventure.calmIslandBoss(progress, biomeIdx, WorldClock.now().isoDate);
            commitAndRefresh(r.message);
            if (r.ok) {
                triggerCelebration(CelebrationOverlayView.STYLE_EVOLVE, "🐲 守護巨獸安撫成功！", r.message);
            }
        });
        LinearLayout.LayoutParams bBtnLp = UiKit.matchWrap();
        bBtnLp.setMargins(0, UiKit.dp(this, 6), 0, 0);
        bossCard.addView(bossActionBtn, bBtnLp);
        col.addView(bossCard, new LinearLayout.LayoutParams(hCardLp));

        // 連貫故事／互動奇遇卡片
        encounterCard = new LinearLayout(this);
        encounterCard.setOrientation(LinearLayout.VERTICAL);
        encounterCard.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12), UiKit.dp(this, 14), UiKit.dp(this, 12));
        encounterTitle = UiKit.addCardTitle(this, encounterCard, "");
        encounterBody = UiKit.addCardSubtitle(this, encounterCard, "");
        encounterBody.setPadding(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 8));

        encounterBtnPrimary = UiKit.makeActionButton(this, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        encounterBtnPrimary.setOnClickListener(v -> onEncounterChoice(0));
        encounterBtnSecondary = UiKit.makeActionButton(this, UiKit.COLOR_WARM_BTN, getColor(R.color.ink));
        encounterBtnSecondary.setOnClickListener(v -> onEncounterChoice(1));
        encounterActionsRow = UiKit.makeButtonRow(this, encounterBtnPrimary, encounterBtnSecondary);
        encounterCard.addView(encounterActionsRow, UiKit.matchWrap());
        col.addView(encounterCard, new LinearLayout.LayoutParams(hCardLp));

        // 親子抬頭任務區
        lookUpContainer = new LinearLayout(this);
        lookUpContainer.setOrientation(LinearLayout.VERTICAL);
        lookUpContainer.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 4), UiKit.dp(this, 16), UiKit.dp(this, 4));
        col.addView(lookUpContainer, UiKit.matchWrap());

        adventureFooterNote = new TextView(this);
        adventureFooterNote.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 4), UiKit.dp(this, 18), UiKit.dp(this, 6));
        adventureFooterNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        adventureFooterNote.setTypeface(Typeface.create("sans", Typeface.BOLD));
        adventureFooterNote.setTextColor(Color.rgb(110, 90, 76));
        col.addView(adventureFooterNote);

        scroll.addView(col);
        return scroll;
    }

    private ScrollView buildListScroll(int tab) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 10), UiKit.dp(this, 14), UiKit.dp(this, 16));
        scroll.addView(list);
        if (tab == TAB_CAMP) campListContainer = list;
        else if (tab == TAB_JOURNAL) journalListContainer = list;
        else squadListContainer = list;
        return scroll;
    }

    private Button makeNavTabButton(int stringResId, int tabIndex) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        btn.setMinHeight(UiKit.dp(this, 46));
        btn.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 6), UiKit.dp(this, 4), UiKit.dp(this, 6));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.create("sans", Typeface.BOLD));
        btn.setText(stringResId);
        btn.setOnClickListener(v -> switchTab(tabIndex));
        return btn;
    }

    private void switchTab(int target) {
        switchTab(target, target != activeTab);
    }

    private void switchTab(int target, boolean animate) {
        activeTab = target;
        adventureScroll.setVisibility(target == TAB_ADVENTURE ? View.VISIBLE : View.GONE);
        campScroll.setVisibility(target == TAB_CAMP ? View.VISIBLE : View.GONE);
        journalScroll.setVisibility(target == TAB_JOURNAL ? View.VISIBLE : View.GONE);
        squadScroll.setVisibility(target == TAB_SQUAD ? View.VISIBLE : View.GONE);
        refreshAllTabs();
        if (animate) {
            View activeScroll = target == TAB_ADVENTURE ? adventureScroll
                : (target == TAB_CAMP ? campScroll : (target == TAB_JOURNAL ? journalScroll : squadScroll));
            Button activeBtn = target == TAB_ADVENTURE ? tabBtnAdventure
                : (target == TAB_CAMP ? tabBtnCamp : (target == TAB_JOURNAL ? tabBtnJournal : tabBtnSquad));
            UiKit.animateFadeSlideIn(this, activeScroll);
            UiKit.animatePopBounce(this, activeBtn);
        }
        if (target == TAB_ADVENTURE) {
            adventureCanvas.startIdleAnimation();
            campController.stopAnimation();
        } else if (target == TAB_CAMP) {
            adventureCanvas.stopIdleAnimation();
            campController.startAnimation();
        } else {
            adventureCanvas.stopIdleAnimation();
            campController.stopAnimation();
        }
    }

    private void refreshAllTabs() {
        WorldClock.Snapshot clock = WorldClock.now();
        refreshTopBar();
        refreshPocketOverlay();
        refreshTabButtons(clock.isoDate);
        refreshAdventureTab(clock);
        if (activeTab == TAB_CAMP) {
            campController.render(campListContainer, progress, adventure, clock);
        } else if (activeTab == TAB_JOURNAL) {
            journalController.render(journalListContainer, progress, adventure, clock.isoDate);
        } else if (activeTab == TAB_SQUAD) {
            squadController.render(squadListContainer, progress, adventure, clock);
        }
    }

    private void refreshTopBar() {
        boolean fam = adventure.family.isFamilyMode();
        modeToggleBtn.setText(fam ? "🎈 親子出任務" : "☀️ 平日模式");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(UiKit.dp(this, 18));
        bg.setColor(fam ? UiKit.COLOR_ACCENT_FIRE : UiKit.COLOR_WARM_BTN);
        modeToggleBtn.setBackground(bg);
        modeToggleBtn.setTextColor(fam ? UiKit.COLOR_PRIMARY_TEXT : getColor(R.color.ink));
        topCurrencyText.setText(String.format(Locale.TAIWAN, "🪙 %d  ✨ %d", progress.coins, adventure.camp.starSand));
    }

    private void refreshPocketOverlay() {
        if (pocketOverlay == null) return;
        if (adventure.family.pocketMode) {
            pocketOverlay.setVisibility(View.VISIBLE);
            pocketStepsLabel.setText(String.format(
                Locale.TAIWAN,
                "本次口袋已收集：%d 步",
                adventure.family.pocketStepsCollected
            ));
        } else {
            pocketOverlay.setVisibility(View.GONE);
        }
    }

    private void refreshTabButtons(String todayStr) {
        boolean hasEnc = adventure.pendingEncounter != null;
        int campAlerts = adventure.vault.count() + adventure.companions.readyEggCount() + adventure.camp.readyGardenCount();
        int claimable = adventure.pendingClaimableDailyCount(progress, todayStr);

        tabBtnAdventure.setText(getString(hasEnc ? R.string.tab_adventure_alert : R.string.tab_adventure));
        tabBtnCamp.setText(campAlerts > 0
            ? getString(R.string.tab_camp_badge, campAlerts)
            : getString(R.string.tab_camp));
        tabBtnJournal.setText(getString(R.string.tab_journal));
        tabBtnSquad.setText(claimable > 0
            ? getString(R.string.tab_squad_badge, claimable)
            : getString(R.string.tab_squad));

        styleTabButton(tabBtnAdventure, activeTab == TAB_ADVENTURE);
        styleTabButton(tabBtnCamp, activeTab == TAB_CAMP);
        styleTabButton(tabBtnJournal, activeTab == TAB_JOURNAL);
        styleTabButton(tabBtnSquad, activeTab == TAB_SQUAD);
    }

    private void styleTabButton(Button btn, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(UiKit.dp(this, 14));
        if (selected) {
            bg.setColor(UiKit.COLOR_PRIMARY);
            btn.setTextColor(UiKit.COLOR_PRIMARY_TEXT);
        } else {
            bg.setColor(Color.rgb(245, 232, 208));
            btn.setTextColor(getColor(R.color.ink));
        }
        btn.setBackground(bg);
    }

    private void refreshAdventureTab(WorldClock.Snapshot clock) {
        String todayStr = clock.isoDate;
        adventureCanvas.bind(progress, adventure, clock, statusText(), stepSensor == null || !hasStepPermission());

        if (adventure.guide.shouldShowTabTip(GuideState.TIP_ADVENTURE)) {
            if (adventureTipSlot.getChildCount() == 0) {
                adventureTipSlot.addView(GuideDialogController.buildContextualTipCard(this, GuideState.TIP_ADVENTURE, () -> {
                    adventure.guide.dismissTabTip(GuideState.TIP_ADVENTURE);
                    commitAndRefresh(null);
                }), UiKit.cardMargins(this));
            }
            adventureTipSlot.setVisibility(View.VISIBLE);
        } else {
            adventureTipSlot.removeAllViews();
            adventureTipSlot.setVisibility(View.GONE);
        }

        int maxUnlocked = adventure.highestUnlockedBiomeIndex(progress);
        if (maxUnlocked > 0) {
            biomeNavRow.setVisibility(View.VISIBLE);
            int dispIdx = adventure.activeDisplayBiomeIndex(progress);
            AdventureCatalog.BiomeDef disp = AdventureCatalog.biome(dispIdx);
            biomeNavLabel.setText(String.format(Locale.TAIWAN, "第 %d / %d 章：%s", dispIdx + 1, maxUnlocked + 1, disp.name));
            biomePrevButton.setEnabled(dispIdx > 0);
            biomePrevButton.setAlpha(dispIdx > 0 ? 1f : 0.45f);
            biomeNextButton.setEnabled(dispIdx < maxUnlocked);
            biomeNextButton.setAlpha(dispIdx < maxUnlocked ? 1f : 0.45f);
        } else {
            biomeNavRow.setVisibility(View.GONE);
        }

        // 營火盒提醒卡
        if (adventure.vault.count() > 0) {
            vaultBannerCard.setVisibility(View.VISIBLE);
            vaultBannerTitle.setText(String.format(
                Locale.TAIWAN,
                "🔥 營火盒裡存了 %d 個寶箱、%d 顆神祕蛋！",
                adventure.vault.chestCount(),
                adventure.vault.eggCount()
            ));
        } else {
            vaultBannerCard.setVisibility(View.GONE);
        }

        // 相機尋寶推薦卡
        ColorScavenger.HuntDef recHunt = ColorScavenger.recommendedHunt(
            adventure.rainbowCollectedMask,
            clock.dayOfMonth + adventure.photoHuntsCompleted
        );
        cameraHuntTitle.setText(String.format(
            Locale.TAIWAN,
            "📸 實境相機尋寶（彩虹 %d/%d · 明信片 %d 張）",
            adventure.rainbowCollectedCount(),
            ColorScavenger.RAINBOW_COUNT,
            adventure.stories.memories.size()
        ));
        cameraHuntBody.setText("今日推薦：" + recHunt.title + " — " + recHunt.prompt);
        cameraRecommendedBtn.setText("📷 拍「" + recHunt.colorLabel + "」");
        final int targetHuntId = recHunt.id;
        cameraRecommendedBtn.setOnClickListener(v -> workshopController.showCaptureSourceDialog(targetHuntId));

        // 島嶼守護巨獸安撫試煉卡
        int frontierBiome = adventure.frontierBiomeIndex(progress);
        if (!adventure.stories.isBossCleared(frontierBiome)) {
            bossCard.setVisibility(View.VISIBLE);
            StoryCatalog.BossDef boss = StoryCatalog.bossForBiome(frontierBiome);
            boolean ready = adventure.stories.canCalmCurrentBoss(frontierBiome);
            bossTitle.setText("🐲 島嶼守護試煉：" + boss.name);
            bossBody.setText(String.format(
                Locale.TAIWAN,
                "%s\n進度：步數 %d / %d 步 ｜ 抬頭任務：%s",
                boss.intro,
                adventure.stories.bossStepProgress,
                StoryCatalog.BOSS_REQUIRED_STEPS,
                adventure.stories.bossLookUpDone ? "已完成 ✓" : "待完成（二選一即可過關！）"
            ));
            bossActionBtn.setText(ready
                ? "✨ 條件達成！安撫巨獸領取稀有蛋"
                : "繼續散步或完成 1 張抬頭任務即可安撫");
            bossActionBtn.setEnabled(ready);
            GradientDrawable bBg = new GradientDrawable();
            bBg.setCornerRadius(UiKit.dp(this, 14));
            bBg.setColor(ready ? UiKit.COLOR_ACCENT_STAR : UiKit.COLOR_DISABLED_BG);
            bossActionBtn.setBackground(bBg);
            bossActionBtn.setTextColor(ready ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT);
        } else {
            bossCard.setVisibility(View.GONE);
        }

        // 互動奇遇與連貫故事卡
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(UiKit.dp(this, 18));
        if (adventure.pendingEncounter != null) {
            cardBg.setColor(UiKit.COLOR_CARD_GOLD);
            cardBg.setStroke(UiKit.dp(this, 2), Color.rgb(226, 168, 62));
            encounterCard.setBackground(cardBg);
            encounterTitle.setText(adventure.pendingEncounter.title);
            encounterBody.setText(adventure.pendingEncounter.prompt);
            encounterBtnPrimary.setText(adventure.pendingEncounter.primaryLabel);
            encounterBtnSecondary.setText(adventure.pendingEncounter.secondaryLabel);
            encounterActionsRow.setVisibility(View.VISIBLE);
        } else {
            cardBg.setColor(Color.rgb(248, 236, 214));
            cardBg.setStroke(UiKit.dp(this, 1), Color.rgb(225, 206, 178));
            encounterCard.setBackground(cardBg);
            long nextEncAt = ((progress.eventBucket + 1L) * StepProgress.STEPS_PER_EVENT);
            long remainSteps = Math.max(0L, nextEncAt - progress.lifetime);
            encounterTitle.setText("冒險日誌速報");
            encounterBody.setText(String.format(
                Locale.TAIWAN,
                "%s\n⏳ 再走 %d 步觸發下一個連貫繪本奇遇。",
                lastNoticeText,
                remainSteps
            ));
            encounterActionsRow.setVisibility(View.GONE);
        }

        // 線下親子抬頭任務卡
        renderLookUpSection(todayStr);

        int claimable = adventure.pendingClaimableDailyCount(progress, todayStr);
        if (claimable > 0) {
            adventureFooterNote.setText(String.format(
                Locale.TAIWAN,
                "🎁 今日有 %d 項步數任務達標，可切到「小隊」領取獎勵！",
                claimable
            ));
            adventureFooterNote.setTextColor(UiKit.COLOR_PRIMARY);
        } else {
            adventureFooterNote.setText("今天走得越多，秘密營地與家庭回憶就越豐富。");
            adventureFooterNote.setTextColor(Color.rgb(110, 90, 76));
        }
    }

    private void renderLookUpSection(String todayStr) {
        lookUpContainer.removeAllViews();
        adventure.stories.ensureDailyLookUpOffers(todayStr, random);
        boolean fam = adventure.family.isFamilyMode();

        String header = fam
            ? "👀 今日親子抬頭任務（放下手機看世界，完成蓋章賺星砂！）"
            : "👀 今日線下抬頭小任務（切到「親子出任務」更適合全家一起玩）";
        UiKit.addSectionHeader(this, lookUpContainer, header);

        // 年齡分級篩選列
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, 0, 0, UiKit.dp(this, 6));
        addAgeFilterPill(filterRow, "全齡", StoryCatalog.AGE_ALL, todayStr);
        addAgeFilterPill(filterRow, "學齡前/低年級", StoryCatalog.AGE_PRESCHOOL, todayStr);
        addAgeFilterPill(filterRow, "中高年級+", StoryCatalog.AGE_ELEMENTARY, todayStr);

        Button rerollBtn = UiKit.makeSmallPillButton(this, "🎲 換題", UiKit.COLOR_WARM_BTN, getColor(R.color.ink));
        rerollBtn.setOnClickListener(v -> {
            adventure.stories.rollLookUpOffers(random);
            commitAndRefresh("已換上新的一組抬頭任務卡！");
        });
        filterRow.addView(rerollBtn);
        lookUpContainer.addView(filterRow, UiKit.matchWrap());

        int showCount = fam ? adventure.stories.offeredLookUpIds.size() : Math.min(1, adventure.stories.offeredLookUpIds.size());
        for (int i = 0; i < showCount; i++) {
            int qId = adventure.stories.offeredLookUpIds.get(i);
            StoryCatalog.LookUpQuestDef q = StoryCatalog.lookUpQuestById(qId);
            boolean done = adventure.stories.isLookUpCompletedToday(qId, todayStr);

            LinearLayout qCard = UiKit.makeSectionCard(this, done ? UiKit.COLOR_CARD_GREEN : UiKit.COLOR_CARD_BG);
            UiKit.addCardTitle(this, qCard, String.format(Locale.TAIWAN, "[%s] %s", q.category, q.title));
            UiKit.addCardSubtitle(this, qCard, q.detail + String.format(
                Locale.TAIWAN, "（+%d 金幣、+%d 星砂）", q.coinReward, q.starReward
            ));
            Button doneBtn = UiKit.makeActionButton(
                this,
                done ? UiKit.COLOR_DISABLED_BG : UiKit.COLOR_PRIMARY,
                done ? UiKit.COLOR_DISABLED_TEXT : UiKit.COLOR_PRIMARY_TEXT
            );
            doneBtn.setText(done ? "今日已完成蓋章 ✓" : "🎉 我們完成了！寫句留念蓋章");
            doneBtn.setEnabled(!done);
            doneBtn.setOnClickListener(v -> showCompleteLookUpDialog(q, todayStr));
            LinearLayout.LayoutParams lp = UiKit.matchWrap();
            lp.setMargins(0, UiKit.dp(this, 6), 0, 0);
            qCard.addView(doneBtn, lp);
            lookUpContainer.addView(qCard, UiKit.cardMargins(this));
        }
    }

    private void addAgeFilterPill(LinearLayout row, String label, String filterCode, String todayStr) {
        boolean active = filterCode.equals(adventure.stories.lookUpAgeFilter);
        Button btn = UiKit.makeSmallPillButton(
            this,
            label,
            active ? UiKit.COLOR_PRIMARY : UiKit.COLOR_CARD_MUTED,
            active ? UiKit.COLOR_PRIMARY_TEXT : getColor(R.color.ink)
        );
        btn.setOnClickListener(v -> {
            adventure.stories.setAgeFilterAndReroll(filterCode, todayStr, random);
            commitAndRefresh("已切換抬頭任務年齡層：" + label);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, UiKit.dp(this, 5), 0);
        row.addView(btn, lp);
    }

    private void showCompleteLookUpDialog(StoryCatalog.LookUpQuestDef quest, String todayStr) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 10), UiKit.dp(this, 20), UiKit.dp(this, 4));
        TextView hint = new TextView(this);
        hint.setText("寫下今天孩子說的好玩的話或小發現（選填，會收進手札留念）：");
        hint.setTextColor(getColor(R.color.ink));
        box.addView(hint);

        EditText noteInput = new EditText(this);
        noteInput.setHint("例：小寶說那朵雲像正在跑步的小狗狗！");
        box.addView(noteInput);

        dismissActiveDialog();
        activeDialog = new AlertDialog.Builder(this)
            .setTitle("👀 完成「" + quest.title + "」")
            .setView(box)
            .setPositiveButton("蓋章領獎！", (d, w) -> {
                AdventureState.ActionResult r = adventure.completeLookUpQuest(
                    progress, quest.id, noteInput.getText().toString(), todayStr
                );
                commitAndRefresh(r.message);
                if (r.ok) {
                    triggerCelebration(CelebrationOverlayView.STYLE_HARVEST, "👀 親子抬頭任務達成！", r.message);
                }
            })
            .setNegativeButton("取消", null)
            .create();
        activeDialog.show();
    }

    private void showCampfireUnboxDialog() {
        if (adventure.vault.isEmpty()) {
            showShortToast("目前營火盒是空的！平日走路滿 1,000 步或撿到蛋就會自動存進來喔！");
            return;
        }
        String todayStr = WorldClock.now().isoDate;
        boolean fam = adventure.family.isFamilyMode();
        int fireLv = adventure.camp.buildingLevel(CampState.BLD_CAMPFIRE);
        int bonusPct = fam ? (10 + Math.max(0, fireLv - 1) * 6) : 0;

        String[] labels = new String[adventure.vault.items.size()];
        for (int i = 0; i < adventure.vault.items.size(); i++) {
            CampfireVault.VaultItem it = adventure.vault.items.get(i);
            labels[i] = it.eggType >= 0
                ? String.format(Locale.TAIWAN, "🥚 %s（存入：%s）", it.title, it.createdDate)
                : String.format(Locale.TAIWAN, "🎁 %s（約 %d 金幣 +%d 星砂）", it.title, it.coins, it.starSand);
        }

        String title = fam
            ? String.format(Locale.TAIWAN, "🔥 親子營火開箱（金幣 +%d%% 加成！）", bonusPct)
            : "🔥 營火開箱盒（切到親子模式享更高加成！）";

        dismissActiveDialog();
        activeDialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels, (d, which) -> {
                if (which >= 0 && which < adventure.vault.items.size()) {
                    String id = adventure.vault.items.get(which).id;
                    CampfireVault.OpenOutcome out = adventure.openVaultItem(progress, id, todayStr);
                    commitAndRefresh(out.message);
                    if (out.ok) {
                        triggerCelebration(CelebrationOverlayView.STYLE_CAMPFIRE, "🔥 營火開箱驚喜！", out.message);
                    }
                    if (out.ok && !adventure.vault.isEmpty()) {
                        showCampfireUnboxDialog(); // 讓孩子連續開箱！
                    }
                }
            })
            .setPositiveButton("一次全開！", (d, w) -> {
                int opened = 0;
                int totalCoins = 0;
                int totalStars = 0;
                int eggsPlaced = 0;
                for (int guard = 0; guard < CampfireVault.MAX_ITEMS && !adventure.vault.isEmpty(); guard++) {
                    String nextId = null;
                    for (CampfireVault.VaultItem it : adventure.vault.items) {
                        if (it.eggType < 0 || adventure.companions.hasFreeEggSlot()) {
                            nextId = it.id;
                            break;
                        }
                    }
                    if (nextId == null) break;
                    CampfireVault.OpenOutcome out = adventure.openVaultItem(progress, nextId, todayStr);
                    if (!out.ok) break;
                    opened++;
                    totalCoins += out.coinsGained;
                    totalStars += out.starSandGained;
                    if (out.eggPlaced) eggsPlaced++;
                }
                if (opened == 0) {
                    showShortToast("孵蛋巢已滿，先孵化一顆蛋就能打開剩餘的神祕蛋喔！");
                } else {
                    String summary = String.format(
                        Locale.TAIWAN,
                        "🔥 營火派對開出 %d 件包裹！+%d 金幣、+%d 星砂%s",
                        opened, totalCoins, totalStars, eggsPlaced > 0 ? ("、放入 " + eggsPlaced + " 顆蛋！") : ""
                    );
                    commitAndRefresh(summary);
                    triggerCelebration(CelebrationOverlayView.STYLE_CAMPFIRE, "🔥 營火派對大開箱！", summary);
                }
            })
            .setNegativeButton("先留著", null)
            .create();
        activeDialog.show();
    }

    private void showMigrationWelcomeDialog(String message) {
        dismissActiveDialog();
        activeDialog = new AlertDialog.Builder(this)
            .setTitle("🎉 歡迎升級《走走冒險 2.0》")
            .setMessage(message)
            .setPositiveButton("📖 看 4 頁新玩法導覽", (d, w) -> guideController.showOnboardingDialog(adventure))
            .setNeutralButton("去看看秘密營地！", (d, w) -> {
                adventure.guide.markOnboardingCompleted();
                saveProgress();
                switchTab(TAB_CAMP);
            })
            .setNegativeButton("太棒了", (d, w) -> {
                adventure.guide.markOnboardingCompleted();
                saveProgress();
            })
            .create();
        activeDialog.show();
    }

    private void onEncounterChoice(int choiceIndex) {
        String todayStr = WorldClock.now().isoDate;
        AdventureState.ActionResult res = adventure.resolvePendingEncounter(progress, choiceIndex, todayStr, random);
        if (!res.ok) {
            showShortToast(res.message);
            return;
        }
        commitAndRefresh(res.message);
        boolean storyFinale = res.message.contains("結局") || res.message.contains("完成整本");
        triggerCelebration(
            storyFinale ? CelebrationOverlayView.STYLE_EVOLVE : CelebrationOverlayView.STYLE_HARVEST,
            storyFinale ? "📖 繪本故事大結局！" : "✨ 奇遇任務完成！",
            res.message
        );
    }

    private void copyDailyReportToClipboard(String todayStr) {
        String report = adventure.formatDailyReport(progress, todayStr);
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("WalkAdventure Report", report));
            showShortToast(getString(R.string.report_copied));
        }
    }

    private String statusText() {
        if (stepSensor == null) return "此裝置沒有步數感測器，無法取得實際步數。";
        if (isPermissionPermanentlyDenied()) return "尚未開啟「身體活動」權限，點一下畫面前往系統設定允許。";
        if (!hasStepPermission()) return "尚未允許「活動辨識」權限，點一下畫面重新授權計步。";
        return "步數感測器已連線 • 純本機離線運作，關閉 App 也會保留進度";
    }

    // ──────────────── App 內自動更新（維持既有安全驗證流程） ────────────────

    private void renderUpdateButton(int state) {
        if (updateButton == null) return;
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(UiKit.dp(this, 20));
        if (state == UPDATE_STATE_READY && readyRelease != null) {
            pill.setColor(UiKit.COLOR_PRIMARY);
            updateButton.setBackground(pill);
            updateButton.setTextColor(UiKit.COLOR_PRIMARY_TEXT);
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
            pill.setColor(UiKit.COLOR_WARM_BTN);
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
                    getPackageName(), PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                info = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            return info.versionName != null ? info.versionName : "1.0.0";
        } catch (PackageManager.NameNotFoundException ignored) {
            return "1.0.0";
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
        devicePrefs.edit().remove(KEY_DOWNLOADED_TAG).remove(KEY_DISMISSED_TAG).apply();
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
        SharedPreferences.Editor editor = devicePrefs.edit().putLong(KEY_UPDATE_LAST_CHECK, checkedAtMs);
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
                finishUpdateCheckOnUi(manual, outcome.status, null, false, false);
                return;
            }
            AppUpdate.ReleaseInfo release = outcome.release;
            if (release == null || !AppUpdate.isRemoteNewer(release.versionName, currentVersion)) {
                finishUpdateCheckOnUi(manual, AppUpdate.CheckOutcome.STATUS_NO_RELEASE, null, true, false);
                return;
            }
            boolean alreadyDownloaded = AppUpdate.verifyDownloadedApk(targetApkFile, release.apkSize, release.apkSha256);
            if (!alreadyDownloaded) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) renderUpdateButton(UPDATE_STATE_DOWNLOADING);
                });
                alreadyDownloaded = AppUpdate.downloadVerifiedApk(release, targetApkFile);
            }
            if (alreadyDownloaded) {
                devicePrefs.edit().putString(KEY_DOWNLOADED_TAG, release.tagName).apply();
                finishUpdateCheckOnUi(manual, AppUpdate.CheckOutcome.STATUS_OK, release, true, false);
            } else {
                finishUpdateCheckOnUi(manual, AppUpdate.CheckOutcome.STATUS_ERROR, null, true, true);
            }
        });
    }

    private void finishUpdateCheckOnUi(
        boolean manual,
        int status,
        AppUpdate.ReleaseInfo downloadedRelease,
        boolean reachedServer,
        boolean downloadFailed
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
                showShortToast(getString(downloadFailed ? R.string.update_download_failed : R.string.update_check_failed));
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

    // ──────────────── 生命週期、權限與計步感測器 ────────────────

    @Override protected void onResume() {
        super.onResume();
        register();
        enableNfcForegroundDispatch();
        if (adventure != null && adventure.footprints.enabled && hasCoarseLocationPermission()) {
            refreshCoarseFootprintLocation(false);
        }
        if (activeTab == TAB_ADVENTURE && adventureCanvas != null) {
            adventureCanvas.startIdleAnimation();
        } else if (activeTab == TAB_CAMP && campController != null) {
            campController.startAnimation();
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
        disableNfcForegroundDispatch();
        if (adventureCanvas != null) adventureCanvas.stopIdleAnimation();
        if (campController != null) campController.stopAnimation();
        if (celebrationOverlay != null) celebrationOverlay.stopAll();
        if (mediaStore != null) mediaStore.releaseAll();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfcIntent(intent);
    }

    @Override protected void onDestroy() {
        dismissActiveDialog();
        if (celebrationOverlay != null) celebrationOverlay.stopAll();
        if (mediaStore != null) mediaStore.releaseAll();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ) {
            register();
            return;
        }
        if (requestCode == REQ_AUDIO) {
            Runnable cb = pendingAudioPermissionCallback;
            pendingAudioPermissionCallback = null;
            if (grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (cb != null) cb.run();
            } else {
                showShortToast("未開啟麥克風權限，仍可照常製作含照片與塗鴉的明信片！");
            }
            return;
        }
        if (requestCode == REQ_LOCATION) {
            Runnable cb = pendingLocationPermissionCallback;
            pendingLocationPermissionCallback = null;
            if (grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (cb != null) cb.run();
            } else {
                showShortToast("未開啟粗略定位，仍可使用「手動打卡新秘密基地」暢玩足跡地圖！");
            }
        }
    }

    private boolean hasRecordAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordAudioPermission(Runnable onGranted) {
        pendingAudioPermissionCallback = onGranted;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
    }

    private boolean hasCoarseLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCoarseLocationPermission(Runnable onGranted) {
        pendingLocationPermissionCallback = onGranted;
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    private void enableNfcForegroundDispatch() {
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) return;
        try {
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = Build.VERSION.SDK_INT >= 31
                ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pending = PendingIntent.getActivity(this, 0, intent, flags);
            nfcAdapter.enableForegroundDispatch(this, pending, null, null);
        } catch (RuntimeException ignored) {
            // 部分無完整 NFC 堆疊機型靜默忽略
        }
    }

    private void disableNfcForegroundDispatch() {
        if (nfcAdapter == null) return;
        try {
            nfcAdapter.disableForegroundDispatch(this);
        } catch (RuntimeException ignored) {
            // 靜默忽略
        }
    }

    @SuppressWarnings("deprecation")
    private void handleNfcIntent(Intent intent) {
        if (intent == null || treasureController == null) return;
        String action = intent.getAction();
        if (!NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
            && !NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
            && !NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            return;
        }
        Tag tag = Build.VERSION.SDK_INT >= 33
            ? intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class)
            : intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;
        String hexUid = TreasureAndQrState.bytesToHexUid(tag.getId());
        if (!hexUid.isEmpty()) {
            treasureController.onNfcTagDiscovered(hexUid, progress, adventure, WorldClock.now().isoDate);
        }
    }

    private void refreshCoarseFootprintLocation(boolean userInitiated) {
        if (!adventure.footprints.enabled) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        Location best = null;
        try {
            Location netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location passiveLoc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            best = netLoc != null ? netLoc : passiveLoc;
        } catch (SecurityException | IllegalArgumentException ignored) {
            return;
        }
        if (best == null) {
            if (userInitiated) {
                showShortToast("尚未取得粗略定位訊號，可以先走到戶外或改用「手動打卡新秘密基地」！");
            }
            return;
        }
        FootprintState.DiscoveryOutcome out = adventure.recordCoarseFootprint(
            progress, best.getLatitude(), best.getLongitude(), WorldClock.now().isoDate
        );
        if (out.isNewTile) {
            commitAndRefresh(out.message);
            triggerCelebration(CelebrationOverlayView.STYLE_HARVEST, "🗺️ 踩破全新足跡迷霧！", out.message);
        } else if (userInitiated) {
            commitAndRefresh("📍 目前所在格已探索過（已累計破霧 " + adventure.footprints.discoveredCount() + " 格）");
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrateSuccess() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (RuntimeException ignored) {
            // 無震動硬體或權限時靜默忽略
        }
    }

    @SuppressWarnings("deprecation")
    private void launchSystemCamera(int huntId) {
        pendingHuntId = huntId;
        pendingMemoryId = "mem_" + System.currentTimeMillis();
        devicePrefs.edit()
            .putInt(KEY_PENDING_HUNT_ID, pendingHuntId)
            .putString(KEY_PENDING_MEMORY_ID, pendingMemoryId)
            .apply();

        File captureFile = MemoryFileProvider.pendingCaptureFile(this);
        if (captureFile.exists()) {
            boolean ignored = captureFile.delete();
        }
        Uri outUri = MemoryFileProvider.uriForPendingCapture(this);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_CAMERA_CAPTURE);
        } catch (ActivityNotFoundException ignored) {
            showShortToast("找不到系統相機，已為你改用相簿挑選照片");
            launchPhotoPicker(huntId);
        }
    }

    @SuppressWarnings("deprecation")
    private void launchPhotoPicker(int huntId) {
        pendingHuntId = huntId;
        pendingMemoryId = "mem_" + System.currentTimeMillis();
        devicePrefs.edit()
            .putInt(KEY_PENDING_HUNT_ID, pendingHuntId)
            .putString(KEY_PENDING_MEMORY_ID, pendingMemoryId)
            .apply();

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "選擇散步照片"), REQ_PICK_PHOTO);
        } catch (ActivityNotFoundException ignored) {
            showShortToast("找不到可用的照片選擇器");
        }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAMERA_CAPTURE && requestCode != REQ_PICK_PHOTO) return;
        if (resultCode != RESULT_OK) {
            mediaStore.deleteTempCapture();
            return;
        }

        int huntId = pendingHuntId > 0 ? pendingHuntId : devicePrefs.getInt(KEY_PENDING_HUNT_ID, 1);
        String memId = (pendingMemoryId != null && !pendingMemoryId.isEmpty())
            ? pendingMemoryId
            : devicePrefs.getString(KEY_PENDING_MEMORY_ID, "mem_" + System.currentTimeMillis());

        String savedPhoto;
        if (requestCode == REQ_CAMERA_CAPTURE) {
            File captured = MemoryFileProvider.pendingCaptureFile(this);
            savedPhoto = mediaStore.importCapturedFile(captured, memId);
            mediaStore.deleteTempCapture();
        } else {
            Uri picked = data != null ? data.getData() : null;
            savedPhoto = mediaStore.importFromContentUri(picked, memId);
        }

        if (savedPhoto == null) {
            showShortToast("照片讀取失敗，請重新拍攝或再試一次");
            return;
        }

        ColorScavenger.HuntDef hunt = ColorScavenger.huntById(huntId);
        ColorScavenger.AnalysisResult analysis = mediaStore.analyzeStoredPhoto(savedPhoto, hunt.targetColor);
        workshopController.showWorkshopDialog(
            progress,
            adventure,
            huntId,
            memId,
            savedPhoto,
            analysis,
            WorldClock.now()
        );
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
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        refreshAllTabs();
    }

    private long bootCount() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, (int) StepProgress.UNKNOWN);
    }

    // ──────────────── 雙層本機存檔（SharedPreferences + 原子 JSON） ────────────────

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
            : StepProgress.todayStepsFromLegacy(p.day, WorldClock.now().isoDate, prefs.getFloat(KEY_LEGACY_BASELINE, -1), p.lastSensorSteps);
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

    private boolean loadV2StateFile(AdventureState target) {
        File file = new File(getFilesDir(), V2_SAVE_FILENAME);
        if (!file.exists()) return false;
        try {
            String raw = readTextFile(file);
            if (SaveCodec.deserializeInto(raw, target)) {
                return true;
            }
            // JSON 損壞時備份成 .bak 並回退安全重建
            File bak = new File(getFilesDir(), V2_SAVE_FILENAME + ".bak");
            boolean ignored = file.renameTo(bak);
        } catch (IOException ignored) {
            // 讀取異常時交由 migrateIfNeeded 安全初始化
        }
        return false;
    }

    private void saveV2StateFileAtomic(AdventureState src) {
        String json = SaveCodec.serialize(src);
        File target = new File(getFilesDir(), V2_SAVE_FILENAME);
        File tmp = new File(getFilesDir(), V2_SAVE_FILENAME + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.getFD().sync();
        } catch (IOException ignored) {
            return;
        }
        if (!tmp.renameTo(target)) {
            // 某些檔案系統若目標已存在時 renameTo 需先移除舊檔
            boolean deleted = !target.exists() || target.delete();
            if (deleted) {
                boolean ignored = tmp.renameTo(target);
            }
        }
    }

    private static String readTextFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

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
        saveV2StateFileAtomic(adventure);
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e == null || e.sensor == null || e.values == null || e.values.length == 0) {
            return;
        }
        int type = e.sensor.getType();
        if (type == Sensor.TYPE_LIGHT) {
            long nowMs = SystemClock.uptimeMillis();
            if (nowMs - lastLightProcessMs < 3000L) return;
            lastLightProcessMs = nowMs;
            EnvironmentSensorState.TriggerResult lightRes = adventure.applyLightReading(
                progress, e.values[0], WorldClock.now().isoDate
            );
            if (lightRes.triggered()) {
                saveProgress();
                if (!adventure.family.pocketMode) {
                    lastNoticeText = lightRes.title + " " + lightRes.message;
                    showShortToast(lightRes.title);
                    triggerCelebration(CelebrationOverlayView.STYLE_HARVEST, lightRes.title, lightRes.message);
                }
                refreshAllTabs();
            }
            return;
        }
        if (type != Sensor.TYPE_STEP_COUNTER) {
            return;
        }
        WorldClock.Snapshot clock = WorldClock.now();
        StepProgress.Update update = progress.onSensorReading((long) e.values[0], clock.isoDate, bootCount(), random);
        String rpgNotice = adventure.applyWalkUpdateV2(progress, update, clock, random);
        EnvironmentSensorState.TriggerResult cadenceRes = adventure.applyCadenceSteps(
            progress, update.steps, SystemClock.uptimeMillis(), clock.isoDate
        );
        saveProgress();
        if (cadenceRes.triggered() && !adventure.family.pocketMode) {
            triggerCelebration(CelebrationOverlayView.STYLE_EVOLVE, cadenceRes.title, cadenceRes.message);
            rpgNotice = rpgNotice == null ? (cadenceRes.title + " " + cadenceRes.message)
                : (rpgNotice + "\n" + cadenceRes.title);
        }
        if (rpgNotice != null && !adventure.family.pocketMode) {
            lastNoticeText = rpgNotice;
            showShortToast(rpgNotice.replace('\n', ' '));
        }
        if (update.steps >= 30L && adventure.footprints.enabled && hasCoarseLocationPermission()) {
            refreshCoarseFootprintLocation(false);
        }
        refreshAllTabs();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
