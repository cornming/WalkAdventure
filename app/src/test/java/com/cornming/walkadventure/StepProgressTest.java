package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class StepProgressTest {
    private static final String MON = "2026-11-09";
    private static final String TUE = "2026-11-10";
    private static final long BOOT = 7;

    /** 固定回傳同一個數字的亂數，寶箱金幣才好驗算（20 + 10 = 30）。 */
    private static final Random FIXED = new Random() {
        @Override public int nextInt(int bound) {
            return 10;
        }
    };

    private static StepProgress started(long sensorSteps) {
        StepProgress p = new StepProgress();
        p.onSensorReading(sensorSteps, MON, BOOT, FIXED);
        return p;
    }

    @Test public void firstReadingOnlySetsTheBaseline() {
        StepProgress p = new StepProgress();
        StepProgress.Update u = p.onSensorReading(52_000, MON, BOOT, FIXED);
        assertFalse("安裝前走的步數不算", u.changed());
        assertEquals(0, p.lifetime);
        assertEquals(0, p.todaySteps);
        assertEquals(52_000, p.lastSensorSteps);
        assertEquals(MON, p.day);
    }

    @Test public void stepsAddToLifetimeAndToday() {
        StepProgress p = started(1_000);
        StepProgress.Update u = p.onSensorReading(1_340, MON, BOOT, FIXED);
        assertEquals(340, u.steps);
        assertEquals(340, p.lifetime);
        assertEquals(340, p.todaySteps);
        assertEquals(0, u.chests);
        assertFalse(u.event);
    }

    @Test public void stepsWalkedWhileTheAppWasClosedAreCounted() {
        StepProgress p = started(1_000);
        p.onSensorReading(1_100, MON, BOOT, FIXED);
        // App 關掉後又走了 250 步，下次打開時一次補上
        p.onSensorReading(1_350, MON, BOOT, FIXED);
        assertEquals(350, p.lifetime);
        assertEquals(350, p.todaySteps);
    }

    @Test public void todayResetsOnANewDayEvenIfTheAppWasNotRunningAtMidnight() {
        StepProgress p = started(1_000);
        p.onSensorReading(4_000, MON, BOOT, FIXED);
        assertEquals(3_000, p.todaySteps);

        // 隔天第一次讀數：差額跨了兩天，只算進累積步數
        assertEquals("換日後還沒有新讀數時顯示 0", 0, p.todayStepsOn(TUE));
        p.onSensorReading(4_600, TUE, BOOT, FIXED);
        assertEquals(TUE, p.day);
        assertEquals(0, p.todaySteps);
        assertEquals(3_600, p.lifetime);

        p.onSensorReading(4_900, TUE, BOOT, FIXED);
        assertEquals(300, p.todaySteps);
        assertEquals(300, p.todayStepsOn(TUE));
        assertEquals(3_900, p.lifetime);
    }

    @Test public void rebootKeepsTodayAndCountsStepsSinceBoot() {
        StepProgress p = started(1_000);
        p.onSensorReading(3_000, MON, BOOT, FIXED);
        // 重開機後感測器歸零，又走了 400 步
        StepProgress.Update u = p.onSensorReading(400, MON, BOOT + 1, FIXED);
        assertEquals(400, u.steps);
        assertEquals(2_400, p.todaySteps);
        assertEquals(2_400, p.lifetime);
    }

    @Test public void rebootIsDetectedEvenWhenTheCounterAlreadyPassedTheOldValue() {
        StepProgress p = started(100);
        p.onSensorReading(300, MON, BOOT, FIXED);
        // 重開機後已經走了 500 步才打開 App：讀數比上次大，只能靠開機次數分辨
        StepProgress.Update u = p.onSensorReading(500, MON, BOOT + 1, FIXED);
        assertEquals(500, u.steps);
        assertEquals(700, p.lifetime);
    }

    @Test public void withoutBootCountASmallerReadingStillMeansReboot() {
        StepProgress p = new StepProgress();
        p.onSensorReading(1_000, MON, StepProgress.UNKNOWN, FIXED);
        p.onSensorReading(1_200, MON, StepProgress.UNKNOWN, FIXED);
        StepProgress.Update u = p.onSensorReading(150, MON, StepProgress.UNKNOWN, FIXED);
        assertEquals(150, u.steps);
        assertEquals(350, p.lifetime);
    }

    @Test public void upgradingFromASaveWithoutBootCountDoesNotLookLikeAReboot() {
        // v0.1.0 的存檔沒有開機次數；升級後第一次讀數不能被當成重開機而重複計算
        StepProgress p = new StepProgress();
        p.day = MON;
        p.lifetime = 2_500;
        p.claimedChest = 2_000;
        p.eventBucket = 5;
        p.lastSensorSteps = 9_000;
        StepProgress.Update u = p.onSensorReading(9_120, MON, BOOT, FIXED);
        assertEquals(120, u.steps);
        assertEquals(2_620, p.lifetime);
        assertEquals(BOOT, p.lastBootCount);
    }

    @Test public void oneChestPerThousandSteps() {
        StepProgress p = started(0);
        StepProgress.Update u = p.onSensorReading(1_050, MON, BOOT, FIXED);
        assertEquals(1, u.chests);
        assertEquals(30, u.chestCoins);
        assertEquals(30, p.coins);
        assertEquals(1_000, p.claimedChest);
        // 同一個里程碑不會再領一次
        u = p.onSensorReading(1_900, MON, BOOT, FIXED);
        assertEquals(0, u.chests);
        assertEquals(30, p.coins);
    }

    @Test public void everyChestCrossedWhileAwayIsAwarded() {
        StepProgress p = started(0);
        StepProgress.Update u = p.onSensorReading(5_300, MON, BOOT, FIXED);
        assertEquals(5, u.chests);
        assertEquals(150, u.chestCoins);
        assertEquals(150, p.coins);
        assertEquals(5_000, p.claimedChest);
    }

    @Test public void chestCoinsStayWithinRange() {
        Random real = new Random(42);
        for (int i = 0; i < 200; i++) {
            StepProgress p = started(0);
            StepProgress.Update u = p.onSensorReading(1_000, MON, BOOT, real);
            assertTrue(u.chestCoins >= StepProgress.CHEST_COINS_MIN && u.chestCoins <= StepProgress.CHEST_COINS_MAX);
        }
    }

    @Test public void eventFiresOncePerFiveHundredSteps() {
        StepProgress p = started(0);
        assertFalse(p.onSensorReading(499, MON, BOOT, FIXED).event);
        assertTrue(p.onSensorReading(500, MON, BOOT, FIXED).event);
        assertFalse(p.onSensorReading(900, MON, BOOT, FIXED).event);
        // 一次跨過好幾個區間只觸發一次
        StepProgress.Update u = p.onSensorReading(2_600, MON, BOOT, FIXED);
        assertTrue(u.event);
        assertEquals(5, p.eventBucket);
    }

    @Test public void derivedValuesForTheScreen() {
        StepProgress p = new StepProgress();
        assertEquals(1, p.level());
        assertEquals(0, p.litCells());
        assertEquals(1_000, p.nextChestAt());

        p.lifetime = 3_250;
        assertEquals(4, p.level());
        assertEquals(250, p.xp());
        assertEquals(3, p.exploredCells());
        assertEquals(3, p.litCells());
        assertEquals(4_000, p.nextChestAt());

        p.lifetime = 8_000;
        assertEquals("八格全亮", 8, p.litCells());
        p.lifetime = 9_000;
        assertEquals("走完一輪重新開始", 0, p.litCells());
    }

    @Test public void legacySaveConvertsBaselineIntoTodaySteps() {
        assertEquals(1_500, StepProgress.todayStepsFromLegacy(MON, MON, 2_000f, 3_500));
        assertEquals("存檔不是今天的", 0, StepProgress.todayStepsFromLegacy(MON, TUE, 2_000f, 3_500));
        assertEquals("沒有存過日期", 0, StepProgress.todayStepsFromLegacy(null, MON, 2_000f, 3_500));
        assertEquals("沒有基準值", 0, StepProgress.todayStepsFromLegacy(MON, MON, -1f, 3_500));
        assertEquals("沒有讀過感測器", 0, StepProgress.todayStepsFromLegacy(MON, MON, 2_000f, StepProgress.UNKNOWN));
    }

    @Test public void announcementKeepsChestWhenEventAlsoFires() {
        StepProgress p = started(0);
        StepProgress.Update u = p.onSensorReading(1_000, MON, BOOT, FIXED);
        assertEquals(1, u.chests);
        assertTrue(u.event);

        String combined = StepProgress.buildAnnouncement(u, "✨ 神秘光芒照亮前方道路");
        assertTrue(combined.contains("🎁 寶箱！獲得 30 金幣"));
        assertTrue(combined.contains("✨ 神秘光芒照亮前方道路"));
        assertTrue(combined.indexOf("寶箱") < combined.indexOf("神秘光芒"));

        StepProgress.Update onlyEvent = new StepProgress.Update();
        onlyEvent.event = true;
        assertEquals("🍀 撿到幸運道具！", StepProgress.buildAnnouncement(onlyEvent, "🍀 撿到幸運道具！"));

        StepProgress.Update multiChest = new StepProgress.Update();
        multiChest.chests = 3;
        multiChest.chestCoins = 95;
        assertEquals("🎁 寶箱 ×3！獲得 95 金幣", StepProgress.buildAnnouncement(multiChest, null));
        assertNull(StepProgress.buildAnnouncement(new StepProgress.Update(), "unused"));
    }

    @Test public void guardsNegativeReadingsNullDayAndExtremeLevels() {
        StepProgress p = started(500);
        assertFalse(p.onSensorReading(-10, MON, BOOT, FIXED).changed());
        assertFalse(p.onSensorReading(700, null, BOOT, FIXED).changed());
        assertEquals(0, p.todayStepsOn(null));

        p.lifetime = Long.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, p.level());
    }
}
