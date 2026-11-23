package com.cornming.walkadventure;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * WalkAdventure 2.0 Phase 3 & Phase 4 核心領域單元測試：
 * 1) 純 Java ISO/IEC 18004 QR Code 編碼矩陣（定位點、版本遞增、長字串安全截斷）
 * 2) NFC 卡片與離線 QR 實體藏寶（建立、NFC UID 匹配、短碼與 QR 字串開箱、防重複領取）
 * 3) 離線 QR 家族禮物卡（防偽校驗碼驗證、篡改攔截、防重複領取、旅伴串門子紀錄）
 * 4) 隱私足跡迷霧探索（200m 粗略相對格量化、首探獎勵、每 5 格掉蛋、手動秘密基地打卡與命名）
 * 5) 真實環境感測彩蛋（暗光夜光小妖、強光陽光菜園澆水、快走疾風追逐、停步歇腳物語、每日冷卻）
 * 6) Phase 4 端側自然百科視覺鑑定引擎（花朵、綠樹、藍天白雲、水岸、夕陽、夜空）與 8 類全集滿獎勵
 * 7) SaveCodec 完整序列化來回還原與舊版 v0.6.0 JSON 無痛向下相容
 */
public class Phase3And4Test {

    @Test
    public void qrCodeGeneratorProducesValidFinderAndScalesVersions() {
        QrCodeGenerator.Matrix m1 = QrCodeGenerator.encode("WATR1:STAR-4821|沙發龍蛋|2");
        assertTrue("Version should be >= 1", m1.version >= 1 && m1.version <= 10);
        assertEquals(17 + 4 * m1.version, m1.size);
        // 驗證左上 7x7 Finder Pattern 外框與中心 3x3 黑點
        for (int i = 0; i < 7; i++) {
            assertTrue(m1.modules[0][i]);
            assertTrue(m1.modules[6][i]);
            assertTrue(m1.modules[i][0]);
            assertTrue(m1.modules[i][6]);
        }
        assertTrue(m1.modules[3][3]);
        assertFalse(m1.modules[1][1]);
        // 固定黑點 (size - 8, 8)
        assertTrue(m1.modules[m1.size - 8][8]);

        // 較長 payload 應自動升級到較高版本且不拋例外
        String longPayload = "WAGIFT1:20261123_ABCD|爸爸和大寶小寶|4|3|1|1|2026-11-23|9F2A|"
            + "extra-padding-for-higher-version-qr-code-test-payload";
        QrCodeGenerator.Matrix mLong = QrCodeGenerator.encode(longPayload);
        assertTrue(mLong.version > m1.version);
        assertEquals(17 + 4 * mLong.version, mLong.size);
    }

    @Test
    public void nfcAndQrHiddenTreasureLifecycleAndRewards() {
        StepProgress progress = new StepProgress();
        AdventureState state = new AdventureState();
        Random rng = new Random(7);

        TreasureAndQrState.HiddenTreasure t1 = state.treasure.createTreasure(
            "客廳沙發下的冰龍蛋",
            "軟軟的、晚上大家一起看繪本的地方",
            "04:a1:b2:c3",
            TreasureAndQrState.REWARD_MYSTERY_EGG,
            "2026-11-23",
            rng
        );
        assertEquals("04A1B2C3", t1.nfcUid);
        assertTrue(t1.hasNfc());
        assertEquals(1, state.treasure.activeTreasureCount());

        // 感應 NFC UID 應成功開箱並放入星光神祕蛋、解鎖尋寶藏寶師成就
        TreasureAndQrState.ClaimOutcome out = state.claimHiddenTreasureByNfc(progress, "04A1B2C3", "2026-11-23");
        assertTrue(out.ok);
        assertEquals(CompanionSystem.EGG_STAR, out.eggTypeDropped);
        assertEquals(1, state.treasure.totalTreasuresClaimed);
        assertEquals(0, state.treasure.activeTreasureCount());
        assertTrue(state.isExtraAchievementUnlocked(14));

        // 同一張卡片已開箱不可重複領取
        assertFalse(state.claimHiddenTreasureByNfc(progress, "04A1B2C3", "2026-11-23").ok);

        // 純離線 QR 藏寶可用完整 QR payload 或短碼解鎖
        TreasureAndQrState.HiddenTreasure t2 = state.treasure.createTreasure(
            "陽台花盆寶箱", "綠色葉子躲在外面曬太陽", "", TreasureAndQrState.REWARD_SNACK_PACK, "2026-11-23", rng
        );
        assertFalse(t2.hasNfc());
        TreasureAndQrState.ClaimOutcome out2 = state.claimHiddenTreasureByCode(progress, t2.toQrPayload(), "2026-11-23");
        assertTrue(out2.ok);
        assertEquals(3, out2.snacksGained);
        assertEquals(1, out2.blanketsGained);
    }

