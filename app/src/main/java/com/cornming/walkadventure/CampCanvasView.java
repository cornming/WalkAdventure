package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.Locale;

/**
 * 互動式親子秘密營地畫布（2.0 新增）：
 * 結合真實時段天空、CC0 繪本建築圖示、中央營火（點擊開箱）、孵蛋巢（點擊暖蛋／破殼）、
 * 3 格奇幻菜園（點擊種植／採收）與在草地漫步的旅伴（點擊摸摸冒愛心）。
 */
final class CampCanvasView extends View {
    private static final float DESIGN_WIDTH = 540f;
    private static final float DESIGN_HEIGHT = 320f;

    interface OnCampTapListener {
        void onCampfireTapped();
        void onEggNestTapped();
        void onGardenPlotTapped(int slot);
        void onCompanionTapped(int companionId);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
    private final StorybookAssets assets;
    private final int ink;

    private final RectF campfireHit = new RectF(220f, 128f, 320f, 212f);
    private final RectF eggNestHit = new RectF(366f, 140f, 514f, 216f);
    private final RectF[] gardenHits = {
        new RectF(26f, 228f, 178f, 304f),
        new RectF(192f, 228f, 344f, 304f),
        new RectF(358f, 228f, 514f, 304f)
    };
    private final RectF[] petHits = {
        new RectF(40f, 140f, 112f, 206f),
        new RectF(122f, 146f, 194f, 212f)
    };
    private final int[] displayedPetIds = {CompanionSystem.ID_NONE, CompanionSystem.ID_NONE};

    private AdventureState state;
    private WorldClock.Snapshot clock = WorldClock.now();
    private OnCampTapListener listener;
    private int animTick = 0;
    private boolean animating = false;
    private int cachedTod = -1;
    private int heartBurstPetId = CompanionSystem.ID_NONE;
    private int heartBurstTicks = 0;

    private final Runnable animStep = new Runnable() {
        @Override public void run() {
            if (!animating) return;
            animTick = (animTick + 1) % 60;
            if (heartBurstTicks > 0) heartBurstTicks--;
            invalidate();
            postDelayed(this, 600);
        }
    };

    CampCanvasView(Context context) {
        super(context);
        assets = new StorybookAssets(context);
        ink = context.getColor(R.color.ink);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setOnCampTapListener(OnCampTapListener listener) {
        this.listener = listener;
    }

    void bind(AdventureState state, WorldClock.Snapshot clock) {
        this.state = state;
        this.clock = clock != null ? clock : WorldClock.now();
        refreshDisplayedPets();
        updateAccessibility();
        invalidate();
    }

    void triggerHeartOnCompanion(int companionId) {
        heartBurstPetId = companionId;
        heartBurstTicks = 4;
        invalidate();
    }

    void startAnimation() {
        if (animating) return;
        animating = true;
        removeCallbacks(animStep);
        postDelayed(animStep, 600);
    }

    void stopAnimation() {
        animating = false;
        removeCallbacks(animStep);
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void refreshDisplayedPets() {
        displayedPetIds[0] = CompanionSystem.ID_NONE;
        displayedPetIds[1] = CompanionSystem.ID_NONE;
        if (state == null) return;
        int active = state.activeCompanionId();
        int idx = 0;
        if (active != CompanionSystem.ID_NONE && state.companions.isOwned(active)) {
            displayedPetIds[idx++] = active;
        }
        List<CompanionSystem.OwnedCompanion> all = state.companions.ownedList();
        for (CompanionSystem.OwnedCompanion oc : all) {
            if (idx >= 2) break;
            if (oc.id != active) {
                displayedPetIds[idx++] = oc.id;
            }
        }
    }

    private void updateAccessibility() {
        if (state == null) return;
        setContentDescription(String.format(
            Locale.TAIWAN,
            "親子秘密營地畫布。營火盒待開 %d 件，孵蛋巢 %d 顆蛋，奇幻菜園成熟 %d 格，已建造設施總等級 %d。可點擊營火、蛋巢、菜園或旅伴互動。",
            state.vault.count(),
            state.companions.eggs.size(),
            state.camp.readyGardenCount(),
            state.camp.totalBuildingLevels()
        ));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int maxH = Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            268,
            getResources().getDisplayMetrics()
        ));
        int idealH = width > 0 ? Math.min(maxH, Math.round(width * (DESIGN_HEIGHT / DESIGN_WIDTH))) : maxH;
        setMeasuredDimension(width, resolveSize(idealH, heightMeasureSpec));
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || listener == null || state == null) {
            return true;
        }
        float scale = Math.min(getWidth() / DESIGN_WIDTH, getHeight() / DESIGN_HEIGHT);
        if (scale <= 0f) return true;
        float lx = event.getX() / scale;
        float ly = event.getY() / scale;

