package com.cornming.walkadventure;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/**
 * 「手札」分頁控制器（Phase 2 升級版）：
 * 1) 彩虹七色現實世界收集冊（點擊任一色立即開啟該色相機尋寶）
 * 2) 回憶時光機（去年的今天／過往精選明信片回顧）
 * 3) 實境冒險明信片牆（附實景縮圖、語音標記、點擊全螢幕賞圖／播放語音／分享）
 * 4) 12 位旅伴收集圖鑑與進化養成
 * 5) 10 條連貫繪本故事冊 ＋ 親子抬頭任務回憶留念卡
 */
final class JournalTabController {
    interface Host {
        void onStateChanged(String toastMessage);
        void onStartPhotoHunt(int huntId);
        void onOpenHuntPicker();
        void onViewPostcard(StoryCatalog.MemoryEntry entry);
        void onOpenTreasureHub();
        void onOpenYearbook();
        void onOpenNatureCategory(NatureVisionEngine.CategoryDef cat);
    }

    private final Context ctx;
    private final Host host;
    private final MemoryMediaStore mediaStore;

    JournalTabController(Context ctx, Host host, MemoryMediaStore mediaStore) {
        this.ctx = ctx;
        this.host = host;
        this.mediaStore = mediaStore;
    }

    void render(
        LinearLayout container,
        StepProgress progress,
        AdventureState state,
        String today
    ) {
        container.removeAllViews();

        if (state.guide.shouldShowTabTip(GuideState.TIP_JOURNAL)) {
            container.addView(GuideDialogController.buildContextualTipCard(ctx, GuideState.TIP_JOURNAL, () -> {
                state.guide.dismissTabTip(GuideState.TIP_JOURNAL);
                host.onStateChanged(null);
            }), UiKit.cardMargins(ctx));
        }

        // 1. 總覽與快速拍照尋寶／年度長圖冊按鈕
        LinearLayout summaryCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, summaryCard, String.format(
            Locale.TAIWAN,
            "📖 家庭探險手札 · 明信片 %d 張 · 旅伴 %d/%d",
            state.stories.memories.size(),
            state.companions.ownedCount(),
            CompanionSystem.SPECIES.length
        ));
        UiKit.addCardSubtitle(ctx, summaryCard, String.format(
            Locale.TAIWAN,
            "🌈 彩虹 %d/%d · 🔬 自然百科 %d/%d · 🎁 實體尋寶 %d 次",
            state.rainbowCollectedCount(),
            ColorScavenger.RAINBOW_COUNT,
            state.natureDex.unlockedCount(),
            NatureVisionEngine.CATEGORY_COUNT,
            state.treasure.totalTreasuresClaimed
        ));
        Button quickCameraBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_FIRE, UiKit.COLOR_PRIMARY_TEXT);
        quickCameraBtn.setText("📸 相機尋寶拍照");
        quickCameraBtn.setOnClickListener(v -> host.onOpenHuntPicker());

        Button yearbookBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
        yearbookBtn.setText("📚 年度回憶長圖冊");
        yearbookBtn.setOnClickListener(v -> host.onOpenYearbook());

        LinearLayout topBtnRow = UiKit.makeButtonRow(ctx, quickCameraBtn, yearbookBtn);
        LinearLayout.LayoutParams qcLp = UiKit.matchWrap();
        qcLp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
        summaryCard.addView(topBtnRow, qcLp);
        container.addView(summaryCard, UiKit.cardMargins(ctx));

        // 1.5 Phase 3：NFC／離線 QR 實體藏寶盒入口卡（雨天在家與露營必玩！）
        LinearLayout treasureCard = UiKit.makeSectionCard(ctx, Color.rgb(255, 238, 224));
        UiKit.addCardTitle(ctx, treasureCard, String.format(
            Locale.TAIWAN,
            "🎁 實體藏寶盒（NFC 卡片／離線 QR · 待尋 %d 個）",
            state.treasure.activeTreasureCount()
        ));
        UiKit.addCardSubtitle(ctx, treasureCard,
            "拿閒置悠遊卡「嗶」一下或產生離線 QR 卡藏在家裡／公園，讓孩子看謎語尋寶開箱！");
        Button treasureBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_STAR, UiKit.COLOR_PRIMARY_TEXT);
        treasureBtn.setText(state.treasure.activeTreasureCount() > 0
            ? "📡 開啟尋寶雷達・大人藏寶工坊"
            : "🕵️ 大人藏寶・設計一個實體謎語寶箱！");
        treasureBtn.setOnClickListener(v -> host.onOpenTreasureHub());
        LinearLayout.LayoutParams tLp = UiKit.matchWrap();
        tLp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
        treasureCard.addView(treasureBtn, tLp);
        container.addView(treasureCard, UiKit.cardMargins(ctx));

        // 2. 現實世界彩虹七色收集冊
        UiKit.addSectionHeader(ctx, container, String.format(
            Locale.TAIWAN,
            "🌈 現實世界彩虹七色收集冊（%d / %d · 點色塊開拍！）",
            state.rainbowCollectedCount(),
            ColorScavenger.RAINBOW_COUNT
        ));
        LinearLayout rainbowCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        LinearLayout dotRow = new LinearLayout(ctx);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < ColorScavenger.RAINBOW_COUNT; i++) {
            final int colorIdx = i;
            boolean got = state.isRainbowColorCollected(colorIdx);
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setOnClickListener(v -> host.onStartPhotoHunt(ColorScavenger.rainbowHuntForColor(colorIdx).id));

            TextView badge = new TextView(ctx);
            badge.setGravity(Gravity.CENTER);
            badge.setText(got ? "✓" : "?");
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            badge.setTextColor(colorIdx == ColorScavenger.COLOR_WHITE ? ctx.getColor(R.color.ink) : Color.WHITE);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(got ? ColorScavenger.RAINBOW_RGB[colorIdx] : Color.rgb(228, 218, 202));
            circle.setStroke(UiKit.dp(ctx, 2), ColorScavenger.RAINBOW_RGB[colorIdx]);
            badge.setBackground(circle);
            int sz = UiKit.dp(ctx, 36);
            col.addView(badge, new LinearLayout.LayoutParams(sz, sz));

            TextView lbl = new TextView(ctx);
            lbl.setText(ColorScavenger.RAINBOW_NAMES[colorIdx].substring(ColorScavenger.RAINBOW_NAMES[colorIdx].length() - 1));
            lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            lbl.setTextColor(ctx.getColor(R.color.ink));
            lbl.setPadding(0, UiKit.dp(ctx, 3), 0, 0);
            col.addView(lbl);

            dotRow.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        rainbowCard.addView(dotRow, UiKit.matchWrap());
        UiKit.addCardSubtitle(ctx, rainbowCard, state.rainbowCollectedCount() >= ColorScavenger.RAINBOW_COUNT
            ? "🎉 已集滿全部七色彩虹！可以隨時繼續拍新明信片留念！"
            : "集滿七色現實世界照片，可獲頒 +100 金幣、+35 星砂與花瓣奇蹟蛋！");
        container.addView(rainbowCard, UiKit.cardMargins(ctx));

        // 2.5 Phase 4：自然生態百科圖鑑（8 大類端側視覺自動鑑定）
        UiKit.addSectionHeader(ctx, container, String.format(
            Locale.TAIWAN,
            "🔬 端側自然百科圖鑑（%d / %d 類 · 點擊看自然小知識）",
            state.natureDex.unlockedCount(),
            NatureVisionEngine.CATEGORY_COUNT
        ));
        LinearLayout dexCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        for (int rowIdx = 0; rowIdx < 2; rowIdx++) {
            LinearLayout gridRow = new LinearLayout(ctx);
            gridRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int cIdx = 0; cIdx < 4; cIdx++) {
                final NatureVisionEngine.CategoryDef cat = NatureVisionEngine.CATEGORIES[rowIdx * 4 + cIdx];
                boolean got = state.natureDex.isUnlocked(cat.id);
                LinearLayout tile = new LinearLayout(ctx);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(UiKit.dp(ctx, 4), UiKit.dp(ctx, 8), UiKit.dp(ctx, 4), UiKit.dp(ctx, 8));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(UiKit.dp(ctx, 12));
                bg.setColor(got ? UiKit.COLOR_CARD_GREEN : UiKit.COLOR_CARD_MUTED);
                bg.setStroke(UiKit.dp(ctx, 1), got ? cat.accentRgb : Color.rgb(220, 206, 186));
                tile.setBackground(bg);
                tile.setOnClickListener(v -> host.onOpenNatureCategory(cat));

                TextView em = new TextView(ctx);
                em.setText(got ? cat.emoji : "❔");
                em.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                em.setGravity(Gravity.CENTER);
                tile.addView(em);

                TextView nm = new TextView(ctx);
                nm.setText(cat.name.substring(0, Math.min(4, cat.name.length())));
                nm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                nm.setTextColor(got ? ctx.getColor(R.color.ink) : UiKit.COLOR_SUBTITLE);
                nm.setGravity(Gravity.CENTER);
                tile.addView(nm);

                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tlp.setMargins(UiKit.dp(ctx, 3), UiKit.dp(ctx, 3), UiKit.dp(ctx, 3), UiKit.dp(ctx, 3));
                gridRow.addView(tile, tlp);
            }
            dexCard.addView(gridRow, UiKit.matchWrap());
        }
        UiKit.addCardSubtitle(ctx, dexCard, state.natureDex.isComplete()
            ? "🌟 恭喜集滿全部 8 大自然生態圖鑑！你們家就是最強小小自然學家！"
            : "每次相機尋寶都會自動進行端側自然鑑定並加贈稀有素材，集滿 8 類送星光奇蹟蛋！");
        container.addView(dexCard, UiKit.cardMargins(ctx));

        // 3. 回憶時光機（去年的今天 / 過往珍藏回顧）
        List<StoryCatalog.MemoryEntry> throwbacks = state.stories.findThrowbackMemories(today);
        if (!throwbacks.isEmpty()) {
            UiKit.addSectionHeader(ctx, container, "🕰️ 回憶時光機（翻翻過去的散步瞬間）");
            for (StoryCatalog.MemoryEntry tb : throwbacks) {
                addMemoryCard(container, tb, UiKit.COLOR_CARD_GOLD);
            }
        }

        // 4. 實境冒險明信片牆
        UiKit.addSectionHeader(ctx, container, String.format(
            Locale.TAIWAN,
            "📮 實境冒險明信片牆（共 %d 張）",
            state.stories.memories.size()
        ));
        if (state.stories.memories.isEmpty()) {
            LinearLayout emptyMem = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
            UiKit.addCardSubtitle(ctx, emptyMem, "還沒有實境明信片！帶孩子散步時點上方「相機尋寶」，拍下路邊的紅色花朵或大白雲，就能做成附帶塗鴉與語音的專屬明信片喔！");
            container.addView(emptyMem, UiKit.cardMargins(ctx));
        } else {
            int maxShow = Math.min(20, state.stories.memories.size());
            for (int i = 0; i < maxShow; i++) {
                addMemoryCard(container, state.stories.memories.get(i), UiKit.COLOR_CARD_BG);
            }
        }

        // 5. 12 位旅伴圖鑑
        UiKit.addSectionHeader(ctx, container, "🐾 旅伴圖鑑與進化養成（共 12 位）");
        for (CompanionSystem.SpeciesDef sp : CompanionSystem.SPECIES) {
            final int cid = sp.id;
            CompanionSystem.OwnedCompanion oc = state.companions.getOwned(cid);
            boolean owned = oc != null;
            boolean equipped = state.activeCompanionId() == cid;

            LinearLayout card = UiKit.makeSectionCard(
                ctx,
                equipped ? UiKit.COLOR_CARD_GREEN : (owned ? UiKit.COLOR_CARD_BG : UiKit.COLOR_CARD_MUTED)
            );
            if (owned) {
                String shinyTag = oc.shiny ? " ✨異色" : "";
                String accName = oc.accessory != CompanionSystem.ACC_NONE
                    ? " · 穿戴：" + CompanionSystem.accessoryDef(oc.accessory).name : "";
                UiKit.addCardTitle(ctx, card, String.format(
                    Locale.TAIWAN,
                    "%s（%s · Lv.%d）%s",
                    sp.name,
                    sp.stageTitle(oc.stage),
                    oc.bondLevel(),
                    shinyTag
                ));
                UiKit.addCardSubtitle(ctx, card, sp.perkSummary + accName);
                UiKit.addCardSubtitle(ctx, card, "💬 " + sp.bondStory);

                int nextGoal = oc.stage == 1 ? CompanionSystem.STAGE_2_BOND_XP
                    : (oc.stage == 2 ? CompanionSystem.STAGE_3_BOND_XP : 1200);
                card.addView(UiKit.makeHorizontalProgress(ctx, nextGoal, Math.min(nextGoal, oc.bondXp)));

                Button equipBtn = UiKit.makeActionButton(
                    ctx,
                    equipped ? UiKit.COLOR_DISABLED_BG : UiKit.COLOR_PRIMARY,
                    equipped ? UiKit.COLOR_DISABLED_TEXT : UiKit.COLOR_PRIMARY_TEXT
                );
                equipBtn.setText(equipped ? "目前同行中 ✓" : "換牠同行");
                equipBtn.setEnabled(!equipped);
                equipBtn.setOnClickListener(v -> {
                    AdventureState.ActionResult r = state.equipV2Companion(cid);
                    host.onStateChanged(r.message);
                });

                Button accBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
                accBtn.setText("🎀 換飾品／餵點心");
                accBtn.setOnClickListener(v -> showCompanionCareDialog(progress, state, cid, today));
                card.addView(UiKit.makeButtonRow(ctx, equipBtn, accBtn), UiKit.matchWrap());
            } else {
                UiKit.addCardTitle(ctx, card, "🔒 " + sp.name + " · " + sp.role);
                UiKit.addCardSubtitle(ctx, card, "技能：" + sp.perkSummary);
                UiKit.addCardSubtitle(ctx, card, "遇見方式：" + sp.obtainHint);

                if (cid >= 1 && cid < AdventureCatalog.COMPANION_COUNT) {
                    AdventureCatalog.CompanionDef legacyDef = AdventureCatalog.companion(cid);
                    boolean afford = progress.coins >= legacyDef.cost;
                    Button recruitBtn = UiKit.makeActionButton(
                        ctx,
                        afford ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG,
                        afford ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT
                    );
                    recruitBtn.setText(String.format(Locale.TAIWAN, "用金幣直接招募（%d 金幣）", legacyDef.cost));
                    recruitBtn.setOnClickListener(v -> {
                        AdventureState.ActionResult r = state.buyOrEquipCompanion(progress, cid, today);
                        host.onStateChanged(r.message);
                    });
                    LinearLayout.LayoutParams lp = UiKit.matchWrap();
                    lp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
                    card.addView(recruitBtn, lp);
                }
            }
            container.addView(card, UiKit.cardMargins(ctx));
        }

        // 6. 連貫繪本故事冊
        UiKit.addSectionHeader(ctx, container, "📚 連貫繪本故事冊（10 條故事 × 3 話）");
        for (StoryCatalog.ArcDef arc : StoryCatalog.ARCS) {
            boolean done = state.stories.isArcCompleted(arc.id);
            boolean active = state.stories.activeArcId == arc.id;
            LinearLayout aCard = UiKit.makeSectionCard(
                ctx,
                done ? UiKit.COLOR_CARD_GREEN : (active ? UiKit.COLOR_CARD_GOLD : UiKit.COLOR_CARD_BG)
            );
            String status = done
                ? "✓ 已讀完（徽章：" + arc.badgeTitle + "）"
                : (active ? "📖 連載中：第 " + state.stories.activeChapter + " / 3 話" : "尚未展開");
            UiKit.addCardTitle(ctx, aCard, arc.title);
            UiKit.addCardSubtitle(ctx, aCard, status);
            if (done || active) {
                int showCount = done ? StoryCatalog.CHAPTERS_PER_ARC : state.stories.activeChapter;
                StringBuilder chaptersLine = new StringBuilder();
                for (int i = 0; i < showCount; i++) {
                    if (i > 0) chaptersLine.append(" → ");
                    chaptersLine.append("第").append(i + 1).append("話 ").append(arc.chapters[i].title);
                }
                UiKit.addCardSubtitle(ctx, aCard, chaptersLine.toString());
            }
            container.addView(aCard, UiKit.cardMargins(ctx));
        }

        // 7. 親子抬頭任務回憶留念卡
        UiKit.addSectionHeader(ctx, container, "💌 親子抬頭任務回憶留念");
        if (state.stories.recentNotes.isEmpty()) {
            LinearLayout emptyNote = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
            UiKit.addCardSubtitle(ctx, emptyNote, "在「探險」分頁完成抬頭任務，就能把今天一起看到的風景與孩子的一句話收進這裡！");
            container.addView(emptyNote, UiKit.cardMargins(ctx));
        } else {
            for (StoryCatalog.LookUpNote note : state.stories.recentNotes) {
                LinearLayout nCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
                UiKit.addCardTitle(ctx, nCard, String.format(Locale.TAIWAN, "%s · %s", note.date, note.questTitle));
                UiKit.addCardSubtitle(ctx, nCard, "出隊：" + note.squadLabel);
                UiKit.addCardSubtitle(ctx, nCard, "「" + note.note + "」");
                container.addView(nCard, UiKit.cardMargins(ctx));
            }
        }
    }

    private void addMemoryCard(LinearLayout container, StoryCatalog.MemoryEntry entry, int bgColor) {
        LinearLayout card = UiKit.makeSectionCard(ctx, bgColor);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Bitmap thumb = mediaStore != null ? mediaStore.loadPreviewBitmap(entry.photoPath, 200) : null;
        if (thumb != null) {
            ImageView iv = new ImageView(ctx);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(thumb);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int sz = UiKit.dp(ctx, 68);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
            ilp.setMargins(0, 0, UiKit.dp(ctx, 12), 0);
            row.addView(iv, ilp);
        }

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        StringBuilder starStr = new StringBuilder();
        for (int s = 0; s < entry.stars; s++) starStr.append("★");
        String badges = (entry.audioPath.isEmpty() ? "" : " 🎤") + (entry.doodlePath.isEmpty() ? "" : " 🎨");
        UiKit.addCardTitle(ctx, col, entry.title + " " + starStr + badges);
        UiKit.addCardSubtitle(ctx, col, String.format(
            Locale.TAIWAN,
            "%s · %s · 🌈%s",
            entry.date,
            entry.squadLabel.isEmpty() ? "探險隊長" : entry.squadLabel,
            entry.colorName.isEmpty() ? "實境發現" : entry.colorName
        ));
        if (!entry.note.isEmpty()) {
            UiKit.addCardSubtitle(ctx, col, "「" + entry.note + "」");
        }
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row, UiKit.matchWrap());

        Button viewBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_WARM_BTN, ctx.getColor(R.color.ink));
        viewBtn.setText(entry.audioPath.isEmpty() ? "📮 查看／分享實境明信片" : "📮 賞圖・聽語音・分享明信片");
        viewBtn.setOnClickListener(v -> host.onViewPostcard(entry));
        LinearLayout.LayoutParams vlp = UiKit.matchWrap();
        vlp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
        card.addView(viewBtn, vlp);

        container.addView(card, UiKit.cardMargins(ctx));
    }

    private void showCompanionCareDialog(StepProgress progress, AdventureState state, int cid, String today) {
        CompanionSystem.SpeciesDef sp = CompanionSystem.species(cid);
        if (sp == null) return;
        String[] options = new String[CompanionSystem.ACC_COUNT + 1];
        options[0] = String.format(Locale.TAIWAN, "🍪 餵一份冒險點心 (+40 羈絆 · 持有 %d 份)", state.camp.snacks);
        for (int a = 0; a < CompanionSystem.ACC_COUNT; a++) {
            CompanionSystem.AccessoryDef ad = CompanionSystem.accessoryDef(a);
            boolean unlocked = state.companions.isAccessoryUnlocked(a);
            options[a + 1] = unlocked
                ? ("🎀 換上：" + ad.name + "（已擁有）")
                : String.format(Locale.TAIWAN, "🛠️ 製作並戴上：%s（%d金幣+%d星砂 · 工坊Lv.%d）",
                    ad.name, ad.coinCost, ad.starCost, ad.minWorkshopLevel);
        }
        new AlertDialog.Builder(ctx)
            .setTitle("照顧「" + sp.name + "」")
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    AdventureState.ActionResult r = state.feedCompanion(progress, cid, today);
                    host.onStateChanged(r.message);
                } else {
                    int accId = which - 1;
                    AdventureState.ActionResult r = state.companions.buyOrEquipAccessory(cid, accId, progress, state.camp);
                    host.onStateChanged(r.message);
                }
            })
            .setNegativeButton("關閉", null)
            .show();
    }
}
