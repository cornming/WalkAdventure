package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/**
 * 繪本風「本機足跡迷霧地圖」Canvas 視圖（隱私優先：只顯示相對 200m 網格與自訂秘密基地名稱）：
 * - 未探索區域以柔和雲霧覆蓋，已踩破格子呈暖綠草地並隨造訪次數加深色澤
 * - 目前所在格有暖金定位圈，已命名的家庭秘密基地插上星星小旗
 * - 點擊任意已探索格子即可為該地命名或更新「家庭秘密基地」名稱
 */
final class FootprintMapView extends View {
    interface OnTileTapListener {
        void onTileTapped(FootprintState.Tile tile);
    }

    private static final int GRID_RADIUS = 3; // 顯示 7 × 7 視野（以目前位置為中心）

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
    private final int ink;

    private FootprintState state;
    private OnTileTapListener tapListener;
    private float cellSize = 48f;
    private float gridLeft = 0f;
    private float gridTop = 0f;

    FootprintMapView(Context context) {
        super(context);
        ink = context.getColor(R.color.ink);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void bind(FootprintState state, OnTileTapListener listener) {
        this.state = state;
        this.tapListener = listener;
        int count = state != null ? state.discoveredCount() : 0;
        int bases = state != null ? state.namedBaseCount() : 0;
        setContentDescription(String.format(
            Locale.TAIWAN,
            "本機足跡迷霧地圖。已踩破 %d 格迷霧，已命名 %d 座家庭秘密基地。點擊已探索格可命名秘密基地。",
            count,
            bases
        ));
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int idealH = Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 250, getResources().getDisplayMetrics()
        ));
        setMeasuredDimension(w, resolveSize(idealH, heightMeasureSpec));
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || state == null || tapListener == null) {
            return true;
        }
        int cols = GRID_RADIUS * 2 + 1;
        int col = (int) Math.floor((event.getX() - gridLeft) / cellSize);
        int row = (int) Math.floor((event.getY() - gridTop) / cellSize);
        if (col >= 0 && col < cols && row >= 0 && row < cols) {
            int rx = state.lastRx + (col - GRID_RADIUS);
            int ry = state.lastRy + (GRID_RADIUS - row);
            FootprintState.Tile hit = state.tiles.get(rx + "," + ry);
            if (hit != null) {
                performClick();
                tapListener.onTileTapped(hit);
            }
        }
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(225, 236, 240));
        rect.set(0f, 0f, w, h);
        c.drawRoundRect(rect, 18f * density, 18f * density, p);

        int cols = GRID_RADIUS * 2 + 1;
        float pad = 12f * density;
        float headerH = 26f * density;
        float availW = Math.max(40f, w - pad * 2f);
        float availH = Math.max(40f, h - pad * 2f - headerH);
        cellSize = Math.min(availW / cols, availH / cols);
        gridLeft = (w - cellSize * cols) * 0.5f;
        gridTop = pad + headerH + (availH - cellSize * cols) * 0.5f;

        // 頂部圖例列
        p.setTypeface(bold);
        p.setTextSize(11.5f * density);
        p.setColor(ink);
        p.setTextAlign(Paint.Align.CENTER);
        String header = state == null
            ? "🗺️ 足跡迷霧探索地圖"
            : String.format(Locale.TAIWAN, "🗺️ 已破霧 %d 格 · ⭐ 秘密基地 %d 座（點綠格命名）",
                state.discoveredCount(), state.namedBaseCount());
        c.drawText(header, w * 0.5f, pad + 15f * density, p);

        int centerRx = state != null ? state.lastRx : 0;
        int centerRy = state != null ? state.lastRy : 0;
        float gap = 3f * density;

        for (int row = 0; row < cols; row++) {
            for (int col = 0; col < cols; col++) {
                int rx = centerRx + (col - GRID_RADIUS);
                int ry = centerRy + (GRID_RADIUS - row);
                float left = gridLeft + col * cellSize + gap;
                float top = gridTop + row * cellSize + gap;
                float right = gridLeft + (col + 1) * cellSize - gap;
                float bottom = gridTop + (row + 1) * cellSize - gap;
                rect.set(left, top, right, bottom);

                FootprintState.Tile tile = state != null ? state.tiles.get(rx + "," + ry) : null;
                boolean isCurrent = (rx == centerRx && ry == centerRy && tile != null);

                if (tile == null) {
                    // 未探索迷霧雲格
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.rgb(206, 218, 224));
                    c.drawRoundRect(rect, 8f * density, 8f * density, p);
                    p.setColor(Color.argb(145, 255, 255, 255));
                    c.drawCircle(rect.centerX() - 4f * density, rect.centerY(), 5f * density, p);
                    c.drawCircle(rect.centerX() + 4f * density, rect.centerY() + 1f * density, 4.2f * density, p);
                } else {
                    // 已踩破草地格（依造訪次數漸深）
                    int green = Math.max(150, 214 - Math.min(48, tile.visitCount * 10));
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(tile.isNamedBase() ? Color.rgb(255, 236, 174) : Color.rgb(142, green, 134));
                    c.drawRoundRect(rect, 8f * density, 8f * density, p);

                    if (isCurrent) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(2.6f * density);
                        p.setColor(Color.rgb(218, 92, 58));
                        c.drawRoundRect(rect, 8f * density, 8f * density, p);
                    }

                    p.setStyle(Paint.Style.FILL);
                    p.setTextAlign(Paint.Align.CENTER);
                    if (tile.isNamedBase()) {
                        p.setTypeface(bold);
                        p.setTextSize(12f * density);
                        p.setColor(Color.rgb(186, 84, 34));
                        c.drawText("🚩", rect.centerX(), rect.centerY() - 1f * density, p);
                        p.setTypeface(regular);
                        p.setTextSize(8.2f * density);
                        p.setColor(ink);
                        String shortName = tile.baseName.length() > 4 ? tile.baseName.substring(0, 4) : tile.baseName;
                        c.drawText(shortName, rect.centerX(), rect.bottom - 4f * density, p);
                    } else if (isCurrent) {
                        p.setTypeface(bold);
                        p.setTextSize(12.5f * density);
                        p.setColor(Color.rgb(218, 92, 58));
                        c.drawText("📍", rect.centerX(), rect.centerY() + 4f * density, p);
                    } else {
                        p.setTypeface(bold);
                        p.setTextSize(10f * density);
                        p.setColor(Color.rgb(46, 107, 52));
                        c.drawText("🌿", rect.centerX(), rect.centerY() + 4f * density, p);
                    }
                }
            }
        }
        p.setTextAlign(Paint.Align.LEFT);
    }
}
