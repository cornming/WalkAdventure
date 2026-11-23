package com.cornming.walkadventure;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Phase 3 & 4 互動體驗控制器：
 * - 實體藏寶工坊（NFC 卡片即時感應綁定 ＋ 離線 QR 藏寶碼產生／分享）
 * - 孩子尋寶雷達（顯示謎語、NFC 嗶卡即開箱、輸入／貼上 QR 短碼開箱）
 * - 離線 QR 家族禮物交換卡（雙機免伺服器送禮＋旅伴來營地串門子）
 * - 本機足跡迷霧探索與家庭秘密基地命名
 * - 《家庭年度冒險回憶長圖冊》預覽與一鍵分享
 */
final class TreasureAndFootprintController {
    interface Host {
        void onStateChanged(String toastMessage);
        void onCelebrate(int style, String title, String subtitle);
        void onVibrateSuccess();
        boolean isNfcAvailable();
        boolean hasCoarseLocationPermission();
        void onRequestCoarseLocationPermission(Runnable onGranted);
        void onRefreshFootprintLocation();
    }

    private final Context ctx;
    private final Host host;
    private final MemoryMediaStore mediaStore;
    private final YearbookRenderer yearbookRenderer;
    private final Random random = new Random();

    // 若「大人藏寶」視窗開啟中，感應 NFC 卡片會直接綁定 UID 到該表單！
    private TextView activeBindNfcStatusView;
    private String[] activeBindNfcUidHolder;

    TreasureAndFootprintController(Context ctx, Host host, MemoryMediaStore mediaStore) {
        this.ctx = ctx;
        this.host = host;
        this.mediaStore = mediaStore;
        this.yearbookRenderer = new YearbookRenderer(ctx);
    }

    boolean isBindingNfcCard() {
        return activeBindNfcUidHolder != null;
    }

    /** 當 MainActivity 收到實體 NFC 感應事件時呼叫：綁定模式寫入 UID，否則自動當作尋寶開箱！ */
    void onNfcTagDiscovered(String hexUid, StepProgress progress, AdventureState state, String today) {
        if (hexUid == null || hexUid.isEmpty()) return;
        if (activeBindNfcUidHolder != null) {
            activeBindNfcUidHolder[0] = hexUid;
            if (activeBindNfcStatusView != null) {
                activeBindNfcStatusView.setText("✅ 已感應綁定卡片 UID：" + hexUid + "（隨時可再嗶別張替換）");
                activeBindNfcStatusView.setTextColor(UiKit.COLOR_PRIMARY);
            }
            host.onVibrateSuccess();
            host.onStateChanged("📡 已感應 NFC 卡片（" + hexUid + "），按「藏好寶箱」就完成囉！");
            return;
        }
        TreasureAndQrState.ClaimOutcome out = state.claimHiddenTreasureByNfc(progress, hexUid, today);
        host.onStateChanged(out.message);
        if (out.ok) {
            host.onVibrateSuccess();
            host.onCelebrate(
                CelebrationOverlayView.STYLE_HATCH,
                "📡 NFC 尋寶大成功！",
                out.treasure != null ? out.treasure.title : out.message
            );
        }
    }

    // ──────────────── 1. 實體藏寶盒主控台（大人藏寶 × 孩子尋寶雷達） ────────────────

