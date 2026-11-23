package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ColorScavengerAndPostcardTest {
    private static final String TODAY = "2026-11-22";

    private static int[] solidPixels(int count, int argb) {
        int[] px = new int[count];
        Arrays.fill(px, argb);
        return px;
    }

    @Test public void analyzesHsvPixelsAcrossAllSevenRainbowColors() {
        // 純紅照片找熱情紅 -> 100% 命中、3 星
        ColorScavenger.AnalysisResult red = ColorScavenger.analyzeRgbPixels(
            solidPixels(100, 0xFFE52525), ColorScavenger.COLOR_RED
        );
        assertTrue(red.targetMatched);
        assertEquals(100, red.targetMatchPercent);
        assertEquals(3, red.suggestedStars);
        assertEquals("熱情紅", red.dominantColorName);

        // 純藍照片找熱情紅 -> 0% 未命中、建議 1 星、主色為晴空藍
        ColorScavenger.AnalysisResult wrong = ColorScavenger.analyzeRgbPixels(
            solidPixels(100, 0xFF2874E0), ColorScavenger.COLOR_RED
        );
        assertFalse(wrong.targetMatched);
        assertEquals(0, wrong.targetMatchPercent);
        assertEquals(1, wrong.suggestedStars);
        assertEquals("晴空藍", wrong.dominantColorName);

        // 戶外寫實情境：90% 綠草 + 10% 黃花找「向日葵黃」-> 達 6% 寬容門檻應判定命中 2 星
        int[] mixed = new int[100];
        Arrays.fill(mixed, 0, 90, 0xFF4B9952);
        Arrays.fill(mixed, 90, 100, 0xFFF5D033);
        ColorScavenger.AnalysisResult yellowInGrass = ColorScavenger.analyzeRgbPixels(
            mixed, ColorScavenger.COLOR_YELLOW
        );
        assertTrue(yellowInGrass.targetMatched);
        assertEquals(10, yellowInGrass.targetMatchPercent);
        assertEquals(2, yellowInGrass.suggestedStars);

        // 純白雲朵找雲朵白
        ColorScavenger.AnalysisResult white = ColorScavenger.analyzeRgbPixels(
            solidPixels(100, 0xFFF7F7F5), ColorScavenger.COLOR_WHITE
        );
        assertTrue(white.targetMatched);
        assertEquals("雲朵白", white.dominantColorName);
    }

    @Test public void completesPhotoHuntsUnlocksRainbowSetBonusVoiceAndAchievements() {
        StepProgress p = new StepProgress();
        p.day = TODAY;
        p.todaySteps = 3_400L;
        AdventureState s = new AdventureState();
        s.family.setFamilyMode(true);
        s.companions.unlockDirect(CompanionSystem.ID_OTTER, 0, false, TODAY);
        s.companions.equippedId = CompanionSystem.ID_OTTER;

        WorldClock.Snapshot clock = WorldClock.fromDateAndHour(TODAY, 15);
        ColorScavenger.AnalysisResult match = ColorScavenger.analyzeRgbPixels(
            solidPixels(50, 0xFFE52525), ColorScavenger.COLOR_RED
        );

        // 完成第 1～6 色，其中第 1 張附語音便條
        for (int c = 0; c < 6; c++) {
            String audio = c == 0 ? "voice_m0.m4a" : "";
            AdventureState.PhotoHuntOutcome res = s.completePhotoHunt(
                p, "m" + c, c + 1, "photo_m" + c + ".jpg", "doodle_m" + c + ".png",
                audio, "發現顏色 #" + c, 3, match, clock
            );
            assertTrue(res.ok);
            assertFalse(res.completedRainbowSet);
            assertNotNull(res.created);
        }

        assertEquals(6, s.rainbowCollectedCount());
        assertTrue("完成首張照片應解鎖成就 #10 小小攝影師", s.isExtraAchievementUnlocked(10));
        assertTrue("累積 5 張明信片應解鎖成就 #12 回憶明信片家", s.isExtraAchievementUnlocked(12));
        assertTrue("附語音便條應解鎖成就 #13 童言錄音師", s.isExtraAchievementUnlocked(13));
        assertTrue("拍照尋寶也應推進島嶼巨獸安撫條件", s.stories.bossLookUpDone);

        // 完成最後第 7 色（雲朵白）-> 觸發彩虹七色大獎（+100 金幣、+35 星砂、花瓣蛋、成就 #11）
        AdventureState.PhotoHuntOutcome seventh = s.completePhotoHunt(
            p, "m6", 7, "photo_m6.jpg", "", "", "白雲像綿羊！", 3, match, clock
        );
        assertTrue(seventh.ok);
        assertTrue(seventh.completedRainbowSet);
        assertEquals(7, s.rainbowCollectedCount());
        assertEquals(1, s.rainbowCompletedSets);
        assertTrue(s.isExtraAchievementUnlocked(11));
        assertEquals("集滿七色應獲贈一顆花瓣奇蹟蛋入巢", 1, s.companions.eggs.size());
        assertEquals(CompanionSystem.EGG_BLOOM, s.companions.eggs.get(0).eggType);
    }

    @Test public void findsOnThisDayAndFlashbackMemoriesAndRoundTripsPhase2Json() {
        AdventureState s = new AdventureState();
        s.rainbowCollectedMask = 0b0010101;
        s.rainbowCompletedSets = 1;
        s.photoHuntsCompleted = 3;
        s.voiceMemoriesCount = 2;

        // 去年今日附近（2025-11-21）與一週前（2026-11-10）與今天（2026-11-22）
        s.stories.addMemory(new StoryCatalog.MemoryEntry(
            "old1", "2025-11-21", "去年的黃花", "爸爸＋小寶", "p1.jpg", "v1.m4a", "d1.png",
            "去年的今天好開心！", 3, "向日葵黃", 55, 3, 5200L, CompanionSystem.ID_SHIBA, 2, true, WorldClock.SEASON_AUTUMN
        ));
        s.stories.addMemory(new StoryCatalog.MemoryEntry(
            "recent1", "2026-11-22", "今天的藍天", "爸爸＋小寶", "p2.jpg", "", "",
            "藍天很漂亮", 5, "晴空藍", 210, 3, 2100L, CompanionSystem.ID_OWL, 1, false, WorldClock.SEASON_AUTUMN
        ));

        List<StoryCatalog.MemoryEntry> throwbacks = s.stories.findThrowbackMemories(TODAY);
        assertEquals(1, throwbacks.size());
        assertEquals("old1", throwbacks.get(0).id);

        // JSON 完整往返驗證
        String json = SaveCodec.serialize(s);
        AdventureState restored = new AdventureState();
        assertTrue(SaveCodec.deserializeInto(json, restored));
        assertEquals(0b0010101, restored.rainbowCollectedMask);
        assertEquals(1, restored.rainbowCompletedSets);
        assertEquals(3, restored.photoHuntsCompleted);
        assertEquals(2, restored.voiceMemoriesCount);
        assertEquals(2, restored.stories.memories.size());
        StoryCatalog.MemoryEntry rOld = restored.stories.memories.get(1);
        assertEquals("d1.png", rOld.doodlePath);
        assertEquals("去年的今天好開心！", rOld.note);
        assertEquals("向日葵黃", rOld.colorName);
        assertEquals(5200L, rOld.stepsOnDay);
        assertEquals(CompanionSystem.ID_SHIBA, rOld.companionId);
        assertTrue(rOld.companionShiny);
    }
}
