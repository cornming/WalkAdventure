package com.cornming.walkadventure;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 統一繪本暖色風格的 Android UI 元件建構工具，供四大分頁控制器與 MainActivity 共用。
 */
final class UiKit {
    static final int COLOR_PRIMARY = Color.rgb(46, 107, 52);
    static final int COLOR_PRIMARY_TEXT = Color.rgb(255, 247, 232);
    static final int COLOR_WARM_BTN = Color.rgb(255, 225, 170);
    static final int COLOR_ACCENT_FIRE = Color.rgb(218, 92, 58);
    static final int COLOR_ACCENT_STAR = Color.rgb(84, 125, 214);
    static final int COLOR_CARD_BG = Color.rgb(255, 250, 240);
    static final int COLOR_CARD_GREEN = Color.rgb(233, 245, 227);
    static final int COLOR_CARD_GOLD = Color.rgb(255, 240, 194);
    static final int COLOR_CARD_MUTED = Color.rgb(241, 230, 212);
    static final int COLOR_DISABLED_BG = Color.rgb(225, 214, 196);
    static final int COLOR_DISABLED_TEXT = Color.rgb(120, 102, 86);
    static final int COLOR_SUBTITLE = Color.rgb(104, 82, 66);

    private UiKit() {}

    static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            ctx.getResources().getDisplayMetrics()
        ));
    }

    static LinearLayout makeSectionCard(Context ctx, int bgColor) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(dp(ctx, 18));
        d.setStroke(dp(ctx, 1), Color.rgb(230, 212, 186));
        card.setBackground(d);
        return card;
    }

    static LinearLayout.LayoutParams cardMargins(Context ctx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(ctx, 10));
        return lp;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static void addSectionHeader(Context ctx, LinearLayout parent, String title) {
        TextView tv = new TextView(ctx);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.create("sans", Typeface.BOLD));
        tv.setTextColor(ctx.getColor(R.color.ink));
        tv.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 6));
        parent.addView(tv);
    }

    static TextView addCardTitle(Context ctx, LinearLayout card, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.create("sans", Typeface.BOLD));
        tv.setTextColor(ctx.getColor(R.color.ink));
        card.addView(tv);
        return tv;
    }

    static TextView addCardSubtitle(Context ctx, LinearLayout card, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(COLOR_SUBTITLE);
        tv.setPadding(0, dp(ctx, 3), 0, 0);
        card.addView(tv);
        return tv;
    }

    static Button makeActionButton(Context ctx, int bgColor, int fgColor) {
        Button btn = new Button(ctx);
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        btn.setMinHeight(dp(ctx, 46));
        btn.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.create("sans", Typeface.BOLD));
        btn.setTextColor(fgColor);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(dp(ctx, 14));
        btn.setBackground(d);
        return btn;
    }

    static Button makeSmallPillButton(Context ctx, String text, int bgColor, int fgColor) {
        Button btn = new Button(ctx);
        btn.setAllCaps(false);
        btn.setStateListAnimator(null);
        btn.setMinHeight(dp(ctx, 40));
        btn.setPadding(dp(ctx, 11), dp(ctx, 5), dp(ctx, 11), dp(ctx, 5));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTypeface(Typeface.create("sans", Typeface.BOLD));
        btn.setTextColor(fgColor);
        btn.setText(text);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bgColor);
        d.setCornerRadius(dp(ctx, 18));
        btn.setBackground(d);
        return btn;
    }

    static ProgressBar makeHorizontalProgress(Context ctx, int max, int progress) {
        ProgressBar pb = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(Math.max(1, max));
        pb.setProgress(Math.max(0, Math.min(max, progress)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(ctx, 5), 0, dp(ctx, 7));
        pb.setLayoutParams(lp);
        return pb;
    }

    static LinearLayout makeButtonRow(Context ctx, View left, View right) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpL.setMargins(0, 0, dp(ctx, 5), 0);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpR.setMargins(dp(ctx, 5), 0, 0, 0);
        row.addView(left, lpL);
        row.addView(right, lpR);
        return row;
    }

    static boolean isReducedMotion(Context ctx) {
        try {
            float scale = android.provider.Settings.Global.getFloat(
                ctx.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            );
            return scale <= 0.01f;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void animateFadeSlideIn(Context ctx, View target) {
        if (target == null || isReducedMotion(ctx)) return;
        target.animate().cancel();
        target.setAlpha(0f);
        target.setTranslationY(dp(ctx, 10));
        target.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(190)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    static void animatePopBounce(Context ctx, View target) {
        if (target == null || isReducedMotion(ctx)) return;
        target.animate().cancel();
        target.setScaleX(0.92f);
        target.setScaleY(0.92f);
        target.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
            .start();
    }
}
