package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.Locale;

/**
 * 互動式親子秘密營地畫布（流暢動畫升級版）：
 * - 營火火焰溫柔跳動＋火星持續緩緩飄升，有待開寶箱時徽章呼吸脈動
 * - 可破殼神祕蛋左右俏皮搖晃＋金光閃爍
 * - 草地旅伴平滑左右漫步，點擊摸摸時飄出 3 顆上升愛心與可愛對話泡泡
 * - 成熟奇幻菜園作物輕微搖曳與星光閃耀
 */
final class CampCanvasView extends View {
    private static final float DESIGN_WIDTH = 540f;
    private static final float DESIGN_HEIGHT = 320f;
    private static final long FRAME_INTERVAL_MS = 33L;

    private static final String[] PET_SPEECHES = {
        "好開心！",
        "散步去！",
        "最喜歡你！",
        "再摸一下~"
    };

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
        new RectF(36f, 138f, 114f, 210f),
        new RectF(120f, 144f, 198f, 214f)
    };
    private final int[] displayedPetIds = {CompanionSystem.ID_NONE, CompanionSystem.ID_NONE};

    private AdventureState state;
    private WorldClock.Snapshot clock = WorldClock.now();
    private OnCampTapListener listener;
    private boolean animating = false;
    private boolean wantAnimating = false;
    private long animStartMs = 0L;
    private long lastDrawMs = 0L;
    private float phaseSec = 0f;
    private int cachedTod = -1;

    private int heartBurstPetId = CompanionSystem.ID_NONE;
    private long heartBurstStartMs = 0L;
    private String heartBurstSpeech = "";
    private int speechSeq = 0;

    private final Runnable animFrame = new Runnable() {
        @Override public void run() {
            if (!animating) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastDrawMs >= FRAME_INTERVAL_MS) {
                lastDrawMs = now;
                phaseSec = (now - animStartMs) / 1000f;
                invalidate();
            }
            postOnAnimation(this);
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
        heartBurstStartMs = SystemClock.uptimeMillis();
        heartBurstSpeech = PET_SPEECHES[(speechSeq++) % PET_SPEECHES.length];
        invalidate();
    }

    void startAnimation() {
        wantAnimating = true;
        resumeFrameLoop();
    }

    private void resumeFrameLoop() {
        if (animating || !wantAnimating || UiKit.isReducedMotion(getContext())) return;
        animating = true;
        if (animStartMs == 0L) animStartMs = SystemClock.uptimeMillis();
        removeCallbacks(animFrame);
        postOnAnimation(animFrame);
    }

    void stopAnimation() {
        wantAnimating = false;
        animating = false;
        removeCallbacks(animFrame);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (wantAnimating) {
            resumeFrameLoop();
        }
    }

    @Override protected void onDetachedFromWindow() {
        animating = false;
        removeCallbacks(animFrame);
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
        int animTick = (int) (phaseSec * 2f);

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

        p.setStyle(Paint.Style.FILL);
        int grassColor = clock.season == WorldClock.SEASON_WINTER
            ? Color.rgb(220, 232, 238)
            : (clock.season == WorldClock.SEASON_AUTUMN ? Color.rgb(218, 206, 156) : Color.rgb(186, 222, 166));
        p.setColor(grassColor);
        rect.set(0f, 118f, w, DESIGN_HEIGHT);
        c.drawRoundRect(rect, 22f, 22f, p);

        int topText = WorldClock.isDarkSky(clock.timeOfDay) ? Color.rgb(255, 247, 232) : ink;
        drawText(c, "⛺ 我們的秘密營地（點營火／旅伴／蛋巢／菜園互動）", 18f, 28f, 13f, true, topText);

        // 上排四座輔助建築
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

        // 左中：悠閒漫步＋可點擊摸摸的旅伴
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < displayedPetIds.length; i++) {
            int pid = displayedPetIds[i];
            if (pid == CompanionSystem.ID_NONE) continue;
            CompanionSystem.OwnedCompanion oc = state.companions.getOwned(pid);
            float strollX = (float) Math.sin(phaseSec * (0.9f + i * 0.35f) + i * 2.1f) * 8.5f;
            float hopY = -(float) Math.abs(Math.sin(phaseSec * (3.2f + i * 0.5f) + i)) * 4.5f;
            float cx = petHits[i].centerX() + strollX;
            float cy = petHits[i].centerY() + hopY;
            if (oc != null) {
                // 柔和腳底陰影
                p.setColor(Color.argb(42, 58, 42, 32));
                c.drawOval(cx - 13f, petHits[i].centerY() + 14f, cx + 13f, petHits[i].centerY() + 20f, p);

                assets.drawCompanion(c, cx, cy, pid, oc.stage, oc.shiny, oc.accessory, 0.92f, animTick, ink);
                drawCenteredText(c, "摸摸", petHits[i].centerX(), petHits[i].bottom + 4f, 10.5f, true, Color.rgb(120, 82, 60));

                // 點擊後 3 顆上升愛心＋可愛對話泡泡（持續 1.6 秒）
                if (heartBurstPetId == pid && heartBurstStartMs > 0L) {
                    float elapsed = (now - heartBurstStartMs) / 1600f;
                    if (elapsed >= 0f && elapsed < 1f) {
                        int alpha = Math.round((1f - elapsed * elapsed) * 255f);
                        drawCenteredText(c, "💖", cx - 14f * elapsed, cy - 22f - elapsed * 24f, 14f, true, Color.argb(alpha, 224, 68, 102));
                        drawCenteredText(c, "💕", cx + 16f * elapsed, cy - 18f - elapsed * 30f, 12f, true, Color.argb(alpha, 236, 98, 134));
                        drawPill(c, heartBurstSpeech, cx, cy - 36f - elapsed * 10f,
                            Color.argb(alpha, 255, 250, 238), Color.argb(alpha, 168, 62, 78));
                    }
                }
            }
        }

        // 若今天透過離線 QR 禮物卡邀請到家人的旅伴串門子，在營火右前方草地熱情亮相！
        if (state.treasure.guestVisit.isActiveOn(clock.isoDate)) {
            TreasureAndQrState.GuestVisit gv = state.treasure.guestVisit;
            float gx = 340f + (float) Math.sin(phaseSec * 1.4f) * 6f;
            float gy = 178f - (float) Math.abs(Math.sin(phaseSec * 3.8f)) * 4f;
            assets.drawCompanion(c, gx, gy, gv.companionId, gv.companionStage, gv.companionShiny,
                CompanionSystem.ACC_NONE, 0.78f, animTick, ink);
            drawPill(c, "💌" + (gv.senderName.length() > 3 ? gv.senderName.substring(0, 3) : gv.senderName) + "作客",
                gx, gy - 26f, Color.rgb(255, 240, 224), Color.rgb(186, 74, 48));
        }

        // 中央：營火廣場（火焰輕微呼吸＋4 顆上升火星＋待開箱脈動徽章）
        float fireGlow = 39f + (float) Math.sin(phaseSec * 5.2f) * 3.5f;
        p.setColor(Color.argb(72, 255, 194, 96));
        c.drawCircle(campfireHit.centerX(), campfireHit.centerY(), fireGlow + 7f, p);
        p.setColor(Color.argb(165, 255, 242, 210));
        c.drawCircle(campfireHit.centerX(), campfireHit.centerY(), fireGlow, p);

        float fireScale = 1f + (float) Math.sin(phaseSec * 6.5f) * 0.045f;
        c.save();
        c.translate(campfireHit.centerX(), campfireHit.centerY() - 2f);
        c.scale(fireScale, fireScale);
        assets.drawIcon(c, assets.drawableForBuilding(CampState.BLD_CAMPFIRE), 0f, 0f, 56f);
        c.restore();

        // 營火火星緩緩飄往上方
        for (int e = 0; e < 4; e++) {
            float et = (phaseSec * 0.65f + e * 0.25f) % 1f;
            float ex = campfireHit.centerX() + (float) Math.sin(phaseSec * 3.8f + e * 1.9f) * 12f;
            float ey = campfireHit.centerY() - 14f - et * 38f;
            int ea = Math.round((1f - et) * 215f);
            p.setColor(Color.argb(ea, 255, 198, 72));
            c.drawCircle(ex, ey, 2.8f * (1f - et * 0.4f), p);
        }

        if (state.vault.count() > 0) {
            float badgeScale = 1f + 0.06f * (float) Math.sin(phaseSec * 5.0f);
            c.save();
            c.translate(campfireHit.centerX(), campfireHit.bottom - 2f);
            c.scale(badgeScale, badgeScale);
            drawPill(c, "🔥開箱 ×" + state.vault.count(), 0f, 0f, Color.rgb(218, 92, 58), Color.rgb(255, 247, 232));
            c.restore();
        } else {
            drawPill(c, "營火 Lv." + state.camp.buildingLevel(CampState.BLD_CAMPFIRE), campfireHit.centerX(), campfireHit.bottom - 2f,
                Color.rgb(255, 244, 216), ink);
        }

        // 右中：孵蛋巢（可破殼的蛋俏皮左右搖晃！）
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
                float ey = eggNestHit.centerY() - 6f;
                c.save();
                c.translate(ex, ey);
                if (slot.isReadyToHatch()) {
                    float wobbleDeg = (float) Math.sin(phaseSec * 9.5f + i) * 14f;
                    c.rotate(wobbleDeg);
                } else {
                    float gentle = (float) Math.sin(phaseSec * 2.2f + i) * 3f;
                    c.rotate(gentle);
                }
                assets.drawIcon(c, assets.eggDrawable(), 0f, 0f, 40f);
                c.restore();

                if (slot.isReadyToHatch()) {
                    float spAlpha = 140f + 115f * (float) Math.abs(Math.sin(phaseSec * 5f + i));
                    drawCenteredText(c, "✨", ex + 18f, ey - 14f, 12f, true, Color.argb(Math.round(spAlpha), 235, 162, 32));
                }
                String tag = slot.isReadyToHatch() ? "破殼!" : Math.round(slot.progressRatio() * 100f) + "%";
                drawCenteredText(
                    c, tag, ex, eggNestHit.bottom - 12f, 11f, true,
                    slot.isReadyToHatch() ? Color.rgb(218, 92, 58) : ink
                );
            }
        }

        // 下排：3 格奇幻小菜園（成熟作物柔和搖曳＋閃亮星光）
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
                c.save();
                c.translate(box.left + 32f, box.centerY());
                if (plot.isReady()) {
                    c.rotate((float) Math.sin(phaseSec * 4.2f + i) * 6f);
                }
                assets.drawIcon(c, assets.drawableForCrop(plot.seedId, plot.isReady()), 0f, 0f, 42f);
                c.restore();

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
