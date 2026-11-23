package com.cornming.walkadventure;

import java.util.Random;

/**
 * 親子秘密營地與奇幻小菜園狀態（純 Java）：
 * 6 座多階段建設建築（Lv.0→5）＋ 3 格走路澆水菜園 ＋ 消耗品與自然素材庫，
 * 徹底解決後期金幣無處可花的問題。
 */
final class CampState {
    static final int BUILDING_COUNT = 6;
    static final int BUILDING_MAX_LEVEL = 5;
    static final int GARDEN_PLOT_COUNT = 3;

    static final int BLD_CAMPFIRE = 0;    // 營火廣場（初始 Lv.1）
    static final int BLD_GARDEN = 1;      // 奇幻小菜園（初始 Lv.1）
    static final int BLD_PLAYGROUND = 2;  // 旅伴樂園
    static final int BLD_OBSERVATORY = 3; // 星光觀景台
    static final int BLD_GALLERY = 4;     // 回憶畫廊小屋（Phase 2 掛真實明信片）
    static final int BLD_WORKSHOP = 5;    // 探險工坊

    static final int SEED_NONE = 0;
    static final int SEED_SUNFLOWER = 1;
    static final int SEED_STARBERRY = 2;
    static final int SEED_RADISH = 3;

    static final int BASE_SNACK_PRICE = 35;
    static final int BASE_BLANKET_PRICE = 18; // 星砂售價

    static final class BuildingDef {
        final int id;
        final String key;
        final String name;
        final String subtitle;
        final int baseCoinCost;
        final int stepCoinCost;
        final int baseStarCost;

        BuildingDef(int id, String key, String name, String subtitle, int baseCoinCost, int stepCoinCost, int baseStarCost) {
            this.id = id;
            this.key = key;
            this.name = name;
            this.subtitle = subtitle;
            this.baseCoinCost = baseCoinCost;
            this.stepCoinCost = stepCoinCost;
            this.baseStarCost = baseStarCost;
        }
    }

    static final class SeedDef {
        final int id;
        final String name;
        final int coinCost;
        final int requiredSteps;
        final int snacksReward;
        final int starSandReward;
        final int petalsReward;
        final int eggDropChancePct;

        SeedDef(
            int id,
            String name,
            int coinCost,
            int requiredSteps,
            int snacksReward,
            int starSandReward,
            int petalsReward,
            int eggDropChancePct
        ) {
            this.id = id;
            this.name = name;
            this.coinCost = coinCost;
            this.requiredSteps = requiredSteps;
            this.snacksReward = snacksReward;
            this.starSandReward = starSandReward;
            this.petalsReward = petalsReward;
            this.eggDropChancePct = eggDropChancePct;
        }
    }

    static final class GardenPlot {
        final int slot;
        int seedId = SEED_NONE;
        int waterSteps = 0;
        int requiredSteps = 0;

        GardenPlot(int slot) {
            this.slot = slot;
        }

        boolean isEmpty() {
            return seedId == SEED_NONE;
        }

        boolean isReady() {
            return seedId != SEED_NONE && requiredSteps > 0 && waterSteps >= requiredSteps;
        }

        float progressRatio() {
            if (seedId == SEED_NONE || requiredSteps <= 0) return 0f;
            return Math.min(1f, waterSteps / (float) requiredSteps);
        }
    }

    static final class HarvestResult {
        final boolean ok;
        final int snacks;
        final int starSand;
        final int petals;
        final int droppedEggType; // -1 表示無掉落
        final String message;

        HarvestResult(boolean ok, int snacks, int starSand, int petals, int droppedEggType, String message) {
            this.ok = ok;
            this.snacks = snacks;
            this.starSand = starSand;
            this.petals = petals;
            this.droppedEggType = droppedEggType;
            this.message = message;
        }
    }

    static final BuildingDef[] BUILDINGS = {
        new BuildingDef(BLD_CAMPFIRE, "campfire", "營火廣場", "提高親子營火開箱金幣與星砂加成", 120, 90, 8),
        new BuildingDef(BLD_GARDEN, "garden", "奇幻小菜園", "解鎖更多花圃並加速走路澆水成長", 110, 85, 6),
        new BuildingDef(BLD_PLAYGROUND, "playground", "旅伴樂園", "蓋滑梯與樹屋，每日摸摸旅伴羈絆提升", 140, 100, 8),
        new BuildingDef(BLD_OBSERVATORY, "observatory", "星光觀景台", "提高稀有神祕蛋與閃亮異色機率", 160, 110, 10),
        new BuildingDef(BLD_GALLERY, "gallery", "回憶畫廊小屋", "展示繪本故事與實境冒險明信片回憶牆", 130, 95, 8),
        new BuildingDef(BLD_WORKSHOP, "workshop", "探險工坊", "種子與點心打折並解鎖旅伴可愛飾品", 150, 100, 9)
    };

