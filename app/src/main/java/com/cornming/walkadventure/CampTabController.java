package com.cornming.walkadventure;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.Locale;

/**
 * 「營地」分頁控制器：
 * 互動式秘密營地畫布、營火開箱、孵蛋巢（暖蛋／毛毯／破殼）、奇幻小菜園、
 * 6 座營地建設升級、點心餵食與經典冒險裝備／工坊。
 */
final class CampTabController {
    interface Host {
        void onStateChanged(String toastMessage);
        void onOpenCampfireDialog();
        void onHatchEggRequested(int eggListIndex);
        void onHarvestGardenRequested(int slot);
        void onCompanionPetted(int companionId);
    }

    private final Context ctx;
    private final Host host;
    private final StorybookAssets assets;
    private CampCanvasView campCanvas;

    CampTabController(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        this.assets = new StorybookAssets(ctx);
    }

    void startAnimation() {
        if (campCanvas != null) campCanvas.startAnimation();
    }

    void stopAnimation() {
        if (campCanvas != null) campCanvas.stopAnimation();
    }

    void render(
        LinearLayout container,
        StepProgress progress,
        AdventureState state,
        WorldClock.Snapshot clock
    ) {
        container.removeAllViews();
        String today = clock.isoDate;

        // 1. 互動式秘密營地畫布
        if (campCanvas == null) {
            campCanvas = new CampCanvasView(ctx);
        }
        campCanvas.bind(state, clock);
        campCanvas.setOnCampTapListener(new CampCanvasView.OnCampTapListener() {
            @Override public void onCampfireTapped() {
                host.onOpenCampfireDialog();
            }

            @Override public void onEggNestTapped() {
                if (state.companions.eggs.isEmpty()) {
                    host.onStateChanged("孵蛋巢空著，繼續散步開寶箱或種彩虹蘿蔔就會撿到神祕蛋！");
                    return;
                }
                for (int i = 0; i < state.companions.eggs.size(); i++) {
                    if (state.companions.eggs.get(i).isReadyToHatch()) {
                        host.onHatchEggRequested(i);
                        return;
                    }
                }
                AdventureState.ActionResult warm = state.companions.warmEggDaily(0, today);
                host.onStateChanged(warm.message);
            }

            @Override public void onGardenPlotTapped(int slot) {
                if (slot >= state.camp.unlockedGardenSlots()) {
                    host.onStateChanged("把「奇幻小菜園」升至 Lv." + (slot + 1) + " 即可解鎖這格花圃！");
                    return;
                }
                CampState.GardenPlot plot = state.camp.gardenPlots[slot];
                if (plot.isEmpty()) {
                    showPlantSeedDialog(progress, state, slot);
                } else if (plot.isReady()) {
                    host.onHarvestGardenRequested(slot);
                } else {
                    int remain = Math.max(0, plot.requiredSteps - plot.waterSteps);
                    host.onStateChanged("🌱 再走 " + remain + " 步澆水就會成熟囉！");
                }
            }

            @Override public void onCompanionTapped(int companionId) {
                host.onCompanionPetted(companionId);
            }
        });
        LinearLayout.LayoutParams canvasLp = UiKit.cardMargins(ctx);
        container.addView(campCanvas, canvasLp);

        // 2. 營地資源總覽＋快速按鈕（營火開箱 / 餵點心）
        LinearLayout walletCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_GOLD);
        UiKit.addCardTitle(ctx, walletCard, String.format(
            Locale.TAIWAN,
            "🪙 金幣 %d · ✨ 星砂 %d · 🍪 點心 %d · 🧣 毛毯 %d",
            progress.coins,
            state.camp.starSand,
            state.camp.snacks,
            state.camp.warmBlankets
        ));
        UiKit.addCardSubtitle(ctx, walletCard, String.format(
            Locale.TAIWAN,
            "🪵 木頭 %d · 🌸 花瓣 %d · 工坊折扣 -%d%% · 異色加成 +%d%%",
            state.camp.wood,
            state.camp.petals,
            state.camp.workshopDiscountPercent(),
            state.camp.shinyBonusPercent()
        ));

