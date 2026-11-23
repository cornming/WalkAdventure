package com.cornming.walkadventure;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 走走冒險的 RPG 進度與戰報狀態：裝備、同伴、多主題區域、奇遇互動、每日任務、連續天數、成就與 7 日歷史。
 * 純 Java 實作，完整相容 v0.1.0–v0.3.0 的 SharedPreferences 存檔並支援單元測試。
 */
final class AdventureState {
    static final int MAX_HISTORY_DAYS = 14;
    static final int MAX_LOG_ENTRIES = 20;
    private static final int MERCHANT_INVEST_COST = 25;

    long bonusXp;
    int bootsLevel;
    int charmLevel;
    int compassLevel;
    int equippedCompanion = AdventureCatalog.COMPANION_NONE;
    int unlockedCompanionsMask = 1; // bit 0 永遠代表「獨自出發」
    int selectedBiomeIndex = -1; // -1 表示自動跟隨目前推進的最前線章節

    int streakDays;
    int bestStreakDays;
    String lastStreakDay;

    String claimedDailyDate;
    int claimedDailyMask;
    int unlockedAchievementsMask;
    int encountersResolved;
    boolean starterGiven;

    AdventureCatalog.PendingEncounter pendingEncounter;
    final List<DailyRecord> dailyHistory = new ArrayList<>();
    final List<LogEntry> adventureLog = new ArrayList<>();

    // Phase 1（v2.0）領域子系統
    final FamilyProfile family = new FamilyProfile();
    final CampfireVault vault = new CampfireVault();
    final CompanionSystem companions = new CompanionSystem();
    final CampState camp = new CampState();
    final StoryCatalog stories = new StoryCatalog();
    final GuideState guide = new GuideState();

    boolean pioneerBadge = false;
    String migratedFrom = "";
    int extraAchievementsMask = 0;
    int campfireUnboxCount = 0;
    int eggsHatchedCount = 0;
    int gardenHarvestCount = 0;
    String lastBirthdayGiftDate = "";
    String lastPenguinSnackDate = "";

    // Phase 2（實境相機尋寶與明信片回憶）
    int rainbowCollectedMask = 0;
    int rainbowCompletedSets = 0;
    int photoHuntsCompleted = 0;
    int voiceMemoriesCount = 0;

    // Phase 3 & 4（實體藏寶 NFC/QR、本機足跡迷霧、環境感測彩蛋、端側自然圖鑑）
    final TreasureAndQrState treasure = new TreasureAndQrState();
    final FootprintState footprints = new FootprintState();
    final EnvironmentSensorState envSensors = new EnvironmentSensorState();
    final NatureVisionEngine.DexState natureDex = new NatureVisionEngine.DexState();

    static final class DailyRecord {
        final String date;
        long steps;
        long coinsEarned;
        int encounters;

        DailyRecord(String date, long steps, long coinsEarned, int encounters) {
            this.date = date;
            this.steps = Math.max(0L, steps);
            this.coinsEarned = Math.max(0L, coinsEarned);
            this.encounters = Math.max(0, encounters);
        }
    }

    static final class LogEntry {
        final String date;
        final String summary;

        LogEntry(String date, String summary) {
            this.date = date == null ? "" : date;
            this.summary = summary == null ? "" : summary;
        }
    }

    static final class ActionResult {
        final boolean ok;
        final String message;

        ActionResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    long totalAdventureXp(StepProgress progress) {
        if (progress == null) return Math.max(0L, bonusXp);
        long life = Math.max(0L, progress.lifetime);
        long extra = Math.max(0L, bonusXp);
        if (Long.MAX_VALUE - life < extra) return Long.MAX_VALUE;
        return life + extra;
    }

    int adventureLevel(StepProgress progress) {
        return (int) Math.min(Integer.MAX_VALUE, (totalAdventureXp(progress) / StepProgress.STEPS_PER_CHEST) + 1);
    }

    long xpInLevel(StepProgress progress) {
        return totalAdventureXp(progress) % StepProgress.STEPS_PER_CHEST;
    }

    long exploredCells(StepProgress progress) {
        return totalAdventureXp(progress) / StepProgress.STEPS_PER_CHEST;
    }

    int frontierBiomeIndex(StepProgress progress) {
        return (int) ((exploredCells(progress) / AdventureCatalog.CELLS_PER_BIOME) % AdventureCatalog.BIOME_COUNT);
    }

    int highestUnlockedBiomeIndex(StepProgress progress) {
        long biomesCrossed = exploredCells(progress) / AdventureCatalog.CELLS_PER_BIOME;
        return (int) Math.min(AdventureCatalog.BIOME_COUNT - 1L, biomesCrossed);
    }

    int activeDisplayBiomeIndex(StepProgress progress) {
        int maxUnlocked = highestUnlockedBiomeIndex(progress);
        if (selectedBiomeIndex >= 0 && selectedBiomeIndex <= maxUnlocked) {
            return selectedBiomeIndex;
        }
        return frontierBiomeIndex(progress);
    }

    int biomeCellsLit(StepProgress progress) {
        return (int) (exploredCells(progress) % AdventureCatalog.CELLS_PER_BIOME);
    }

    int displayBiomeLitCells(StepProgress progress) {
        if (activeDisplayBiomeIndex(progress) != frontierBiomeIndex(progress)) {
            return AdventureCatalog.CELLS_PER_BIOME;
        }
        return biomeCellsLit(progress);
    }

    /** 升級或啟動時同步當日步數快照與既有成就（不改動真實步數計數）。 */
    void syncSnapshot(StepProgress progress, String today) {
        if (progress == null || today == null) return;
        DailyRecord rec = ensureDailyRecord(today);
        long todaySteps = progress.todayStepsOn(today);
        if (todaySteps > rec.steps) {
            rec.steps = todaySteps;
        }
        updateStreak(today, todaySteps);
        evaluateAchievements(progress, today);
    }

    int adventureCycle(StepProgress progress) {
        long perCycle = (long) AdventureCatalog.BIOME_COUNT * AdventureCatalog.CELLS_PER_BIOME;
        return (int) (exploredCells(progress) / perCycle) + 1;
    }

    long nextNodeAtXp(StepProgress progress) {
        return (exploredCells(progress) + 1L) * StepProgress.STEPS_PER_CHEST;
    }

    void shiftDisplayBiome(StepProgress progress, int delta) {
        int maxUnlocked = highestUnlockedBiomeIndex(progress);
        int current = activeDisplayBiomeIndex(progress);
        if (maxUnlocked <= 0) {
            selectedBiomeIndex = -1;
            return;
        }
        int next = Math.max(0, Math.min(maxUnlocked, current + delta));
        selectedBiomeIndex = (next == frontierBiomeIndex(progress)) ? -1 : next;
    }

    int activeCompanionId() {
        if (companions.equippedId != CompanionSystem.ID_NONE) {
            return companions.equippedId;
        }
        return equippedCompanion;
    }

    String activeCompanionName() {
        int id = activeCompanionId();
        CompanionSystem.SpeciesDef sp = CompanionSystem.species(id);
        if (sp != null) return sp.name;
        return AdventureCatalog.companion(equippedCompanion).name;
    }

    void syncLegacyCompanionsIntoV2(String today) {
        for (int legacyId = 1; legacyId < AdventureCatalog.COMPANION_COUNT; legacyId++) {
            if (isCompanionUnlocked(legacyId)) {
                companions.unlockDirect(legacyId, 0, false, today);
            }
        }
        if (companions.equippedId == CompanionSystem.ID_NONE
            && equippedCompanion > 0
            && companions.isOwned(equippedCompanion)) {
            companions.equippedId = equippedCompanion;
        }
        syncV2EquippedMirror();
    }

    void syncV2EquippedMirror() {
        for (int legacyId = 1; legacyId < AdventureCatalog.COMPANION_COUNT; legacyId++) {
            if (companions.isOwned(legacyId)) {
                unlockedCompanionsMask |= (1 << legacyId);
            }
        }
        if (companions.equippedId >= 0 && companions.equippedId < AdventureCatalog.COMPANION_COUNT) {
            equippedCompanion = companions.equippedId;
        }
    }

    int xpMultiplierPercent() {
        int pct = bootsLevel * 10;
        int cid = activeCompanionId();
        if (cid == CompanionSystem.ID_DRAGON) {
            pct += companions.isOwned(CompanionSystem.ID_DRAGON) ? companions.dragonXpBonusPercent() : 20;
        }
        return pct;
    }

    int coinMultiplierPercent() {
        return charmLevel * 15;
    }

    boolean isCompanionUnlocked(int companionId) {
        if (companionId == AdventureCatalog.COMPANION_NONE) return true;
        if (companions.isOwned(companionId)) return true;
        if (companionId < 0 || companionId >= AdventureCatalog.COMPANION_COUNT) return false;
        return (unlockedCompanionsMask & (1 << companionId)) != 0;
    }

    boolean isAchievementUnlocked(int index) {
        if (index < 0 || index >= AdventureCatalog.ACHIEVEMENT_COUNT) return false;
        return (unlockedAchievementsMask & (1 << index)) != 0;
    }

    boolean isExtraAchievementUnlocked(int index) {
        if (index < 0 || index >= AdventureCatalog.EXTRA_ACHIEVEMENT_COUNT) return false;
        return (extraAchievementsMask & (1 << index)) != 0;
    }

    boolean isDailyQuestClaimed(int questIndex, String today) {
        if (today == null || !today.equals(claimedDailyDate)) return false;
        if (questIndex < 0 || questIndex >= AdventureCatalog.DAILY_QUEST_COUNT) return false;
        return (claimedDailyMask & (1 << questIndex)) != 0;
    }