    static final SeedDef[] SEEDS = {
        new SeedDef(SEED_NONE, "空花圃", 0, 0, 0, 0, 0, 0),
        new SeedDef(SEED_SUNFLOWER, "向日葵種子", 20, 1000, 2, 8, 2, 0),
        new SeedDef(SEED_STARBERRY, "星星莓種子", 40, 2200, 4, 18, 4, 8),
        new SeedDef(SEED_RADISH, "彩虹蘿蔔種子", 65, 3500, 6, 32, 6, 22)
    };

    int starSand = 0;
    int snacks = 2;
    int warmBlankets = 1;
    int wood = 10;
    int petals = 6;

    final int[] buildingLevels = new int[BUILDING_COUNT];
    final GardenPlot[] gardenPlots = new GardenPlot[GARDEN_PLOT_COUNT];

    CampState() {
        buildingLevels[BLD_CAMPFIRE] = 1;
        buildingLevels[BLD_GARDEN] = 1;
        for (int i = 0; i < GARDEN_PLOT_COUNT; i++) {
            gardenPlots[i] = new GardenPlot(i);
        }
    }

    static BuildingDef buildingDef(int id) {
        if (id < 0 || id >= BUILDINGS.length) return BUILDINGS[0];
        return BUILDINGS[id];
    }

    static SeedDef seedDef(int seedId) {
        if (seedId < 0 || seedId >= SEEDS.length) return SEEDS[0];
        return SEEDS[seedId];
    }

    int buildingLevel(int id) {
        if (id < 0 || id >= BUILDING_COUNT) return 0;
        return buildingLevels[id];
    }

    void setBuildingLevel(int id, int lv) {
        if (id < 0 || id >= BUILDING_COUNT) return;
        buildingLevels[id] = Math.max(0, Math.min(BUILDING_MAX_LEVEL, lv));
    }

    int totalBuildingLevels() {
        int sum = 0;
        for (int lv : buildingLevels) sum += lv;
        return sum;
    }

    int upgradeCoinCost(int id) {
        int cur = buildingLevel(id);
        if (cur >= BUILDING_MAX_LEVEL) return 0;
        BuildingDef def = buildingDef(id);
        return def.baseCoinCost + cur * def.stepCoinCost;
    }

    int upgradeStarSandCost(int id) {
        int cur = buildingLevel(id);
        if (cur >= BUILDING_MAX_LEVEL) return 0;
        if (cur == 0) return 0; // 第一級建造免星砂，方便老玩家直接用溢出金幣開建！
        BuildingDef def = buildingDef(id);
        return def.baseStarCost * cur;
    }

    int unlockedGardenSlots() {
        int gardenLv = buildingLevel(BLD_GARDEN);
        if (gardenLv <= 0) return 0;
        if (gardenLv == 1) return 1;
        if (gardenLv == 2) return 2;
        return GARDEN_PLOT_COUNT;
    }

    int workshopDiscountPercent() {
        return Math.min(25, buildingLevel(BLD_WORKSHOP) * 5);
    }

    int discountedCoinPrice(int basePrice) {
        int disc = workshopDiscountPercent();
        return Math.max(5, basePrice - (basePrice * disc) / 100);
    }

    int snackCoinPrice() {
        return discountedCoinPrice(BASE_SNACK_PRICE);
    }

    int seedCoinPrice(int seedId) {
        return discountedCoinPrice(seedDef(seedId).coinCost);
    }

    int effectiveSeedSteps(int seedId) {
        int base = seedDef(seedId).requiredSteps;
        int gardenLv = buildingLevel(BLD_GARDEN);
        int reductionPct = gardenLv >= 5 ? 25 : (gardenLv == 4 ? 15 : 0);
        return Math.max(400, base - (base * reductionPct) / 100);
    }

    int shinyBonusPercent() {
        return buildingLevel(BLD_OBSERVATORY) * 2; // 最高 +10% 異色加成
    }

    int dailyPetBondXp() {
        return 15 + buildingLevel(BLD_PLAYGROUND) * 5; // 15 -> 40 羈絆 XP
    }

    AdventureState.ActionResult upgradeBuilding(StepProgress progress, int id) {
        if (progress == null || id < 0 || id >= BUILDING_COUNT) {
            return new AdventureState.ActionResult(false, "找不到這項營地設施");
        }
        int cur = buildingLevel(id);
        BuildingDef def = buildingDef(id);
        if (cur >= BUILDING_MAX_LEVEL) {
            return new AdventureState.ActionResult(false, def.name + "已達最高等級 ★");
        }
        int coinCost = upgradeCoinCost(id);
        int starCost = upgradeStarSandCost(id);
        if (progress.coins < coinCost) {
            return new AdventureState.ActionResult(false, "金幣不足，需要 " + coinCost + " 金幣");
        }
        if (starSand < starCost) {
            return new AdventureState.ActionResult(false, "星砂不足，需要 " + starCost + " 顆星砂");
        }
        progress.coins -= coinCost;
        starSand -= starCost;
        buildingLevels[id] = cur + 1;
        String verb = cur == 0 ? "落成！" : ("升至 Lv." + (cur + 1) + "！");
        return new AdventureState.ActionResult(true, "⛺ 「" + def.name + "」" + verb);
    }

