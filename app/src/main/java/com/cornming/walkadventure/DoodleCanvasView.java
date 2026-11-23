package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * 親子手指塗鴉畫布：
 * 顯示剛拍好的實景尋寶照片作為底圖，讓孩子用手指在照片上畫愛心、笑臉或簽名，
 * 匯出時只匯出透明塗鴉圖層以精準疊印至實境冒險明信片。
 */
final class DoodleCanvasView extends View {
    static final int[] PALETTE = {
        0xFFE64B4B, // 紅
        0xFFF5BF42, // 黃
        0xFF4CA85B, // 綠
        0xFF3E84D6, // 藍
        0xFFFFFFFF, // 白
        0xFF3A2A20  // 深棕
    };

    private static final class Stroke {
        final Path path;
        final int color;
        final float widthRatio;

        Stroke(Path path, int color, float widthRatio) {
            this.path = path;
            this.color = color;
            this.widthRatio = widthRatio;
        }
    }

    private final Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final List<Stroke> strokes = new ArrayList<>();
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();

    private Bitmap backgroundPhoto;
    private int currentColor = PALETTE[0];
    private float currentWidthRatio = 0.016f;
    private Stroke activeStroke;

    DoodleCanvasView(Context context) {
        super(context);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription("手指塗鴉畫布，可在尋寶照片上自由繪畫");
    }

    void setBackgroundPhoto(Bitmap photo) {
        this.backgroundPhoto = photo;
        invalidate();
    }

    void setStrokeColor(int color) {
        this.currentColor = color;
    }

    void setThickBrush(boolean thick) {
        this.currentWidthRatio = thick ? 0.028f : 0.015f;
    }

    boolean hasStrokes() {
        return !strokes.isEmpty();
    }

    void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    void clear() {
        strokes.clear();
        activeStroke = null;
        invalidate();
    }

    /** 將所有塗鴉筆畫渲染到指定尺寸的透明 Bitmap，用於疊印在實境冒險明信片上。 */
    Bitmap renderTransparentOverlay(int outWidth, int outHeight) {
        if (strokes.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return null;
        Bitmap overlay = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(overlay);
        c.drawColor(Color.TRANSPARENT);
        float sx = outWidth / (float) getWidth();
        float sy = outHeight / (float) getHeight();
        c.save();
        c.scale(sx, sy);
        drawAllStrokes(c, getWidth());
        c.restore();
        return overlay;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = width > 0 ? Math.round(width * 0.78f) : 480;
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Path path = new Path();
                path.moveTo(x, y);
                path.lineTo(x + 0.5f, y + 0.5f);
                activeStroke = new Stroke(path, currentColor, currentWidthRatio);
                strokes.add(activeStroke);
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeStroke != null) {
                    activeStroke.path.lineTo(x, y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeStroke = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(245, 236, 220));
        if (backgroundPhoto != null && !backgroundPhoto.isRecycled()) {
            computeCenterCropRect(backgroundPhoto.getWidth(), backgroundPhoto.getHeight(), getWidth(), getHeight(), srcRect);
            dstRect.set(0f, 0f, getWidth(), getHeight());
            canvas.drawBitmap(backgroundPhoto, srcRect, dstRect, bgPaint);
        }
        drawAllStrokes(canvas, getWidth());
    }

    private void drawAllStrokes(Canvas canvas, int refWidth) {
        float base = Math.max(240f, refWidth);
        for (Stroke st : strokes) {
            drawPaint.setColor(st.color);
            drawPaint.setStrokeWidth(Math.max(4f, base * st.widthRatio));
            canvas.drawPath(st.path, drawPaint);
        }
    }

    static void computeCenterCropRect(int srcW, int srcH, int dstW, int dstH, Rect outSrc) {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
            outSrc.set(0, 0, Math.max(1, srcW), Math.max(1, srcH));
            return;
        }
        float srcAspect = srcW / (float) srcH;
        float dstAspect = dstW / (float) dstH;
        if (srcAspect > dstAspect) {
            int newW = Math.round(srcH * dstAspect);
            int left = (srcW - newW) / 2;
            outSrc.set(left, 0, left + newW, srcH);
        } else {
            int newH = Math.round(srcW / dstAspect);
            int top = (srcH - newH) / 2;
            outSrc.set(0, top, srcW, top + newH);
        }
    }
}