    int pendingClaimableDailyCount(StepProgress progress, String today) {
        if (progress == null || today == null) return 0;
        long todaySteps = progress.todayStepsOn(today);
        int count = 0;
        for (AdventureCatalog.DailyQuestDef q : AdventureCatalog.DAILY_QUESTS) {
            if (todaySteps >= q.targetSteps && !isDailyQuestClaimed(q.index, today)) {
                count++;
            }
        }
        return count;
    }

    int effectiveStreak(String today) {
        if (lastStreakDay == null || today == null) return 0;
        if (today.equals(lastStreakDay)) return streakDays;
        LocalDate parsedToday = parseDateSafe(today);
        LocalDate parsedLast = parseDateSafe(lastStreakDay);
        if (parsedToday == null || parsedLast == null) return 0;
        if (parsedToday.minusDays(1).equals(parsedLast)) {
            return streakDays;
        }
        return 0;
    }

    String currentTitle(StepProgress progress) {
        for (int i = AdventureCatalog.ACHIEVEMENTS.length - 1; i >= 0; i--) {
            if (isAchievementUnlocked(i)) {
                return AdventureCatalog.ACHIEVEMENTS[i].title;
            }
        }
        return adventureLevel(progress) >= 3 ? "熟練旅人" : "冒險者";
    }

    /** 若是第一次使用新版且尚未擁有待處理奇遇，放上一個村長補給包讓玩家立刻有互動樂趣（不偽造步數）。 */
    boolean ensureStarterEncounter(String today) {
        if (starterGiven || pendingEncounter != null) return false;
        starterGiven = true;
        pendingEncounter = AdventureCatalog.createStarterEncounter();
        appendLog(today, "📜 展開冒險新篇章！村長送來了出發補給包");
        return true;
    }

    /**
     * 將一次真實感測器步數更新同步套用至冒險狀態，並計算裝備／同伴加成、每日紀錄、連勝與奇遇生成。
     * 回傳可供畫面與無障礙朗讀使用的綜合提醒文字（若無特殊事件則為 null）。
     */
    String applyWalkUpdate(StepProgress progress, StepProgress.Update walkUpdate, String today, Random random) {
        if (progress == null || walkUpdate == null || today == null) return null;

        long todaySteps = progress.todayStepsOn(today);
        DailyRecord todayRecord = ensureDailyRecord(today);
        todayRecord.steps = todaySteps;

        long extraCoinsFromChest = 0L;
        if (walkUpdate.steps > 0) {
            int xpBonusPct = xpMultiplierPercent();
            long gainedExtraXp = (walkUpdate.steps * xpBonusPct) / 100L;
            if (gainedExtraXp > 0) {
                addBonusXp(gainedExtraXp);
            }
        }

        if (walkUpdate.chests > 0) {
            long baseCoins = walkUpdate.chestCoins;
            long charmExtra = (baseCoins * coinMultiplierPercent()) / 100L;
            long shibaExtra = (equippedCompanion == AdventureCatalog.COMPANION_SHIBA)
                ? walkUpdate.chests * 8L
                : 0L;
            extraCoinsFromChest = charmExtra + shibaExtra;
            progress.coins += extraCoinsFromChest;
            long totalChestCoins = baseCoins + extraCoinsFromChest;
            todayRecord.coinsEarned += totalChestCoins;
            appendLog(today, String.format(
                Locale.ROOT,
                "🎁 開啟 %d 個步數寶箱，獲得 %d 金幣",
                walkUpdate.chests,
                totalChestCoins
            ));
        }

        updateStreak(today, todaySteps);

        StringBuilder notice = new StringBuilder();
        if (walkUpdate.chests > 0) {
            long totalChestCoins = walkUpdate.chestCoins + extraCoinsFromChest;
            notice.append(walkUpdate.chests == 1
                ? "🎁 寶箱！獲得 " + totalChestCoins + " 金幣"
                : "🎁 寶箱 ×" + walkUpdate.chests + "！獲得 " + totalChestCoins + " 金幣");
        }

        if (walkUpdate.event && random != null) {
            if (pendingEncounter != null) {
                ActionResult auto = resolvePendingEncounter(progress, 0, today, random);
                if (auto.ok && !auto.message.isEmpty()) {
                    appendLog(today, "⏭️ 上個奇遇已自動結算：" + auto.message);
                }
            }
            int biome = frontierBiomeIndex(progress);
            pendingEncounter = AdventureCatalog.generateEncounter(biome, compassLevel, random);
            if (notice.length() > 0) notice.append("\n");
            notice.append("❗ ").append(pendingEncounter.title);
        }

        int unlockedNew = evaluateAchievements(progress, today);
        if (unlockedNew > 0) {
            if (notice.length() > 0) notice.append("\n");
            notice.append("🏆 解鎖 ").append(unlockedNew).append(" 項新成就！");
        }
        return notice.length() > 0 ? notice.toString() : null;
    }

    ActionResult resolvePendingEncounter(StepProgress progress, int choiceIndex, String today, Random random) {
        if (progress == null || pendingEncounter == null) {
            return new ActionResult(false, "目前沒有等待處理的奇遇");
        }
        AdventureCatalog.PendingEncounter enc = pendingEncounter;
        Random rng = random != null ? random : new Random();
        int bonusGoldPct = coinMultiplierPercent();
        boolean luckyCrit = rng.nextInt(100) < (15 + compassLevel * 12);
        long gainedCoins = 0L;
        long gainedXp = 0L;
        String detail;

        switch (enc.type) {
            case AdventureCatalog.ENCOUNTER_STARTER:
                pendingEncounter = null;
                if (choiceIndex == 0) {
                    gainedCoins = 30;
                    detail = "打開村長補給包，獲得 30 金幣！";
                } else {
                    gainedXp = 60;
                    detail = "完成出發暖身，獲得 60 冒險 XP！";
                }
                break;

            case AdventureCatalog.ENCOUNTER_MONSTER:
                pendingEncounter = null;
                if (choiceIndex == 0) {
                    long base = 18 + enc.biomeIndex * 5L + rng.nextInt(10);
                    gainedCoins = applyCoinBonuses(base, bonusGoldPct, luckyCrit);
                    gainedXp = 45 + enc.biomeIndex * 10L;
                    detail = "⚔️ 擊退「" + AdventureCatalog.biome(enc.biomeIndex).name + "」守衛怪！+"
                        + gainedCoins + " 金幣、+" + gainedXp + " XP";
                } else {
                    gainedXp = 65 + enc.biomeIndex * 8L;
                    gainedCoins = 8;
                    detail = "🍖 溫柔安撫了小怪獸，收到謝禮 +8 金幣、+" + gainedXp + " XP";
                }
                break;

            case AdventureCatalog.ENCOUNTER_CHEST:
                pendingEncounter = null;
                if (choiceIndex == 0) {
                    long base = 24 + enc.biomeIndex * 6L + rng.nextInt(14);
                    gainedCoins = applyCoinBonuses(base, bonusGoldPct, luckyCrit);
                    detail = "🔑 開啟神秘寶箱！獲得 " + gainedCoins + " 金幣" + (luckyCrit ? "（幸運暴擊！）" : "");
                } else {
                    long base = 16 + enc.biomeIndex * 4L + rng.nextInt(12);
                    gainedCoins = applyCoinBonuses(base, bonusGoldPct, luckyCrit);
                    gainedXp = 50;
                    detail = "✨ 許願開箱獲得 " + gainedCoins + " 金幣與 +50 XP";
                }
                break;

            case AdventureCatalog.ENCOUNTER_MERCHANT:
                if (choiceIndex == 1) {
                    if (progress.coins < MERCHANT_INVEST_COST) {
                        return new ActionResult(false, "金幣不足 " + MERCHANT_INVEST_COST + "，可以先選免費祝福！");
                    }
                    progress.coins -= MERCHANT_INVEST_COST;
                    pendingEncounter = null;
                    long jackpot = 35 + rng.nextInt(56) + (luckyCrit ? 20 : 0);
                    gainedCoins = applyCoinBonuses(jackpot, bonusGoldPct, false);
                    long net = gainedCoins - MERCHANT_INVEST_COST;
                    gainedXp = 35;
                    detail = "🎁 抽中冒險福袋！開出 " + gainedCoins + " 金幣（淨賺 +" + net + "）與 +35 XP";
                } else {
                    pendingEncounter = null;
                    gainedCoins = applyCoinBonuses(18, bonusGoldPct, luckyCrit);
                    detail = "🤝 收下商人祝福，獲得 " + gainedCoins + " 金幣";
                }
                break;

            case AdventureCatalog.ENCOUNTER_STORY:
                pendingEncounter = null;
                StoryCatalog.ArcDef arc = StoryCatalog.arc(stories.activeArcId);
                StoryCatalog.ChapterDef ch = stories.currentStoryChapter();
                boolean pickA = (choiceIndex == 0);
                long baseStoryCoins = pickA ? ch.coinsA : ch.coinsB;
                int baseStoryStar = pickA ? ch.starA : ch.starB;
                int bondGain = pickA ? ch.bondA : ch.bondB;
                gainedCoins = applyCoinBonuses(baseStoryCoins, bonusGoldPct, luckyCrit);
                gainedXp = 60L + enc.biomeIndex * 10L;
                int starTotal = baseStoryStar;
                if (activeCompanionId() == CompanionSystem.ID_FOX && WorldClock.now().isNightOrDusk()) {
                    starTotal *= 2;
                }
                camp.starSand += starTotal;
                camp.wood += 2;
                camp.petals += 1;
                if (activeCompanionId() == CompanionSystem.ID_SQUIRREL) {
                    camp.wood += 3;
                    camp.petals += 2;
                }
                CompanionSystem.OwnedCompanion activeOc = companions.getOwned(activeCompanionId());
                if (activeOc != null) {
                    activeOc.addBondXp(bondGain);
                }
                boolean arcDone = stories.advanceStoryArc();
                String eggBonusNote = "";
                if (arcDone) {
                    stories.unlockSeasonStamp(6); // 繪本讀者
                    if (arc.rewardEggType >= 0) {
                        if (companions.hasFreeEggSlot()) {
                            companions.addEgg(arc.rewardEggType, enc.biomeIndex);
                            eggBonusNote = " 獲得一顆「" + CompanionSystem.eggTypeDef(arc.rewardEggType).name + "」放入蛋巢！";
                        } else {
                            enqueueIntoVaultSafe(
                                progress,
                                CampfireVault.KIND_EGG,
                                "故事獎勵：" + CompanionSystem.eggTypeDef(arc.rewardEggType).name,
                                0,
                                4,
                                arc.rewardEggType,
                                today
                            );
                            eggBonusNote = " 獲得神祕蛋並已存入營火盒！";
                        }
                    }
                }
                detail = "📖 " + (pickA ? ch.outcomeA : ch.outcomeB)
                    + "（+" + gainedCoins + " 金幣、+" + starTotal + " 星砂"
                    + (arcDone ? "，完成整本故事！" : "") + "）" + eggBonusNote;
                break;

            case AdventureCatalog.ENCOUNTER_SHRINE:
            default:
                pendingEncounter = null;
                if (choiceIndex == 0) {
                    gainedXp = 90 + enc.biomeIndex * 15L + (luckyCrit ? 30 : 0);
                    detail = "🌟 神壇照耀！獲得 +" + gainedXp + " 冒險 XP";
                } else {
                    gainedCoins = applyCoinBonuses(30 + enc.biomeIndex * 5L, bonusGoldPct, luckyCrit);
                    detail = "🪙 財運祝福降臨！獲得 +" + gainedCoins + " 金幣";
                }
                break;
        }

        if (enc.type != AdventureCatalog.ENCOUNTER_STARTER) {
            int cid = activeCompanionId();
            if (cid == CompanionSystem.ID_SLIME) {
                gainedXp += companions.isOwned(CompanionSystem.ID_SLIME) ? companions.slimeEncounterBonusXp() : 20;
                gainedCoins += 6;
            } else if (cid == CompanionSystem.ID_DRAGON) {
                gainedCoins += 15;
            } else if (cid == CompanionSystem.ID_SQUIRREL && enc.type != AdventureCatalog.ENCOUNTER_STORY) {
                camp.wood += 2;
                camp.petals += 2;
            }
            if (enc.type != AdventureCatalog.ENCOUNTER_STORY) {
                CompanionSystem.OwnedCompanion oc = companions.getOwned(cid);
                if (oc != null) oc.addBondXp(10);
            }
            encountersResolved++;
        }

        progress.coins += gainedCoins;
        addBonusXp(gainedXp);

        DailyRecord rec = ensureDailyRecord(today);
        rec.coinsEarned += gainedCoins;
        rec.encounters++;
        appendLog(today, detail);
        evaluateAchievements(progress, today);
        if (enc.type == AdventureCatalog.ENCOUNTER_STORY) {
            evaluateExtraAchievements(progress, today);
        }
        return new ActionResult(true, detail);
    }