    AdventureState.ActionResult plantSeed(StepProgress progress, int slot, int seedId) {
        if (progress == null || slot < 0 || slot >= GARDEN_PLOT_COUNT) {
            return new AdventureState.ActionResult(false, "無效的花圃編號");
        }
        if (slot >= unlockedGardenSlots()) {
            return new AdventureState.ActionResult(false, "升級「奇幻小菜園」即可解鎖這格花圃！");
        }
        if (seedId <= SEED_NONE || seedId >= SEEDS.length) {
            return new AdventureState.ActionResult(false, "請選擇有效的種子");
        }
        GardenPlot plot = gardenPlots[slot];
        if (!plot.isEmpty()) {
            return new AdventureState.ActionResult(false, "這格花圃已經種著植物囉");
        }
        int cost = seedCoinPrice(seedId);
        if (progress.coins < cost) {
            return new AdventureState.ActionResult(false, "金幣不足，購買種子需要 " + cost + " 金幣");
        }
        progress.coins -= cost;
        plot.seedId = seedId;
        plot.waterSteps = 0;
        plot.requiredSteps = effectiveSeedSteps(seedId);
        return new AdventureState.ActionResult(true, "🌱 已在 " + (slot + 1) + " 號花圃種下「" + seedDef(seedId).name + "」，走路就會自動澆水！");
    }

    /** 走路步數自動澆水給所有成長中的花圃，回傳新成熟的格子數。 */
    int waterGardensByWalk(long steps, boolean hasCloudSheep) {
        if (steps <= 0) return 0;
        long effective = hasCloudSheep ? steps + (steps * 25L) / 100L : steps;
        int newlyReady = 0;
        int unlocked = unlockedGardenSlots();
        for (int i = 0; i < unlocked; i++) {
            GardenPlot plot = gardenPlots[i];
            if (!plot.isEmpty() && !plot.isReady()) {
                long updated = Math.min((long) plot.requiredSteps, plot.waterSteps + effective);
                plot.waterSteps = (int) updated;
                if (plot.isReady()) {
                    newlyReady++;
                }
            }
        }
        return newlyReady;
    }

    int readyGardenCount() {
        int c = 0;
        for (int i = 0; i < unlockedGardenSlots(); i++) {
            if (gardenPlots[i].isReady()) c++;
        }
        return c;
    }

    HarvestResult harvestPlot(int slot, boolean hasSakuraDeer, Random random) {
        if (slot < 0 || slot >= GARDEN_PLOT_COUNT) {
            return new HarvestResult(false, 0, 0, 0, -1, "無效的花圃編號");
        }
        GardenPlot plot = gardenPlots[slot];
        if (plot.isEmpty()) {
            return new HarvestResult(false, 0, 0, 0, -1, "這格花圃目前是空的");
        }
        if (!plot.isReady()) {
            int remain = Math.max(0, plot.requiredSteps - plot.waterSteps);
            return new HarvestResult(false, 0, 0, 0, -1, "還差 " + remain + " 步澆水就成熟囉！");
        }
        SeedDef def = seedDef(plot.seedId);
        int gainedSnacks = def.snacksReward + (hasSakuraDeer ? 1 : 0);
        int gainedStar = def.starSandReward;
        int gainedPetals = def.petalsReward;
        snacks += gainedSnacks;
        starSand += gainedStar;
        petals += gainedPetals;

        int eggType = -1;
        if (def.eggDropChancePct > 0 && random != null && random.nextInt(100) < def.eggDropChancePct) {
            eggType = CompanionSystem.EGG_BLOOM;
        }

        plot.seedId = SEED_NONE;
        plot.waterSteps = 0;
        plot.requiredSteps = 0;

        String msg = "🧺 採收「" + def.name.replace("種子", "") + "」！獲得點心 ×" + gainedSnacks
            + "、星砂 +" + gainedStar + (eggType >= 0 ? "，還挖到一顆花瓣神祕蛋！" : "");
        return new HarvestResult(true, gainedSnacks, gainedStar, gainedPetals, eggType, msg);
    }

    AdventureState.ActionResult buySnack(StepProgress progress) {
        if (progress == null) return new AdventureState.ActionResult(false, "無法購買");
        int price = snackCoinPrice();
        if (progress.coins < price) {
            return new AdventureState.ActionResult(false, "金幣不足，需要 " + price + " 金幣");
        }
        progress.coins -= price;
        snacks++;
        return new AdventureState.ActionResult(true, "🍪 買到 1 份冒險點心（目前持有 " + snacks + " 份）");
    }

    AdventureState.ActionResult buyWarmBlanket() {
        if (starSand < BASE_BLANKET_PRICE) {
            return new AdventureState.ActionResult(false, "星砂不足，需要 " + BASE_BLANKET_PRICE + " 顆星砂");
        }
        starSand -= BASE_BLANKET_PRICE;
        warmBlankets++;
        return new AdventureState.ActionResult(true, "🧣 換到 1 條暖蛋毛毯（目前持有 " + warmBlankets + " 條）");
    }
}
