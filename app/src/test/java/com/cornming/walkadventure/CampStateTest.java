package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class CampStateTest {
    private static final Random ZERO_RNG = new Random() {
        @Override public int nextInt(int bound) {
            return 0;
        }
    };

    @Test public void upgradesBuildingsEnforcesCoinsAndStarSandAndUnlocksGardenSlots() {
        StepProgress p = new StepProgress();
        p.coins = 500;
        CampState camp = new CampState();

        // 初始：營火 Lv.1、菜園 Lv.1（解鎖 1 格花圃）
        assertEquals(1, camp.unlockedGardenSlots());

        // 建造未落成的設施（Lv.0 -> Lv.1）免星砂
        assertTrue(camp.upgradeBuilding(p, CampState.BLD_WORKSHOP).ok);
        assertEquals(1, camp.buildingLevel(CampState.BLD_WORKSHOP));
        assertEquals(5, camp.workshopDiscountPercent());

        // 菜園 Lv.1 -> Lv.2 需要星砂：星砂不足時拒絕升級
        camp.starSand = 0;
        assertFalse(camp.upgradeBuilding(p, CampState.BLD_GARDEN).ok);

        camp.starSand = 20;
        assertTrue(camp.upgradeBuilding(p, CampState.BLD_GARDEN).ok);
        assertEquals(2, camp.buildingLevel(CampState.BLD_GARDEN));
        assertEquals(2, camp.unlockedGardenSlots());
    }

    @Test public void plantsWatersWithWalkStepsAndHarvestsRewards() {
        StepProgress p = new StepProgress();
        p.coins = 200;
        CampState camp = new CampState();
        camp.setBuildingLevel(CampState.BLD_GARDEN, 2);

        // 1 號與 2 號可種，3 號尚未解鎖
        assertTrue(camp.plantSeed(p, 0, CampState.SEED_SUNFLOWER).ok);
        assertTrue(camp.plantSeed(p, 1, CampState.SEED_RADISH).ok);
        assertFalse(camp.plantSeed(p, 2, CampState.SEED_SUNFLOWER).ok);

        // 走路 800 步搭配雲朵羊綿綿（+25% = 1,000 步）讓向日葵直接成熟！
        int ready = camp.waterGardensByWalk(800, true);
        assertEquals(1, ready);
        assertTrue(camp.gardenPlots[0].isReady());
        assertFalse(camp.gardenPlots[1].isReady());

        // 採收 1 號花圃並帶有櫻花小鹿（額外 +1 點心）
        int snacksBefore = camp.snacks;
        int starBefore = camp.starSand;
        CampState.HarvestResult hr = camp.harvestPlot(0, true, ZERO_RNG);
        assertTrue(hr.ok);
        assertEquals(3, hr.snacks); // 基礎 2 + 小鹿 1
        assertEquals(snacksBefore + 3, camp.snacks);
        assertEquals(starBefore + 8, camp.starSand);
        assertTrue(camp.gardenPlots[0].isEmpty());

        // 讓彩虹蘿蔔成熟採收，ZERO_RNG -> 0 < 22 -> 必掉花瓣神祕蛋！
        camp.waterGardensByWalk(3_000, false);
        CampState.HarvestResult rad = camp.harvestPlot(1, false, ZERO_RNG);
        assertTrue(rad.ok);
        assertEquals(CompanionSystem.EGG_BLOOM, rad.droppedEggType);
    }
}
