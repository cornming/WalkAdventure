package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class FamilyAndVaultTest {
    private static final String DAY1 = "2026-11-22";
    private static final Random ZERO_RNG = new Random() {
        @Override public int nextInt(int bound) {
            return 0;
        }
    };

    @Test public void managesFamilyMembersSquadToggleAndCap() {
        FamilyProfile fp = new FamilyProfile();
        assertEquals(1, fp.members.size());
        assertFalse("不能刪除最後一位成員", fp.removeMember("m1").ok);

        assertTrue(fp.addMember("媽媽", 1, FamilyProfile.PRESET_COLORS[1], "11-22", FamilyProfile.ROLE_ADULT).ok);
        assertTrue(fp.addMember("小寶", 2, FamilyProfile.PRESET_COLORS[2], "05-01", FamilyProfile.ROLE_CHILD).ok);
        assertEquals(3, fp.members.size());
        assertTrue(fp.hasChildMember());
        assertEquals("媽媽", fp.birthdayCelebrantName(WorldClock.fromDateAndHour(DAY1, 10)));

        // 切換出隊成員且至少保留一位
        assertTrue(fp.toggleSquadMember("m1"));
        assertTrue(fp.toggleSquadMember("m2"));
        assertFalse("最後一位出隊成員不可取消", fp.toggleSquadMember("m3"));
        assertEquals("小寶", fp.formatActiveSquadLabel());

        // 填滿上限 6 人後再新增應被拒
        assertTrue(fp.addMember("大寶", 3, 0xFF0000, "", FamilyProfile.ROLE_CHILD).ok);
        assertTrue(fp.addMember("阿公", 4, 0xFF0000, "", FamilyProfile.ROLE_ADULT).ok);
        assertTrue(fp.addMember("阿嬤", 5, 0xFF0000, "", FamilyProfile.ROLE_ADULT).ok);
        assertFalse(fp.addMember("第七人", 0, 0xFF0000, "", FamilyProfile.ROLE_ADULT).ok);
    }

    @Test public void vaultsWeekdayChestsAndGrantsFamilyCampfireBonusOnOpen() {
        StepProgress p = new StepProgress();
        p.onSensorReading(0L, DAY1, 1L, ZERO_RNG);
        AdventureState s = new AdventureState();
        s.family.setFamilyMode(false);
        s.family.autoVaultBigRewards = true;
        s.camp.setBuildingLevel(CampState.BLD_CAMPFIRE, 2); // 親子模式 +16%

        WorldClock.Snapshot morning = WorldClock.fromDateAndHour(DAY1, 10);
        StepProgress.Update u = p.onSensorReading(1_000L, DAY1, 1L, ZERO_RNG); // base chest = 20
        s.applyWalkUpdateV2(p, u, morning, ZERO_RNG);

        // 平日自動鎖存：20 金幣已從直接錢包移入營火盒（只剩成就 1,000 步的 20 金幣）
        assertEquals(20L, p.coins);
        assertTrue(s.vault.chestCount() >= 1);
        String chestId = s.vault.items.get(0).id;

        // 切回親子模式開箱：20 金幣 * 1.16 = 23 金幣，外加營火星砂加成
        s.family.setFamilyMode(true);
        long coinsBefore = p.coins;
        int starsBefore = s.camp.starSand;
        CampfireVault.OpenOutcome out = s.openVaultItem(p, chestId, DAY1);
        assertTrue(out.ok);
        assertEquals(23, out.coinsGained);
        assertEquals(coinsBefore + 23L, p.coins);
        assertTrue(out.starSandGained > 4);
        assertEquals(starsBefore + out.starSandGained, s.camp.starSand);
        assertTrue(s.stories.isSeasonStampUnlocked(2));
    }

    @Test public void vaultOverflowAutoClaimsOldestAndPocketModeTracksSteps() {
        CampfireVault vault = new CampfireVault();
        for (int i = 0; i < CampfireVault.MAX_ITEMS; i++) {
            assertEquals(null, vault.enqueue(CampfireVault.KIND_CHEST, "箱" + i, 10 + i, 2, -1, DAY1));
        }
        CampfireVault.VaultItem overflow = vault.enqueue(CampfireVault.KIND_CHEST, "新箱", 50, 5, -1, DAY1);
        assertEquals("箱0", overflow.title);
        assertEquals(10, overflow.coins);
        assertEquals(CampfireVault.MAX_ITEMS, vault.count());

        FamilyProfile fp = new FamilyProfile();
        fp.startPocketMode();
        fp.recordPocketSteps(650L);
        fp.recordPocketSteps(350L);
        assertEquals(1_000L, fp.finishPocketMode());
        assertFalse(fp.pocketMode);
        assertEquals(0L, fp.pocketStepsCollected);
    }

    @Test public void triggersBirthdayGiftOncePerDayAndPenguinStreakSnack() {
        StepProgress p = new StepProgress();
        AdventureState s = new AdventureState();
        s.family.addMember("壽星小寶", 2, FamilyProfile.PRESET_COLORS[2], "11-22", FamilyProfile.ROLE_CHILD);
        s.companions.unlockDirect(CompanionSystem.ID_PENGUIN, 0, false, DAY1);
        s.companions.equippedId = CompanionSystem.ID_PENGUIN;
        s.streakDays = 3;
        s.lastStreakDay = DAY1;

        WorldClock.Snapshot bdayClock = WorldClock.fromDateAndHour(DAY1, 10);
        int snacksBefore = s.camp.snacks;
        String notice = s.syncV2DailyEnvironment(p, bdayClock, ZERO_RNG);
        assertTrue(notice != null && notice.contains("生日快樂") && notice.contains("企鵝嘟嘟"));
        assertEquals(50L, p.coins);
        assertEquals(15, s.camp.starSand);
        assertEquals(snacksBefore + 3, s.camp.snacks); // 生日 +2 + 企鵝 +1

        // 同日重複同步不再發送生日或企鵝點心
        s.syncV2DailyEnvironment(p, bdayClock, ZERO_RNG);
        assertEquals(50L, p.coins);
        assertEquals(snacksBefore + 3, s.camp.snacks);
    }
}