    @Test
    public void familyGiftQrCardChecksumAntiTamperAndCampGuestVisit() {
        StepProgress senderProg = new StepProgress();
        AdventureState sender = new AdventureState();
        sender.companions.unlockDirect(CompanionSystem.ID_DRAGON, 0, true, "2026-11-20");
        sender.companions.equippedId = CompanionSystem.ID_DRAGON;

        TreasureAndQrState.FamilyGiftCard card = sender.treasure.createFamilyGiftCard(
            "媽媽", CompanionSystem.ID_DRAGON, 2, true, TreasureAndQrState.GIFT_EXPLORER_BOX, "2026-11-23", new Random(42)
        );
        assertTrue(card.encodedString.startsWith(TreasureAndQrState.GIFT_PREFIX));

        // 篡改任一字元應遭校驗碼攔截
        String tampered = card.encodedString.replace("媽媽", "陌生");
        assertNull(TreasureAndQrState.parseGiftCard(tampered));

        // 另一支手機領取禮物卡應入帳金幣／毛毯並邀請旅伴串門子，且不可重複領取
        StepProgress receiverProg = new StepProgress();
        AdventureState receiver = new AdventureState();
        int beforeBlankets = receiver.camp.warmBlankets;
        TreasureAndQrState.GiftRedeemOutcome res = receiver.redeemFamilyGiftCode(
            receiverProg, card.encodedString, "2026-11-23"
        );
        assertTrue(res.ok);
        assertEquals(beforeBlankets + 1, receiver.camp.warmBlankets);
        assertTrue(receiver.treasure.guestVisit.isActiveOn("2026-11-23"));
        assertEquals(CompanionSystem.ID_DRAGON, receiver.treasure.guestVisit.companionId);
        assertTrue(receiver.isExtraAchievementUnlocked(15));

        assertFalse(receiver.redeemFamilyGiftCode(receiverProg, card.encodedString, "2026-11-23").ok);
    }

    @Test
    public void footprintFogOfWarPrivacyQuantizationAndSecretBaseNaming() {
        StepProgress progress = new StepProgress();
        AdventureState state = new AdventureState();

        // 預設關閉不記錄
        assertFalse(state.footprints.enabled);
        assertFalse(state.recordCoarseFootprint(progress, 25.0330, 121.5654, "2026-11-23").isNewTile);

        // 開啟後首次記錄錨定為 (0, 0) 並發放首探獎勵
        state.footprints.enabled = true;
        FootprintState.DiscoveryOutcome d1 = state.recordCoarseFootprint(progress, 25.0330, 121.5654, "2026-11-23");
        assertTrue(d1.isNewTile);
        assertEquals(0, d1.tile.rx);
        assertEquals(0, d1.tile.ry);
        assertEquals(FootprintState.NEW_TILE_COINS, d1.coinsGained);

        // 同一 200m 格子內微幅移動不重複發獎
        FootprintState.DiscoveryOutcome same = state.recordCoarseFootprint(progress, 25.0331, 121.5655, "2026-11-23");
        assertFalse(same.isNewTile);
        assertEquals(2, state.footprints.currentTile().visitCount);

        // 走滿 5 格應掉蛋並解鎖「迷霧開拓者」成就
        for (int step = 1; step <= 4; step++) {
            FootprintState.DiscoveryOutcome dn = state.recordCoarseFootprint(
                progress, 25.0330 + step * 0.0025, 121.5654, "2026-11-23"
            );
            assertTrue(dn.isNewTile);
            if (step == 4) {
                assertTrue("5th new tile should drop mist egg", dn.droppedEgg);
            }
        }
        assertEquals(5, state.footprints.discoveredCount());
        assertTrue(state.isExtraAchievementUnlocked(16));

        // 命名秘密基地
        assertTrue(state.footprints.nameTile(0, 0, "外婆家公園"));
        assertEquals(1, state.footprints.namedBaseCount());
        assertEquals("外婆家公園", state.footprints.namedBases().get(0).baseName);

        // 手動打卡模式
        FootprintState.DiscoveryOutcome manual = state.recordManualSecretBase(progress, "河濱秘密基地", "2026-11-24");
        assertTrue(manual.isNewTile);
        assertEquals(6, state.footprints.discoveredCount());
    }

