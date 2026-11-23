package com.cornming.walkadventure;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 繪本風互動新手導覽（4 頁滑動感卡片）與隨時可開的《冒險說明手冊》對話框控制器。
 */
final class GuideDialogController {
    interface Host {
        void onOnboardingFinished(boolean jumpToSquad);
        void onJumpToTab(int tabIndex);
    }

    private final Context ctx;
    private final Host host;

    GuideDialogController(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
    }

    /** 顯示 4 頁互動繪本導覽，支援頁碼圓點、平順卡片進場動畫與一鍵開始冒險。 */
    void showOnboardingDialog(AdventureState state) {
        final int[] pageIndex = {0};

        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackgroundColor(ctx.getColor(R.color.bg));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 14), UiKit.dp(ctx, 18), UiKit.dp(ctx, 14));
        root.setBackgroundColor(ctx.getColor(R.color.bg));
        scroll.addView(root);

        TextView stepBadge = new TextView(ctx);
        stepBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        stepBadge.setTypeface(Typeface.create("sans", Typeface.BOLD));
        stepBadge.setTextColor(Color.rgb(122, 98, 78));
        stepBadge.setGravity(Gravity.CENTER);
        stepBadge.setPadding(0, 0, 0, UiKit.dp(ctx, 8));
        root.addView(stepBadge, UiKit.matchWrap());

        LinearLayout card = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 18), UiKit.dp(ctx, 18), UiKit.dp(ctx, 18));

        TextView emojiTv = new TextView(ctx);
        emojiTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        emojiTv.setGravity(Gravity.CENTER);
        emojiTv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(emojiTv);

        TextView titleTv = new TextView(ctx);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        titleTv.setTypeface(Typeface.create("sans", Typeface.BOLD));
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 4));
        card.addView(titleTv);

        TextView subTv = new TextView(ctx);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        subTv.setTypeface(Typeface.create("sans", Typeface.BOLD));
        subTv.setTextColor(Color.rgb(112, 86, 66));
        subTv.setGravity(Gravity.CENTER);
        subTv.setPadding(0, 0, 0, UiKit.dp(ctx, 10));
        card.addView(subTv);

        TextView bodyTv = new TextView(ctx);
        bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bodyTv.setTextColor(ctx.getColor(R.color.ink));
        bodyTv.setLineSpacing(UiKit.dp(ctx, 4), 1f);
        card.addView(bodyTv);

        root.addView(card, UiKit.cardMargins(ctx));

        // 頁碼圓點列
        LinearLayout dotsRow = new LinearLayout(ctx);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, UiKit.dp(ctx, 4), 0, UiKit.dp(ctx, 10));
        View[] dots = new View[GuideState.ONBOARDING_PAGES.length];
        for (int i = 0; i < dots.length; i++) {
            View dot = new View(ctx);
            int sz = UiKit.dp(ctx, 9);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(sz, sz);
            dlp.setMargins(UiKit.dp(ctx, 5), 0, UiKit.dp(ctx, 5), 0);
            dotsRow.addView(dot, dlp);
            dots[i] = dot;
        }
        root.addView(dotsRow, UiKit.matchWrap());

        // 底部控制列（上一頁／跳過 ｜ 下一頁／開始冒險）
        Button leftBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_CARD_MUTED, ctx.getColor(R.color.ink));
        Button rightBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        root.addView(UiKit.makeButtonRow(ctx, leftBtn, rightBtn), UiKit.matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setView(scroll)
            .setCancelable(true)
            .create();

        Runnable renderPage = () -> {
            GuideState.OnboardingPage page = GuideState.ONBOARDING_PAGES[pageIndex[0]];
            stepBadge.setText("📖 走走冒險 2.0 新手導覽 · " + (pageIndex[0] + 1) + " / " + GuideState.ONBOARDING_PAGES.length);
            emojiTv.setText(page.emoji);
            titleTv.setText(page.title);
            titleTv.setTextColor(page.accentRgb);
            subTv.setText(page.subtitle);
            bodyTv.setText(page.body);

            // 輕快進場微動畫（自動尊重系統移除動畫設定）
            if (!UiKit.isReducedMotion(ctx)) {
                card.animate().cancel();
                card.setAlpha(0.25f);
                card.setTranslationY(UiKit.dp(ctx, 10));
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
                UiKit.animatePopBounce(ctx, emojiTv);
            }

            for (int i = 0; i < dots.length; i++) {
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(i == pageIndex[0] ? page.accentRgb : Color.rgb(218, 204, 184));
                dots[i].setBackground(gd);
            }

            boolean isFirst = pageIndex[0] == 0;
            boolean isLast = pageIndex[0] == GuideState.ONBOARDING_PAGES.length - 1;
            leftBtn.setText(isFirst ? "略過導覽" : "◀ 上一頁");
            rightBtn.setText(isLast ? "🚀 開始走走冒險！" : "下一頁 ▶");
        };

        leftBtn.setOnClickListener(v -> {
            if (pageIndex[0] == 0) {
                state.guide.markOnboardingCompleted();
                dialog.dismiss();
                host.onOnboardingFinished(false);
            } else {
                pageIndex[0]--;
                renderPage.run();
            }
        });

        rightBtn.setOnClickListener(v -> {
            if (pageIndex[0] < GuideState.ONBOARDING_PAGES.length - 1) {
                pageIndex[0]++;
                renderPage.run();
            } else {
                state.guide.markOnboardingCompleted();
                dialog.dismiss();
                host.onOnboardingFinished(false);
            }
        });

        dialog.setOnCancelListener(d -> {
            state.guide.markOnboardingCompleted();
            host.onOnboardingFinished(false);
        });

        renderPage.run();
        dialog.show();
    }

    /** 隨時可從頂部「❓」開啟的《走走冒險 2.0 說明手冊》：含重播導覽與 8 項常見玩法解答。 */
    void showHelpManualDialog(AdventureState state) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UiKit.dp(ctx, 16), UiKit.dp(ctx, 12), UiKit.dp(ctx, 16), UiKit.dp(ctx, 12));
        scroll.addView(col);

        Button replayBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_FIRE, UiKit.COLOR_PRIMARY_TEXT);
        replayBtn.setText("📖 重新播放 4 頁新手繪本導覽");
        Button resetTipsBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
        resetTipsBtn.setText("💡 重新顯示各分頁提示");

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle("❓ 走走冒險 2.0 說明指南")
            .setView(scroll)
            .setPositiveButton("我知道了", null)
            .create();

        replayBtn.setOnClickListener(v -> {
            dialog.dismiss();
            showOnboardingDialog(state);
        });
        resetTipsBtn.setOnClickListener(v -> {
            state.guide.seenTipsMask = 0;
            dialog.dismiss();
            host.onOnboardingFinished(false);
        });

        LinearLayout topRow = UiKit.makeButtonRow(ctx, replayBtn, resetTipsBtn);
        LinearLayout.LayoutParams trLp = UiKit.matchWrap();
        trLp.setMargins(0, 0, 0, UiKit.dp(ctx, 10));
        col.addView(topRow, trLp);

        for (GuideState.HelpTopic topic : GuideState.HELP_TOPICS) {
            LinearLayout card = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
            UiKit.addCardTitle(ctx, card, topic.icon + " " + topic.title);
            UiKit.addCardSubtitle(ctx, card, topic.body);
            col.addView(card, UiKit.cardMargins(ctx));
        }

        // 快速導覽傳送門按鈕
        LinearLayout navCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, navCard, "🧭 快速前往體驗");
        Button goCamp = UiKit.makeSmallPillButton(ctx, "⛺ 去秘密營地", UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        goCamp.setOnClickListener(v -> {
            dialog.dismiss();
            host.onJumpToTab(1);
        });
        Button goSquad = UiKit.makeSmallPillButton(ctx, "👨‍👩‍👧 設家人護照", UiKit.COLOR_ACCENT_STAR, UiKit.COLOR_PRIMARY_TEXT);
        goSquad.setOnClickListener(v -> {
            dialog.dismiss();
            host.onJumpToTab(3);
        });
        LinearLayout navRow = UiKit.makeButtonRow(ctx, goCamp, goSquad);
        LinearLayout.LayoutParams nLp = UiKit.matchWrap();
        nLp.setMargins(0, UiKit.dp(ctx, 6), 0, 0);
        navCard.addView(navRow, nLp);
        col.addView(navCard, UiKit.cardMargins(ctx));

        dialog.show();
    }

    /** 建構可關閉的分頁情境提示卡（供探險／營地／手札／小隊 4 分頁共用）。 */
    static LinearLayout buildContextualTipCard(Context ctx, int tipIndex, Runnable onDismiss) {
        GuideState.TabTip tip = GuideState.tabTip(tipIndex);
        LinearLayout card = UiKit.makeSectionCard(ctx, Color.rgb(255, 245, 214));
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        UiKit.addCardTitle(ctx, textCol, tip.title);
        UiKit.addCardSubtitle(ctx, textCol, tip.message);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button okBtn = UiKit.makeSmallPillButton(ctx, "懂了 ✓", UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bLp.setMargins(UiKit.dp(ctx, 8), 0, 0, 0);
        okBtn.setOnClickListener(v -> {
            if (UiKit.isReducedMotion(ctx)) {
                if (onDismiss != null) onDismiss.run();
                return;
            }
            card.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (onDismiss != null) onDismiss.run();
                })
                .start();
        });
        row.addView(okBtn, bLp);
        card.addView(row, UiKit.matchWrap());
        return card;
    }
}
