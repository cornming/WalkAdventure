package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class AdventureStateTest {
    private static final String DAY1 = "2026-11-10";
    private static final String DAY2 = "2026-11-11";
    private static final String DAY3 = "2026-11-12";
    private static final String DAY5 = "2026-11-14";
    private static final long BOOT = 10L;

    /** 固定亂數（永遠回傳 0），方便精確驗算機率與獎勵上限/下限。 */
    private static final Random ZERO_RANDOM = new Random() {
        @Override public int nextInt(int bound) {
            return 0;
        }
    };

    private static StepProgress startedProgress(long initialSensor) {
        StepProgress p = new StepProgress();
        p.onSensorReading(initialSensor, DAY1, BOOT, ZERO_RANDOM);
        return p;
    }

    @Test public void upgradesGearAppliesXpAndCoinMultipliersWithoutFakingRealSteps() {
        StepProgress p = startedProgress(0L);
        p.coins = 300L;
        AdventureState s = new AdventureState();

        // Lv.0 -> Lv.1 疾風探險靴（50 金幣）
        AdventureState.ActionResult r1 = s.upgradeGear(p, AdventureCatalog.GEAR_BOOTS, DAY1);
        assertTrue(r1.ok);
        assertEquals(1, s.bootsLevel);
        assertEquals(250L, p.coins);
        assertEquals(10, s.xpMultiplierPercent());

        // Lv.0 -> Lv.1 幸運四葉草（60 金幣）
        AdventureState.ActionResult r2 = s.upgradeGear(p, AdventureCatalog.GEAR_CHARM, DAY1);
        assertTrue(r2.ok);
        assertEquals(1, s.charmLevel);
        assertEquals(190L, p.coins);
        assertEquals(15, s.coinMultiplierPercent());

        // 走路 1,000 步：真實步數維持 1,000，額外 XP +100（totalXp = 1,100），寶箱基礎 20 金幣 + 幸運四葉草 15% (+3)
        // 同時還會解鎖 1,000 步成就 (+20 金幣)
        StepProgress.Update u = p.onSensorReading(1_000L, DAY1, BOOT, ZERO_RANDOM);
        String notice = s.applyWalkUpdate(p, u, DAY1, ZERO_RANDOM);
        assertNotNull(notice);
        assertEquals("真實累計步數絕不可被加成灌水", 1_000L, p.lifetime);
        assertEquals("真實今日步數絕不可被加成灌水", 1_000L, p.todayStepsOn(DAY1));
        assertEquals(100L, s.bonusXp);
        assertEquals(1_100L, s.totalAdventureXp(p));
        // 190 + 20(寶箱) + 3(四葉草15%) + 20(成就:初入冒險) = 233
        assertEquals(233L, p.coins);

        // 裝備升到滿等後應拒絕再升，金幣不足時也拒絕
        for (int lv = 1; lv < AdventureCatalog.GEAR_MAX_LEVEL; lv++) {
            p.coins = 1_000L;
            assertTrue(s.upgradeGear(p, AdventureCatalog.GEAR_COMPASS, DAY1).ok);
        }
        // 已升滿 Lv.5 的羅盤再升應失敗
        p.coins = 1_000L;
        // 再升 boots 至滿
        s.setGearLevel(AdventureCatalog.GEAR_BOOTS, AdventureCatalog.GEAR_MAX_LEVEL);
        assertFalse(s.upgradeGear(p, AdventureCatalog.GEAR_BOOTS, DAY1).ok);

        p.coins = 10L;
        assertFalse(s.upgradeGear(p, AdventureCatalog.GEAR_CHARM, DAY1).ok);
    }

    @Test public void unlocksEquipsAndAppliesAllCompanionPerks() {
        StepProgress p = startedProgress(0L);
        AdventureState s = new AdventureState();

        // 金幣不足無法招募柴犬阿福（80 金幣）
        p.coins = 50L;
        assertFalse(s.buyOrEquipCompanion(p, AdventureCatalog.COMPANION_SHIBA, DAY1).ok);
        assertFalse(s.isCompanionUnlocked(AdventureCatalog.COMPANION_SHIBA));

        // 招募柴犬阿福：扣 80 金幣 + 自動裝備 + 解鎖成就8「毛茸茸小隊」(+40 金幣)
        p.coins = 100L;
        assertTrue(s.buyOrEquipCompanion(p, AdventureCatalog.COMPANION_SHIBA, DAY1).ok);
        assertTrue(s.isCompanionUnlocked(AdventureCatalog.COMPANION_SHIBA));
        assertEquals(AdventureCatalog.COMPANION_SHIBA, s.equippedCompanion);
        assertEquals(60L, p.coins); // 100 - 80 + 40

        // 柴犬每箱額外 +8 金幣
        StepProgress.Update chestWalk = p.onSensorReading(1_000L, DAY1, BOOT, ZERO_RANDOM);
        s.applyWalkUpdate(p, chestWalk, DAY1, ZERO_RANDOM);
        // 60 + 20(寶箱) + 8(柴犬) + 20(初入冒險成就) = 108
        assertEquals(108L, p.coins);

        // 免費切回獨自出發、再切回柴犬不花錢
        assertTrue(s.buyOrEquipCompanion(p, AdventureCatalog.COMPANION_NONE, DAY1).ok);
        assertEquals(AdventureCatalog.COMPANION_NONE, s.equippedCompanion);
        assertEquals(108L, p.coins);
        assertTrue(s.buyOrEquipCompanion(p, AdventureCatalog.COMPANION_SHIBA, DAY1).ok);
        assertEquals(108L, p.coins);

        // 招募小綠史萊姆（140 金幣）：非新手包奇遇額外 +20 XP、+6 金幣
        p.coins = 200L;
        assertTrue(s.buyOrEquipCompanion(p, AdventureCatalog.COMPANION_SLIME, DAY1).ok);
        assertEquals(60L, p.coins);
        s.pendingEncounter = new AdventureCatalog.PendingEncounter(
            AdventureCatalog.ENCOUNTER_SHRINE, 0, "t", "p", "a", "b"
        );
        assertTrue(s.resolvePendingEncounter(p, 0, DAY1, ZERO_RANDOM).ok);
        assertEquals(66L, p.coins); // +6 金幣
        // 90(神壇基礎, ZERO_RANDOM 必觸發 luckyCrit -> +30 = 120) + 20(史萊姆) = 140
        assertEquals(140L, s.bonusXp);

        // 貓頭鷹波波（+25% 每日任務金幣）
        p.coins = 220L;
        assertTrue(s.buyOrEquipCompanion(p, AdventureCatalog.COMPANION_OWL, DAY1).ok);
        assertEquals(31, s.dailyQuestCoinReward(0)); // 25 * 1.25 = 31

        // 迷你火龍（+20% XP & 奇遇 +15 金幣）
        p.coins = 380L;
        assertTrue(s.buyOrEquipCompanion(p, AdventureCatalog.COMPANION_DRAGON, DAY1).ok);
        assertEquals(20, s.xpMultiplierPercent());
        s.pendingEncounter = new AdventureCatalog.PendingEncounter(
            AdventureCatalog.ENCOUNTER_SHRINE, 0, "t", "p", "a", "b"
        );
        assertTrue(s.resolvePendingEncounter(p, 0, DAY1, ZERO_RANDOM).ok);
        assertEquals(15L, p.coins);
    }

    @Test public void resolvesEncountersEnforcesMerchantCostAndAutoResolvesPrevious() {
        StepProgress p = startedProgress(0L);
        AdventureState s = new AdventureState();

        // 村長出發補給包僅一次，不計入 encountersResolved
        assertTrue(s.ensureStarterEncounter(DAY1));
        assertFalse(s.ensureStarterEncounter(DAY1));
        assertEquals(AdventureCatalog.ENCOUNTER_STARTER, s.pendingEncounter.type);
        assertTrue(s.resolvePendingEncounter(p, 0, DAY1, ZERO_RANDOM).ok);
        assertNull(s.pendingEncounter);
        assertEquals(30L, p.coins);
        assertEquals(0, s.encountersResolved);

        // 商人選擇花 25 金幣抽福袋時，金幣不足不可消耗奇遇
        p.coins = 10L;
        s.pendingEncounter = new AdventureCatalog.PendingEncounter(
            AdventureCatalog.ENCOUNTER_MERCHANT, 0, "商人", "提示", "免費", "抽獎"
        );
        assertFalse(s.resolvePendingEncounter(p, 1, DAY1, ZERO_RANDOM).ok);
        assertNotNull(s.pendingEncounter);
        assertEquals(10L, p.coins);

        // 金幣足夠時扣 25 再發福袋（ZERO_RANDOM 下 roll=0 -> luckyCrit true, 35+20=55 -> 10-25+55=40）
        p.coins = 25L;
        assertTrue(s.resolvePendingEncounter(p, 1, DAY1, ZERO_RANDOM).ok);
        assertNull(s.pendingEncounter);
        assertEquals(55L, p.coins);
        assertEquals(1, s.encountersResolved);

        // 還沒解掉舊奇遇時若又走到新奇遇觸發點，舊奇遇會自動結算（choice 0）不吃獎勵
        s.pendingEncounter = new AdventureCatalog.PendingEncounter(
            AdventureCatalog.ENCOUNTER_SHRINE, 0, "舊神壇", "p", "經驗", "金幣"
        );
        long xpBefore = s.bonusXp;
        StepProgress.Update u = p.onSensorReading(500L, DAY1, BOOT, ZERO_RANDOM);
        assertTrue(u.event);
        s.applyWalkUpdate(p, u, DAY1, ZERO_RANDOM);
        assertTrue(s.bonusXp > xpBefore);
        assertEquals(2, s.encountersResolved);
        assertNotNull(s.pendingEncounter);

        // 怪物迎戰／安撫與路邊寶箱雙選項皆可結算
        s.pendingEncounter = new AdventureCatalog.PendingEncounter(
            AdventureCatalog.ENCOUNTER_MONSTER, 1, "迷路樹葉妖", "p", "迎戰", "安撫"
        );
        assertTrue(s.resolvePendingEncounter(p, 1, DAY1, ZERO_RANDOM).ok);
        s.pendingEncounter = new AdventureCatalog.PendingEncounter(
            AdventureCatalog.ENCOUNTER_CHEST, 1, "神秘寶箱", "p", "開箱", "許願"
        );
        assertTrue(s.resolvePendingEncounter(p, 1, DAY1, ZERO_RANDOM).ok);
        assertEquals(4, s.encountersResolved);
    }

    @Test public void tracksDailyQuestsAndMultiDayStreaksAcrossMidnight() {
        StepProgress p = startedProgress(0L);
        AdventureState s = new AdventureState();

        // 未達 1,500 步無法領暖身任務
        StepProgress.Update u1 = p.onSensorReading(1_200L, DAY1, BOOT, ZERO_RANDOM);
        s.applyWalkUpdate(p, u1, DAY1, ZERO_RANDOM);
        assertEquals(0, s.pendingClaimableDailyCount(p, DAY1));
        assertFalse(s.claimDailyQuest(p, 0, DAY1).ok);
        assertEquals(0, s.effectiveStreak(DAY1));

        // 達到 4,000 步：可領 2 項每日任務，連勝成為 1 天
        StepProgress.Update u2 = p.onSensorReading(4_000L, DAY1, BOOT, ZERO_RANDOM);
        s.applyWalkUpdate(p, u2, DAY1, ZERO_RANDOM);
        assertEquals(1, s.effectiveStreak(DAY1));
        assertEquals(2, s.pendingClaimableDailyCount(p, DAY1));
        assertTrue(s.claimDailyQuest(p, 0, DAY1).ok);
        assertFalse("同日重複領取應失敗", s.claimDailyQuest(p, 0, DAY1).ok);
        assertEquals(1, s.pendingClaimableDailyCount(p, DAY1));

        // 第 2 天（DAY2）：換日後重新累計今日步數，前一天連勝在尚未走路前先維持 1
        assertEquals(1, s.effectiveStreak(DAY2));
        p.onSensorReading(4_500L, DAY2, BOOT, ZERO_RANDOM); // 換日第一筆不計今日
        StepProgress.Update day2Walk = p.onSensorReading(6_200L, DAY2, BOOT, ZERO_RANDOM); // 今日 1,700 步
        s.applyWalkUpdate(p, day2Walk, DAY2, ZERO_RANDOM);
        assertEquals(2, s.effectiveStreak(DAY2));
        assertTrue("換日後應可再次領取每日暖身任務", s.claimDailyQuest(p, 0, DAY2).ok);

        // 第 3 天（DAY3）：再達 1,500 步，連勝 3 天解鎖成就9「三日火種」
        p.onSensorReading(6_300L, DAY3, BOOT, ZERO_RANDOM);
        StepProgress.Update day3Walk = p.onSensorReading(7_900L, DAY3, BOOT, ZERO_RANDOM); // 今日 1,600 步
        s.applyWalkUpdate(p, day3Walk, DAY3, ZERO_RANDOM);
        assertEquals(3, s.effectiveStreak(DAY3));
        assertEquals(3, s.bestStreakDays);
        assertTrue(s.isAchievementUnlocked(9));

        // 跳過 DAY4 到 DAY5：有效連勝歸零，但最佳連勝保持 3
        assertEquals(0, s.effectiveStreak(DAY5));
        assertEquals(3, s.bestStreakDays);
    }

    @Test public void exploresBiomesSupportsPreviewAndBackfillsLegacyAchievements() {
        // 模擬從 v0.3.0 升級的玩家：已走 8,500 步、領過 8 個寶箱、bonusXp = 0
        StepProgress p = new StepProgress();
        p.day = DAY1;
        p.lifetime = 8_500L;
        p.todaySteps = 2_000L;
        p.claimedChest = 8_000L;
        p.coins = 200L;

        AdventureState s = new AdventureState();
        s.syncSnapshot(p, DAY1);

        // 已跨入第 2 章（8 格），解鎖：1k(#0), 5k(#1), 精靈林(#4), 5寶箱(#6) -> 20+40+50+50 = +160 金幣
        assertEquals(1, s.frontierBiomeIndex(p));
        assertEquals(1, s.highestUnlockedBiomeIndex(p));
        assertEquals(0, s.displayBiomeLitCells(p));
        assertTrue(s.isAchievementUnlocked(0));
        assertTrue(s.isAchievementUnlocked(1));
        assertTrue(s.isAchievementUnlocked(4));
        assertTrue(s.isAchievementUnlocked(6));
        assertEquals(360L, p.coins);
        assertEquals("寶箱獵人", s.currentTitle(p));

        // 重複執行 syncSnapshot 不應重複發放成就獎勵
        s.syncSnapshot(p, DAY1);
        assertEquals(360L, p.coins);

        // 切換回第一章檢視，顯示滿 8 格；再切換回前線章節回復 -1
        s.shiftDisplayBiome(p, -1);
        assertEquals(0, s.activeDisplayBiomeIndex(p));
        assertEquals(AdventureCatalog.CELLS_PER_BIOME, s.displayBiomeLitCells(p));
        s.shiftDisplayBiome(p, 1);
        assertEquals(1, s.activeDisplayBiomeIndex(p));
        assertEquals(-1, s.selectedBiomeIndex);

        // 40,000 XP 以上進入第 2 周目
        p.lifetime = 42_000L;
        assertEquals(2, s.adventureCycle(p));
        assertEquals(0, s.frontierBiomeIndex(p));
        assertEquals(AdventureCatalog.BIOME_COUNT - 1, s.highestUnlockedBiomeIndex(p));
    }

    @Test public void buildsWeeklyHistoryLogCapReportAndRoundTripsSerialization() {
        StepProgress p = new StepProgress();
        p.day = DAY3;
        p.lifetime = 3_400L;
        p.todaySteps = 2_100L;
        p.coins = 123L;

        AdventureState original = new AdventureState();
        original.ensureDailyRecord(DAY1).steps = 1_600L;
        original.ensureDailyRecord(DAY1).coinsEarned = 35L;
        original.syncSnapshot(p, DAY3);
        // 累計 3,400 步觸發成就 #0「初入冒險」（+20 金幣），持有金幣 123 -> 143
        assertEquals(143L, p.coins);

        // 7 日圖表補滿 7 天且最後一天為 DAY3 即時步數
        List<AdventureState.DailyRecord> week = original.lastSevenDays(p, DAY3);
        assertEquals(7, week.size());
        assertEquals(DAY3, week.get(6).date);
        assertEquals(2_100L, week.get(6).steps);
        assertEquals(1_600L, week.get(4).steps);
        assertEquals(0L, week.get(0).steps);

        // 日誌超過 MAX_LOG_ENTRIES 時保留最新 20 筆，且正確跳脫特殊字元
        for (int i = 0; i < 25; i++) {
            original.appendLog(DAY3, "測試|斜線\\換行\n事件#" + i);
        }
        assertEquals(AdventureState.MAX_LOG_ENTRIES, original.adventureLog.size());
        assertTrue(original.adventureLog.get(0).summary.endsWith("事件#24"));

        original.pendingEncounter = new AdventureCatalog.PendingEncounter(
            AdventureCatalog.ENCOUNTER_MONSTER,
            2,
            "遭遇|怪\\物",
            "第一行\n第二行",
            "選項A|1",
            "選項B\\2"
        );

        String histSer = original.serializeHistory();
        String logSer = original.serializeLog();
        String encSer = original.serializePendingEncounter();

        AdventureState restored = new AdventureState();
        restored.parseHistory(histSer);
        restored.parseLog(logSer);
        restored.parsePendingEncounter(encSer);

        assertEquals(original.dailyHistory.size(), restored.dailyHistory.size());
        assertEquals(1_600L, restored.dailyHistory.get(0).steps);
        assertEquals(35L, restored.dailyHistory.get(0).coinsEarned);
        assertEquals(original.adventureLog.size(), restored.adventureLog.size());
        assertEquals("測試|斜線\\換行\n事件#24", restored.adventureLog.get(0).summary);

        assertNotNull(restored.pendingEncounter);
        assertEquals(AdventureCatalog.ENCOUNTER_MONSTER, restored.pendingEncounter.type);
        assertEquals(2, restored.pendingEncounter.biomeIndex);
        assertEquals("遭遇|怪\\物", restored.pendingEncounter.title);
        assertEquals("第一行\n第二行", restored.pendingEncounter.prompt);
        assertEquals("選項A|1", restored.pendingEncounter.primaryLabel);
        assertEquals("選項B\\2", restored.pendingEncounter.secondaryLabel);

        // 戰報文字包含關鍵統計
        String report = original.formatDailyReport(p, DAY3);
        assertTrue(report.contains(DAY3));
        assertTrue(report.contains("2100 步"));
        assertTrue(report.contains("持有金幣：143"));
    }
}
