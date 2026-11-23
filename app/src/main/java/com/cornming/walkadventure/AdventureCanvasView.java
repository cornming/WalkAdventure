package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import java.util.Locale;

/**
 * 冒險分頁主畫布（平滑動畫繪本版）：
 * 支援真實時段漸層天空、柔和太陽／月光脈動、8 顆連續飄落四季微粒、壽星彩旗、
 * 平滑補間經驗條、12 位進化旅伴呼吸律動與小人流暢漫步動畫。
 */
final class AdventureCanvasView extends View {
    private static final float DESIGN_WIDTH = 540f;
    private static final float DESIGN_HEIGHT = 556f;
    private static final long FRAME_INTERVAL_MS = 33L; // ~30fps 節能流暢動畫

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path triangle = new Path();
    private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
    private final StorybookAssets assets;
    private final int bg;
    private final int ink;

    private StepProgress progress;
    private AdventureState state;
    private WorldClock.Snapshot clock = WorldClock.now();
    private String today = "";
    private String companionName = "";
    private String statusSummary = "";
    private boolean statusHighlighted = false;

    private boolean animating = false;
    private long animStartMs = 0L;
    private long lastDrawMs = 0L;
    private float phaseSec = 0f;
    private float displayedXpRatio = -1f;
    private int cachedSkyTod = -1;

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