    private long applyCoinBonuses(long base, int pct, boolean luckyCrit) {
        long val = base + (base * pct) / 100L;
        if (luckyCrit) val += Math.max(8L, base / 2L);
        return val;
    }

    ActionResult upgradeGear(StepProgress progress, int gearIndex, String today) {
        if (progress == null || gearIndex < 0 || gearIndex >= AdventureCatalog.GEAR_COUNT) {
            return new ActionResult(false, "找不到這項裝備");
        }
        int curLevel = gearLevel(gearIndex);
        if (curLevel >= AdventureCatalog.GEAR_MAX_LEVEL) {
            return new ActionResult(false, AdventureCatalog.gearName(gearIndex) + "已達最高等級");
        }
        int cost = AdventureCatalog.gearUpgradeCost(gearIndex, curLevel);
        if (progress.coins < cost) {
            return new ActionResult(false, "金幣不足，升級需要 " + cost + " 金幣");
        }
        progress.coins -= cost;
        setGearLevel(gearIndex, curLevel + 1);
        String msg = "🛡️ " + AdventureCatalog.gearName(gearIndex) + " 升至 Lv." + (curLevel + 1) + "！";
        appendLog(today, msg);
        return new ActionResult(true, msg);
    }

    ActionResult buyOrEquipCompanion(StepProgress progress, int companionId, String today) {
        if (progress == null || companionId < 0 || companionId >= AdventureCatalog.COMPANION_COUNT) {
            return new ActionResult(false, "找不到這位同伴");
        }
        AdventureCatalog.CompanionDef def = AdventureCatalog.companion(companionId);
        if (isCompanionUnlocked(companionId)) {
            equippedCompanion = companionId;
            if (companionId == AdventureCatalog.COMPANION_NONE) {
                companions.equippedId = CompanionSystem.ID_NONE;
            } else {
                companions.unlockDirect(companionId, 0, false, today);
                companions.equippedId = companionId;
            }
            return new ActionResult(
                true,
                companionId == AdventureCatalog.COMPANION_NONE
                    ? "已切換為獨自冒險模式"
                    : "🐾 已換上夥伴「" + def.name + "」同行！"
            );
        }
        if (progress.coins < def.cost) {
            return new ActionResult(false, "金幣不足，招募「" + def.name + "」需要 " + def.cost + " 金幣");
        }
        progress.coins -= def.cost;
        unlockedCompanionsMask |= (1 << companionId);
        equippedCompanion = companionId;
        companions.unlockDirect(companionId, 0, false, today);
        companions.equippedId = companionId;
        String msg = "🐾 成功招募夥伴「" + def.name + "」！";
        appendLog(today, msg);
        evaluateAchievements(progress, today);
        return new ActionResult(true, msg);
    }

    ActionResult equipV2Companion(int companionId) {
        ActionResult res = companions.equip(companionId);
        if (res.ok) {
            syncV2EquippedMirror();
        }
        return res;
    }

    int dailyQuestCoinReward(int questIndex) {
        if (questIndex < 0 || questIndex >= AdventureCatalog.DAILY_QUEST_COUNT) return 0;
        int rewardCoins = AdventureCatalog.DAILY_QUESTS[questIndex].coinReward;
        if (activeCompanionId() == CompanionSystem.ID_OWL) {
            int bonusPct = companions.isOwned(CompanionSystem.ID_OWL) ? companions.owlDailyQuestBonusPercent() : 25;
            rewardCoins = rewardCoins + (rewardCoins * bonusPct) / 100;
        }
        return rewardCoins;
    }

    ActionResult claimDailyQuest(StepProgress progress, int questIndex, String today) {
        if (progress == null || today == null || questIndex < 0 || questIndex >= AdventureCatalog.DAILY_QUEST_COUNT) {
            return new ActionResult(false, "無效的任務編號");
        }
        AdventureCatalog.DailyQuestDef def = AdventureCatalog.DAILY_QUESTS[questIndex];
        long todaySteps = progress.todayStepsOn(today);
        if (todaySteps < def.targetSteps) {
            return new ActionResult(false, "今日步數尚未達成 " + def.targetSteps + " 步");
        }
        if (isDailyQuestClaimed(questIndex, today)) {
            return new ActionResult(false, "這項任務今天已經領過了");
        }
        if (!today.equals(claimedDailyDate)) {
            claimedDailyDate = today;
            claimedDailyMask = 0;
        }
        claimedDailyMask |= (1 << questIndex);

        int rewardCoins = dailyQuestCoinReward(questIndex);
        progress.coins += rewardCoins;
        addBonusXp(def.xpReward);
        int starBonus = 4 + questIndex * 3;
        if (activeCompanionId() == CompanionSystem.ID_OWL && WorldClock.now().isNightOrDusk()) {
            starBonus += 5;
        }
        camp.starSand += starBonus;
        DailyRecord rec = ensureDailyRecord(today);
        rec.coinsEarned += rewardCoins;
        updateStreak(today, todaySteps);

        String msg = "🎯 完成每日任務「" + def.title + "」！+" + rewardCoins + " 金幣、+" + def.xpReward + " XP";
        appendLog(today, msg);
        evaluateAchievements(progress, today);
        return new ActionResult(true, msg);
    }

    int evaluateAchievements(StepProgress progress, String today) {
        if (progress == null) return 0;
        long totalChests = Math.max(0L, progress.claimedChest / StepProgress.STEPS_PER_CHEST);
        int maxBiome = highestUnlockedBiomeIndex(progress);
        boolean hasCompanion = (unlockedCompanionsMask & ~1) != 0 || companions.ownedCount() > 0;
        int newlyUnlocked = 0;

        boolean[] conditions = {
            progress.lifetime >= 1_000L,
            progress.lifetime >= 5_000L,
            progress.lifetime >= 20_000L,
            progress.lifetime >= 50_000L,
            maxBiome >= 1,
            maxBiome >= 3,
            totalChests >= 5L,
            encountersResolved >= 5,
            hasCompanion,
            bestStreakDays >= 3
        };

        for (int i = 0; i < AdventureCatalog.ACHIEVEMENTS.length; i++) {
            if (!isAchievementUnlocked(i) && conditions[i]) {
                unlockedAchievementsMask |= (1 << i);
                AdventureCatalog.AchievementDef def = AdventureCatalog.ACHIEVEMENTS[i];
                progress.coins += def.coinReward;
                if (today != null) {
                    ensureDailyRecord(today).coinsEarned += def.coinReward;
                    appendLog(today, "🏆 解鎖成就「" + def.title + "」+" + def.coinReward + " 金幣");
                }
                newlyUnlocked++;
            }
        }
        return newlyUnlocked;
    }

