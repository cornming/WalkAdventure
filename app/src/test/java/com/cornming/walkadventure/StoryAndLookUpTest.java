package com.cornming.walkadventure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class StoryAndLookUpTest {
    private static final String DAY1 = "2026-11-22";
    private static final Random SEEDED_RNG = new Random(42);

    @Test public void advancesThreeChapterStoryArcAndDropsCompletionEgg() {
        StepProgress p = new StepProgress();
        AdventureState s = new AdventureState();
        s.stories.activeArcId = 0; // 迷路小松鼠的三顆橡果（獎勵霧林蛋）
        s.stories.activeChapter = 1;

        for (int ch = 1; ch <= 3; ch++) {
            StoryCatalog.ArcDef arc = StoryCatalog.arc(s.stories.activeArcId);
            StoryCatalog.ChapterDef def = s.stories.currentStoryChapter();
            s.pendingEncounter = AdventureCatalog.createStoryEncounter(0, arc, def);
            assertTrue(s.resolvePendingEncounter(p, 0, DAY1, SEEDED_RNG).ok);
        }

        assertTrue(s.stories.isArcCompleted(0));
        assertEquals(1, s.stories.completedArcCount());
        assertEquals(1, s.stories.activeArcId);
        assertEquals(1, s.stories.activeChapter);
        assertTrue("完成故事線應解鎖繪本讀者季節印章", s.stories.isSeasonStampUnlocked(6));
        assertEquals("完成第 0 條故事線應掉落霧林蛋入巢", 1, s.companions.eggs.size());
        assertEquals(CompanionSystem.EGG_MIST, s.companions.eggs.get(0).eggType);
    }

    @Test public void completesLookUpQuestRecordsNoteAndUnlocksIslandBossCalm() {
        StepProgress p = new StepProgress();
        AdventureState s = new AdventureState();
        s.family.setFamilyMode(true);
        s.stories.ensureDailyLookUpOffers(DAY1, SEEDED_RNG);
        assertEquals(3, s.stories.offeredLookUpIds.size());
        Set<Integer> distinct = new HashSet<>(s.stories.offeredLookUpIds);
        assertEquals("每日抽出的 3 張抬頭任務不可重複", 3, distinct.size());

        // 還沒完成步數或抬頭任務前不能安撫巨獸
        assertFalse(s.calmIslandBoss(p, 0, DAY1).ok);

        int firstQuestId = s.stories.offeredLookUpIds.get(0);
        AdventureState.ActionResult lu = s.completeLookUpQuest(p, firstQuestId, "看到像恐龍的大白雲！", DAY1);
        assertTrue(lu.ok);
        assertFalse("同張任務同日不可重複領取", s.completeLookUpQuest(p, firstQuestId, "", DAY1).ok);
        assertEquals(1, s.stories.recentNotes.size());
        assertEquals("看到像恐龍的大白雲！", s.stories.recentNotes.get(0).note);
        assertTrue(s.stories.isSeasonStampUnlocked(1));

        // 完成抬頭任務後，即可立刻安撫該島嶼守護巨獸！
        assertTrue(s.stories.canCalmCurrentBoss(0));
        AdventureState.ActionResult bossRes = s.calmIslandBoss(p, 0, DAY1);
        assertTrue(bossRes.ok);
        assertTrue(s.stories.isBossCleared(0));
        assertFalse("同一座島的巨獸不可重複安撫領獎", s.calmIslandBoss(p, 0, DAY1).ok);
        assertEquals(1, s.companions.eggs.size());
    }
}