    void showTreasureHubDialog(StepProgress progress, AdventureState state, String today) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UiKit.dp(ctx, 16), UiKit.dp(ctx, 12), UiKit.dp(ctx, 16), UiKit.dp(ctx, 12));
        scroll.addView(col);

        LinearLayout introCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, introCard, "🎁 下雨天與露營超好玩：大人藏寶・孩子尋寶！");
        String nfcNote = host.isNfcAvailable()
            ? "📡 本機支援 NFC！拿閒置悠遊卡／門禁卡直接嗶一下就能綁定藏寶，也可以產生可愛 QR 碼列印藏起來！"
            : "📱 本機無 NFC 也可完整遊玩：產生離線 QR 藏寶卡＆可愛短碼藏在家裡或公園，找到就能開箱！";
        UiKit.addCardSubtitle(ctx, introCard, nfcNote);

        Button hideBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_FIRE, UiKit.COLOR_PRIMARY_TEXT);
        hideBtn.setText("🕵️ 大人藏寶（新建）");
        Button unlockBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        unlockBtn.setText("🔑 輸入／掃描藏寶碼");

        AlertDialog hubDialog = new AlertDialog.Builder(ctx)
            .setTitle("🎁 實體藏寶盒（待尋 " + state.treasure.activeTreasureCount() + " 個）")
            .setView(scroll)
            .setPositiveButton("關閉", null)
            .create();

        hideBtn.setOnClickListener(v -> {
            hubDialog.dismiss();
            showHideTreasureDialog(progress, state, today);
        });
        unlockBtn.setOnClickListener(v -> {
            hubDialog.dismiss();
            showUnlockTreasureByCodeDialog(progress, state, today);
        });

        LinearLayout btnRow = UiKit.makeButtonRow(ctx, hideBtn, unlockBtn);
        LinearLayout.LayoutParams rLp = UiKit.matchWrap();
        rLp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
        introCard.addView(btnRow, rLp);
        col.addView(introCard, UiKit.cardMargins(ctx));

        // 孩子尋寶雷達列表（顯示待尋找的謎語）
        List<TreasureAndQrState.HiddenTreasure> actives = state.treasure.activeTreasures();
        UiKit.addSectionHeader(ctx, col, "📡 孩子尋寶雷達（待尋找的謎語寶箱：" + actives.size() + " 個）");
        if (actives.isEmpty()) {
            LinearLayout empty = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
            UiKit.addCardSubtitle(ctx, empty, "目前還沒有藏好的寶箱！請大人點上方「大人藏寶」，設定一個家裡或公園的謎語驚喜吧！");
            col.addView(empty, UiKit.cardMargins(ctx));
        } else {
            for (TreasureAndQrState.HiddenTreasure t : actives) {
                LinearLayout tCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
                String tag = t.hasNfc() ? "📡 NFC＋QR" : "🖨️ 離線 QR";
                UiKit.addCardTitle(ctx, tCard, "[" + tag + "] " + t.title);
                UiKit.addCardSubtitle(ctx, tCard, "🧩 謎語提示：「" + t.riddle + "」");
                UiKit.addCardSubtitle(ctx, tCard, "🎁 內容：" + TreasureAndQrState.rewardLabel(t.rewardType));
                if (t.hasNfc()) {
                    UiKit.addCardSubtitle(ctx, tCard, "💡 找到卡片直接靠在手機背面「嗶！」一下就會炸開寶箱！");
                }

                Button qrBtn = UiKit.makeSmallPillButton(ctx, "🖨️ 看 QR 藏寶卡", UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
                qrBtn.setOnClickListener(v -> showQrCardPreviewDialog(
                    "🎁 走走冒險 · 實體尋寶卡",
                    t.title,
                    "謎語提示：" + t.riddle,
                    t.toQrPayload(),
                    "開箱密碼：" + t.secretCode,
                    t.secretCode,
                    UiKit.COLOR_ACCENT_FIRE
                ));
                Button delBtn = UiKit.makeSmallPillButton(ctx, "移除", UiKit.COLOR_CARD_MUTED, ctx.getColor(R.color.ink));
                delBtn.setOnClickListener(v -> {
                    state.treasure.removeTreasure(t.id);
                    host.onStateChanged("已移除藏寶箱「" + t.title + "」");
                    hubDialog.dismiss();
                    showTreasureHubDialog(progress, state, today);
                });
                LinearLayout actRow = UiKit.makeButtonRow(ctx, qrBtn, delBtn);
                LinearLayout.LayoutParams aLp = UiKit.matchWrap();
                aLp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
                tCard.addView(actRow, aLp);
                col.addView(tCard, UiKit.cardMargins(ctx));
            }
        }

        // 近期已找到的紀錄
        int foundShown = 0;
        for (TreasureAndQrState.HiddenTreasure t : state.treasure.treasures) {
            if (!t.claimed) continue;
            if (foundShown == 0) {
                UiKit.addSectionHeader(ctx, col, "🏆 已尋獲的實體寶藏紀錄（共 " + state.treasure.totalTreasuresClaimed + " 次）");
            }
            LinearLayout fCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GREEN);
            UiKit.addCardTitle(ctx, fCard, "✅ " + t.title + "（" + t.claimedDate + "）");
            UiKit.addCardSubtitle(ctx, fCard, "謎語：" + t.riddle);
            col.addView(fCard, UiKit.cardMargins(ctx));
            if (++foundShown >= 5) break;
        }

        hubDialog.show();
    }

    private void showHideTreasureDialog(StepProgress progress, AdventureState state, String today) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout form = new LinearLayout(ctx);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 12), UiKit.dp(ctx, 18), UiKit.dp(ctx, 8));
        scroll.addView(form);

        final String[] nfcUidHolder = {""};
        activeBindNfcUidHolder = nfcUidHolder;

        EditText titleInput = new EditText(ctx);
        titleInput.setHint("寶箱名稱（例：沙發下的星光龍蛋）");
        form.addView(titleInput);

        EditText riddleInput = new EditText(ctx);
        riddleInput.setHint("寫給孩子的謎語提示（例：晚上大家一起唸繪本的地方）");
        riddleInput.setMinLines(2);
        form.addView(riddleInput);

        TextView rewardTitle = new TextView(ctx);
        rewardTitle.setText("選擇找到後開出的獎勵內容：");
        rewardTitle.setTextColor(ctx.getColor(R.color.ink));
        rewardTitle.setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 4));
        form.addView(rewardTitle);

        RadioGroup rewardGroup = new RadioGroup(ctx);
        RadioButton rbCoin = new RadioButton(ctx);
        rbCoin.setText(TreasureAndQrState.rewardLabel(TreasureAndQrState.REWARD_COIN_CHEST));
        RadioButton rbSnack = new RadioButton(ctx);
        rbSnack.setText(TreasureAndQrState.rewardLabel(TreasureAndQrState.REWARD_SNACK_PACK));
        RadioButton rbEgg = new RadioButton(ctx);
        rbEgg.setText(TreasureAndQrState.rewardLabel(TreasureAndQrState.REWARD_MYSTERY_EGG));
        rewardGroup.addView(rbCoin);
        rewardGroup.addView(rbSnack);
        rewardGroup.addView(rbEgg);
        rbCoin.setChecked(true);
        form.addView(rewardGroup);

        LinearLayout nfcCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        TextView nfcStatus = UiKit.addCardSubtitle(ctx, nfcCard, host.isNfcAvailable()
            ? "📡（選填）現在把閒置悠遊卡／NFC 貼紙靠在手機背面「嗶」一下，即可自動綁定卡片！不綁也能直接用 QR 碼遊玩。"
            : "🖨️ 建立後將自動產生專屬離線 QR 藏寶卡與開箱短碼！");
        activeBindNfcStatusView = nfcStatus;
        LinearLayout.LayoutParams nLp = UiKit.cardMargins(ctx);
        nLp.topMargin = UiKit.dp(ctx, 8);
        form.addView(nfcCard, nLp);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle("🕵️ 大人藏寶工坊")
            .setView(scroll)
            .setPositiveButton("🎁 藏好寶箱！", (d, w) -> {
                activeBindNfcUidHolder = null;
                activeBindNfcStatusView = null;
                int rType = rbEgg.isChecked() ? TreasureAndQrState.REWARD_MYSTERY_EGG
                    : (rbSnack.isChecked() ? TreasureAndQrState.REWARD_SNACK_PACK : TreasureAndQrState.REWARD_COIN_CHEST);
                TreasureAndQrState.HiddenTreasure created = state.treasure.createTreasure(
                    titleInput.getText().toString(),
                    riddleInput.getText().toString(),
                    nfcUidHolder[0],
                    rType,
                    today,
                    random
                );
                host.onStateChanged("🎁 已藏好「" + created.title + "」！開箱密碼：" + created.secretCode);
                showQrCardPreviewDialog(
                    "🎁 走走冒險 · 實體尋寶卡",
                    created.title,
                    "謎語提示：" + created.riddle,
                    created.toQrPayload(),
                    "開箱密碼：" + created.secretCode,
                    created.secretCode,
                    UiKit.COLOR_ACCENT_FIRE
                );
            })
            .setNegativeButton("取消", (d, w) -> {
                activeBindNfcUidHolder = null;
                activeBindNfcStatusView = null;
            })
            .create();
        dialog.setOnDismissListener(d -> {
            activeBindNfcUidHolder = null;
            activeBindNfcStatusView = null;
        });
        dialog.show();
    }

    private void showUnlockTreasureByCodeDialog(StepProgress progress, AdventureState state, String today) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiKit.dp(ctx, 18), UiKit.dp(ctx, 10), UiKit.dp(ctx, 18), UiKit.dp(ctx, 4));

        TextView hint = new TextView(ctx);
        hint.setText("用相機掃描藏寶 QR 碼複製內容，或直接輸入卡片上的開箱短碼（例：STAR-4821）：");
        hint.setTextColor(ctx.getColor(R.color.ink));
        box.addView(hint);

        EditText codeInput = new EditText(ctx);
        codeInput.setHint("輸入 STAR-4821 或貼上 WATR1:...");
        box.addView(codeInput);

        new AlertDialog.Builder(ctx)
            .setTitle("🔑 開啟實體藏寶箱")
            .setView(box)
            .setPositiveButton("🎉 打開寶箱！", (d, w) -> {
                TreasureAndQrState.ClaimOutcome out = state.claimHiddenTreasureByCode(
                    progress, codeInput.getText().toString(), today
                );
                host.onStateChanged(out.message);
                if (out.ok) {
                    host.onVibrateSuccess();
                    host.onCelebrate(
                        CelebrationOverlayView.STYLE_HATCH,
                        "🎁 實體尋寶大成功！",
                        out.treasure != null ? out.treasure.title : out.message
                    );
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ──────────────── 2. 離線 QR 家族禮物交換卡（免伺服器多機互動） ────────────────

    void showFamilyGiftExchangeDialog(StepProgress progress, AdventureState state, String today) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UiKit.dp(ctx, 16), UiKit.dp(ctx, 12), UiKit.dp(ctx, 16), UiKit.dp(ctx, 12));
        scroll.addView(col);

        LinearLayout info = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, info, "💌 免伺服器多機同樂：家族禮物與旅伴串門子");
        UiKit.addCardSubtitle(ctx, info, "如果爸媽或孩子有兩支手機，可以產生一張離線 QR 禮物卡傳給對方！對方領取禮物後，你的旅伴還會飛去他的營地串門子一整天喔！");
        col.addView(info, UiKit.cardMargins(ctx));

        RadioGroup gGroup = new RadioGroup(ctx);
        RadioButton rbCare = new RadioButton(ctx);
        rbCare.setText(TreasureAndQrState.giftTypeLabel(TreasureAndQrState.GIFT_CARE_PACK));
        RadioButton rbBox = new RadioButton(ctx);
        rbBox.setText(TreasureAndQrState.giftTypeLabel(TreasureAndQrState.GIFT_EXPLORER_BOX));
        gGroup.addView(rbCare);
        gGroup.addView(rbBox);
        rbCare.setChecked(true);
        col.addView(gGroup);

        Button genBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        genBtn.setText("🎁 產生我的離線 QR 禮物卡");
        LinearLayout.LayoutParams gLp = UiKit.matchWrap();
        gLp.setMargins(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 12));
        col.addView(genBtn, gLp);

        EditText redeemInput = new EditText(ctx);
        redeemInput.setHint("收到家人的禮物碼？貼在這裡（WAGIFT1:...）");
        redeemInput.setMinLines(2);
        col.addView(redeemInput);

        Button redeemBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_STAR, UiKit.COLOR_PRIMARY_TEXT);
        redeemBtn.setText("✨ 領取家人禮物並邀請旅伴作客！");
        LinearLayout.LayoutParams rLp = UiKit.matchWrap();
        rLp.setMargins(0, UiKit.dp(ctx, 6), 0, 0);
        col.addView(redeemBtn, rLp);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle("💌 離線 QR 家族禮物交換")
            .setView(scroll)
            .setPositiveButton("關閉", null)
            .create();

        genBtn.setOnClickListener(v -> {
            int gType = rbBox.isChecked() ? TreasureAndQrState.GIFT_EXPLORER_BOX : TreasureAndQrState.GIFT_CARE_PACK;
            int cid = state.activeCompanionId();
            CompanionSystem.OwnedCompanion oc = state.companions.getOwned(cid);
            String sender = state.family.members.isEmpty() ? "探險隊長" : state.family.members.get(0).name;
            TreasureAndQrState.FamilyGiftCard card = state.treasure.createFamilyGiftCard(
                sender,
                cid,
                oc != null ? oc.stage : 1,
                oc != null && oc.shiny,
                gType,
                today,
                random
            );
            state.evaluateExtraAchievements(progress, today);
            host.onStateChanged("💌 已產生離線家族禮物卡！分享 QR 圖或複製禮物碼給家人吧！");
            dialog.dismiss();
            String buddyDesc = cid != CompanionSystem.ID_NONE ? ("並派出「" + state.activeCompanionName() + "」去營地串門子！") : "";
            showQrCardPreviewDialog(
                "💌 走走冒險 · 家族禮物卡",
                "來自「" + card.senderName + "」的禮物",
                TreasureAndQrState.giftTypeLabel(card.giftType) + " " + buddyDesc,
                card.encodedString,
                "掃描或貼上禮物碼領取",
                card.encodedString,
                UiKit.COLOR_PRIMARY
            );
        });

        redeemBtn.setOnClickListener(v -> {
            TreasureAndQrState.GiftRedeemOutcome out = state.redeemFamilyGiftCode(
                progress, redeemInput.getText().toString(), today
            );
            host.onStateChanged(out.message);
            if (out.ok) {
                host.onVibrateSuccess();
                host.onCelebrate(CelebrationOverlayView.STYLE_EVOLVE, "💌 收到家人暖心禮物！", out.message);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showQrCardPreviewDialog(
        String headerBadge,
        String mainTitle,
        String description,
        String qrPayload,
        String backupLabel,
        String copyText,
        int accentRgb
    ) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(UiKit.dp(ctx, 16), UiKit.dp(ctx, 12), UiKit.dp(ctx, 16), UiKit.dp(ctx, 8));
        scroll.addView(col);

        Bitmap cardBmp = yearbookRenderer.renderQrCardBitmap(
            headerBadge, mainTitle, description, qrPayload, backupLabel, accentRgb
        );
        ImageView iv = new ImageView(ctx);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(cardBmp);
        iv.setContentDescription(mainTitle + " 的離線 QR 碼卡片");
        col.addView(iv, UiKit.matchWrap());

        Button copyBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
        copyBtn.setText("📋 複製文字代碼");
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("WalkAdventure Code", copyText));
                host.onStateChanged("已複製代碼到剪貼簿！");
            }
        });

        Button shareBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        shareBtn.setText("📤 分享／列印 QR 圖卡");
        shareBtn.setOnClickListener(v -> {
            File png = yearbookRenderer.exportShareableQrCardPng(
                headerBadge, mainTitle, description, qrPayload, backupLabel, accentRgb
            );
            if (png == null) {
                host.onStateChanged("無法產生分享圖卡，請稍後再試");
                return;
            }
            sharePngUri(MemoryFileProvider.uriForShareQrCard(ctx), mainTitle + " — 走走冒險 2.0");
        });

        LinearLayout row = UiKit.makeButtonRow(ctx, copyBtn, shareBtn);
        LinearLayout.LayoutParams rLp = UiKit.matchWrap();
        rLp.setMargins(0, UiKit.dp(ctx, 10), 0, 0);
        col.addView(row, rLp);

        AlertDialog d = new AlertDialog.Builder(ctx)
            .setTitle(mainTitle)
            .setView(scroll)
            .setPositiveButton("完成", null)
            .create();
        d.setOnDismissListener(dialog -> {
            if (!cardBmp.isRecycled()) cardBmp.recycle();
        });
        d.show();
    }

    // ──────────────── 3. 本機足跡迷霧探索與家庭秘密基地 ────────────────

    void showFootprintMapDialog(StepProgress progress, AdventureState state, String today) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UiKit.dp(ctx, 16), UiKit.dp(ctx, 10), UiKit.dp(ctx, 16), UiKit.dp(ctx, 10));
        scroll.addView(col);

        LinearLayout privacyCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, privacyCard, "🛡️ 隱私保護足跡迷霧（200m 粗略網格）");
        UiKit.addCardSubtitle(ctx, privacyCard, "僅使用低耗電粗略定位並只存相對格數，絕不保存街道門牌或上傳伺服器！帶孩子去新公園踩破迷霧立即獲得首探大寶箱。");

        CheckBox enableCb = new CheckBox(ctx);
        enableCb.setText("開啟自動粗略定位破霧探索（預設關閉）");
        enableCb.setTextColor(ctx.getColor(R.color.ink));
        enableCb.setChecked(state.footprints.enabled);
        privacyCard.addView(enableCb);
        col.addView(privacyCard, UiKit.cardMargins(ctx));

        FootprintMapView mapView = new FootprintMapView(ctx);
        col.addView(mapView, UiKit.cardMargins(ctx));

        Button refreshLocBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        refreshLocBtn.setText("🧭 立即掃描腳下迷霧");
        Button manualBaseBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
        manualBaseBtn.setText("🚩 手動打卡新秘密基地");

        LinearLayout actRow = UiKit.makeButtonRow(ctx, refreshLocBtn, manualBaseBtn);
        col.addView(actRow, UiKit.cardMargins(ctx));

        // 已命名的家庭秘密基地清單
        List<FootprintState.Tile> bases = state.footprints.namedBases();
        if (!bases.isEmpty()) {
            UiKit.addSectionHeader(ctx, col, "🚩 我們的家庭秘密基地（" + bases.size() + " 座）");
            for (FootprintState.Tile b : bases) {
                LinearLayout bCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
                UiKit.addCardTitle(ctx, bCard, "⭐ " + b.baseName);
                UiKit.addCardSubtitle(ctx, bCard, String.format(
                    Locale.TAIWAN, "首次探訪：%s · 累計到訪 %d 次", b.discoveredDate, b.visitCount
                ));
                col.addView(bCard, UiKit.cardMargins(ctx));
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle("🗺️ 本機足跡迷霧與秘密基地")
            .setView(scroll)
            .setPositiveButton("關閉", null)
            .create();

        mapView.bind(state.footprints, tile -> showRenameSecretBaseDialog(state, tile, () -> {
            mapView.bind(state.footprints, null);
            dialog.dismiss();
            showFootprintMapDialog(progress, state, today);
        }));

        enableCb.setOnClickListener(v -> {
            if (enableCb.isChecked()) {
                Runnable turnOn = () -> {
                    state.footprints.enabled = true;
                    host.onStateChanged("🗺️ 已開啟本機足跡迷霧探索！");
                    host.onRefreshFootprintLocation();
                    mapView.invalidate();
                };
                if (!host.hasCoarseLocationPermission()) {
                    enableCb.setChecked(false);
                    host.onRequestCoarseLocationPermission(() -> {
                        enableCb.setChecked(true);
                        turnOn.run();
                    });
                } else {
                    turnOn.run();
                }
            } else {
                state.footprints.enabled = false;
                host.onStateChanged("已暫停粗略定位足跡探索（既有探索紀錄仍會保留）");
            }
        });

        refreshLocBtn.setOnClickListener(v -> {
            if (!state.footprints.enabled) {
                host.onStateChanged("請先勾選上方「開啟自動粗略定位破霧探索」，或改用右側「手動打卡新秘密基地」！ ");
                return;
            }
            if (!host.hasCoarseLocationPermission()) {
                host.onRequestCoarseLocationPermission(() -> {
                    host.onRefreshFootprintLocation();
                    mapView.invalidate();
                });
            } else {
                host.onRefreshFootprintLocation();
                mapView.invalidate();
            }
        });

        manualBaseBtn.setOnClickListener(v -> {
            EditText baseInput = new EditText(ctx);
            baseInput.setHint("例：外婆家公園、祕密溜滑梯、河濱步道");
            new AlertDialog.Builder(ctx)
                .setTitle("🚩 打卡今日家庭秘密基地")
                .setMessage("出遊到達新公園或喜歡的地方，不用開定位也能手動點亮一格足跡並取個專屬名字（每日可領一次首探寶箱）！")
                .setView(baseInput)
                .setPositiveButton("插旗打卡！", (d2, w2) -> {
                    FootprintState.DiscoveryOutcome out = state.recordManualSecretBase(
                        progress, baseInput.getText().toString(), today
                    );
                    host.onStateChanged(out.message);
                    if (out.isNewTile) {
                        host.onCelebrate(CelebrationOverlayView.STYLE_HARVEST, "🗺️ 發現家庭秘密基地！", out.message);
                    }
                    dialog.dismiss();
                    showFootprintMapDialog(progress, state, today);
                })
                .setNegativeButton("取消", null)
                .show();
        });

        dialog.show();
    }

    private void showRenameSecretBaseDialog(AdventureState state, FootprintState.Tile tile, Runnable onUpdated) {
        EditText input = new EditText(ctx);
        input.setHint("幫這個格子取名字（例：秘密溜滑梯）");
        input.setText(tile.baseName);
        new AlertDialog.Builder(ctx)
            .setTitle("🚩 命名家庭秘密基地")
            .setView(input)
            .setPositiveButton("儲存名稱", (d, w) -> {
                state.footprints.nameTile(tile.rx, tile.ry, input.getText().toString());
                host.onStateChanged("🚩 已更新秘密基地名稱！");
                if (onUpdated != null) onUpdated.run();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ──────────────── 4. Phase 4 《家庭年度回憶長圖冊》預覽與分享 ────────────────

    void showYearbookDialog(StepProgress progress, AdventureState state, WorldClock.Snapshot clock) {
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 10), UiKit.dp(ctx, 14), UiKit.dp(ctx, 10));
        scroll.addView(col);

        Bitmap poster = yearbookRenderer.renderYearbookPoster(progress, state, mediaStore, clock);
        ImageView iv = new ImageView(ctx);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(poster);
        iv.setContentDescription("家庭年度冒險回憶長圖冊預覽");
        col.addView(iv, UiKit.matchWrap());

        Button shareBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        shareBtn.setText("📤 匯出分享高解析年度長圖冊");
        shareBtn.setOnClickListener(v -> {
            File png = yearbookRenderer.exportShareableYearbookPng(progress, state, mediaStore, clock);
            if (png == null) {
                host.onStateChanged("長圖冊匯出失敗，請稍後再試");
                return;
            }
            sharePngUri(MemoryFileProvider.uriForShareYearbook(ctx), "走走冒險 2.0 — 家庭年度回憶畫冊");
            host.onCelebrate(CelebrationOverlayView.STYLE_EVOLVE, "📚 年度回憶長圖冊完成！", "把全家人的珍貴足跡分享留念吧！");
        });
        LinearLayout.LayoutParams sLp = UiKit.matchWrap();
        sLp.setMargins(0, UiKit.dp(ctx, 10), 0, 0);
        col.addView(shareBtn, sLp);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
            .setTitle("📚 家庭年度冒險回憶長圖冊")
            .setView(scroll)
            .setPositiveButton("太棒了", null)
            .create();
        dialog.setOnDismissListener(d -> {
            if (!poster.isRecycled()) poster.recycle();
        });
        dialog.show();
    }

    /** 顯示自然生態圖鑑單一類別的親子小百科彈窗。 */
    void showNatureCategoryTriviaDialog(NatureVisionEngine.CategoryDef cat, AdventureState state) {
        boolean unlocked = state.natureDex.isUnlocked(cat.id);
        int count = state.natureDex.countFor(cat.id);
        String msg = (unlocked
            ? "✅ 已收集此自然章（累計拍到 " + count + " 次）\n\n"
            : "🔒 尚未鑑定到此類別，帶孩子出門用「相機尋寶」拍下相關風景就能點亮喔！\n\n")
            + "💡 " + cat.trivia + "\n\n"
            + "🎁 鑑定稀有掉落加成：" + cat.dropSummary;
        new AlertDialog.Builder(ctx)
            .setTitle(cat.emoji + " " + cat.name + " · " + cat.badgeTitle)
            .setMessage(msg)
            .setPositiveButton("好有趣！", null)
            .show();
    }

    private void sharePngUri(Uri shareUri, String subject) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(MemoryFileProvider.MIME_PNG);
        share.putExtra(Intent.EXTRA_STREAM, shareUri);
        share.putExtra(Intent.EXTRA_TEXT, subject);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            ctx.startActivity(Intent.createChooser(share, "分享給家人收藏"));
        } catch (ActivityNotFoundException ignored) {
            host.onStateChanged("找不到可用的分享應用程式");
        }
    }
}