    int evaluateExtraAchievements(StepProgress progress, String today) {
        if (progress == null) return 0;
        int newlyUnlocked = 0;
        boolean[] conds = {
            progress.lifetime >= 100_000L,
            family.members.size() >= 2 && family.isFamilyMode(),
            campfireUnboxCount >= 3,
            eggsHatchedCount >= 1,
            companions.ownedCount() >= 6,
            companions.maxStageAchieved() >= 3,
            camp.totalBuildingLevels() >= 10,
            gardenHarvestCount >= 5,
            stories.completedArcCount() >= 3,
            stories.totalLookUpCompleted >= 5,
            photoHuntsCompleted >= 1,
            rainbowCompletedSets >= 1 || rainbowCollectedMask == ColorScavenger.RAINBOW_COMPLETE_MASK,
            stories.memories.size() >= 5 || photoHuntsCompleted >= 5,
            voiceMemoriesCount >= 1,
            treasure.totalTreasuresClaimed >= 1,
            treasure.totalGiftsExchanged >= 1,
            footprints.discoveredCount() >= 5,
            envSensors.totalSensorEventsTriggered >= 1,
            natureDex.unlockedCount() >= 4,
            natureDex.isComplete() || natureDex.encyclopediaCompletedSets >= 1
        };
        for (int i = 0; i < AdventureCatalog.EXTRA_ACHIEVEMENTS.length; i++) {
            if (!isExtraAchievementUnlocked(i) && conds[i]) {
                extraAchievementsMask |= (1 << i);
                AdventureCatalog.AchievementDef def = AdventureCatalog.EXTRA_ACHIEVEMENTS[i];
                progress.coins += def.coinReward;
                camp.starSand += 10;
                if (today != null) {
                    ensureDailyRecord(today).coinsEarned += def.coinReward;
                    appendLog(today, "🌟 解鎖 2.0 成就「" + def.title + "」+" + def.coinReward + " 金幣、+10 星砂");
                }
                newlyUnlocked++;
            }
        }
        return newlyUnlocked;
    }

    static final class PhotoHuntOutcome {
        final boolean ok;
        final StoryCatalog.MemoryEntry created;
        final StoryCatalog.MemoryEntry evictedOldest;
        final boolean completedRainbowSet;
        final boolean completedNatureDex;
        final int coinsGained;
        final int starSandGained;
        final String message;

        PhotoHuntOutcome(
            boolean ok,
            StoryCatalog.MemoryEntry created,
            StoryCatalog.MemoryEntry evictedOldest,
            boolean completedRainbowSet,
            int coinsGained,
            int starSandGained,
            String message
        ) {
            this(ok, created, evictedOldest, completedRainbowSet, false, coinsGained, starSandGained, message);
        }

        PhotoHuntOutcome(
            boolean ok,
            StoryCatalog.MemoryEntry created,
            StoryCatalog.MemoryEntry evictedOldest,
            boolean completedRainbowSet,
            boolean completedNatureDex,
            int coinsGained,
            int starSandGained,
            String message
        ) {
            this.ok = ok;
            this.created = created;
            this.evictedOldest = evictedOldest;
            this.completedRainbowSet = completedRainbowSet;
            this.completedNatureDex = completedNatureDex;
            this.coinsGained = coinsGained;
            this.starSandGained = starSandGained;
            this.message = message;
        }
    }

    boolean isRainbowColorCollected(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= ColorScavenger.RAINBOW_COUNT) return false;
        return (rainbowCollectedMask & (1 << colorIndex)) != 0;
    }

    int rainbowCollectedCount() {
        return Integer.bitCount(rainbowCollectedMask & ColorScavenger.RAINBOW_COMPLETE_MASK);
    }

    /** 相容既有 10 參數呼叫的過載（無自然分類標籤）。 */
    PhotoHuntOutcome completePhotoHunt(
        StepProgress progress,
        String memoryId,
        int huntId,
        String photoPath,
        String doodlePath,
        String audioPath,
        String kidNote,
        int parentStars,
        ColorScavenger.AnalysisResult analysis,
        WorldClock.Snapshot clock
    ) {
        return completePhotoHunt(
            progress, memoryId, huntId, photoPath, doodlePath, audioPath, kidNote, parentStars, analysis, -1, clock
        );
    }

    /**
     * 完成一次實境相機尋寶並製成冒險明信片（含 Phase 4 自然生態端側視覺鑑定與稀有素材加成）：
     * 發放金幣、星砂、旅伴羈絆、自然素材、更新彩虹七色與自然百科圖鑑，並連動島嶼守護巨獸與成就系統。
     */
    PhotoHuntOutcome completePhotoHunt(
        StepProgress progress,
        String memoryId,
        int huntId,
        String photoPath,
        String doodlePath,
        String audioPath,
        String kidNote,
        int parentStars,
        ColorScavenger.AnalysisResult analysis,
        int natureCategoryId,
        WorldClock.Snapshot clock
    ) {
        if (progress == null || clock == null) {
            return new PhotoHuntOutcome(false, null, null, false, false, 0, 0, "目前無法儲存明信片");
        }
        ColorScavenger.HuntDef hunt = ColorScavenger.huntById(huntId);
        int clampedStars = Math.max(1, Math.min(3, parentStars));
        boolean colorMatched = analysis != null && analysis.targetMatched;
        int domHue = analysis != null ? analysis.dominantHue : -1;
        String colorLabel = hunt.isRainbowColorHunt()
            ? hunt.colorLabel
            : (analysis != null ? analysis.dominantColorName : hunt.colorLabel);

        int coinsGained = hunt.baseCoins + clampedStars * 8 + (colorMatched ? 10 : 0);
        int starGained = hunt.baseStarSand + clampedStars * 4 + (colorMatched ? 4 : 0);
        if (activeCompanionId() == CompanionSystem.ID_OTTER) {
            starGained += 12;
        }

        // Phase 4：自然生態圖鑑鑑定加成與稀有掉落
        boolean completedNatureNow = false;
        String natureBonusTag = "";
        int safeNatureCat = (natureCategoryId >= 0 && natureCategoryId < NatureVisionEngine.CATEGORY_COUNT)
            ? natureCategoryId : -1;
        if (safeNatureCat >= 0) {
            NatureVisionEngine.CategoryDef nDef = NatureVisionEngine.category(safeNatureCat);
            boolean firstInDex = natureDex.recordCategory(safeNatureCat);
            coinsGained += nDef.bonusCoins;
            starGained += nDef.bonusStarSand;
            camp.wood += nDef.bonusWood;
            camp.petals += nDef.bonusPetals;
            camp.snacks += nDef.bonusSnacks;
            natureBonusTag = " · 🔬 " + nDef.emoji + nDef.badgeTitle + (firstInDex ? "（新圖鑑！）" : "");
            if (firstInDex && natureDex.isComplete() && natureDex.encyclopediaCompletedSets == 0) {
                completedNatureNow = true;
                natureDex.encyclopediaCompletedSets++;
                coinsGained += 120;
                starGained += 40;
                if (companions.hasFreeEggSlot()) {
                    companions.addEgg(CompanionSystem.EGG_STAR, frontierBiomeIndex(progress));
                } else {
                    enqueueIntoVaultSafe(
                        progress,
                        CampfireVault.KIND_EGG,
                        "自然百科星光奇蹟蛋",
                        0,
                        10,
                        CompanionSystem.EGG_STAR,
                        clock.isoDate
                    );
                }
                appendLog(clock.isoDate, "🔬 集滿全部 8 種自然生態圖鑑！獲得 +120 金幣、+40 星砂與星光奇蹟蛋！");
            }
        }

        progress.coins += coinsGained;
        camp.starSand += starGained;
        CompanionSystem.OwnedCompanion oc = companions.getOwned(activeCompanionId());
        int bondGain = 22 + clampedStars * 6;
        if (oc != null) {
            oc.addBondXp(bondGain);
        }

        // 拍照尋寶同樣可作為「安撫島嶼守護巨獸」的實境互動條件！
        stories.bossLookUpDone = true;
        if (family.isFamilyMode() || family.members.size() >= 2) {
            stories.unlockSeasonStamp(1); // 親子出動
        }

        int cid = activeCompanionId();
        int cStage = oc != null ? oc.stage : 1;
        boolean cShiny = oc != null && oc.shiny;
        String cleanNote = kidNote != null ? kidNote.trim() : "";
        if (cleanNote.length() > 80) cleanNote = cleanNote.substring(0, 80);

        StoryCatalog.MemoryEntry entry = new StoryCatalog.MemoryEntry(
            memoryId,
            clock.isoDate,
            hunt.title,
            family.formatActiveSquadLabel(),
            photoPath,
            audioPath,
            doodlePath,
            cleanNote,
            hunt.id,
            colorLabel,
            domHue,
            clampedStars,
            progress.todayStepsOn(clock.isoDate),
            cid,
            cStage,
            cShiny,
            clock.season,
            safeNatureCat
        );
        StoryCatalog.MemoryEntry evicted = stories.addMemory(entry);

        photoHuntsCompleted++;
        if (audioPath != null && !audioPath.trim().isEmpty()) {
            voiceMemoriesCount++;
        }

        // 彩虹七色圖鑑收集判定
        boolean completedRainbowNow = false;
        if (hunt.isRainbowColorHunt()) {
            int bit = 1 << hunt.targetColor;
            boolean isNewColor = (rainbowCollectedMask & bit) == 0;
            rainbowCollectedMask |= bit;
            if (isNewColor && (rainbowCollectedMask & ColorScavenger.RAINBOW_COMPLETE_MASK) == ColorScavenger.RAINBOW_COMPLETE_MASK) {
                completedRainbowNow = true;
                rainbowCompletedSets++;
                progress.coins += 100L;
                camp.starSand += 35;
                coinsGained += 100;
                starGained += 35;
                if (companions.hasFreeEggSlot()) {
                    companions.addEgg(CompanionSystem.EGG_BLOOM, frontierBiomeIndex(progress));
                } else {
                    enqueueIntoVaultSafe(
                        progress,
                        CampfireVault.KIND_EGG,
                        "彩虹七色奇蹟蛋",
                        0,
                        8,
                        CompanionSystem.EGG_BLOOM,
                        clock.isoDate
                    );
                }
                appendLog(clock.isoDate, "🌈 集滿現實世界彩虹七色！獲得 +100 金幣、+35 星砂與花瓣奇蹟蛋！");
            }
        }

        DailyRecord rec = ensureDailyRecord(clock.isoDate);
        rec.coinsEarned += coinsGained;
        String msg = String.format(
            Locale.TAIWAN,
            "📮 實境明信片「%s」完成！+%d 金幣、+%d 星砂%s%s%s",
            hunt.title,
            coinsGained,
            starGained,
            natureBonusTag,
            completedRainbowNow ? " · 🌈 達成彩虹七色全收集大獎！" : "",
            completedNatureNow ? " · 🔬 集滿 8 大自然百科全書！" : ""
        );
        appendLog(clock.isoDate, msg);
        evaluateExtraAchievements(progress, clock.isoDate);
        return new PhotoHuntOutcome(true, entry, evicted, completedRainbowNow, completedNatureNow, coinsGained, starGained, msg);
    }