        if (campfireHit.contains(lx, ly)) {
            performClick();
            listener.onCampfireTapped();
            return true;
        }
        if (eggNestHit.contains(lx, ly)) {
            performClick();
            listener.onEggNestTapped();
            return true;
        }
        for (int i = 0; i < gardenHits.length; i++) {
            if (gardenHits[i].contains(lx, ly)) {
                performClick();
                listener.onGardenPlotTapped(i);
                return true;
            }
        }
        for (int i = 0; i < petHits.length; i++) {
            if (displayedPetIds[i] != CompanionSystem.ID_NONE && petHits[i].contains(lx, ly)) {
                performClick();
                triggerHeartOnCompanion(displayedPetIds[i]);
                listener.onCompanionTapped(displayedPetIds[i]);
                return true;
            }
        }
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (state == null) return;
        float scale = Math.min(getWidth() / DESIGN_WIDTH, getHeight() / DESIGN_HEIGHT);
        if (scale <= 0f) return;

        c.save();
        c.scale(scale, scale);
        float w = getWidth() / scale;

        // 圓角卡片底景與真實時段天空
        if (cachedTod != clock.timeOfDay || skyPaint.getShader() == null) {
            cachedTod = clock.timeOfDay;
            skyPaint.setShader(new LinearGradient(
                0f, 0f, 0f, 150f,
                WorldClock.skyTopRgb(clock.timeOfDay),
                WorldClock.skyBottomRgb(clock.timeOfDay),
                Shader.TileMode.CLAMP
            ));
        }
        rect.set(0f, 0f, w, DESIGN_HEIGHT);
        c.drawRoundRect(rect, 22f, 22f, skyPaint);

        // 溫暖草地
        p.setStyle(Paint.Style.FILL);
        int grassColor = clock.season == WorldClock.SEASON_WINTER
            ? Color.rgb(220, 232, 238)
            : (clock.season == WorldClock.SEASON_AUTUMN ? Color.rgb(218, 206, 156) : Color.rgb(186, 222, 166));
        p.setColor(grassColor);
        rect.set(0f, 118f, w, DESIGN_HEIGHT);
        c.drawRoundRect(rect, 22f, 22f, p);

        // 頂部標題
        int topText = WorldClock.isDarkSky(clock.timeOfDay) ? Color.rgb(255, 247, 232) : ink;
        drawText(c, "⛺ 我們的秘密營地（點營火／旅伴／菜園互動）", 18f, 28f, 13.5f, true, topText);

        // 上排四座輔助建築（旅伴樂園、星光觀景台、回憶畫廊、探險工坊）
        int[] topBlds = {
            CampState.BLD_PLAYGROUND,
            CampState.BLD_OBSERVATORY,
            CampState.BLD_GALLERY,
            CampState.BLD_WORKSHOP
        };
        float[] bldXs = {76f, 206f, 336f, 464f};
        for (int i = 0; i < topBlds.length; i++) {
            int bldId = topBlds[i];
            int lv = state.camp.buildingLevel(bldId);
            float cx = bldXs[i];
            if (lv > 0) {
                assets.drawIcon(c, assets.drawableForBuilding(bldId), cx, 78f, 52f);
                drawPill(c, CampState.buildingDef(bldId).name.substring(0, 2) + " Lv." + lv, cx, 112f, Color.rgb(255, 246, 224), ink);
            } else {
                p.setColor(Color.argb(120, 255, 247, 232));
                rect.set(cx - 34f, 56f, cx + 34f, 102f);
                c.drawRoundRect(rect, 12f, 12f, p);
                drawCenteredText(c, "待建造", cx, 78f, 11.5f, true, Color.rgb(122, 100, 82));
                drawCenteredText(c, CampState.buildingDef(bldId).name.substring(0, 4), cx, 96f, 10f, false, Color.rgb(122, 100, 82));
            }
        }

        // 左中：在營地散步的旅伴（可點摸摸）
        float bob = (animTick % 2 == 0) ? 0f : -3f;
        for (int i = 0; i < displayedPetIds.length; i++) {
            int pid = displayedPetIds[i];
            if (pid == CompanionSystem.ID_NONE) continue;
            CompanionSystem.OwnedCompanion oc = state.companions.getOwned(pid);
            float cx = petHits[i].centerX();
            float cy = petHits[i].centerY() + bob;
            if (oc != null) {
                assets.drawCompanion(c, cx, cy, pid, oc.stage, oc.shiny, oc.accessory, 0.92f, animTick, ink);
                drawCenteredText(c, "摸摸", cx, petHits[i].bottom + 6f, 10.5f, true, Color.rgb(120, 82, 60));
                if (heartBurstTicks > 0 && heartBurstPetId == pid) {
                    drawCenteredText(c, "💖", cx, cy - 26f - (4 - heartBurstTicks) * 3f, 16f, true, Color.rgb(220, 68, 98));
                }
            }
        }