    @Test
    public void environmentLightAndCadenceEasterEggsRespectDailyCooldowns() {
        StepProgress progress = new StepProgress();
        progress.day = "2026-11-23";
        progress.todaySteps = 200L;
        AdventureState state = new AdventureState();
        state.camp.gardenPlots[0].seedId = 1;
        state.camp.gardenPlots[0].requiredSteps = 500;
        state.camp.gardenPlots[0].waterSteps = 0;

        // 暗光觸發夜光小妖，同日第二次不重複觸發
        EnvironmentSensorState.TriggerResult dark = state.applyLightReading(progress, 6f, "2026-11-23");
        assertEquals(EnvironmentSensorState.EVENT_NIGHT_SPRITE, dark.eventType);
        assertEquals(EnvironmentSensorState.EVENT_NONE, state.applyLightReading(progress, 5f, "2026-11-23").eventType);
        assertTrue(state.isExtraAchievementUnlocked(17));

        // 強陽光替菜園澆水 +200 步
        EnvironmentSensorState.TriggerResult sun = state.applyLightReading(progress, 15000f, "2026-11-23");
        assertEquals(EnvironmentSensorState.EVENT_SUN_BLESSING, sun.eventType);
        assertEquals(200, state.camp.gardenPlots[0].waterSteps);

        // 60 秒內快走 >= 80 步觸發疾風追逐戰
        long t0 = 1_000_000L;
        assertEquals(EnvironmentSensorState.EVENT_NONE, state.applyCadenceSteps(progress, 45L, t0, "2026-11-23").eventType);
        EnvironmentSensorState.TriggerResult burst = state.applyCadenceSteps(progress, 45L, t0 + 25_000L, "2026-11-23");
        assertEquals(EnvironmentSensorState.EVENT_WIND_CHASE, burst.eventType);

        // 累計走滿 150 步後歇腳 >= 3 分鐘再起步觸發路邊歇腳物語
        state.applyCadenceSteps(progress, 80L, t0 + 35_000L, "2026-11-23");
        EnvironmentSensorState.TriggerResult rest = state.applyCadenceSteps(progress, 10L, t0 + 250_000L, "2026-11-23");
        assertEquals(EnvironmentSensorState.EVENT_ROADSIDE_REST, rest.eventType);
    }

    @Test
    public void natureVisionClassifiesScenesAndCompletesEncyclopedia() {
        int w = 24;
        int h = 24;

        // 1. 濃綠森林
        int[] forest = solidPixels(w * h, 0xFF2E8B45);
        assertEquals(NatureVisionEngine.CAT_TREE_LEAF, NatureVisionEngine.classifyPixels(forest, w, h).categoryId);

        // 2. 藍天白雲（上藍下亮白）
        int[] sky = new int[w * h];
        for (int y = 0; y < h; y++) {
            int col = y < h / 2 ? 0xFF4A9EE8 : 0xFFF2F6FA;
            for (int x = 0; x < w; x++) sky[y * w + x] = col;
        }
        assertEquals(NatureVisionEngine.CAT_SKY_CLOUD, NatureVisionEngine.classifyPixels(sky, w, h).categoryId);

        // 3. 鮮豔花朵（外圍綠葉＋中心粉紅花瓣）
        int[] flower = solidPixels(w * h, 0xFF3E8E4C);
        for (int y = h / 4; y < (h * 3) / 4; y++) {
            for (int x = w / 4; x < (w * 3) / 4; x++) {
                flower[y * w + x] = 0xFFE84A8A;
            }
        }
        assertEquals(NatureVisionEngine.CAT_FLOWER, NatureVisionEngine.classifyPixels(flower, w, h).categoryId);

        // 4. 星空夜色（深黑夜空＋亮星點）
        int[] night = solidPixels(w * h, 0xFF0A0F1E);
        night[3 * w + 5] = 0xFFFFFFFF;
        night[6 * w + 14] = 0xFFFFFFCC;
        assertEquals(NatureVisionEngine.CAT_NIGHT_STAR, NatureVisionEngine.classifyPixels(night, w, h).categoryId);

        // 集滿 8 大自然類別應解鎖成就 18 & 19 並送星光奇蹟蛋
        StepProgress progress = new StepProgress();
        AdventureState state = new AdventureState();
        WorldClock.Snapshot clock = WorldClock.fromDateTime(java.time.LocalDateTime.of(2026, 11, 23, 10, 0));
        ColorScavenger.AnalysisResult dummy = ColorScavenger.analyzeRgbPixels(forest, ColorScavenger.COLOR_ANY);
        AdventureState.PhotoHuntOutcome lastOutcome = null;
        for (int cat = 0; cat < NatureVisionEngine.CATEGORY_COUNT; cat++) {
            lastOutcome = state.completePhotoHunt(
                progress, "m_" + cat, 8, "p.jpg", "", "", "自然發現", 3, dummy, cat, clock
            );
            assertTrue(lastOutcome.ok);
        }
        assertNotNull(lastOutcome);
        assertTrue(lastOutcome.completedNatureDex);
        assertEquals(NatureVisionEngine.CATEGORY_COUNT, state.natureDex.unlockedCount());
        assertTrue(state.isExtraAchievementUnlocked(18));
        assertTrue(state.isExtraAchievementUnlocked(19));
    }