    // ──────────────── Phase 3：實體藏寶（NFC/QR）、家族禮物卡、足跡迷霧、環境感測 ────────────────

    TreasureAndQrState.ClaimOutcome claimHiddenTreasureByNfc(StepProgress progress, String rawHexUid, String today) {
        TreasureAndQrState.HiddenTreasure hit = treasure.findActiveByNfcUid(rawHexUid);
        if (hit == null) {
            return new TreasureAndQrState.ClaimOutcome(
                false, "這張卡片目前沒有藏寶喔！可以請大人到「實體藏寶工坊」綁定它。", null, 0, 0, 0, 0, -1
            );
        }
        return grantHiddenTreasure(progress, hit, today, "📡 NFC 嗶卡感應成功！");
    }

    TreasureAndQrState.ClaimOutcome claimHiddenTreasureByCode(StepProgress progress, String rawCodeOrQr, String today) {
        TreasureAndQrState.HiddenTreasure hit = treasure.findActiveByCodeOrQr(rawCodeOrQr);
        if (hit == null) {
            return new TreasureAndQrState.ClaimOutcome(
                false, "找不到對應的待開啟藏寶碼，請確認大小寫或檢查是否已開啟過囉！", null, 0, 0, 0, 0, -1
            );
        }
        return grantHiddenTreasure(progress, hit, today, "🔎 尋寶密碼解鎖成功！");
    }

    private TreasureAndQrState.ClaimOutcome grantHiddenTreasure(
        StepProgress progress,
        TreasureAndQrState.HiddenTreasure target,
        String today,
        String prefix
    ) {
        if (progress == null || target == null) {
            return new TreasureAndQrState.ClaimOutcome(false, "目前無法開啟藏寶箱", null, 0, 0, 0, 0, -1);
        }
        target.claimed = true;
        target.claimedDate = today != null ? today : "";
        treasure.totalTreasuresClaimed++;

        int coins = 0;
        int stars = 0;
        int snacks = 0;
        int blankets = 0;
        int eggType = -1;
        String rewardDetail;

        if (target.rewardType == TreasureAndQrState.REWARD_SNACK_PACK) {
            snacks = 3;
            blankets = 1;
            stars = 12;
            camp.snacks += snacks;
            camp.warmBlankets += blankets;
            camp.starSand += stars;
            rewardDetail = "點心 ×3、暖蛋毛毯 ×1、星砂 +12";
        } else if (target.rewardType == TreasureAndQrState.REWARD_MYSTERY_EGG) {
            coins = 30;
            stars = 10;
            eggType = CompanionSystem.EGG_STAR;
            progress.coins += coins;
            camp.starSand += stars;
            if (companions.hasFreeEggSlot()) {
                companions.addEgg(eggType, frontierBiomeIndex(progress));
                rewardDetail = "金幣 +30、星砂 +10、星光神祕蛋已放入孵蛋巢！";
            } else {
                enqueueIntoVaultSafe(progress, CampfireVault.KIND_EGG, "實體尋寶：" + target.title, 0, 5, eggType, today);
                rewardDetail = "金幣 +30、星砂 +10、星光神祕蛋已收進營火盒！";
            }
        } else {
            coins = 60;
            stars = 20;
            progress.coins += coins;
            camp.starSand += stars;
            rewardDetail = "金幣 +60、星砂 +20";
        }

        stories.bossLookUpDone = true;
        if (today != null) {
            ensureDailyRecord(today).coinsEarned += coins;
        }
        String msg = prefix + " 找到「" + target.title + "」！獲得 " + rewardDetail;
        appendLog(today, "🎁 " + msg);
        evaluateExtraAchievements(progress, today);
        return new TreasureAndQrState.ClaimOutcome(true, msg, target, coins, stars, snacks, blankets, eggType);
    }

    TreasureAndQrState.GiftRedeemOutcome redeemFamilyGiftCode(StepProgress progress, String rawQrText, String today) {
        if (progress == null) {
            return new TreasureAndQrState.GiftRedeemOutcome(false, "無法領取禮物卡", null);
        }
        TreasureAndQrState.FamilyGiftCard card = TreasureAndQrState.parseGiftCard(rawQrText);
        if (card == null) {
            return new TreasureAndQrState.GiftRedeemOutcome(false, "禮物卡格式無效或校驗碼不符，請確認有完整貼上喔！", null);
        }
        if (treasure.hasRedeemedGift(card.giftId)) {
            return new TreasureAndQrState.GiftRedeemOutcome(false, "這張家族禮物卡已經領過囉！", card);
        }

        int coins;
        int stars;
        int snacks = 0;
        int blankets = 0;
        if (card.giftType == TreasureAndQrState.GIFT_EXPLORER_BOX) {
            coins = 65;
            stars = 20;
            blankets = 1;
        } else {
            coins = 35;
            stars = 15;
            snacks = 2;
        }
        progress.coins += coins;
        camp.starSand += stars;
        camp.snacks += snacks;
        camp.warmBlankets += blankets;
        treasure.recordRedeemedGift(card, today);
        if (today != null) {
            ensureDailyRecord(today).coinsEarned += coins;
        }

        String guestNote = "";
        if (card.companionId != CompanionSystem.ID_NONE && CompanionSystem.species(card.companionId) != null) {
            guestNote = "，「" + card.senderName + "」的 " + CompanionSystem.species(card.companionId).name + " 今天來營地串門子囉！";
        }
        String msg = "💌 收到來自「" + card.senderName + "」的家族禮物！+" + coins + " 金幣、+" + stars + " 星砂"
            + (snacks > 0 ? ("、點心 ×" + snacks) : "")
            + (blankets > 0 ? ("、毛毯 ×" + blankets) : "")
            + guestNote;
        appendLog(today, msg);
        evaluateExtraAchievements(progress, today);
        return new TreasureAndQrState.GiftRedeemOutcome(true, msg, card);
    }

    FootprintState.DiscoveryOutcome recordCoarseFootprint(StepProgress progress, double lat, double lon, String today) {
        FootprintState.DiscoveryOutcome out = footprints.recordCoarseLocation(lat, lon, today);
        applyFootprintOutcome(progress, out, today);
        return out;
    }

    FootprintState.DiscoveryOutcome recordManualSecretBase(StepProgress progress, String baseName, String today) {
        FootprintState.DiscoveryOutcome out = footprints.recordManualBaseCheckIn(baseName, today);
        applyFootprintOutcome(progress, out, today);
        return out;
    }

    private void applyFootprintOutcome(StepProgress progress, FootprintState.DiscoveryOutcome out, String today) {
        if (progress == null || out == null || !out.isNewTile) return;
        progress.coins += out.coinsGained;
        camp.starSand += out.starSandGained;
        if (out.droppedEgg) {
            if (companions.hasFreeEggSlot()) {
                companions.addEgg(CompanionSystem.EGG_MIST, frontierBiomeIndex(progress));
            } else {
                enqueueIntoVaultSafe(progress, CampfireVault.KIND_EGG, "足跡首探霧林蛋", 0, 5, CompanionSystem.EGG_MIST, today);
            }
        }
        if (today != null) {
            ensureDailyRecord(today).coinsEarned += out.coinsGained;
        }
        appendLog(today, out.message);
        evaluateExtraAchievements(progress, today);
    }