        // 中央：營火廣場（點我開箱）
        p.setColor(Color.argb(165, 255, 242, 210));
        c.drawCircle(campfireHit.centerX(), campfireHit.centerY(), 40f, p);
        assets.drawIcon(c, assets.drawableForBuilding(CampState.BLD_CAMPFIRE), campfireHit.centerX(), campfireHit.centerY() - 2f, 56f);
        if (state.vault.count() > 0) {
            drawPill(c, "🔥開箱 ×" + state.vault.count(), campfireHit.centerX(), campfireHit.bottom - 2f,
                Color.rgb(218, 92, 58), Color.rgb(255, 247, 232));
        } else {
            drawPill(c, "營火 Lv." + state.camp.buildingLevel(CampState.BLD_CAMPFIRE), campfireHit.centerX(), campfireHit.bottom - 2f,
                Color.rgb(255, 244, 216), ink);
        }

        // 右中：孵蛋巢
        p.setColor(Color.argb(180, 255, 248, 232));
        c.drawRoundRect(eggNestHit, 16f, 16f, p);
        if (state.companions.eggs.isEmpty()) {
            drawCenteredText(c, "🪹 孵蛋巢空著", eggNestHit.centerX(), eggNestHit.centerY() - 4f, 12.5f, true, Color.rgb(122, 100, 82));
            drawCenteredText(c, "散步或種菜撿蛋", eggNestHit.centerX(), eggNestHit.centerY() + 14f, 10.5f, false, Color.rgb(122, 100, 82));
        } else {
            int eggCount = state.companions.eggs.size();
            for (int i = 0; i < eggCount; i++) {
                CompanionSystem.EggSlot slot = state.companions.eggs.get(i);
                float ex = eggCount == 1 ? eggNestHit.centerX() : (eggNestHit.left + 40f + i * 68f);
                float ey = eggNestHit.centerY() - 6f + (slot.isReadyToHatch() ? bob * 1.4f : 0f);
                assets.drawIcon(c, assets.eggDrawable(), ex, ey, 40f);
                String tag = slot.isReadyToHatch() ? "破殼!" : Math.round(slot.progressRatio() * 100f) + "%";
                drawCenteredText(
                    c,
                    tag,
                    ex,
                    eggNestHit.bottom - 12f,
                    11f,
                    true,
                    slot.isReadyToHatch() ? Color.rgb(218, 92, 58) : ink
                );
            }
        }

        // 下排：3 格奇幻小菜園
        int unlockedSlots = state.camp.unlockedGardenSlots();
        for (int i = 0; i < CampState.GARDEN_PLOT_COUNT; i++) {
            RectF box = gardenHits[i];
            CampState.GardenPlot plot = state.camp.gardenPlots[i];
            boolean unlocked = i < unlockedSlots;
            p.setColor(unlocked ? Color.rgb(255, 250, 236) : Color.argb(135, 230, 218, 198));
            c.drawRoundRect(box, 14f, 14f, p);
            if (!unlocked) {
                drawCenteredText(c, "🔒 菜園 Lv." + (i + 1) + " 解鎖", box.centerX(), box.centerY(), 11.5f, true, Color.rgb(120, 102, 86));
            } else if (plot.isEmpty()) {
                drawCenteredText(c, "🌱 " + (i + 1) + "號空花圃", box.centerX(), box.centerY() - 8f, 12f, true, ink);
                drawCenteredText(c, "點擊種下種子", box.centerX(), box.centerY() + 12f, 11f, false, Color.rgb(72, 124, 76));
            } else {
                assets.drawIcon(c, assets.drawableForCrop(plot.seedId, plot.isReady()), box.left + 32f, box.centerY(), 42f);
                String cropName = CampState.seedDef(plot.seedId).name.replace("種子", "");
                drawText(c, cropName, box.left + 58f, box.top + 30f, 12f, true, ink);
                String prog = plot.isReady()
                    ? "✨ 點擊採收！"
                    : (Math.round(plot.progressRatio() * 100f) + "% 澆水中");
                drawText(c, prog, box.left + 58f, box.top + 52f, 11f, plot.isReady(),
                    plot.isReady() ? Color.rgb(46, 107, 52) : Color.rgb(112, 92, 76));
            }
        }

        c.restore();
    }

    private void drawPill(Canvas c, String label, float cx, float cy, int bgColor, int fgColor) {
        p.setTextSize(11f);
        p.setTypeface(bold);
        float tw = p.measureText(label);
        float halfW = Math.max(30f, tw * 0.5f + 10f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bgColor);
        rect.set(cx - halfW, cy - 11f, cx + halfW, cy + 11f);
        c.drawRoundRect(rect, 11f, 11f, p);
        drawCenteredText(c, label, cx, cy, 11f, true, fgColor);
    }

    private void drawText(Canvas c, String s, float x, float y, float size, boolean isBold, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(isBold ? bold : regular);
        c.drawText(s, x, y, p);
    }

    private void drawCenteredText(Canvas c, String s, float cx, float cy, float size, boolean isBold, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(isBold ? bold : regular);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) * 0.5f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }
}
