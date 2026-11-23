package com.cornming.walkadventure;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 繪本新手導覽與各分頁情境提示狀態（GuideState）與 SaveCodec 序列化測試。
 */
public class GuideStateTest {

    @Test
    public void defaultsShowOnboardingAndAllFourTabTips() {
        GuideState g = new GuideState();
        assertTrue(g.shouldShowOnboarding());
        assertEquals(4, GuideState.ONBOARDING_PAGES.length);
        assertEquals(4, GuideState.TAB_TIPS.length);
        assertEquals(11, GuideState.HELP_TOPICS.length);

        for (int i = 0; i < GuideState.TIP_COUNT; i++) {
            assertTrue("Tip " + i + " should be visible initially", g.shouldShowTabTip(i));
            assertNotNull(GuideState.tabTip(i).title);
            assertFalse(GuideState.tabTip(i).message.isEmpty());
        }
        assertFalse(g.shouldShowTabTip(-1));
        assertFalse(g.shouldShowTabTip(GuideState.TIP_COUNT));
    }

    @Test
    public void dismissTabTipsIndependentlyAndReset() {
        GuideState g = new GuideState();
        g.markOnboardingCompleted();
        assertFalse(g.shouldShowOnboarding());

        g.dismissTabTip(GuideState.TIP_CAMP);
        g.dismissTabTip(GuideState.TIP_SQUAD);

        assertTrue(g.shouldShowTabTip(GuideState.TIP_ADVENTURE));
        assertFalse(g.shouldShowTabTip(GuideState.TIP_CAMP));
        assertTrue(g.shouldShowTabTip(GuideState.TIP_JOURNAL));
        assertFalse(g.shouldShowTabTip(GuideState.TIP_SQUAD));

        g.resetAllGuides();
        assertTrue(g.shouldShowOnboarding());
        for (int i = 0; i < GuideState.TIP_COUNT; i++) {
            assertTrue(g.shouldShowTabTip(i));
        }
    }

    @Test
    public void saveCodecRoundTripPreservesGuideState() {
        AdventureState original = new AdventureState();
        original.guide.markOnboardingCompleted();
        original.guide.dismissTabTip(GuideState.TIP_ADVENTURE);
        original.guide.dismissTabTip(GuideState.TIP_JOURNAL);

        String json = SaveCodec.serialize(original);
        assertTrue(json.contains("\"onboardingCompleted\":true"));

        AdventureState restored = new AdventureState();
        assertTrue(SaveCodec.deserializeInto(json, restored));
        assertFalse(restored.guide.shouldShowOnboarding());
        assertFalse(restored.guide.shouldShowTabTip(GuideState.TIP_ADVENTURE));
        assertTrue(restored.guide.shouldShowTabTip(GuideState.TIP_CAMP));
        assertFalse(restored.guide.shouldShowTabTip(GuideState.TIP_JOURNAL));
        assertTrue(restored.guide.shouldShowTabTip(GuideState.TIP_SQUAD));
    }

    @Test
    public void saveCodecLegacyV2JsonWithoutGuideKeysDefaultsCleanly() {
        String legacyV2 = "{\"schemaVersion\":2,\"migratedFrom\":\"0.4.0\",\"pioneerBadge\":true}";
        AdventureState restored = new AdventureState();
        assertTrue(SaveCodec.deserializeInto(legacyV2, restored));
        assertTrue(restored.guide.shouldShowOnboarding());
        assertEquals(0, restored.guide.seenTipsMask);
        for (int i = 0; i < GuideState.TIP_COUNT; i++) {
            assertTrue(restored.guide.shouldShowTabTip(i));
        }
    }
}