    EnvironmentSensorState.TriggerResult applyLightReading(StepProgress progress, float lux, String today) {
        boolean walkedToday = progress != null && progress.todayStepsOn(today) > 0L;
        EnvironmentSensorState.TriggerResult res = envSensors.onLightReading(lux, today, walkedToday);
        if (!res.triggered() || progress == null) return res;

        if (res.eventType == EnvironmentSensorState.EVENT_NIGHT_SPRITE) {
            camp.starSand += 14;
            if (companions.hasFreeEggSlot() && companions.eggs.isEmpty()) {
                companions.addEgg(CompanionSystem.EGG_NIGHT, frontierBiomeIndex(progress));
            }
        } else if (res.eventType == EnvironmentSensorState.EVENT_SUN_BLESSING) {
            camp.starSand += 10;
            camp.waterGardensByWalk(200L, activeCompanionId() == CompanionSystem.ID_SHEEP);
        }
        appendLog(today, res.title + " " + res.message);
        evaluateExtraAchievements(progress, today);
        return res;
    }

    EnvironmentSensorState.TriggerResult applyCadenceSteps(StepProgress progress, long deltaSteps, long nowMs, String today) {
        EnvironmentSensorState.TriggerResult res = envSensors.onStepBatch(deltaSteps, nowMs, today);
        if (!res.triggered() || progress == null) return res;

        if (res.eventType == EnvironmentSensorState.EVENT_WIND_CHASE) {
            progress.coins += 30L;
            addBonusXp(120L);
            camp.starSand += 10;
            if (today != null) ensureDailyRecord(today).coinsEarned += 30L;
        } else if (res.eventType == EnvironmentSensorState.EVENT_ROADSIDE_REST) {
            camp.starSand += 8;
            CompanionSystem.OwnedCompanion oc = companions.getOwned(activeCompanionId());
            if (oc != null) oc.addBondXp(20);
        }
        appendLog(today, res.title + " " + res.message);
        evaluateExtraAchievements(progress, today);
        return res;
    }

    /** 啟動或換日時同步 2.0 環境狀態（四季護照、每日抬頭任務、家庭壽星禮包與企鵝連勝點心）。 */
    String syncV2DailyEnvironment(StepProgress progress, WorldClock.Snapshot clock, Random random) {
        if (progress == null || clock == null) return null;
        String today = clock.isoDate;
        stories.syncSeasonKey(clock);
        stories.ensureDailyLookUpOffers(today, random);
        if (progress.todayStepsOn(today) >= 1500L) {
            stories.unlockSeasonStamp(0);
        }
        if (camp.totalBuildingLevels() >= 4) {
            stories.unlockSeasonStamp(5);
        }
        StringBuilder notice = new StringBuilder();

        String birthdayName = family.birthdayCelebrantName(clock);
        if (!birthdayName.isEmpty() && !today.equals(lastBirthdayGiftDate)) {
            lastBirthdayGiftDate = today;
            progress.coins += 50L;
            camp.starSand += 15;
            camp.snacks += 2;
            String bMsg = "🎂 祝「" + birthdayName + "」生日快樂！營地掛滿彩旗，送出 +50 金幣、+15 星砂與點心 ×2！";
            appendLog(today, bMsg);
            notice.append(bMsg);
        }

        if (activeCompanionId() == CompanionSystem.ID_PENGUIN
            && effectiveStreak(today) >= 2
            && !today.equals(lastPenguinSnackDate)) {
            lastPenguinSnackDate = today;
            camp.snacks += 1;
            String pMsg = "🐧 企鵝嘟嘟為你的連勝歡呼！送出今日點心 ×1";
            appendLog(today, pMsg);
            if (notice.length() > 0) notice.append("\n");
            notice.append(pMsg);
        }

        evaluateExtraAchievements(progress, today);
        return notice.length() > 0 ? notice.toString() : null;
    }

    /**
     * 2.0 感測器步數同步：
     * 維持真實步數純粹不灌水，同時串聯營火盒鎖存、孵蛋推進、奇幻菜園自動澆水、島嶼巨獸進度、連貫故事奇遇與季節印章。
     */
    String applyWalkUpdateV2(
        StepProgress progress,
        StepProgress.Update walkUpdate,
        WorldClock.Snapshot clock,
        Random random
    ) {
        if (progress == null || walkUpdate == null || clock == null) return null;
        String today = clock.isoDate;
        Random rng = random != null ? random : new Random();
        stories.syncSeasonKey(clock);
        stories.ensureDailyLookUpOffers(today, rng);

        long todaySteps = progress.todayStepsOn(today);
        DailyRecord todayRecord = ensureDailyRecord(today);
        todayRecord.steps = todaySteps;

        StringBuilder notice = new StringBuilder();
        if (walkUpdate.steps > 0) {
            family.recordPocketSteps(walkUpdate.steps);

            int xpBonusPct = xpMultiplierPercent();
            long gainedExtraXp = (walkUpdate.steps * xpBonusPct) / 100L;
            if (gainedExtraXp > 0) {
                addBonusXp(gainedExtraXp);
            }

            // 推進孵蛋、菜園澆水、島嶼守護巨獸進度
            int eggsReady = companions.advanceEggsByWalk(walkUpdate.steps);
            int plotsReady = camp.waterGardensByWalk(walkUpdate.steps, activeCompanionId() == CompanionSystem.ID_SHEEP);
            stories.advanceBossSteps(walkUpdate.steps, frontierBiomeIndex(progress));

            if (clock.timeOfDay == WorldClock.TIME_DAWN || clock.isNightOrDusk()) {
                stories.unlockSeasonStamp(7); // 星夜／清晨漫步
            }
            if (todaySteps >= 1500L) {
                stories.unlockSeasonStamp(0); // 本季起走
            }

            if (eggsReady > 0) {
                appendNotice(notice, "🥚 有 " + eggsReady + " 顆神祕蛋搖晃起來，可以去營地破殼囉！");
            }
            if (plotsReady > 0) {
                appendNotice(notice, "🌻 奇幻菜園有 " + plotsReady + " 格作物成熟可以採收了！");
            }
        }

        // 千步寶箱處理：平日開啟自動鎖存時，把寶箱收入營火盒等晚上與孩子一起開！
        if (walkUpdate.chests > 0) {
            long baseCoins = walkUpdate.chestCoins;
            long charmExtra = (baseCoins * coinMultiplierPercent()) / 100L;
            long shibaExtra = (activeCompanionId() == CompanionSystem.ID_SHIBA)
                ? walkUpdate.chests * (long) companions.shibaChestBonusPerChest()
                : 0L;
            long totalChestCoins = baseCoins + charmExtra + shibaExtra;
            int chestStars = walkUpdate.chests * 4;

            boolean shouldVault = family.autoVaultBigRewards && !family.isFamilyMode();
            if (shouldVault) {
                // StepProgress.onSensorReading 已把 baseCoins 先加進 progress.coins，移入營火盒前先扣回避免重複發放
                progress.coins = Math.max(0L, progress.coins - baseCoins);
                enqueueIntoVaultSafe(
                    progress,
                    CampfireVault.KIND_CHEST,
                    walkUpdate.chests == 1 ? "千步冒險寶箱" : ("千步冒險寶箱 ×" + walkUpdate.chests),
                    (int) Math.min(Integer.MAX_VALUE, totalChestCoins),
                    chestStars,
                    -1,
                    today
                );
                appendLog(today, "🔥 已將 " + walkUpdate.chests + " 個步數寶箱存入營火盒，留給晚上一起開箱！");
                appendNotice(notice, "🔥 " + walkUpdate.chests + " 個步數寶箱已存進營火盒，回家跟家人一起開箱享加成！");
            } else {
                long extraCoins = charmExtra + shibaExtra;
                progress.coins += extraCoins;
                camp.starSand += chestStars;
                todayRecord.coinsEarned += totalChestCoins;
                appendLog(today, String.format(
                    Locale.ROOT,
                    "🎁 開啟 %d 個步數寶箱，獲得 %d 金幣、+%d 星砂",
                    walkUpdate.chests,
                    totalChestCoins,
                    chestStars
                ));
                appendNotice(notice, walkUpdate.chests == 1
                    ? "🎁 寶箱！獲得 " + totalChestCoins + " 金幣、+" + chestStars + " 星砂"
                    : "🎁 寶箱 ×" + walkUpdate.chests + "！獲得 " + totalChestCoins + " 金幣、+" + chestStars + " 星砂");
            }

            // 寶箱里程碑掉落神祕蛋（約 40% 機率，或蛋巢全空時保底 70%）
            int eggChance = companions.eggs.isEmpty() ? 70 : 40;
            if (rng.nextInt(100) < eggChance) {
                int eggType = CompanionSystem.pickEggTypeForContext(frontierBiomeIndex(progress), clock, rng);
                String eggName = CompanionSystem.eggTypeDef(eggType).name;
                if (shouldVault || !companions.hasFreeEggSlot()) {
                    enqueueIntoVaultSafe(progress, CampfireVault.KIND_EGG, "撿到的" + eggName, 0, 3, eggType, today);
                    appendNotice(notice, "🥚 路上撿到「" + eggName + "」，已放進營火盒！");
                } else {
                    companions.addEgg(eggType, frontierBiomeIndex(progress));
                    appendNotice(notice, "🥚 路上撿到「" + eggName + "」，已放進孵蛋巢！");
                }
            }
        }

        updateStreak(today, todaySteps);

        // 每 500 步奇遇：優先觸發多話連貫繪本故事（60%），其餘為經典奇遇
        if (walkUpdate.event) {
            if (pendingEncounter != null) {
                ActionResult auto = resolvePendingEncounter(progress, 0, today, rng);
                if (auto.ok && !auto.message.isEmpty()) {
                    appendLog(today, "⏭️ 上個奇遇已自動結算：" + auto.message);
                }
            }
            int biome = frontierBiomeIndex(progress);
            if (rng.nextInt(100) < 60) {
                StoryCatalog.ArcDef arc = StoryCatalog.arc(stories.activeArcId);
                StoryCatalog.ChapterDef ch = stories.currentStoryChapter();
                pendingEncounter = AdventureCatalog.createStoryEncounter(biome, arc, ch);
            } else {
                pendingEncounter = AdventureCatalog.generateEncounter(biome, compassLevel, rng);
            }
            appendNotice(notice, "❗ " + pendingEncounter.title);
        }

        int unlockedOld = evaluateAchievements(progress, today);
        int unlockedNew = evaluateExtraAchievements(progress, today);
        int totalUnlocked = unlockedOld + unlockedNew;
        if (totalUnlocked > 0) {
            appendNotice(notice, "🏆 解鎖 " + totalUnlocked + " 項新成就！");
        }
        return notice.length() > 0 ? notice.toString() : null;
    }