        Button campfireBtn = UiKit.makeActionButton(
            ctx,
            state.vault.count() > 0 ? UiKit.COLOR_ACCENT_FIRE : UiKit.COLOR_WARM_BTN,
            state.vault.count() > 0 ? UiKit.COLOR_PRIMARY_TEXT : ctx.getColor(R.color.ink)
        );
        campfireBtn.setText(state.vault.count() > 0
            ? String.format(Locale.TAIWAN, "🔥 營火開箱 (%d)", state.vault.count())
            : "🔥 營火開箱 (0)");
        campfireBtn.setOnClickListener(v -> host.onOpenCampfireDialog());

        int activeCid = state.activeCompanionId();
        boolean canFeed = activeCid != CompanionSystem.ID_NONE && state.companions.isOwned(activeCid) && state.camp.snacks > 0;
        Button feedBtn = UiKit.makeActionButton(
            ctx,
            canFeed ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG,
            canFeed ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT
        );
        feedBtn.setText(activeCid != CompanionSystem.ID_NONE
            ? "🍪 餵 " + state.activeCompanionName()
            : "🍪 先選旅伴同行");
        feedBtn.setEnabled(activeCid != CompanionSystem.ID_NONE && state.companions.isOwned(activeCid));
        feedBtn.setOnClickListener(v -> {
            AdventureState.ActionResult res = state.feedCompanion(progress, activeCid, today);
            host.onStateChanged(res.message);
        });

        LinearLayout actionRow = UiKit.makeButtonRow(ctx, campfireBtn, feedBtn);
        LinearLayout.LayoutParams rowLp = UiKit.matchWrap();
        rowLp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
        walletCard.addView(actionRow, rowLp);
        container.addView(walletCard, UiKit.cardMargins(ctx));

