package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import java.util.Locale;

/**
 * 冒險分頁的主畫布：顯示冒險者卡片、裝備夥伴頭像、連勝火焰、真實步數與五大主題地圖與小人走路動畫。
 */
final class AdventureCanvasView extends View {
    private static final float DESIGN_WIDTH = 540f;
    private static final float DESIGN_HEIGHT = 536f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path triangle = new Path();
    private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
    private final int bg;
    private final int ink;

    private StepProgress progress;
    private AdventureState state;
    private String today = "";
    private String companionName = "";
    private String statusSummary = "";
    private boolean statusHighlighted = false;
    private boolean animating = false;
    private int animTick = 0;

    private final Runnable animStep = new Runnable() {
        @Override public void run() {
            if (!animating) return;
            animTick = (animTick + 1) % 60;
            invalidate();
            postDelayed(this, 650);
        }
    };

    AdventureCanvasView(Context context) {
        super(context);
        bg = context.getColor(R.color.bg);
        ink = context.getColor(R.color.ink);
        setBackgroundColor(bg);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void bind(
        StepProgress progress,
        AdventureState state,
        String today,
        String statusSummary,
        boolean statusHighlighted
    ) {
        this.progress = progress;
        this.state = state;
        this.today = today != null ? today : "";
        this.companionName = AdventureCatalog.companion(state.equippedCompanion).name;
        this.statusSummary = statusSummary != null ? statusSummary : "";
        this.statusHighlighted = statusHighlighted;
        updateContentDescription();
        invalidate();
    }

    void startIdleAnimation() {
        if (animating) return;
        animating = true;
        removeCallbacks(animStep);
        postDelayed(animStep, 650);
    }

    void stopIdleAnimation() {
        animating = false;
        removeCallbacks(animStep);
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
        String enc = state.pendingEncounter != null
            ? "。有新奇遇等待選擇：" + state.pendingEncounter.title
            : "";
        setContentDescription(String.format(
            Locale.TAIWAN,
            "走走冒險，等級 %d %s，旅伴 %s，金幣 %d，連續達標 %d 天，今日步數 %d，累計真實步數 %d，"
                + "目前顯示第 %d 章 %s，本區進度 %d 之 %d 格。%s%s",
            state.adventureLevel(progress),
            state.currentTitle(progress),
            companionName,
            progress.coins,
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
            460,
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

    private void drawCoinIcon(Canvas c, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(235, 178, 42));
        c.drawCircle(cx, cy, r, p);
        p.setColor(Color.rgb(255, 224, 112));
        c.drawCircle(cx, cy, r * 0.72f, p);
        p.setColor(Color.rgb(184, 126, 18));
        rect.set(cx - r * 0.14f, cy - r * 0.36f, cx + r * 0.14f, cy + r * 0.36f);
        c.drawRoundRect(rect, 2, 2, p);
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

    private void drawCompanionSprite(Canvas c, float cx, float cy, int companionId, float bobOffset) {
        if (companionId == AdventureCatalog.COMPANION_NONE) return;
        float y = cy + bobOffset;
        p.setStyle(Paint.Style.FILL);
        switch (companionId) {
            case AdventureCatalog.COMPANION_SHIBA:
                // 柴犬臉與垂耳
                p.setColor(Color.rgb(225, 146, 66));
                drawTriangle(c, cx - 15, y - 4, cx - 18, y - 20, cx - 4, y - 13);
                drawTriangle(c, cx + 15, y - 4, cx + 18, y - 20, cx + 4, y - 13);
                c.drawCircle(cx, y, 16, p);
                p.setColor(Color.rgb(255, 244, 222));
                c.drawCircle(cx, y + 4, 11, p);
                p.setColor(ink);
                c.drawCircle(cx - 5, y - 2, 2.2f, p);
                c.drawCircle(cx + 5, y - 2, 2.2f, p);
                c.drawCircle(cx, y + 3, 2.6f, p);
                break;

            case AdventureCatalog.COMPANION_SLIME:
                // 小綠史萊姆
                p.setColor(Color.rgb(98, 189, 108));
                rect.set(cx - 17, y - 12, cx + 17, y + 14);
                c.drawRoundRect(rect, 15, 14, p);
                p.setColor(Color.rgb(174, 236, 179));
                c.drawCircle(cx - 6, y - 6, 3.5f, p);
                p.setColor(ink);
                c.drawCircle(cx - 5, y + 1, 2.3f, p);
                c.drawCircle(cx + 5, y + 1, 2.3f, p);
                break;

            case AdventureCatalog.COMPANION_OWL:
                // 貓頭鷹波波
                p.setColor(Color.rgb(140, 108, 86));
                c.drawCircle(cx, y + 1, 15, p);
                p.setColor(Color.rgb(255, 245, 224));
                c.drawCircle(cx - 5, y - 3, 5.5f, p);
                c.drawCircle(cx + 5, y - 3, 5.5f, p);
                p.setColor(ink);
                c.drawCircle(cx - 5, y - 3, 2.2f, p);
                c.drawCircle(cx + 5, y - 3, 2.2f, p);
                p.setColor(Color.rgb(235, 162, 46));
                drawTriangle(c, cx - 3, y + 1, cx + 3, y + 1, cx, y + 6);
                break;

            case AdventureCatalog.COMPANION_DRAGON:
            default:
                // 迷你火龍
                p.setColor(Color.rgb(245, 182, 54));
                drawTriangle(c, cx - 18, y - 3, cx - 27, y - 13, cx - 10, y - 11);
                drawTriangle(c, cx + 18, y - 3, cx + 27, y - 13, cx + 10, y - 11);
                p.setColor(Color.rgb(218, 92, 58));
                c.drawCircle(cx, y, 15, p);
                p.setColor(Color.rgb(255, 219, 128));
                drawTriangle(c, cx - 9, y - 12, cx - 5, y - 22, cx - 2, y - 13);
                drawTriangle(c, cx + 9, y - 12, cx + 5, y - 22, cx + 2, y - 13);
                p.setColor(ink);
                c.drawCircle(cx - 5, y - 2, 2.2f, p);
                c.drawCircle(cx + 5, y - 2, 2.2f, p);
                break;
        }
    }

    private void drawBiomeScenery(Canvas c, float left, float top, float right, float bottom, int biomeIdx) {
        p.setStyle(Paint.Style.FILL);
        float w = right - left;
        if (biomeIdx == 0) {
            p.setColor(Color.rgb(255, 224, 120));
            c.drawCircle(right - 34, top + 28, 13, p);
            p.setColor(Color.argb(65, 112, 175, 116));
            c.drawCircle(left + w * 0.25f, bottom + 14, 52, p);
            c.drawCircle(left + w * 0.72f, bottom + 18, 60, p);
        } else if (biomeIdx == 1) {
            p.setColor(Color.argb(65, 62, 132, 88));
            drawTriangle(c, left + 40, bottom - 2, left + 65, top + 46, left + 90, bottom - 2);
            drawTriangle(c, right - 95, bottom - 2, right - 68, top + 40, right - 41, bottom - 2);
            p.setColor(Color.argb(180, 255, 240, 140));
            float sparkleOffset = (animTick % 2 == 0) ? 0f : 3f;
            c.drawCircle(left + 120, top + 34 + sparkleOffset, 3, p);
            c.drawCircle(right - 130, top + 48 - sparkleOffset, 3, p);
        } else if (biomeIdx == 2) {
            p.setColor(Color.argb(75, 222, 156, 62));
            drawTriangle(c, right - 125, bottom - 2, right - 88, top + 44, right - 51, bottom - 2);
            p.setColor(Color.rgb(245, 175, 65));
            c.drawCircle(right - 36, top + 28, 14, p);
        } else if (biomeIdx == 3) {
            p.setColor(Color.argb(80, 96, 152, 222));
            drawTriangle(c, left + 38, bottom - 2, left + 56, top + 42, left + 74, bottom - 2);
            drawTriangle(c, right - 88, bottom - 2, right - 68, top + 38, right - 48, bottom - 2);
            p.setColor(Color.rgb(255, 232, 140));
            float tw = (animTick % 2 == 0) ? 3.5f : 2f;
            c.drawCircle(left + 140, top + 24, tw, p);
            c.drawCircle(right - 120, top + 30, 5f - tw, p);
        } else {
            p.setColor(Color.argb(75, 214, 90, 56));
            drawTriangle(c, right - 120, bottom - 2, right - 80, top + 40, right - 40, bottom - 2);
            p.setColor(Color.rgb(255, 198, 92));
            float ember = (animTick % 2 == 0) ? -2f : 2f;
            c.drawCircle(right - 80, top + 32 + ember, 3.2f, p);
            c.drawCircle(right - 64, top + 24 - ember, 2.4f, p);
        }
    }

    private void drawMiniExplorer(Canvas c, float cx, float cy, float bob) {
        float y = cy + bob;
        p.setStyle(Paint.Style.FILL);
        // 身體披風
        p.setColor(Color.rgb(74, 138, 86));
        c.drawCircle(cx, y + 4, 8.5f, p);
        // 頭部
        p.setColor(Color.rgb(255, 232, 198));
        c.drawCircle(cx, y - 7, 7, p);
        // 探險帽
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

        int biomeIdx = state.activeDisplayBiomeIndex(progress);
        AdventureCatalog.BiomeDef biome = AdventureCatalog.biome(biomeIdx);
        long xp = state.xpInLevel(progress);
        float xpRatio = Math.max(0f, Math.min(1f, xp / (float) StepProgress.STEPS_PER_CHEST));
        int streak = state.effectiveStreak(today);
        float bob = (animTick % 2 == 0) ? 0f : -2.5f;

        // 頂部標題與金幣／連勝狀態列
        text(c, "走走冒險", 24, 46, 28, true, ink);
        if (streak > 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(255, 222, 168));
            rect.set(148, 22, 250, 50);
            c.drawRoundRect(rect, 14, 14, p);
            drawCenteredText(c, "🔥 連 " + streak + " 天", 199, 36, 13, true, Color.rgb(168, 72, 20));
        }

        String coinText = String.valueOf(progress.coins);
        p.setTextSize(20);
        p.setTypeface(bold);
        float coinWidth = p.measureText(coinText);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(ink);
        c.drawText(coinText, w - 24, 45, p);
        p.setTextAlign(Paint.Align.LEFT);
        drawCoinIcon(c, w - 24 - coinWidth - 16, 38, 11);

        // 角色與夥伴卡
        p.setColor(Color.rgb(255, 225, 170));
        c.drawRoundRect(20, 62, w - 20, 174, 24, 24, p);
        drawExplorerBadge(c, 60, 118, 27, biome.accentRgb);
        text(c, "Lv." + state.adventureLevel(progress) + " · " + state.currentTitle(progress), 100, 96, 20, true, ink);

        String subPerk = "夥伴：" + companionName
            + " · 經驗 +" + state.xpMultiplierPercent() + "% / 金幣 +" + state.coinMultiplierPercent() + "%";
        text(c, subPerk, 100, 122, 13, false, Color.rgb(104, 78, 60));

        p.setColor(Color.rgb(230, 210, 180));
        c.drawRoundRect(100, 134, w - 92, 150, 8, 8, p);
        if (xpRatio > 0f) {
            p.setColor(biome.accentRgb);
            float barRight = Math.min(w - 92f, Math.max(112f, 100f + (w - 192f) * xpRatio));
            c.drawRoundRect(100, 134, barRight, 150, 8, 8, p);
        }
        text(c, xp + " / " + StepProgress.STEPS_PER_CHEST + " XP", 100, 166, 12, false, ink);
        drawCompanionSprite(c, w - 54, 124, state.equippedCompanion, bob);

        // 今日真實步數區塊
        text(c, "今日真實步數", 24, 210, 16, true, ink);
        text(c, String.valueOf(progress.todayStepsOn(today)), 24, 256, 44, true, ink);
        String lifeText = String.format(
            Locale.TAIWAN,
            "累計 %d 步 · 每 1,000 步寶箱（下次 %d）",
            progress.lifetime,
            progress.nextChestAt()
        );
        text(c, lifeText, 24, 282, 13, false, Color.rgb(104, 78, 60));

        // 主題地圖關卡卡片
        p.setColor(biome.tintRgb);
        c.drawRoundRect(20, 300, w - 20, 488, 22, 22, p);
        drawBiomeScenery(c, 20, 300, w - 20, 488, biomeIdx);

        int cycle = state.adventureCycle(progress);
        String chapterHeader = "第 " + (biome.index + 1) + " 章 · " + biome.name
            + (cycle > 1 ? "（第 " + cycle + " 周目）" : "");
        text(c, chapterHeader, 34, 330, 17, true, ink);
        text(c, "地標：" + biome.landmark + " · " + biome.description, 34, 353, 12, false, Color.rgb(100, 80, 64));

        // 8 格探索路徑與小人動畫
        int lit = state.displayBiomeLitCells(progress);
        boolean isFrontier = (biomeIdx == state.frontierBiomeIndex(progress));
        float pathY = 422f;
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
            String nodeGlyph = cleared ? "★" : (isGoal ? "⚑" : "·");
            drawCenteredText(
                c,
                nodeGlyph,
                x,
                pathY,
                18,
                true,
                cleared ? Color.rgb(255, 250, 240) : ink
            );
        }

        if (isFrontier) {
            int heroSlot = Math.min(AdventureCatalog.CELLS_PER_BIOME - 1, lit);
            float heroX = 54f + heroSlot * stepX;
            drawMiniExplorer(c, heroX, pathY - 26f, bob);
        }

        String mapFoot = isFrontier
            ? "本區進度 " + lit + " / " + AdventureCatalog.CELLS_PER_BIOME + " 格 · 距離下格還差 "
                + (StepProgress.STEPS_PER_CHEST - xp) + " XP"
            : "本區已全數探索完成！可切換回前線章節繼續前進";
        text(c, mapFoot, 34, 472, 13, true, ink);

        // 感測器或權限狀態提示
        text(
            c,
            statusSummary,
            24,
            516,
            statusHighlighted ? 14 : 12,
            statusHighlighted,
            statusHighlighted ? Color.rgb(176, 68, 32) : Color.rgb(104, 78, 60)
        );

        c.restore();
    }
}