    private void enqueueIntoVaultSafe(
        StepProgress progress,
        String kind,
        String title,
        int coins,
        int starSand,
        int eggType,
        String today
    ) {
        CampfireVault.VaultItem overflow = vault.enqueue(kind, title, coins, starSand, eggType, today);
        if (overflow != null) {
            progress.coins += overflow.coins;
            camp.starSand += overflow.starSand;
            if (overflow.eggType >= 0 && companions.hasFreeEggSlot()) {
                companions.addEgg(overflow.eggType, 0);
            } else if (overflow.eggType >= 0) {
                camp.starSand += 10; // 蛋槽滿且溢出時折換星砂，絕不蒸發
            }
            appendLog(today, "🔥 營火盒已滿，自動結算最舊包裹「" + overflow.title + "」");
        }
    }

    CampfireVault.OpenOutcome openVaultItem(StepProgress progress, String itemId, String today) {
        if (progress == null) {
            return new CampfireVault.OpenOutcome(false, 0, 0, false, "無法開箱");
        }
        CampfireVault.VaultItem target = null;
        for (CampfireVault.VaultItem it : vault.items) {
            if (it.id.equals(itemId)) {
                target = it;
                break;
            }
        }
        if (target == null) {
            return new CampfireVault.OpenOutcome(false, 0, 0, false, "這個營火包裹已經打開過了");
        }
        if (target.eggType >= 0 && !companions.hasFreeEggSlot()) {
            return new CampfireVault.OpenOutcome(false, 0, 0, false, "孵蛋巢目前兩格都滿了，先去孵化一顆蛋再打開這顆蛋喔！");
        }

        vault.removeById(itemId);
        int fireLv = camp.buildingLevel(CampState.BLD_CAMPFIRE);
        boolean famMode = family.isFamilyMode();
        boolean hasTortoise = (activeCompanionId() == CompanionSystem.ID_TORTOISE);

        int finalCoins = CampfireVault.computeBonusCoins(target.coins, fireLv, famMode, hasTortoise);
        int finalStars = CampfireVault.computeBonusStarSand(target.starSand, fireLv, famMode);
        progress.coins += finalCoins;
        camp.starSand += finalStars;

        boolean eggPlaced = false;
        if (target.eggType >= 0) {
            eggPlaced = companions.addEgg(target.eggType, frontierBiomeIndex(progress));
        }

        campfireUnboxCount++;
        stories.unlockSeasonStamp(2); // 暖心營火
        DailyRecord rec = ensureDailyRecord(today);
        rec.coinsEarned += finalCoins;

        String bonusTag = famMode ? "（親子營火加成！）" : "";
        String msg = eggPlaced
            ? "🔥 打開「" + target.title + "」" + bonusTag + "！神祕蛋已放入孵蛋巢、星砂 +" + finalStars
            : "🔥 打開「" + target.title + "」" + bonusTag + "！獲得 +" + finalCoins + " 金幣、+" + finalStars + " 星砂";
        appendLog(today, msg);
        evaluateExtraAchievements(progress, today);
        return new CampfireVault.OpenOutcome(true, finalCoins, finalStars, eggPlaced, msg);
    }

    ActionResult completeLookUpQuest(StepProgress progress, int questId, String kidNote, String today) {
        if (progress == null || today == null) {
            return new ActionResult(false, "目前無法完成任務");
        }
        if (stories.isLookUpCompletedToday(questId, today)) {
            return new ActionResult(false, "這張抬頭任務今天已經蓋章過囉！");
        }
        StoryCatalog.LookUpQuestDef def = StoryCatalog.lookUpQuestById(questId);
        stories.lookUpDate = today;
        stories.completedLookUpIds.add(questId);
        stories.totalLookUpCompleted++;
        stories.bossLookUpDone = true;

        int gainedStar = def.starReward;
        int gainedCoins = def.coinReward;
        if (activeCompanionId() == CompanionSystem.ID_OTTER) {
            gainedStar += 15;
            CompanionSystem.OwnedCompanion oc = companions.getOwned(CompanionSystem.ID_OTTER);
            if (oc != null) oc.addBondXp(25);
        }
        camp.starSand += gainedStar;
        progress.coins += gainedCoins;
        CompanionSystem.OwnedCompanion activeOc = companions.getOwned(activeCompanionId());
        if (activeOc != null && activeCompanionId() != CompanionSystem.ID_OTTER) {
            activeOc.addBondXp(18);
        }

        String squadLabel = family.formatActiveSquadLabel();
        stories.recordNote(today, def.title, squadLabel, kidNote);
        if (family.isFamilyMode() || family.members.size() >= 2) {
            stories.unlockSeasonStamp(1); // 親子出動
        }
        DailyRecord rec = ensureDailyRecord(today);
        rec.coinsEarned += gainedCoins;

        String msg = "👀 完成抬頭任務「" + def.title + "」！+" + gainedCoins + " 金幣、+" + gainedStar + " 星砂";
        appendLog(today, msg);
        evaluateExtraAchievements(progress, today);
        return new ActionResult(true, msg);
    }

    ActionResult calmIslandBoss(StepProgress progress, int biomeIndex, String today) {
        if (progress == null) return new ActionResult(false, "無法挑戰巨獸");
        StoryCatalog.BossDef boss = StoryCatalog.bossForBiome(biomeIndex);
        if (stories.isBossCleared(biomeIndex)) {
            return new ActionResult(false, "已經安撫過「" + boss.name + "」囉！");
        }
        if (!stories.canCalmCurrentBoss(biomeIndex)) {
            int remain = Math.max(0, StoryCatalog.BOSS_REQUIRED_STEPS - stories.bossStepProgress);
            return new ActionResult(false, "再走 " + remain + " 步或完成 1 張親子抬頭任務即可安撫牠！");
        }

        stories.bossClearedMask |= (1 << biomeIndex);
        stories.bossStepProgress = 0;
        stories.bossLookUpDone = false;

        progress.coins += boss.rewardCoins;
        camp.starSand += boss.rewardStarSand;
        DailyRecord rec = ensureDailyRecord(today);
        rec.coinsEarned += boss.rewardCoins;

        String eggInfo;
        if (companions.hasFreeEggSlot()) {
            companions.addEgg(boss.rewardEggType, biomeIndex);
            eggInfo = "並獲贈「" + CompanionSystem.eggTypeDef(boss.rewardEggType).name + "」放入孵蛋巢！";
        } else {
            enqueueIntoVaultSafe(
                progress,
                CampfireVault.KIND_BOSS_GIFT,
                "巨獸謝禮：" + CompanionSystem.eggTypeDef(boss.rewardEggType).name,
                0,
                6,
                boss.rewardEggType,
                today
            );
            eggInfo = "獲贈的神祕蛋已收入營火盒！";
        }
        String msg = "🐲 成功安撫「" + boss.name + "」！+" + boss.rewardCoins + " 金幣、+" + boss.rewardStarSand + " 星砂，" + eggInfo;
        appendLog(today, msg);
        evaluateExtraAchievements(progress, today);
        return new ActionResult(true, msg);
    }

    CompanionSystem.HatchResult hatchEgg(StepProgress progress, int eggListIndex, String today, Random random) {
        CompanionSystem.HatchResult res = companions.hatchEgg(
            eggListIndex,
            camp.shinyBonusPercent(),
            camp,
            today,
            random
        );
        if (res.ok) {
            eggsHatchedCount++;
            syncV2EquippedMirror();
            stories.unlockSeasonStamp(3); // 破殼新生
            appendLog(today, res.message);
            evaluateAchievements(progress, today);
            evaluateExtraAchievements(progress, today);
        }
        return res;
    }

    CampState.HarvestResult harvestGardenPlot(StepProgress progress, int slot, String today, Random random) {
        boolean hasDeer = activeCompanionId() == CompanionSystem.ID_DEER;
        CampState.HarvestResult res = camp.harvestPlot(slot, hasDeer, random);
        if (res.ok) {
            gardenHarvestCount++;
            stories.unlockSeasonStamp(4); // 奇幻園丁
            if (res.droppedEggType >= 0) {
                if (companions.hasFreeEggSlot()) {
                    companions.addEgg(res.droppedEggType, 0);
                } else {
                    enqueueIntoVaultSafe(progress, CampfireVault.KIND_EGG, "菜園花瓣神祕蛋", 0, 3, res.droppedEggType, today);
                }
            }
            appendLog(today, res.message);
            evaluateExtraAchievements(progress, today);
        }
        return res;
    }