        // 3. 孵蛋巢區塊
        UiKit.addSectionHeader(ctx, container, String.format(
            Locale.TAIWAN,
            "🥚 孵蛋巢（%d / %d 格 · 走路推進孵蛋）",
            state.companions.eggs.size(),
            CompanionSystem.MAX_EGG_SLOTS
        ));
        if (state.companions.eggs.isEmpty()) {
            LinearLayout emptyEgg = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
            UiKit.addCardSubtitle(ctx, emptyEgg, "目前沒有正在孵化的蛋。散步開寶箱、完成連貫故事結局或收成彩虹蘿蔔都會撿到神祕蛋！");
            container.addView(emptyEgg, UiKit.cardMargins(ctx));
        } else {
            for (int i = 0; i < state.companions.eggs.size(); i++) {
                final int idx = i;
                CompanionSystem.EggSlot slot = state.companions.eggs.get(i);
                CompanionSystem.EggTypeDef def = CompanionSystem.eggTypeDef(slot.eggType);
                boolean ready = slot.isReadyToHatch();
                LinearLayout eggCard = UiKit.makeSectionCard(ctx, ready ? UiKit.COLOR_CARD_GREEN : UiKit.COLOR_CARD_BG);
                addIconTitleRow(eggCard, assets.eggDrawable(),
                    def.name + (ready ? "（✨ 準備破殼！）" : ""),
                    String.format(Locale.TAIWAN, "孵化進度：%d / %d 步", slot.stepsWalked, slot.stepsRequired)
                );
                eggCard.addView(UiKit.makeHorizontalProgress(ctx, slot.stepsRequired, slot.stepsWalked));

                if (ready) {
                    Button hatchBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_ACCENT_FIRE, UiKit.COLOR_PRIMARY_TEXT);
                    hatchBtn.setText("🐣 點擊破殼迎接新夥伴！");
                    hatchBtn.setOnClickListener(v -> host.onHatchEggRequested(idx));
                    eggCard.addView(hatchBtn, UiKit.matchWrap());
                } else {
                    boolean warmedToday = today.equals(slot.lastWarmDate);
                    Button warmBtn = UiKit.makeActionButton(
                        ctx,
                        warmedToday ? UiKit.COLOR_DISABLED_BG : UiKit.COLOR_WARM_BTN,
                        warmedToday ? UiKit.COLOR_DISABLED_TEXT : ctx.getColor(R.color.ink)
                    );
                    warmBtn.setText(warmedToday ? "💛 今日已暖蛋" : "💛 親子暖蛋 (+300步)");
                    warmBtn.setEnabled(!warmedToday);
                    warmBtn.setOnClickListener(v -> {
                        AdventureState.ActionResult r = state.companions.warmEggDaily(idx, today);
                        host.onStateChanged(r.message);
                    });

                    boolean hasBlanket = state.camp.warmBlankets > 0;
                    Button blanketBtn = UiKit.makeActionButton(
                        ctx,
                        hasBlanket ? UiKit.COLOR_ACCENT_STAR : UiKit.COLOR_DISABLED_BG,
                        hasBlanket ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT
                    );
                    blanketBtn.setText("🧣 毛毯 (+1000步)");
                    blanketBtn.setEnabled(hasBlanket);
                    blanketBtn.setOnClickListener(v -> {
                        AdventureState.ActionResult r = state.companions.useWarmBlanket(idx, state.camp);
                        host.onStateChanged(r.message);
                    });
                    eggCard.addView(UiKit.makeButtonRow(ctx, warmBtn, blanketBtn), UiKit.matchWrap());
                }
                container.addView(eggCard, UiKit.cardMargins(ctx));
            }
        }

        // 4. 奇幻小菜園區塊
        UiKit.addSectionHeader(ctx, container, String.format(
            Locale.TAIWAN,
            "🌱 奇幻小菜園（已解鎖 %d / %d 格 · 走路自動澆水）",
            state.camp.unlockedGardenSlots(),
            CampState.GARDEN_PLOT_COUNT
        ));
        for (int s = 0; s < state.camp.unlockedGardenSlots(); s++) {
            final int slotIdx = s;
            CampState.GardenPlot plot = state.camp.gardenPlots[s];
            LinearLayout plotCard = UiKit.makeSectionCard(ctx, plot.isReady() ? UiKit.COLOR_CARD_GREEN : UiKit.COLOR_CARD_BG);
            if (plot.isEmpty()) {
                UiKit.addCardTitle(ctx, plotCard, (s + 1) + " 號花圃（空地）");
                UiKit.addCardSubtitle(ctx, plotCard, "種下奇幻種子，走路就會自動澆水，成熟可採收旅伴點心與星砂！");
                Button plantBtn = UiKit.makeActionButton(ctx, UiKit.COLOR_PRIMARY, UiKit.COLOR_PRIMARY_TEXT);
                plantBtn.setText("🌱 選擇種子播種");
                plantBtn.setOnClickListener(v -> showPlantSeedDialog(progress, state, slotIdx));
                LinearLayout.LayoutParams lp = UiKit.matchWrap();
                lp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
                plotCard.addView(plantBtn, lp);
            } else {
                CampState.SeedDef seed = CampState.seedDef(plot.seedId);
                addIconTitleRow(
                    plotCard,
                    assets.drawableForCrop(plot.seedId, plot.isReady()),
                    (s + 1) + " 號花圃：" + seed.name.replace("種子", "") + (plot.isReady() ? " ✨ 已成熟！" : ""),
                    String.format(Locale.TAIWAN, "澆水進度：%d / %d 步", plot.waterSteps, plot.requiredSteps)
                );
                plotCard.addView(UiKit.makeHorizontalProgress(ctx, plot.requiredSteps, plot.waterSteps));
                Button harvestBtn = UiKit.makeActionButton(
                    ctx,
                    plot.isReady() ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG,
                    plot.isReady() ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT
                );
                harvestBtn.setText(plot.isReady() ? "🧺 立即採收點心與星砂！" : "散步澆水成長中…");
                harvestBtn.setEnabled(plot.isReady());
                harvestBtn.setOnClickListener(v -> host.onHarvestGardenRequested(slotIdx));
                plotCard.addView(harvestBtn, UiKit.matchWrap());
            }
            container.addView(plotCard, UiKit.cardMargins(ctx));
        }

        // 5. 營地建設升級（6 座建築）
        UiKit.addSectionHeader(ctx, container, "⛺ 秘密營地建設（Lv.0 → Lv.5）");
        for (CampState.BuildingDef def : CampState.BUILDINGS) {
            final int bId = def.id;
            int lv = state.camp.buildingLevel(bId);
            boolean maxed = lv >= CampState.BUILDING_MAX_LEVEL;
            int coinCost = state.camp.upgradeCoinCost(bId);
            int starCost = state.camp.upgradeStarSandCost(bId);
            boolean canAfford = !maxed && progress.coins >= coinCost && state.camp.starSand >= starCost;

            LinearLayout bCard = UiKit.makeSectionCard(ctx, lv > 0 ? UiKit.COLOR_CARD_BG : UiKit.COLOR_CARD_MUTED);
            addIconTitleRow(
                bCard,
                assets.drawableForBuilding(bId),
                String.format(Locale.TAIWAN, "%s（Lv.%d / %d）", def.name, lv, CampState.BUILDING_MAX_LEVEL),
                def.subtitle
            );
            bCard.addView(UiKit.makeHorizontalProgress(ctx, CampState.BUILDING_MAX_LEVEL, lv));
            Button upBtn = UiKit.makeActionButton(
                ctx,
                maxed ? UiKit.COLOR_DISABLED_BG : (canAfford ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG),
                maxed ? UiKit.COLOR_DISABLED_TEXT : (canAfford ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT)
            );
            if (maxed) {
                upBtn.setText("已建成最高等級 ★");
                upBtn.setEnabled(false);
            } else {
                String costText = starCost > 0
                    ? String.format(Locale.TAIWAN, "%s（%d 金幣 + %d 星砂）", lv == 0 ? "建造設施" : "升級設施", coinCost, starCost)
                    : String.format(Locale.TAIWAN, "建造設施（%d 金幣）", coinCost);
                upBtn.setText(costText);
                upBtn.setOnClickListener(v -> {
                    AdventureState.ActionResult r = state.upgradeCampBuilding(progress, bId, today);
                    host.onStateChanged(r.message);
                });
            }
            bCard.addView(upBtn, UiKit.matchWrap());
            container.addView(bCard, UiKit.cardMargins(ctx));
        }

        // 6. 工坊補給站（點心、毛毯）
        UiKit.addSectionHeader(ctx, container, "🧺 營地工坊補給");
        LinearLayout supplyCard = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
        UiKit.addCardTitle(ctx, supplyCard, "冒險點心與暖蛋毛毯");
        UiKit.addCardSubtitle(ctx, supplyCard, "點心可快速提升旅伴羈絆並觸發進化；暖蛋毛毯可立即推進 +1,000 步孵蛋進度。");
        int snackPrice = state.camp.snackCoinPrice();
        Button buySnackBtn = UiKit.makeActionButton(
            ctx,
            progress.coins >= snackPrice ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG,
            progress.coins >= snackPrice ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT
        );
        buySnackBtn.setText(String.format(Locale.TAIWAN, "🍪 買點心 (%d金幣)", snackPrice));
        buySnackBtn.setOnClickListener(v -> {
            AdventureState.ActionResult r = state.camp.buySnack(progress);
            host.onStateChanged(r.message);
        });

        Button buyBlanketBtn = UiKit.makeActionButton(
            ctx,
            state.camp.starSand >= CampState.BASE_BLANKET_PRICE ? UiKit.COLOR_ACCENT_STAR : UiKit.COLOR_DISABLED_BG,
            state.camp.starSand >= CampState.BASE_BLANKET_PRICE ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT
        );
        buyBlanketBtn.setText(String.format(Locale.TAIWAN, "🧣 換毛毯 (%d星砂)", CampState.BASE_BLANKET_PRICE));
        buyBlanketBtn.setOnClickListener(v -> {
            AdventureState.ActionResult r = state.camp.buyWarmBlanket();
            host.onStateChanged(r.message);
        });
        LinearLayout sRow = UiKit.makeButtonRow(ctx, buySnackBtn, buyBlanketBtn);
        LinearLayout.LayoutParams sLp = UiKit.matchWrap();
        sLp.setMargins(0, UiKit.dp(ctx, 8), 0, 0);
        supplyCard.addView(sRow, sLp);
        container.addView(supplyCard, UiKit.cardMargins(ctx));

        // 7. 經典冒險裝備升級（保留原有 3 件永久裝備）
        UiKit.addSectionHeader(ctx, container, "🎒 冒險裝備升級（永久被動加成）");
        for (int g = 0; g < AdventureCatalog.GEAR_COUNT; g++) {
            final int gearIdx = g;
            int level = state.gearLevel(gearIdx);
            int cost = AdventureCatalog.gearUpgradeCost(gearIdx, level);
            boolean maxed = level >= AdventureCatalog.GEAR_MAX_LEVEL;
            boolean affordable = !maxed && progress.coins >= cost;
            LinearLayout card = UiKit.makeSectionCard(ctx, UiKit.COLOR_CARD_BG);
            UiKit.addCardTitle(ctx, card, AdventureCatalog.gearName(gearIdx));
            UiKit.addCardSubtitle(ctx, card, AdventureCatalog.gearEffectSummary(gearIdx, level));
            card.addView(UiKit.makeHorizontalProgress(ctx, AdventureCatalog.GEAR_MAX_LEVEL, level));
            Button buyBtn = UiKit.makeActionButton(
                ctx,
                maxed ? UiKit.COLOR_DISABLED_BG : (affordable ? UiKit.COLOR_PRIMARY : UiKit.COLOR_DISABLED_BG),
                maxed ? UiKit.COLOR_DISABLED_TEXT : (affordable ? UiKit.COLOR_PRIMARY_TEXT : UiKit.COLOR_DISABLED_TEXT)
            );
            buyBtn.setText(maxed ? "已達最高等級 ★" : String.format(Locale.TAIWAN, "升級裝備（%d 金幣）", cost));
            buyBtn.setEnabled(!maxed);
            buyBtn.setOnClickListener(v -> {
                AdventureState.ActionResult res = state.upgradeGear(progress, gearIdx, today);
                host.onStateChanged(res.message);
            });
            card.addView(buyBtn, UiKit.matchWrap());
            container.addView(card, UiKit.cardMargins(ctx));
        }
    }

    private void showPlantSeedDialog(StepProgress progress, AdventureState state, int slot) {
        String[] labels = new String[CampState.SEEDS.length - 1];
        for (int i = 1; i < CampState.SEEDS.length; i++) {
            CampState.SeedDef sd = CampState.SEEDS[i];
            int price = state.camp.seedCoinPrice(sd.id);
            int steps = state.camp.effectiveSeedSteps(sd.id);
            labels[i - 1] = String.format(
                Locale.TAIWAN,
                "%s（%d 金幣 · 需 %d 步 → 點心×%d、星砂+%d%s）",
                sd.name, price, steps, sd.snacksReward, sd.starSandReward,
                sd.eggDropChancePct > 0 ? "、有機會掉蛋！" : ""
            );
        }
        new AlertDialog.Builder(ctx)
            .setTitle("選擇要種在 " + (slot + 1) + " 號花圃的種子")
            .setItems(labels, (d, which) -> {
                int seedId = which + 1;
                AdventureState.ActionResult res = state.camp.plantSeed(progress, slot, seedId);
                host.onStateChanged(res.message);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void addIconTitleRow(LinearLayout card, Drawable icon, String title, String subtitle) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(icon);
            iv.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int sz = UiKit.dp(ctx, 40);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
            ilp.setMargins(0, 0, UiKit.dp(ctx, 10), 0);
            row.addView(iv, ilp);
        }
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        UiKit.addCardTitle(ctx, textCol, title);
        UiKit.addCardSubtitle(ctx, textCol, subtitle);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row, UiKit.matchWrap());
    }
}