    AdventureCanvasView(Context context) {
        super(context);
        assets = new StorybookAssets(context);
        bg = context.getColor(R.color.bg);
        ink = context.getColor(R.color.ink);
        setBackgroundColor(bg);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void bind(
        StepProgress progress,
        AdventureState state,
        WorldClock.Snapshot clock,
        String statusSummary,
        boolean statusHighlighted
    ) {
        this.progress = progress;
        this.state = state;
        this.clock = clock != null ? clock : WorldClock.now();
        this.today = this.clock.isoDate;
        this.companionName = state != null ? state.activeCompanionName() : "";
        this.statusSummary = statusSummary != null ? statusSummary : "";
        this.statusHighlighted = statusHighlighted;
        updateContentDescription();
        invalidate();
    }

    void startIdleAnimation() {
        if (animating || UiKit.isReducedMotion(getContext())) return;
        animating = true;
        if (animStartMs == 0L) animStartMs = SystemClock.uptimeMillis();
        removeCallbacks(animFrame);
        postOnAnimation(animFrame);
    }

    void stopIdleAnimation() {
        animating = false;
        removeCallbacks(animFrame);
    }

    @Override protected void onDetachedFromWindow() {
        stopIdleAnimation();
        super.onDetachedFromWindow();
    }

    private void updateContentDescription() {
        if (progress == null || state == null) return;
        int biomeIdx = state.activeDisplayBiomeIndex(progress);
        AdventureCatalog.BiomeDef biome = AdventureCatalog.biome(biomeIdx);
        long todaySteps = progress.todayStepsOn(today);
        int streak = state.effectiveStreak(today);
        String modeLabel = state.family.isFamilyMode() ? "親子出任務模式" : "平日日常模式";
        String enc = state.pendingEncounter != null
            ? "。有新奇遇等待選擇：" + state.pendingEncounter.title
            : "";
        setContentDescription(String.format(
            Locale.TAIWAN,
            "走走冒險，%s，%s %s，出隊小隊 %s，等級 %d %s，旅伴 %s，金幣 %d，星砂 %d，連續達標 %d 天，今日步數 %d，累計真實步數 %d，"
                + "目前顯示第 %d 章 %s，本區進度 %d 之 %d 格。%s%s",
            modeLabel,
            WorldClock.seasonLabel(clock.season),
            WorldClock.timeOfDayLabel(clock.timeOfDay),
            state.family.formatActiveSquadLabel(),
            state.adventureLevel(progress),
            state.currentTitle(progress),
            companionName,
            progress.coins,
            state.camp.starSand,
            streak,
            todaySteps,
            progress.lifetime,
            biome.index + 1,
            biome.name,
            state.displayBiomeLitCells(progress),
            AdventureCatalog.CELLS_PER_BIOME,
            statusSummary,
            enc
        ));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int maxH = Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            476,
            getResources().getDisplayMetrics()
        ));
        int idealH = width > 0 ? Math.min(maxH, Math.round(width * (DESIGN_HEIGHT / DESIGN_WIDTH))) : maxH;
        setMeasuredDimension(width, resolveSize(idealH, heightMeasureSpec));
    }

    private void text(Canvas c, String s, float x, float y, float size, boolean isBold, int color) {
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

    private void drawSkyHeader(Canvas c, float w) {
        if (cachedSkyTod != clock.timeOfDay || skyPaint.getShader() == null) {
            cachedSkyTod = clock.timeOfDay;
            skyPaint.setShader(new LinearGradient(
                0f, 0f, 0f, 198f,
                WorldClock.skyTopRgb(clock.timeOfDay),
                WorldClock.skyBottomRgb(clock.timeOfDay),
                Shader.TileMode.CLAMP
            ));
        }
        c.drawRect(0f, 0f, w, 198f, skyPaint);

        float pulse = (float) Math.sin(phaseSec * 2.2f);
        p.setStyle(Paint.Style.FILL);
        if (clock.timeOfDay == WorldClock.TIME_NIGHT) {
            p.setColor(Color.argb(40, 255, 240, 175));
            c.drawCircle(w - 58f, 34f, 19f + pulse * 2.5f, p);
            p.setColor(Color.rgb(255, 238, 168));
            c.drawCircle(w - 58f, 34f, 13f, p);
            p.setColor(WorldClock.skyTopRgb(clock.timeOfDay));
            c.drawCircle(w - 52f, 30f, 11f, p);
            for (int s = 0; s < 5; s++) {
                float sx = 140f + s * 64f;
                float sy = 16f + ((s * 17) % 34);
                float tw = 1.6f + 1.4f * (float) Math.abs(Math.sin(phaseSec * 2.4f + s * 1.3f));
                p.setColor(Color.argb(215, 255, 246, 190));
                c.drawCircle(sx, sy, tw, p);
            }
        } else {
            int haloColor = clock.timeOfDay == WorldClock.TIME_DUSK
                ? Color.argb(55, 255, 160, 96)
                : Color.argb(55, 255, 224, 118);
            p.setColor(haloColor);
            c.drawCircle(w - 52f, 32f, 21f + pulse * 3f, p);
            p.setColor(clock.timeOfDay == WorldClock.TIME_DUSK ? Color.rgb(245, 122, 66) : Color.rgb(255, 218, 102));
            c.drawCircle(w - 52f, 32f, 14f, p);
        }

        // 8 顆流暢飄浮的四季微粒（春櫻花瓣／夏螢火蟲／秋楓葉／冬雪花）
        for (int i = 0; i < 8; i++) {
            float seed = i * 1.17f;
            float px;
            float py;
            if (clock.season == WorldClock.SEASON_SUMMER) {
                px = 60f + ((i * 68f + (float) Math.sin(phaseSec * 1.1f + seed) * 26f) % (w - 80f));
                py = 22f + (i % 3) * 18f + (float) Math.cos(phaseSec * 1.8f + seed) * 7f;
                int glowAlpha = 110 + (int) (130 * Math.abs(Math.sin(phaseSec * 2.6f + seed)));
                p.setColor(Color.argb(glowAlpha, 255, 242, 128));
                c.drawCircle(px, py, 3.2f, p);
            } else {
                float fallProgress = ((phaseSec * (0.14f + (i % 3) * 0.04f) + i * 0.13f) % 1f);
                py = 8f + fallProgress * 78f;
                px = ((48f + i * 64f + (float) Math.sin(phaseSec * 1.7f + seed) * 18f + fallProgress * 28f) % (w - 40f));
                int alpha = Math.round((1f - Math.abs(fallProgress - 0.5f) * 1.6f) * 195f);
                alpha = Math.max(40, Math.min(200, alpha));
                if (clock.season == WorldClock.SEASON_SPRING) {
                    p.setColor(Color.argb(alpha, 245, 152, 186));
                    c.drawOval(px - 4f, py - 2.4f, px + 4f, py + 2.4f, p);
                } else if (clock.season == WorldClock.SEASON_AUTUMN) {
                    p.setColor(Color.argb(alpha, 224, 122, 48));
                    c.drawOval(px - 4.2f, py - 2.6f, px + 4.2f, py + 2.6f, p);
                } else {
                    p.setColor(Color.argb(alpha, 255, 255, 255));
                    c.drawCircle(px, py, 2.8f, p);
                }
            }
        }

        // 家人壽星慶生彩旗（微風輕輕擺動）
        String bday = state.family.birthdayCelebrantName(clock);
        if (!bday.isEmpty()) {
            int[] flagColors = {0xFFE86688, 0xFFF5BF42, 0xFF548FD6, 0xFF70AF74};
            float sway = (float) Math.sin(phaseSec * 3.2f) * 2.2f;
            for (int i = 0; i < 8; i++) {
                float fx = 28f + i * 62f;
                p.setColor(flagColors[i % flagColors.length]);
                drawTriangle(c, fx, 0f, fx + 24f, 0f, fx + 12f + sway, 14f);
            }
        }
    }

    private void drawExplorerBadge(Canvas c, float cx, float cy, float r, int accentRgb) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255, 245, 224));
        c.drawCircle(cx, cy, r, p);
        p.setColor(accentRgb);
        c.drawCircle(cx, cy, r * 0.8f, p);
        p.setColor(Color.rgb(92, 64, 48));
        rect.set(cx - r * 0.42f, cy - r * 0.44f, cx + r * 0.42f, cy + r * 0.04f);
        c.drawRoundRect(rect, 8, 8, p);
        rect.set(cx - r * 0.54f, cy - r * 0.06f, cx + r * 0.54f, cy + r * 0.1f);
        c.drawRoundRect(rect, 4, 4, p);
        p.setColor(Color.rgb(255, 232, 198));
        c.drawCircle(cx, cy + r * 0.2f, r * 0.3f, p);
    }

    private void drawBiomeScenery(Canvas c, float left, float top, float right, float bottom, int biomeIdx) {
        p.setStyle(Paint.Style.FILL);
        float w = right - left;
        float wave = (float) Math.sin(phaseSec * 2.4f);
        if (biomeIdx == 0) {
            p.setColor(Color.rgb(255, 224, 120));
            c.drawCircle(right - 34, top + 28, 13 + wave * 1.2f, p);
            p.setColor(Color.argb(65, 112, 175, 116));
            c.drawCircle(left + w * 0.25f, bottom + 14, 52, p);
            c.drawCircle(left + w * 0.72f, bottom + 18, 60, p);
        } else if (biomeIdx == 1) {
            p.setColor(Color.argb(65, 62, 132, 88));
            drawTriangle(c, left + 40, bottom - 2, left + 65, top + 46, left + 90, bottom - 2);
            drawTriangle(c, right - 95, bottom - 2, right - 68, top + 40, right - 41, bottom - 2);
            p.setColor(Color.argb(190, 255, 240, 140));
            c.drawCircle(left + 120 + wave * 6f, top + 34 + wave * 3f, 3.2f, p);
            c.drawCircle(right - 130 - wave * 5f, top + 48 - wave * 3f, 2.8f, p);
        } else if (biomeIdx == 2) {
            p.setColor(Color.argb(75, 222, 156, 62));
            drawTriangle(c, right - 125, bottom - 2, right - 88, top + 44, right - 51, bottom - 2);
            p.setColor(Color.rgb(245, 175, 65));
            c.drawCircle(right - 36, top + 28, 14 + wave * 1.2f, p);
        } else if (biomeIdx == 3) {
            p.setColor(Color.argb(80, 96, 152, 222));
            drawTriangle(c, left + 38, bottom - 2, left + 56, top + 42, left + 74, bottom - 2);
            drawTriangle(c, right - 88, bottom - 2, right - 68, top + 38, right - 48, bottom - 2);
            p.setColor(Color.rgb(255, 232, 140));
            float tw = 2.6f + 1.2f * wave;
            c.drawCircle(left + 140, top + 24, tw, p);
            c.drawCircle(right - 120, top + 30, 5.2f - tw, p);
        } else {
            p.setColor(Color.argb(75, 214, 90, 56));
            drawTriangle(c, right - 120, bottom - 2, right - 80, top + 40, right - 40, bottom - 2);
            p.setColor(Color.rgb(255, 198, 92));
            float ember = (phaseSec * 14f) % 18f;
            c.drawCircle(right - 80 + wave * 3f, top + 38 - ember, 3.2f, p);
            c.drawCircle(right - 64 - wave * 3f, top + 30 - ((ember + 9f) % 18f), 2.4f, p);
        }
    }

    private void drawMiniExplorer(Canvas c, float cx, float cy, float bob) {
        float y = cy + bob;
        p.setStyle(Paint.Style.FILL);
        // 腳下柔和投影（隨跳躍縮小）
        p.setColor(Color.argb(45, 58, 42, 32));
        float shadowR = Math.max(4f, 8.5f + bob * 0.45f);
        c.drawOval(cx - shadowR, cy + 12f, cx + shadowR, cy + 16f, p);

        p.setColor(Color.rgb(74, 138, 86));
        c.drawCircle(cx, y + 4, 8.5f, p);
        p.setColor(Color.rgb(255, 232, 198));
        c.drawCircle(cx, y - 7, 7, p);
        p.setColor(Color.rgb(92, 64, 48));
        rect.set(cx - 9, y - 13, cx + 9, y - 10);
        c.drawRoundRect(rect, 2, 2, p);
        rect.set(cx - 6, y - 18, cx + 6, y - 12);
        c.drawRoundRect(rect, 3, 3, p);
    }

    private void drawTriangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        triangle.reset();
        triangle.moveTo(x1, y1);
        triangle.lineTo(x2, y2);
        triangle.lineTo(x3, y3);
        triangle.close();
        c.drawPath(triangle, p);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (progress == null || state == null) return;
        float availableWidth = getWidth();
        float availableHeight = getHeight();
        float scale = Math.min(availableWidth / DESIGN_WIDTH, availableHeight / DESIGN_HEIGHT);
        if (scale <= 0f) return;

        c.save();
        c.scale(scale, scale);
        float w = availableWidth / scale;

        drawSkyHeader(c, w);

        int biomeIdx = state.activeDisplayBiomeIndex(progress);
        AdventureCatalog.BiomeDef biome = AdventureCatalog.biome(biomeIdx);
        long xp = state.xpInLevel(progress);
        float targetXpRatio = Math.max(0f, Math.min(1f, xp / (float) StepProgress.STEPS_PER_CHEST));
        if (displayedXpRatio < 0f || !animating) {
            displayedXpRatio = targetXpRatio;
        } else {
            displayedXpRatio += (targetXpRatio - displayedXpRatio) * 0.22f;
        }
        int streak = state.effectiveStreak(today);
        float petBob = (float) Math.sin(phaseSec * 3.0f) * 3.2f;
        float explorerBob = -(float) Math.abs(Math.sin(phaseSec * 4.2f)) * 5.2f;
        int animTick = (int) (phaseSec * 2f);
        int headerInk = WorldClock.isDarkSky(clock.timeOfDay) ? Color.rgb(255, 247, 232) : ink;

        String envTag = WorldClock.seasonLabel(clock.season) + " · " + WorldClock.timeOfDayLabel(clock.timeOfDay);
        if (!clock.holidayName.isEmpty()) {
            envTag = "🎉 " + clock.holidayName + " · " + WorldClock.timeOfDayLabel(clock.timeOfDay);
        }
        String bday = state.family.birthdayCelebrantName(clock);
        if (!bday.isEmpty()) {
            envTag = "🎂 " + bday + " 生日快樂！";
        }
        text(c, envTag, 24, 34, 15, true, headerInk);

        if (streak > 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255, 222, 168));
            rect.set(24, 42, 128, 66);
            c.drawRoundRect(rect, 12, 12, p);
            drawCenteredText(c, "🔥 連 " + streak + " 天", 76, 54, 12, true, Color.rgb(168, 72, 20));
        }

        String squadPill = (state.family.isFamilyMode() ? "🎈出隊：" : "☀️小隊：") + state.family.formatActiveSquadLabel();
        if (squadPill.length() > 18) squadPill = squadPill.substring(0, 17) + "…";
        p.setStyle(Paint.Style.FILL);
        p.setColor(state.family.isFamilyMode() ? Color.rgb(255, 232, 184) : Color.argb(210, 255, 250, 240));
        float sqLeft = streak > 0 ? 136f : 24f;
        rect.set(sqLeft, 42, Math.min(w - 24f, sqLeft + 250f), 66);
        c.drawRoundRect(rect, 12, 12, p);
        text(c, squadPill, sqLeft + 10f, 58f, 12, true, ink);

        // 角色與進化旅伴卡
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255, 232, 184));
        c.drawRoundRect(20, 76, w - 20, 192, 24, 24, p);
        drawExplorerBadge(c, 58, 134, 26, biome.accentRgb);

        if (state.pioneerBadge) {
            assets.drawIcon(c, assets.pioneerBadgeDrawable(), 80, 156, 22);
        }

        text(c, "Lv." + state.adventureLevel(progress) + " · " + state.currentTitle(progress), 96, 108, 19, true, ink);

        int cid = state.activeCompanionId();
        CompanionSystem.OwnedCompanion oc = state.companions.getOwned(cid);
        String stageNote = (oc != null && cid != CompanionSystem.ID_NONE)
            ? ("（" + CompanionSystem.species(cid).stageTitle(oc.stage) + "）")
            : "";
        text(c, "夥伴：" + companionName + stageNote, 96, 132, 13, false, Color.rgb(104, 78, 60));

        p.setColor(Color.rgb(230, 210, 180));
        c.drawRoundRect(96, 144, w - 98, 160, 8, 8, p);
        if (displayedXpRatio > 0.005f) {
            p.setColor(biome.accentRgb);
            float barRight = Math.min(w - 98f, Math.max(108f, 96f + (w - 194f) * displayedXpRatio));
            c.drawRoundRect(96, 144, barRight, 160, 8, 8, p);
        }
        text(
            c,
            xp + " / " + StepProgress.STEPS_PER_CHEST + " XP · 經驗 +" + state.xpMultiplierPercent() + "% · 金幣 +" + state.coinMultiplierPercent() + "%",
            96, 178, 11.5f, false, ink
        );

        if (cid != CompanionSystem.ID_NONE) {
            int stage = oc != null ? oc.stage : 1;
            boolean shiny = oc != null && oc.shiny;
            int acc = oc != null ? oc.accessory : CompanionSystem.ACC_NONE;
            assets.drawCompanion(c, w - 54f, 136f + petBob, cid, stage, shiny, acc, 1.0f, animTick, ink);
        }

        // 今日真實步數區塊
        text(c, "今日真實步數", 24, 224, 15, true, ink);
        text(c, String.valueOf(progress.todayStepsOn(today)), 24, 268, 42, true, ink);
        text(c, String.format(
            Locale.TAIWAN,
            "累計 %d 步 · 每 1,000 步寶箱（下次 %d）",
            progress.lifetime,
            progress.nextChestAt()
        ), 24, 294, 12.5f, false, Color.rgb(104, 78, 60));

        // 營火盒呼吸脈動提示徽章
        if (state.vault.count() > 0) {
            float vScale = 1f + 0.04f * (float) Math.sin(phaseSec * 4.5f);
            c.save();
            c.translate(w - 95f, 244f);
            c.scale(vScale, vScale);
            p.setColor(Color.rgb(255, 224, 198));
            rect.set(-73f, -20f, 73f, 20f);
            c.drawRoundRect(rect, 14f, 14f, p);
            drawCenteredText(c, "🔥 營火盒 ×" + state.vault.count(), 0f, 0f, 13, true, Color.rgb(176, 62, 32));
            c.restore();
        }

        // 主題島嶼地圖關卡卡片
        p.setColor(biome.tintRgb);
        c.drawRoundRect(20, 310, w - 20, 502, 22, 22, p);
        drawBiomeScenery(c, 20, 310, w - 20, 502, biomeIdx);

        int cycle = state.adventureCycle(progress);
        String chapterHeader = "第 " + (biome.index + 1) + " 章 · " + biome.name
            + (cycle > 1 ? "（第 " + cycle + " 周目）" : "");
        text(c, chapterHeader, 34, 340, 17, true, ink);
        StoryCatalog.BossDef boss = StoryCatalog.bossForBiome(biomeIdx);
        boolean bossCleared = state.stories.isBossCleared(biomeIdx);
        String bossLine = bossCleared
            ? "👑 已安撫守護巨獸「" + boss.name + "」"
            : "🐲 守護巨獸：" + boss.name + "（走滿步數或抬頭／相機尋寶安撫）";
        text(c, bossLine, 34, 362, 12, false, Color.rgb(100, 80, 64));

        int lit = state.displayBiomeLitCells(progress);
        boolean isFrontier = (biomeIdx == state.frontierBiomeIndex(progress));
        float pathY = 434f;
        float stepX = (w - 108f) / (AdventureCatalog.CELLS_PER_BIOME - 1);
        for (int i = 0; i < AdventureCatalog.CELLS_PER_BIOME; i++) {
            float x = 54f + i * stepX;
            if (i < AdventureCatalog.CELLS_PER_BIOME - 1) {
                p.setColor(i < lit ? biome.accentRgb : Color.rgb(206, 190, 168));
                c.drawRect(x + 18, pathY - 3, x + stepX - 18, pathY + 3, p);
            }
            boolean cleared = i < lit;
            boolean isGoal = (i == AdventureCatalog.CELLS_PER_BIOME - 1);
            p.setColor(cleared ? biome.accentRgb : (isGoal ? Color.rgb(245, 191, 66) : Color.rgb(225, 212, 190)));
            c.drawCircle(x, pathY, isGoal ? 22f : 19f, p);
            String nodeGlyph = isGoal ? (bossCleared ? "👑" : "🐲") : (cleared ? "★" : "·");
            int glyphColor = cleared ? Color.rgb(255, 249, 238) : ink;
            drawCenteredText(c, nodeGlyph, x, pathY, isGoal ? 16 : 15, true, glyphColor);
        }

        if (isFrontier) {
            int explorerSlot = Math.min(AdventureCatalog.CELLS_PER_BIOME - 1, lit);
            float ex = 54f + explorerSlot * stepX;
            drawMiniExplorer(c, ex, pathY - 32f, explorerBob);
        }

        String progressLabel = isFrontier
            ? String.format(
                Locale.TAIWAN,
                "本區進度 %d / %d 格 · 再 %d XP 點亮下一格",
                lit,
                AdventureCatalog.CELLS_PER_BIOME,
                Math.max(0L, state.nextNodeAtXp(progress) - state.totalAdventureXp(progress))
            )
            : String.format(Locale.TAIWAN, "已探索完成（%d / %d 格）", lit, AdventureCatalog.CELLS_PER_BIOME);
        text(c, progressLabel, 34, 486, 12.5f, true, ink);

        int statusColor = statusHighlighted ? Color.rgb(180, 62, 34) : Color.rgb(112, 92, 76);
        text(c, statusSummary, 24, 534, 13, statusHighlighted, statusColor);

        c.restore();
    }
}
