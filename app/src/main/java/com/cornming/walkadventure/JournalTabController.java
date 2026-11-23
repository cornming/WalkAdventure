package com.cornming.walkadventure;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.Locale;

/**
 * 「手札」分頁控制器：
 * 1) 12 位旅伴收集圖鑑（3 階段進化、閃亮異色、飾品穿戴、設為同行、元老金幣直招）
 * 2) 10 條連貫繪本故事冊
 * 3) 親子抬頭任務回憶留念卡（Phase 2 相機尋寶與冒險明信片預留升級區）
 */
final class JournalTabController {
    interface Host {
        void onStateChanged(String toastMessage);
    }

    private final Context ctx;
    private final Host host;

    JournalTabController(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
    }

    void render(
        LinearLayout container,
        StepProgress progress,
        AdventureState state,
        String today
    ) {
        container.removeAllViews();

        // 1. 旅伴收集圖鑑總覽
        LinearLayout summaryCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, summaryCard, String.format(
            Locale.TAIWAN,
            "📖 家庭探險手札 · 旅伴 %d / %d · 繪本 %d / %d",
            state.companions.ownedCount(),
            CompanionSystem.SPECIES.length,
            state.stories.completedArcCount(),
            StoryCatalog.ARC_COUNT
        ));
        UiKit.addCardSubtitle(ctx, summaryCard, String.format(
            Locale.TAIWAN,
            "✨ 閃亮異色：%d 位 · 👀 累計完成親子抬頭任務：%d 次",
            state.companions.shinyCount(),
            state.stories.totalLookUpCompleted
        ));
        container.addView(summaryCard, UiKit.cardMargins(ctx));

        // 2. 12 位旅伴圖鑑
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

                // 保留 4 位元老旅伴也可直接用金幣招募（向下相容老習慣）
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

        // 3. 連貫繪本故事冊
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

        // 4. 親子抬頭任務回憶留念卡
        UiKit.addSectionHeader(ctx, container, "💌 親子散步回憶留念卡");
        if (state.stories.recentNotes.isEmpty()) {
            LinearLayout emptyNote = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
            UiKit.addCardSubtitle(ctx, emptyNote, "在「探險」分頁切換親子模式完成抬頭任務，就能把今天一起看到的風景與孩子的一句話收進這裡！");
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
