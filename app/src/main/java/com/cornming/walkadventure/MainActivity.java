package com.cornming.walkadventure;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int REQ = 20;

    // SharedPreferences 的欄位名稱。前七個沿用 v0.1.0，升級後存檔照樣讀得到。
    private static final String KEY_DAY = "day";
    private static final String KEY_LIFETIME = "lifetime";
    private static final String KEY_COINS = "coins";
    private static final String KEY_CLAIMED_CHEST = "claimed_chest";
    private static final String KEY_EVENT_BUCKET = "event_bucket";
    private static final String KEY_LAST_SENSOR = "last_sensor_steps";
    private static final String KEY_LEGACY_BASELINE = "baseline";
    private static final String KEY_TODAY = "today_steps";
    private static final String KEY_BOOT_COUNT = "boot_count";

    private static final String[] EVENTS = {
        "🧙 遇見旅行 NPC！送你一份祝福",
        "👾 小怪獸出現了！你用腳步擊退牠",
        "🍀 撿到幸運道具！",
        "✨ 神秘光芒照亮前方道路"
    };

    private GameView view;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private SharedPreferences prefs;
    private StepProgress progress;
    private final Random random = new Random();
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("walk_adventure", MODE_PRIVATE);
        progress = loadProgress();
        view = new GameView(this);
        setContentView(view);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        // 權限被拒絕時，點一下畫面可以再問一次
        view.setOnClickListener(v -> { if (!hasStepPermission()) requestStepPermission(); });
        if (!hasStepPermission()) requestStepPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        register();
    }

    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    private boolean hasStepPermission() {
        return Build.VERSION.SDK_INT < 29
            || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStepPermission() {
        if (Build.VERSION.SDK_INT >= 29) requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ);
    }

    // 權限對話框關閉後 Activity 會回到 onResume，所以允許權限後這裡會再跑一次。
    private void register() {
        view.sensorMissing = stepSensor == null;
        view.permissionMissing = stepSensor != null && !hasStepPermission();
        if (!view.sensorMissing && !view.permissionMissing) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        view.invalidate();
    }

    private String today() {
        return dayFormat.format(new Date());
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
        p.lastSensorSteps = prefs.getLong(KEY_LAST_SENSOR, StepProgress.UNKNOWN);
        p.lastBootCount = prefs.getLong(KEY_BOOT_COUNT, StepProgress.UNKNOWN);
        p.day = prefs.getString(KEY_DAY, null);
        p.todaySteps = prefs.contains(KEY_TODAY)
            ? prefs.getLong(KEY_TODAY, 0)
            : StepProgress.todayStepsFromLegacy(p.day, today(), prefs.getFloat(KEY_LEGACY_BASELINE, -1), p.lastSensorSteps);
        return p;
    }

    /** 一次讀數的所有變更合併成一次寫入。 */
    private void saveProgress() {
        prefs.edit()
            .putString(KEY_DAY, progress.day)
            .putLong(KEY_TODAY, progress.todaySteps)
            .putLong(KEY_LIFETIME, progress.lifetime)
            .putLong(KEY_COINS, progress.coins)
            .putLong(KEY_CLAIMED_CHEST, progress.claimedChest)
            .putLong(KEY_EVENT_BUCKET, progress.eventBucket)
            .putLong(KEY_LAST_SENSOR, progress.lastSensorSteps)
            .putLong(KEY_BOOT_COUNT, progress.lastBootCount)
            .remove(KEY_LEGACY_BASELINE)
            .apply();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_STEP_COUNTER) return;
        StepProgress.Update update = progress.onSensorReading((long) e.values[0], today(), bootCount(), random);
        saveProgress();
        if (update.chests > 0) {
            announce(update.chests == 1
                ? "🎁 寶箱！獲得 " + update.chestCoins + " 金幣"
                : "🎁 寶箱 ×" + update.chests + "！獲得 " + update.chestCoins + " 金幣");
        }
        if (update.event) announce(EVENTS[random.nextInt(EVENTS.length)]);
        view.invalidate();
    }

    private void announce(String message) {
        view.message = message;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    class GameView extends View {
        // 畫面以 540 寬的設計座標排版，再依螢幕寬度等比例放大，
        // 不同解析度與像素密度的手機看到的比例才會一樣。
        private static final float DESIGN_WIDTH = 540f;
        private static final float DESIGN_HEIGHT = 690f;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
        private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
        private final int ink = Color.rgb(58, 42, 32);
        String message = "準備出發！每一步都是冒險進度。";
        boolean sensorMissing = false;
        boolean permissionMissing = false;
        // 狀態列、導覽列與瀏海佔掉的邊界（Android 15 起畫面預設延伸到系統列底下）
        private int insetLeft, insetTop, insetRight, insetBottom;

        GameView(Context c) {
            super(c);
            setBackgroundColor(Color.rgb(255, 247, 232));
        }

        @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetLeft = bars.left;
                insetTop = bars.top;
                insetRight = bars.right;
                insetBottom = bars.bottom;
            } else {
                insetLeft = insets.getSystemWindowInsetLeft();
                insetTop = insets.getSystemWindowInsetTop();
                insetRight = insets.getSystemWindowInsetRight();
                insetBottom = insets.getSystemWindowInsetBottom();
            }
            invalidate();
            return super.onApplyWindowInsets(insets);
        }

        void text(Canvas c, String s, float x, float y, float size, boolean isBold) {
            p.setTextSize(size);
            p.setColor(ink);
            p.setTypeface(isBold ? bold : regular);
            c.drawText(s, x, y, p);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float availableWidth = getWidth() - insetLeft - insetRight;
            float availableHeight = getHeight() - insetTop - insetBottom;
            float scale = Math.min(availableWidth / DESIGN_WIDTH, availableHeight / DESIGN_HEIGHT);
            if (scale <= 0) return;
            c.save();
            c.translate(insetLeft, insetTop);
            c.scale(scale, scale);
            // 以下座標都是設計座標；w 是設計座標下的可用寬度
            float w = availableWidth / scale;

            long xp = progress.xp();
            float xpRatio = xp / (float) StepProgress.STEPS_PER_CHEST;
            int lit = progress.litCells();

            text(c, "走走冒險", 28, 55, 30, true);
            text(c, "🪙 " + progress.coins, w - 145, 55, 20, true);

            // 角色卡
            p.setColor(Color.rgb(255, 225, 170));
            c.drawRoundRect(20, 78, w - 20, 190, 28, 28, p);
            text(c, "🧑‍🚀", 38, 143, 55, false);
            text(c, "Lv." + progress.level(), 105, 118, 24, true);
            text(c, "冒險者", 105, 151, 17, false);
            p.setColor(Color.rgb(230, 210, 180));
            c.drawRoundRect(105, 164, w - 45, 181, 10, 10, p);
            p.setColor(Color.rgb(116, 181, 120));
            c.drawRoundRect(105, 164, 105 + (w - 150) * xpRatio, 181, 10, 10, p);
            text(c, xp + " / " + StepProgress.STEPS_PER_CHEST + " XP", 105, 207, 14, false);

            text(c, "今日步數", 25, 250, 18, true);
            text(c, "" + progress.todayStepsOn(today()), 25, 300, 48, true);
            text(c, "每 " + StepProgress.STEPS_PER_CHEST + " 步探索一格地圖", 25, 326, 15, false);

            // 冒險地圖
            text(c, "冒險地圖", 25, 370, 20, true);
            float y = 405;
            for (int i = 0; i < StepProgress.MAP_CELLS; i++) {
                float x = 45 + i * 65;
                p.setColor(i < lit ? Color.rgb(112, 175, 116) : Color.rgb(220, 205, 180));
                c.drawCircle(x, y, 23, p);
                text(c, i < lit ? "★" : "·", x - 8, y + 8, 22, true);
                if (i < StepProgress.MAP_CELLS - 1) {
                    p.setColor(Color.rgb(190, 170, 140));
                    c.drawRect(x + 23, y - 3, x + 42, y + 3, p);
                }
            }
            text(c, "目前探索：" + progress.exploredCells() + " 格", 25, 455, 16, false);
            text(c, "🎁 下一個寶箱：" + progress.nextChestAt() + " 步", 25, 485, 16, false);

            p.setColor(Color.rgb(255, 239, 190));
            c.drawRoundRect(20, 510, w - 20, 585, 20, 20, p);
            text(c, message, 38, 555, 15, true);

            if (sensorMissing) text(c, "此裝置沒有步數感測器，無法取得實際步數。", 25, 625, 14, true);
            else if (permissionMissing) text(c, "尚未允許「活動辨識」權限，無法計步。點一下畫面重新授權。", 25, 625, 14, true);
            else text(c, "步數感測器已連線 • 關閉 App 也會保留進度", 25, 625, 13, false);
            text(c, "今天走得越多，冒險世界就越遠。", 25, 665, 16, true);
            c.restore();
        }
    }
}
