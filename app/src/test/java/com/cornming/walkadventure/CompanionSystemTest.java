package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class CompanionSystemTest {
    private static final String DAY1 = "2026-11-22";
    private static final String DAY2 = "2026-11-23";
    private static final Random ZERO_RNG = new Random() {
        @Override public int nextInt(int bound) {
            return 0;
        }
    };

    @Test public void incubatesWarmsDailyAndHatchesShinyCompanion() {
        CompanionSystem cs = new CompanionSystem();
        CampState camp = new CampState();

        assertTrue(cs.addEgg(CompanionSystem.EGG_STARTER_MINI, 0)); // 500 步
        assertEquals(1, cs.eggs.size());
        assertFalse("尚未走滿步數不可破殼", cs.hatchEgg(0, 0, camp, DAY1, ZERO_RNG).ok);

        // 每日暖蛋同日只能用 1 次（+300 步），跨日後可再暖一次（滿 500 步）
        assertTrue(cs.warmEggDaily(0, DAY1).ok);
        assertEquals(300, cs.eggs.get(0).stepsWalked);
        assertFalse(cs.warmEggDaily(0, DAY1).ok);
        assertTrue(cs.warmEggDaily(0, DAY2).ok);
        assertTrue(cs.eggs.get(0).isReadyToHatch());

        // ZERO_RNG -> 0 < 8 -> 必出閃亮異色！
        CompanionSystem.HatchResult res = cs.hatchEgg(0, 0, camp, DAY2, ZERO_RNG);
        assertTrue(res.ok);
        assertTrue(res.isNewSpecies);
        assertTrue(res.isShiny);
        assertEquals(0, cs.eggs.size());
        assertEquals(1, cs.ownedCount());
        assertEquals(res.companionId, cs.equippedId);
    }

    @Test public void feedsSnacksEvolvesThroughAllThreeStagesAndHandlesDuplicateHatch() {
        CompanionSystem cs = new CompanionSystem();
        CampState camp = new CampState();
        camp.snacks = 25;

        cs.unlockDirect(CompanionSystem.ID_SHEEP, 0, false, DAY1);
        CompanionSystem.OwnedCompanion sheep = cs.getOwned(CompanionSystem.ID_SHEEP);
        assertEquals(1, sheep.stage);

        // 餵 6 份點心 -> 240 XP -> 進化第 2 階段
        for (int i = 0; i < 6; i++) {
            assertTrue(cs.feedSnack(CompanionSystem.ID_SHEEP, camp).ok);
        }
        assertEquals(240, sheep.bondXp);
        assertEquals(2, sheep.stage);

        // 再餵 12 份點心 -> 720 XP -> 進化第 3 階段傳說大師
        for (int i = 0; i < 12; i++) {
            assertTrue(cs.feedSnack(CompanionSystem.ID_SHEEP, camp).ok);
        }
        assertEquals(720, sheep.bondXp);
        assertEquals(3, sheep.stage);

        // 重複孵到同一隻轉化為 +80 羈絆與 +20 星砂，且 ZERO_RNG 觸發異色覺醒
        cs.unlockDirect(CompanionSystem.ID_SHIBA, 0, false, DAY1);
        cs.unlockDirect(CompanionSystem.ID_SLIME, 0, false, DAY1);
        assertTrue(cs.addEgg(CompanionSystem.EGG_STARTER_MINI, 0));
        cs.advanceEggsByWalk(500);
        int starBefore = camp.starSand;
        CompanionSystem.HatchResult dup = cs.hatchEgg(0, 0, camp, DAY1, ZERO_RNG);
        assertTrue(dup.ok);
        assertFalse(dup.isNewSpecies);
        assertEquals(20, dup.bonusStarSand);
        assertEquals(starBefore + 20, camp.starSand);
        assertTrue(cs.getOwned(dup.companionId).shiny);
    }

    @Test public void craftsAndEquipsWorkshopAccessoriesAndPetsDaily() {
        CompanionSystem cs = new CompanionSystem();
        CampState camp = new CampState();
        StepProgress p = new StepProgress();
        p.coins = 500;
        camp.starSand = 50;

        cs.unlockDirect(CompanionSystem.ID_SHIBA, 0, false, DAY1);
        // 工坊 Lv.0 不可製作草帽
        assertFalse(cs.buyOrEquipAccessory(CompanionSystem.ID_SHIBA, CompanionSystem.ACC_STRAW_HAT, p, camp).ok);

        camp.setBuildingLevel(CampState.BLD_WORKSHOP, 1);
        assertTrue(cs.buyOrEquipAccessory(CompanionSystem.ID_SHIBA, CompanionSystem.ACC_STRAW_HAT, p, camp).ok);
        assertEquals(CompanionSystem.ACC_STRAW_HAT, cs.getOwned(CompanionSystem.ID_SHIBA).accessory);
        assertEquals(380L, p.coins);
        assertEquals(40, camp.starSand);

        // 每日摸摸給一次羈絆，同日再摸不重複加 XP
        assertTrue(cs.petInCamp(CompanionSystem.ID_SHIBA, 20, DAY1).ok);
        assertEquals(20, cs.getOwned(CompanionSystem.ID_SHIBA).bondXp);
        assertTrue(cs.petInCamp(CompanionSystem.ID_SHIBA, 20, DAY1).ok);
        assertEquals(20, cs.getOwned(CompanionSystem.ID_SHIBA).bondXp);
    }
}
