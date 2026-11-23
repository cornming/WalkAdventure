package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SaveCodecMigrationTest {
    private static final String DAY1 = "2026-11-22";

    @Test public void migratesMaxedV04VeteranWithoutLosingCoinsStepsOrCompanionsAndIsIdempotent() {
        // 模擬 v0.4.0 全破封頂老玩家：55,000 步、4,500 溢出金幣、3 裝備滿 Lv.5、4 旅伴全解鎖
        StepProgress p = new StepProgress();
        p.day = DAY1;
        p.lifetime = 55_000L;
        p.todaySteps = 3_200L;
        p.coins = 4_500L;
        p.claimedChest = 55_000L;

        AdventureState s = new AdventureState();
        s.bootsLevel = 5;
        s.charmLevel = 5;
        s.compassLevel = 5;
        s.unlockedCompanionsMask = 0b11111; // 獨自 + 4 元老旅伴
        s.equippedCompanion = AdventureCatalog.COMPANION_DRAGON;
        s.encountersResolved = 35;

        SaveCodec.MigrationResult first = SaveCodec.migrateIfNeeded(p, s, false, DAY1);
        assertTrue(first.migrated);
        assertTrue(first.wasVeteran);
        assertTrue(s.pioneerBadge);
        assertEquals("0.4.0", s.migratedFrom);
        assertEquals("絕不可沒收任何老玩家金幣", 4_500L, p.coins);
        assertEquals("絕不可改動真實累計步數", 55_000L, p.lifetime);
        assertEquals(4, s.companions.ownedCount());
        assertEquals(CompanionSystem.ID_DRAGON, s.companions.equippedId);
        // 55,000 / 200 = 275 羈絆 XP -> 元老旅伴直接進階至第 2 階段！
        assertEquals(2, s.companions.getOwned(CompanionSystem.ID_DRAGON).stage);
        assertEquals(50, s.camp.starSand);
        assertEquals(2, s.companions.eggs.size());

        // 冪等性驗證：再次呼叫不重複發送星砂或重複塞蛋
        SaveCodec.MigrationResult second = SaveCodec.migrateIfNeeded(p, s, true, DAY1);
        assertFalse(second.migrated);
        assertEquals(50, s.camp.starSand);
        assertEquals(2, s.companions.eggs.size());
    }

    @Test public void roundTripsFullV2JsonWithSpecialCharactersAndRejectsCorruptJson() {
        AdventureState original = new AdventureState();
        original.migratedFrom = "0.4.0";
        original.pioneerBadge = true;
        original.extraAchievementsMask = 0b10101;
        original.campfireUnboxCount = 4;
        original.eggsHatchedCount = 2;
        original.gardenHarvestCount = 3;

        original.family.setFamilyMode(true);
        original.family.addMember("小寶\"A\\B", 2, 0xFF4B82C9, "05-18", FamilyProfile.ROLE_CHILD);
        original.vault.enqueue(CampfireVault.KIND_CHEST, "千步寶箱\n驚喜", 88, 12, -1, DAY1);
        original.companions.unlockDirect(CompanionSystem.ID_FOX, 300, true, DAY1);
        original.companions.equippedId = CompanionSystem.ID_FOX;
        original.companions.addEgg(CompanionSystem.EGG_NIGHT, 1);
        original.camp.starSand = 77;
        original.camp.setBuildingLevel(CampState.BLD_OBSERVATORY, 3);
        original.camp.gardenPlots[0].seedId = CampState.SEED_STARBERRY;
        original.camp.gardenPlots[0].waterSteps = 999;
        original.camp.gardenPlots[0].requiredSteps = 2200;
        original.stories.activeArcId = 4;
        original.stories.activeChapter = 2;
        original.stories.recordNote(DAY1, "雲朵動物園", "探險隊長＋小寶", "有一隻 \"大白熊\" 在飛！");

        String json = SaveCodec.serialize(original);
        AdventureState restored = new AdventureState();
        assertTrue(SaveCodec.deserializeInto(json, restored));

        assertTrue(restored.pioneerBadge);
        assertEquals(0b10101, restored.extraAchievementsMask);
        assertEquals(4, restored.campfireUnboxCount);
        assertTrue(restored.family.isFamilyMode());
        assertEquals(2, restored.family.members.size());
        assertEquals("小寶\"A\\B", restored.family.members.get(1).name);
        assertEquals("05-18", restored.family.members.get(1).birthday);
        assertEquals(1, restored.vault.count());
        assertEquals("千步寶箱\n驚喜", restored.vault.items.get(0).title);
        assertEquals(CompanionSystem.ID_FOX, restored.companions.equippedId);
        assertTrue(restored.companions.getOwned(CompanionSystem.ID_FOX).shiny);
        assertEquals(2, restored.companions.getOwned(CompanionSystem.ID_FOX).stage);
        assertEquals(77, restored.camp.starSand);
        assertEquals(3, restored.camp.buildingLevel(CampState.BLD_OBSERVATORY));
        assertEquals(999, restored.camp.gardenPlots[0].waterSteps);
        assertEquals(4, restored.stories.activeArcId);
        assertEquals("有一隻 \"大白熊\" 在飛！", restored.stories.recentNotes.get(0).note);

        // 損壞 JSON 必須優雅回傳 false 而不拋例外
        assertFalse(SaveCodec.deserializeInto("", new AdventureState()));
        assertFalse(SaveCodec.deserializeInto("{broken json", new AdventureState()));
        assertFalse(SaveCodec.deserializeInto("{\"noSchema\":1}", new AdventureState()));
    }

    @Test public void initializesFreshInstallAndMigratesV03LegacySaveCleanly() {
        // 全新安裝：發送 500 步迎新小葉蛋，不設元老徽章
        StepProgress freshP = new StepProgress();
        AdventureState freshS = new AdventureState();
        SaveCodec.MigrationResult freshRes = SaveCodec.migrateIfNeeded(freshP, freshS, false, DAY1);
        assertTrue(freshRes.migrated);
        assertFalse(freshRes.wasVeteran);
        assertFalse(freshS.pioneerBadge);
        assertEquals("fresh", freshS.migratedFrom);
        assertEquals(1, freshS.companions.eggs.size());
        assertEquals(CompanionSystem.EGG_STARTER_MINI, freshS.companions.eggs.get(0).eggType);

        // v0.3.0 只有累計步數與金幣（無裝備與旅伴）
        StepProgress oldP = new StepProgress();
        oldP.lifetime = 8_000L;
        oldP.coins = 320L;
        AdventureState oldS = new AdventureState();
        SaveCodec.MigrationResult oldRes = SaveCodec.migrateIfNeeded(oldP, oldS, false, DAY1);
        assertTrue(oldRes.migrated);
        assertTrue(oldRes.wasVeteran);
        assertTrue(oldS.pioneerBadge);
        assertEquals("0.3.0", oldS.migratedFrom);
        assertEquals(320L, oldP.coins);
        assertEquals(30, oldS.camp.starSand);
        assertEquals(1, oldS.companions.eggs.size());
        assertEquals(CompanionSystem.EGG_MIST, oldS.companions.eggs.get(0).eggType);
    }
}
