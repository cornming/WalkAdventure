package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

/**
 * CC0 繪本素材統一載入與繪製工具：
 * 1) 載入並快取 res/drawable/sb_*.xml（或可抽換的 Kenney CC0 PNG）
 * 2) 統一繪製 12 位旅伴（支援 3 階段進化、閃亮異色光暈與可穿戴飾品）與家庭成員小頭像，
 *    供 AdventureCanvasView 與 CampCanvasView 共用。
 */
final class StorybookAssets {
    private final Context context;
    private final SparseArray<Drawable> cache = new SparseArray<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    StorybookAssets(Context context) {
        this.context = context.getApplicationContext();
    }

    Drawable drawableForBuilding(int buildingId) {
        int resId;
        switch (buildingId) {
            case CampState.BLD_CAMPFIRE: resId = R.drawable.sb_campfire; break;
            case CampState.BLD_GARDEN: resId = R.drawable.sb_garden; break;
            case CampState.BLD_PLAYGROUND: resId = R.drawable.sb_playground; break;
            case CampState.BLD_OBSERVATORY: resId = R.drawable.sb_observatory; break;
            case CampState.BLD_GALLERY: resId = R.drawable.sb_gallery; break;
            case CampState.BLD_WORKSHOP: resId = R.drawable.sb_workshop; break;
            default: resId = R.drawable.sb_campfire; break;
        }
        return loadCached(resId);
    }

    Drawable drawableForCrop(int seedId, boolean ready) {
        if (!ready) return loadCached(R.drawable.sb_sprout);
        switch (seedId) {
            case CampState.SEED_SUNFLOWER: return loadCached(R.drawable.sb_sunflower);
            case CampState.SEED_STARBERRY: return loadCached(R.drawable.sb_starberry);
            case CampState.SEED_RADISH: return loadCached(R.drawable.sb_radish);
            default: return loadCached(R.drawable.sb_sprout);
        }
    }

    Drawable eggDrawable() {
        return loadCached(R.drawable.sb_egg);
    }

    Drawable starSandDrawable() {
        return loadCached(R.drawable.sb_star_sand);
    }

    Drawable pioneerBadgeDrawable() {
        return loadCached(R.drawable.sb_pioneer_badge);
    }

    void drawIcon(Canvas canvas, Drawable d, float cx, float cy, float size) {
        if (d == null) return;
        int half = Math.round(size * 0.5f);
        int left = Math.round(cx) - half;
        int top = Math.round(cy) - half;
        d.setBounds(left, top, left + half * 2, top + half * 2);
        d.draw(canvas);
    }

    /** 繪製 12 位旅伴（依階段 1/2/3 呈現不同成長細節＋異色光暈＋可穿戴飾品）。 */
    void drawCompanion(
        Canvas c,
        float cx,
        float cy,
        int companionId,
        int stage,
        boolean shiny,
        int accessory,
        float scale,
        int animTick,
        int inkColor
    ) {
        if (companionId == CompanionSystem.ID_NONE) return;
        CompanionSystem.SpeciesDef sp = CompanionSystem.species(companionId);
        if (sp == null) return;

        c.save();
        c.translate(cx, cy);
        float effectiveScale = scale * (stage >= 3 ? 1.14f : (stage == 2 ? 1.06f : 0.96f));
        c.scale(effectiveScale, effectiveScale);

        int mainColor = shiny ? sp.shinyRgb : sp.primaryRgb;

        // 閃亮異色或第 3 階段大師光環
        if (shiny || stage >= 3) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(shiny ? Color.argb(65, 255, 232, 118) : Color.argb(42, 255, 210, 120));
            c.drawCircle(0f, 0f, 22f + (animTick % 2 == 0 ? 1.5f : 0f), p);
            if (shiny) {
                p.setColor(Color.rgb(255, 238, 120));
                float spY = (animTick % 2 == 0) ? -18f : -21f;
                c.drawCircle(-17f, spY, 2.6f, p);
                c.drawCircle(18f, spY + 6f, 2.2f, p);
            }
        }