    ActionResult upgradeCampBuilding(StepProgress progress, int buildingId, String today) {
        ActionResult res = camp.upgradeBuilding(progress, buildingId);
        if (res.ok) {
            if (camp.totalBuildingLevels() >= 4) {
                stories.unlockSeasonStamp(5); // 營地匠人
            }
            appendLog(today, res.message);
            evaluateExtraAchievements(progress, today);
        }
        return res;
    }

    ActionResult petCompanionInCamp(StepProgress progress, int companionId, String today) {
        ActionResult res = companions.petInCamp(companionId, camp.dailyPetBondXp(), today);
        if (res.ok) {
            evaluateExtraAchievements(progress, today);
        }
        return res;
    }

    ActionResult feedCompanion(StepProgress progress, int companionId, String today) {
        ActionResult res = companions.feedSnack(companionId, camp);
        if (res.ok) {
            appendLog(today, res.message);
            evaluateExtraAchievements(progress, today);
        }
        return res;
    }

    private static void appendNotice(StringBuilder sb, String line) {
        if (line == null || line.isEmpty()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(line);
    }

    int gearLevel(int gearIndex) {
        switch (gearIndex) {
            case AdventureCatalog.GEAR_BOOTS: return bootsLevel;
            case AdventureCatalog.GEAR_CHARM: return charmLevel;
            case AdventureCatalog.GEAR_COMPASS: return compassLevel;
            default: return 0;
        }
    }

    void setGearLevel(int gearIndex, int level) {
        int clamped = Math.max(0, Math.min(AdventureCatalog.GEAR_MAX_LEVEL, level));
        switch (gearIndex) {
            case AdventureCatalog.GEAR_BOOTS: bootsLevel = clamped; break;
            case AdventureCatalog.GEAR_CHARM: charmLevel = clamped; break;
            case AdventureCatalog.GEAR_COMPASS: compassLevel = clamped; break;
            default: break;
        }
    }

    private void addBonusXp(long delta) {
        if (delta <= 0) return;
        if (Long.MAX_VALUE - bonusXp < delta) {
            bonusXp = Long.MAX_VALUE;
        } else {
            bonusXp += delta;
        }
    }

    private void updateStreak(String today, long todaySteps) {
        if (today == null || todaySteps < AdventureCatalog.DAILY_QUESTS[0].targetSteps) return;
        if (today.equals(lastStreakDay)) return;

        LocalDate todayDate = parseDateSafe(today);
        LocalDate lastDate = parseDateSafe(lastStreakDay);
        if (todayDate != null && lastDate != null && todayDate.minusDays(1).equals(lastDate)) {
            streakDays = Math.max(1, streakDays + 1);
        } else {
            streakDays = 1;
        }
        lastStreakDay = today;
        if (streakDays > bestStreakDays) {
            bestStreakDays = streakDays;
        }
    }

    DailyRecord ensureDailyRecord(String today) {
        for (DailyRecord r : dailyHistory) {
            if (today.equals(r.date)) return r;
        }
        DailyRecord created = new DailyRecord(today, 0L, 0L, 0);
        dailyHistory.add(created);
        Collections.sort(dailyHistory, (a, b) -> a.date.compareTo(b.date));
        while (dailyHistory.size() > MAX_HISTORY_DAYS) {
            dailyHistory.remove(0);
        }
        return created;
    }

    /** 產生過去 7 天（含今日，由舊到新）完整每日步數與戰報序列；沒開 App 的日子補 0。 */
    List<DailyRecord> lastSevenDays(StepProgress progress, String today) {
        Map<String, DailyRecord> byDate = new LinkedHashMap<>();
        for (DailyRecord r : dailyHistory) {
            byDate.put(r.date, r);
        }
        LocalDate end = parseDateSafe(today);
        if (end == null) {
            return Collections.emptyList();
        }
        List<DailyRecord> out = new ArrayList<>(7);
        for (int offset = 6; offset >= 0; offset--) {
            String date = end.minusDays(offset).toString();
            DailyRecord existing = byDate.get(date);
            if (offset == 0 && progress != null) {
                long liveTodaySteps = progress.todayStepsOn(today);
                long coins = existing != null ? existing.coinsEarned : 0L;
                int encs = existing != null ? existing.encounters : 0;
                long steps = existing != null ? Math.max(existing.steps, liveTodaySteps) : liveTodaySteps;
                out.add(new DailyRecord(date, steps, coins, encs));
            } else if (existing != null) {
                out.add(existing);
            } else {
                out.add(new DailyRecord(date, 0L, 0L, 0));
            }
        }
        return out;
    }

    void appendLog(String date, String summary) {
        if (summary == null || summary.trim().isEmpty()) return;
        adventureLog.add(0, new LogEntry(date == null ? "" : date, summary.trim()));
        while (adventureLog.size() > MAX_LOG_ENTRIES) {
            adventureLog.remove(adventureLog.size() - 1);
        }
    }

    String formatDailyReport(StepProgress progress, String today) {
        if (progress == null) return "";
        String safeToday = today == null ? "" : today;
        DailyRecord todayRec = null;
        for (DailyRecord r : dailyHistory) {
            if (safeToday.equals(r.date)) {
                todayRec = r;
                break;
            }
        }
        AdventureCatalog.BiomeDef biome = AdventureCatalog.biome(frontierBiomeIndex(progress));
        String buddyName = activeCompanionName();
        long todaySteps = progress.todayStepsOn(safeToday);
        long todayCoins = todayRec != null ? todayRec.coinsEarned : 0L;
        int todayEnc = todayRec != null ? todayRec.encounters : 0;
        return String.format(
            Locale.TAIWAN,
            "【走走冒險 戰報｜%s】\n"
                + "出隊小隊：%s（%s）\n"
                + "Lv.%d %s（同伴：%s）\n"
                + "目前區域：%s（累計探索 %d 格）\n"
                + "今日步數：%d 步｜連續達標：%d 天\n"
                + "今日收益：+%d 金幣、解決 %d 次奇遇｜持有金幣：%d｜星砂：%d",
            safeToday,
            family.formatActiveSquadLabel(),
            family.isFamilyMode() ? "親子出任務" : "平日模式",
            adventureLevel(progress),
            currentTitle(progress),
            buddyName,
            biome.name,
            exploredCells(progress),
            todaySteps,
            effectiveStreak(safeToday),
            todayCoins,
            todayEnc,
            progress.coins,
            camp.starSand
        );
    }

    String serializeHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dailyHistory.size(); i++) {
            DailyRecord r = dailyHistory.get(i);
            if (i > 0) sb.append('\n');
            sb.append(escapeField(r.date))
                .append('|').append(r.steps)
                .append('|').append(r.coinsEarned)
                .append('|').append(r.encounters);
        }
        return sb.toString();
    }

    void parseHistory(String raw) {
        dailyHistory.clear();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) continue;
            List<String> parts = splitEscaped(line, '|');
            if (parts.size() < 4) continue;
            String date = parts.get(0);
            if (parseDateSafe(date) == null) continue;
            long steps = parseLongSafe(parts.get(1), 0L);
            long coins = parseLongSafe(parts.get(2), 0L);
            int enc = (int) parseLongSafe(parts.get(3), 0L);
            dailyHistory.add(new DailyRecord(date, steps, coins, enc));
        }
        Collections.sort(dailyHistory, (a, b) -> a.date.compareTo(b.date));
        while (dailyHistory.size() > MAX_HISTORY_DAYS) {
            dailyHistory.remove(0);
        }
    }

    String serializeLog() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < adventureLog.size(); i++) {
            LogEntry entry = adventureLog.get(i);
            if (i > 0) sb.append('\n');
            sb.append(escapeField(entry.date)).append('|').append(escapeField(entry.summary));
        }
        return sb.toString();
    }

    void parseLog(String raw) {
        adventureLog.clear();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String line : raw.split("\n")) {
            if (line.trim().isEmpty()) continue;
            List<String> parts = splitEscaped(line, '|');
            if (parts.size() < 2) continue;
            adventureLog.add(new LogEntry(parts.get(0), parts.get(1)));
            if (adventureLog.size() >= MAX_LOG_ENTRIES) break;
        }
    }

    String serializePendingEncounter() {
        if (pendingEncounter == null) return "";
        return pendingEncounter.type
            + "|" + pendingEncounter.biomeIndex
            + "|" + escapeField(pendingEncounter.title)
            + "|" + escapeField(pendingEncounter.prompt)
            + "|" + escapeField(pendingEncounter.primaryLabel)
            + "|" + escapeField(pendingEncounter.secondaryLabel);
    }

    void parsePendingEncounter(String raw) {
        pendingEncounter = null;
        if (raw == null || raw.trim().isEmpty()) return;
        List<String> parts = splitEscaped(raw, '|');
        if (parts.size() < 6) return;
        int type = (int) parseLongSafe(parts.get(0), -1);
        int biome = (int) parseLongSafe(parts.get(1), 0);
        if (type < 0) return;
        pendingEncounter = new AdventureCatalog.PendingEncounter(
            type,
            biome,
            parts.get(2),
            parts.get(3),
            parts.get(4),
            parts.get(5)
        );
    }

    private static LocalDate parseDateSafe(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static long parseLongSafe(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String escapeField(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '|') {
                sb.append("\\p");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                // 略過 CR
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<String> splitEscaped(String line, char delim) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                if (next == '\\') cur.append('\\');
                else if (next == 'p') cur.append('|');
                else if (next == 'n') cur.append('\n');
                else cur.append(next);
            } else if (c == delim) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