    @Test
    public void saveCodecRoundTripsPhase3And4AndKeepsLegacyCompatibility() {
        StepProgress progress = new StepProgress();
        AdventureState src = new AdventureState();
        src.treasure.createTreasure("書櫃寶箱", "充滿故事書的地方", "AABBCCDD", 1, "2026-11-23", new Random(9));
        src.treasure.totalTreasuresClaimed = 2;
        src.treasure.guestVisit.senderName = "爸爸";
        src.treasure.guestVisit.companionId = CompanionSystem.ID_OTTER;
        src.treasure.guestVisit.visitDate = "2026-11-23";
        src.footprints.enabled = true;
        src.recordCoarseFootprint(progress, 25.0330, 121.5654, "2026-11-23");
        src.footprints.nameTile(0, 0, "祕密滑梯");
        src.envSensors.lastNightSpriteDate = "2026-11-23";
        src.envSensors.totalSensorEventsTriggered = 3;
        src.natureDex.recordCategory(NatureVisionEngine.CAT_FLOWER);
        src.natureDex.recordCategory(NatureVisionEngine.CAT_SKY_CLOUD);

        String json = SaveCodec.serialize(src);
        AdventureState dst = new AdventureState();
        assertTrue(SaveCodec.deserializeInto(json, dst));

        assertEquals(1, dst.treasure.treasures.size());
        assertEquals("AABBCCDD", dst.treasure.treasures.get(0).nfcUid);
        assertEquals(2, dst.treasure.totalTreasuresClaimed);
        assertEquals("爸爸", dst.treasure.guestVisit.senderName);
        assertEquals(CompanionSystem.ID_OTTER, dst.treasure.guestVisit.companionId);
        assertTrue(dst.footprints.enabled);
        assertEquals(1, dst.footprints.discoveredCount());
        assertEquals("祕密滑梯", dst.footprints.namedBases().get(0).baseName);
        assertEquals("2026-11-23", dst.envSensors.lastNightSpriteDate);
        assertEquals(3, dst.envSensors.totalSensorEventsTriggered);
        assertEquals(2, dst.natureDex.unlockedCount());
        assertTrue(dst.natureDex.isUnlocked(NatureVisionEngine.CAT_FLOWER));

        // 驗證 v0.6.0 舊存檔（無 Phase 3 & 4 欄位）安全預設
        String legacyV06 = "{\"schemaVersion\":2,\"migratedFrom\":\"fresh\",\"onboardingCompleted\":true}";
        AdventureState legacy = new AdventureState();
        assertTrue(SaveCodec.deserializeInto(legacyV06, legacy));
        assertFalse(legacy.footprints.enabled);
        assertEquals(0, legacy.treasure.treasures.size());
        assertEquals(0, legacy.natureDex.unlockedCount());
    }

    private static int[] solidPixels(int count, int argb) {
        int[] px = new int[count];
        for (int i = 0; i < count; i++) px[i] = argb;
        return px;
    }
}