        // 第 3 階段大師專屬背後小披風
        if (stage >= 3) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(214, 88, 66));
            drawTriangle(c, -16f, 4f, 0f, -6f, 16f, 4f);
            rect.set(-14f, 3f, 14f, 16f);
            c.drawRoundRect(rect, 6f, 6f, p);
        }

        p.setStyle(Paint.Style.FILL);
        switch (companionId) {
            case CompanionSystem.ID_SHIBA:
                p.setColor(mainColor);
                drawTriangle(c, -15f, -4f, -18f, -20f, -4f, -13f);
                drawTriangle(c, 15f, -4f, 18f, -20f, 4f, -13f);
                c.drawCircle(0f, 0f, 16f, p);
                p.setColor(Color.rgb(255, 244, 222));
                c.drawCircle(0f, 4f, 11f, p);
                drawEyes(c, -5f, 5f, -2f, inkColor);
                p.setColor(inkColor);
                c.drawCircle(0f, 3f, 2.5f, p);
                break;

            case CompanionSystem.ID_SLIME:
                p.setColor(mainColor);
                rect.set(-17f, -12f, 17f, 14f);
                c.drawRoundRect(rect, 15f, 14f, p);
                p.setColor(Color.argb(130, 255, 255, 255));
                c.drawCircle(-6f, -6f, 3.6f, p);
                drawEyes(c, -5f, 5f, 1f, inkColor);
                break;

            case CompanionSystem.ID_OWL:
                p.setColor(mainColor);
                c.drawCircle(0f, 1f, 15f, p);
                drawTriangle(c, -12f, -9f, -15f, -18f, -5f, -13f);
                drawTriangle(c, 12f, -9f, 15f, -18f, 5f, -13f);
                p.setColor(Color.rgb(255, 245, 224));
                c.drawCircle(-5.5f, -3f, 5.5f, p);
                c.drawCircle(5.5f, -3f, 5.5f, p);
                drawEyes(c, -5.5f, 5.5f, -3f, inkColor);
                p.setColor(Color.rgb(235, 162, 46));
                drawTriangle(c, -3f, 1f, 3f, 1f, 0f, 6f);
                break;

            case CompanionSystem.ID_DRAGON:
                p.setColor(Color.rgb(245, 182, 54));
                drawTriangle(c, -17f, -3f, -26f, -13f, -9f, -11f);
                drawTriangle(c, 17f, -3f, 26f, -13f, 9f, -11f);
                p.setColor(mainColor);
                c.drawCircle(0f, 0f, 15f, p);
                p.setColor(Color.rgb(255, 219, 128));
                drawTriangle(c, -9f, -12f, -5f, -22f, -2f, -13f);
                drawTriangle(c, 9f, -12f, 5f, -22f, 2f, -13f);
                drawEyes(c, -5f, 5f, -2f, inkColor);
                break;

            case CompanionSystem.ID_SHEEP:
                p.setColor(mainColor);
                c.drawCircle(-10f, -4f, 9f, p);
                c.drawCircle(10f, -4f, 9f, p);
                c.drawCircle(0f, -10f, 10f, p);
                c.drawCircle(0f, 4f, 13f, p);
                p.setColor(Color.rgb(240, 214, 186));
                c.drawCircle(0f, 2f, 9f, p);
                drawEyes(c, -4f, 4f, 1f, inkColor);
                break;

            case CompanionSystem.ID_SQUIRREL:
                p.setColor(mainColor);
                c.drawCircle(13f, -2f, 11f, p); // 蓬蓬大尾巴
                c.drawCircle(0f, 1f, 14f, p);
                c.drawCircle(-9f, -12f, 5f, p);
                c.drawCircle(9f, -12f, 5f, p);
                p.setColor(Color.rgb(255, 238, 212));
                c.drawCircle(0f, 5f, 8.5f, p);
                drawEyes(c, -4.5f, 4.5f, -1f, inkColor);
                break;

            case CompanionSystem.ID_FOX:
                p.setColor(mainColor);
                drawTriangle(c, -14f, -4f, -16f, -22f, -3f, -13f);
                drawTriangle(c, 14f, -4f, 16f, -22f, 3f, -13f);
                c.drawCircle(0f, 0f, 15f, p);
                p.setColor(Color.rgb(255, 246, 224));
                drawTriangle(c, -11f, 3f, 11f, 3f, 0f, 14f);
                p.setColor(Color.rgb(255, 232, 120)); // 螢火尾燈
                c.drawCircle(16f, 6f, 4.5f, p);
                drawEyes(c, -5f, 5f, -2f, inkColor);
                break;

            case CompanionSystem.ID_TORTOISE:
                p.setColor(mainColor);
                rect.set(-16f, -12f, 16f, 10f);
                c.drawRoundRect(rect, 14f, 14f, p);
                p.setColor(Color.rgb(168, 200, 118));
                c.drawCircle(0f, 9f, 7f, p);
                c.drawCircle(-13f, 9f, 5.5f, p);
                drawEyes(c, -15f, -11f, 8f, inkColor);
                break;

            case CompanionSystem.ID_PENGUIN:
                p.setColor(mainColor);
                c.drawCircle(0f, 0f, 15.5f, p);
                p.setColor(Color.rgb(255, 252, 244));
                c.drawCircle(0f, 4f, 10.5f, p);
                drawEyes(c, -5f, 5f, -3f, inkColor);
                p.setColor(Color.rgb(245, 168, 52));
                drawTriangle(c, -4f, 1f, 4f, 1f, 0f, 6f);
                break;

            case CompanionSystem.ID_OTTER:
                p.setColor(mainColor);
                c.drawCircle(-12f, -10f, 4.5f, p);
                c.drawCircle(12f, -10f, 4.5f, p);
                c.drawCircle(0f, 0f, 15f, p);
                p.setColor(Color.rgb(245, 228, 204));
                c.drawCircle(0f, 4f, 10f, p);
                drawEyes(c, -5f, 5f, -2f, inkColor);
                p.setColor(Color.rgb(112, 82, 64));
                c.drawCircle(0f, 3f, 3f, p);
                break;

            case CompanionSystem.ID_DEER:
                p.setColor(Color.rgb(138, 96, 66));
                rect.set(-9f, -22f, -6f, -11f);
                c.drawRect(rect, p);
                rect.set(6f, -22f, 9f, -11f);
                c.drawRect(rect, p);
                p.setColor(mainColor);
                c.drawCircle(0f, 0f, 15f, p);
                p.setColor(Color.rgb(255, 232, 238));
                c.drawCircle(-7f, -18f, 3.2f, p);
                c.drawCircle(7f, -18f, 3.2f, p);
                drawEyes(c, -5f, 5f, -1f, inkColor);
                break;

            case CompanionSystem.ID_WHALE:
            default:
                p.setColor(mainColor);
                rect.set(-18f, -11f, 18f, 13f);
                c.drawRoundRect(rect, 14f, 14f, p);
                p.setColor(Color.rgb(255, 225, 118)); // 獨角星角
                drawTriangle(c, -3f, -10f, 3f, -10f, 0f, -22f);
                p.setColor(Color.rgb(232, 242, 255));
                rect.set(-12f, 4f, 12f, 12f);
                c.drawRoundRect(rect, 6f, 6f, p);
                drawEyes(c, -6f, 6f, -1f, inkColor);
                break;
        }

        // 第 2 階段以上成長特徵：可愛小領巾（若未戴紅圍巾飾品）
        if (stage >= 2 && accessory != CompanionSystem.ACC_RED_SCARF) {
            p.setColor(Color.rgb(245, 191, 66));
            rect.set(-10f, 10f, 10f, 15f);
            c.drawRoundRect(rect, 4f, 4f, p);
        }

        // 穿戴飾品
        drawAccessory(c, accessory);
        c.restore();
    }

    private void drawAccessory(Canvas c, int accessory) {
        p.setStyle(Paint.Style.FILL);
        if (accessory == CompanionSystem.ACC_STRAW_HAT) {
            p.setColor(Color.rgb(240, 200, 108));
            rect.set(-16f, -17f, 16f, -13f);
            c.drawRoundRect(rect, 4f, 4f, p);
            rect.set(-10f, -25f, 10f, -15f);
            c.drawRoundRect(rect, 5f, 5f, p);
            p.setColor(Color.rgb(214, 88, 66));
            rect.set(-10f, -18f, 10f, -15.5f);
            c.drawRect(rect, p);
        } else if (accessory == CompanionSystem.ACC_RED_SCARF) {
            p.setColor(Color.rgb(218, 76, 68));
            rect.set(-12f, 9f, 12f, 15.5f);
            c.drawRoundRect(rect, 5f, 5f, p);
            rect.set(5f, 12f, 11f, 21f);
            c.drawRoundRect(rect, 3f, 3f, p);
        } else if (accessory == CompanionSystem.ACC_STAR_CROWN) {
            p.setColor(Color.rgb(255, 214, 72));
            drawTriangle(c, -10f, -14f, -8f, -24f, -2f, -15f);
            drawTriangle(c, -4f, -14f, 0f, -26f, 4f, -14f);
            drawTriangle(c, 2f, -15f, 8f, -24f, 10f, -14f);
            rect.set(-10f, -16f, 10f, -12.5f);
            c.drawRoundRect(rect, 2f, 2f, p);
        }
    }

    private void drawEyes(Canvas c, float lx, float rx, float y, int inkColor) {
        p.setColor(inkColor);
        c.drawCircle(lx, y, 2.2f, p);
        c.drawCircle(rx, y, 2.2f, p);
    }

    private void drawTriangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        c.drawPath(path, p);
    }

    private Drawable loadCached(int resId) {
        Drawable existing = cache.get(resId);
        if (existing != null) return existing;
        Drawable loaded = context.getDrawable(resId);
        if (loaded != null) {
            cache.put(resId, loaded);
        }
        return loaded;
    }
}
