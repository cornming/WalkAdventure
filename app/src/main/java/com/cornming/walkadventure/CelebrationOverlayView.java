package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 全螢幕穿透式慶祝動畫覆蓋層（60fps 流暢粒子＋彈跳獎勵橫幅＋破殼／進化閃光光環）：
 * 維持非可點擊與非聚焦狀態完全穿透觸控，並自動尊重系統「移除動畫」無障礙設定。
 */
final class CelebrationOverlayView extends View {
    static final int STYLE_CAMPFIRE = 0;  // 營火火星＋金幣星砂噴發
    static final int STYLE_HATCH = 1;     // 蛋殼搖晃閃光＋彩帶炸開
    static final int STYLE_EVOLVE = 2;    // 金色星芒擴散光環＋滿天彩紙
    static final int STYLE_HARVEST = 3;   // 花瓣與星星飄浮上升

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float gravity;
        float size;
        int color;
        int shape; // 0=circle, 1=star/diamond, 2=confetti rect
        long bornMs;
        long lifeMs;
    }

    private static final class FloatingBanner {
        final String title;
        final String subtitle;
        final int accentRgb;
        final long bornMs;
        final long durationMs;
        final boolean showEggCrack;

        FloatingBanner(String title, String subtitle, int accentRgb, long bornMs, long durationMs, boolean showEggCrack) {
            this.title = title;
            this.subtitle = subtitle;
            this.accentRgb = accentRgb;
            this.bornMs = bornMs;
            this.durationMs = durationMs;
            this.showEggCrack = showEggCrack;
        }
    }

    private static final int[] RAINBOW_CONFETTI = {
        0xFFE86688, 0xFFF5BF42, 0xFF70AF74, 0xFF548FD6, 0xFFB07CE8, 0xFFED8B3B
    };
    private static final int[] FIRE_COLORS = {
        0xFFFFD15C, 0xFFEB6E34, 0xFFFF9F43, 0xFFFFF0A8, 0xFF6A89E8
    };
    private static final int[] HARVEST_COLORS = {
        0xFF70AF74, 0xFFF5BF42, 0xFFE88AA4, 0xFF78C2D4, 0xFFFFE38A
    };

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface bold = Typeface.create("sans", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans", Typeface.NORMAL);
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private FloatingBanner activeBanner;
    private long lastFrameMs = 0L;
    private boolean running = false;

    private final Runnable frameLoop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            float dtSec = lastFrameMs > 0L ? Math.min(0.05f, (now - lastFrameMs) / 1000f) : 0.016f;
            lastFrameMs = now;
            stepPhysics(now, dtSec);
            invalidate();
            if (particles.isEmpty() && activeBanner == null) {
                running = false;
                setVisibility(GONE);
            } else {
                postOnAnimation(this);
            }
        }
    };

    CelebrationOverlayView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    void triggerCelebration(int style, String title, String subtitle) {
        triggerCelebrationInternal(style, title, subtitle, true);
    }

    private void triggerCelebrationInternal(int style, String title, String subtitle, boolean allowDefer) {
        if (UiKit.isReducedMotion(getContext())) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            if (allowDefer) {
                setVisibility(VISIBLE);
                post(() -> triggerCelebrationInternal(style, title, subtitle, false));
            }
            return;
        }

        long now = SystemClock.uptimeMillis();
        float cx = w * 0.5f;
        float cy = style == STYLE_CAMPFIRE ? h * 0.52f : (style == STYLE_HATCH ? h * 0.44f : h * 0.48f);

        int[] palette;
        int count;
        int accent;
        if (style == STYLE_CAMPFIRE) {
            palette = FIRE_COLORS;
            count = 36;
            accent = 0xFFDA5C3A;
        } else if (style == STYLE_HATCH) {
            palette = RAINBOW_CONFETTI;
            count = 46;
            accent = 0xFFE59B28;
        } else if (style == STYLE_EVOLVE) {
            palette = RAINBOW_CONFETTI;
            count = 54;
            accent = 0xFF548FD6;
        } else {
            palette = HARVEST_COLORS;
            count = 28;
            accent = 0xFF2E6B34;
        }

        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < count; i++) {
            Particle pt = new Particle();
            pt.x = cx + (random.nextFloat() - 0.5f) * 48f * density;
            pt.y = cy + (random.nextFloat() - 0.5f) * 32f * density;
            double angle = -Math.PI + random.nextDouble() * Math.PI * 2.0;
            if (style == STYLE_CAMPFIRE || style == STYLE_HARVEST) {
                angle = -Math.PI * 0.92 + random.nextDouble() * Math.PI * 0.84; // 朝上噴發
            }
            float speed = (140f + random.nextFloat() * 260f) * density;
            pt.vx = (float) (Math.cos(angle) * speed);
            pt.vy = (float) (Math.sin(angle) * speed) - 80f * density;
            pt.gravity = (style == STYLE_HARVEST ? 220f : 420f) * density;
            pt.size = (4.5f + random.nextFloat() * 5.5f) * density;
            pt.color = palette[i % palette.length];
            pt.shape = i % 3;
            pt.bornMs = now;
            pt.lifeMs = 950L + random.nextInt(700);
            particles.add(pt);
        }

        if (title != null && !title.isEmpty()) {
            activeBanner = new FloatingBanner(
                title,
                subtitle != null ? subtitle : "",
                accent,
                now,
                style == STYLE_HATCH || style == STYLE_EVOLVE ? 2200L : 1700L,
                style == STYLE_HATCH || style == STYLE_EVOLVE
            );
        }

        setVisibility(VISIBLE);
        if (!running) {
            running = true;
            lastFrameMs = now;
            removeCallbacks(frameLoop);
            postOnAnimation(frameLoop);
        }
    }

    void stopAll() {
        running = false;
        removeCallbacks(frameLoop);
        particles.clear();
        activeBanner = null;
        setVisibility(GONE);
    }

    @Override protected void onDetachedFromWindow() {
        stopAll();
        super.onDetachedFromWindow();
    }

    private void stepPhysics(long now, float dtSec) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle pt = it.next();
            if (now - pt.bornMs >= pt.lifeMs) {
                it.remove();
                continue;
            }
            pt.vy += pt.gravity * dtSec;
            pt.x += pt.vx * dtSec;
            pt.y += pt.vy * dtSec;
            pt.vx *= 0.985f;
        }
        if (activeBanner != null && (now - activeBanner.bornMs) >= activeBanner.durationMs) {
            activeBanner = null;
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = SystemClock.uptimeMillis();
        float density = getResources().getDisplayMetrics().density;

        // 1. 繪製慶祝粒子
        p.setStyle(Paint.Style.FILL);
        for (Particle pt : particles) {
            float t = Math.max(0f, Math.min(1f, (now - pt.bornMs) / (float) pt.lifeMs));
            int alpha = Math.round((1f - t * t) * 255f);
            p.setColor((pt.color & 0x00FFFFFF) | (alpha << 24));
            if (pt.shape == 0) {
                c.drawCircle(pt.x, pt.y, pt.size * (1f - t * 0.35f), p);
            } else if (pt.shape == 1) {
                // 四角菱形星芒
                float r = pt.size * 1.15f;
                c.save();
                c.translate(pt.x, pt.y);
                c.rotate(t * 220f);
                rect.set(-r * 0.5f, -r * 0.5f, r * 0.5f, r * 0.5f);
                c.drawRect(rect, p);
                c.restore();
            } else {
                c.save();
                c.translate(pt.x, pt.y);
                c.rotate(t * 320f);
                rect.set(-pt.size * 0.7f, -pt.size * 0.35f, pt.size * 0.7f, pt.size * 0.35f);
                c.drawRoundRect(rect, 2f * density, 2f * density, p);
                c.restore();
            }
        }

        // 2. 繪製彈跳慶祝橫幅（含破殼閃光環）
        if (activeBanner != null) {
            float progress = Math.max(0f, Math.min(1f, (now - activeBanner.bornMs) / (float) activeBanner.durationMs));
            float enter = Math.min(1f, progress / 0.16f);
            float exit = progress > 0.78f ? Math.max(0f, (1f - progress) / 0.22f) : 1f;
            float alphaF = Math.min(enter, exit);
            float bounceScale = enter < 1f ? (0.78f + 0.26f * (float) Math.sin(enter * Math.PI * 0.65)) : 1f;

            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.36f - progress * 24f * density;

            // 破殼／進化向外擴散光環
            if (activeBanner.showEggCrack && progress < 0.45f) {
                float ringT = progress / 0.45f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth((5f - 4f * ringT) * density);
                int ringAlpha = Math.round((1f - ringT) * 210f);
                p.setColor(Color.argb(ringAlpha, 255, 220, 102));
                c.drawCircle(cx, getHeight() * 0.44f, (28f + ringT * 110f) * density, p);
            }

            c.save();
            c.translate(cx, cy);
            c.scale(bounceScale, bounceScale);

            float cardW = Math.min(getWidth() - 40f * density, 310f * density);
            float cardH = activeBanner.subtitle.isEmpty() ? 58f * density : 78f * density;
            rect.set(-cardW * 0.5f, -cardH * 0.5f, cardW * 0.5f, cardH * 0.5f);

            int bgAlpha = Math.round(alphaF * 246f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(bgAlpha, 255, 250, 238));
            c.drawRoundRect(rect, 20f * density, 20f * density, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * density);
            p.setColor((activeBanner.accentRgb & 0x00FFFFFF) | (bgAlpha << 24));
            c.drawRoundRect(rect, 20f * density, 20f * density, p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(bold);
            p.setTextSize(16f * density);
            p.setColor((activeBanner.accentRgb & 0x00FFFFFF) | (bgAlpha << 24));
            float titleY = activeBanner.subtitle.isEmpty() ? 6f * density : -6f * density;
            c.drawText(activeBanner.title, 0f, titleY, p);

            if (!activeBanner.subtitle.isEmpty()) {
                p.setTypeface(regular);
                p.setTextSize(12.5f * density);
                p.setColor(Color.argb(bgAlpha, 78, 58, 44));
                String sub = activeBanner.subtitle.length() > 26
                    ? activeBanner.subtitle.substring(0, 25) + "…"
                    : activeBanner.subtitle;
                c.drawText(sub, 0f, 18f * density, p);
            }
            p.setTextAlign(Paint.Align.LEFT);
            c.restore();
        }
    }
}
