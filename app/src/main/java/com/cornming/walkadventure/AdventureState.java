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

    int xpMultiplierPercent() {
        int pct = bootsLevel * 10;
        if (equippedCompanion == AdventureCatalog.COMPANION_DRAGON) pct += 20;
        return pct;
    }

    int coinMultiplierPercent() {
        return charmLevel * 15;
    }

    boolean isCompanionUnlocked(int companionId) {
        if (companionId == AdventureCatalog.COMPANION_NONE) return true;
        if (companionId < 0 || companionId >= AdventureCatalog.COMPANION_COUNT) return false;
        return (unlockedCompanionsMask & (1 << companionId)) != 0;
    }

    boolean isAchievementUnlocked(int index) {
        if (index < 0 || index >= AdventureCatalog.ACHIEVEMENT_COUNT) return false;
        return (unlockedAchievementsMask & (1 << index)) != 0;
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
            if (equippedCompanion == AdventureCatalog.COMPANION_SLIME) {
                gainedXp += 20;
                gainedCoins += 6;
            } else if (equippedCompanion == AdventureCatalog.COMPANION_DRAGON) {
                gainedCoins += 15;
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
        String msg = "🐾 成功招募夥伴「" + def.name + "」！";
        appendLog(today, msg);
        evaluateAchievements(progress, today);
        return new ActionResult(true, msg);
    }

    int dailyQuestCoinReward(int questIndex) {
        if (questIndex < 0 || questIndex >= AdventureCatalog.DAILY_QUEST_COUNT) return 0;
        int rewardCoins = AdventureCatalog.DAILY_QUESTS[questIndex].coinReward;
        if (equippedCompanion == AdventureCatalog.COMPANION_OWL) {
            rewardCoins = rewardCoins + (rewardCoins * 25) / 100;
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
        boolean hasCompanion = (unlockedCompanionsMask & ~1) != 0;
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
        AdventureCatalog.CompanionDef buddy = AdventureCatalog.companion(equippedCompanion);
        long todaySteps = progress.todayStepsOn(safeToday);
        long todayCoins = todayRec != null ? todayRec.coinsEarned : 0L;
        int todayEnc = todayRec != null ? todayRec.encounters : 0;
        return String.format(
            Locale.TAIWAN,
            "【走走冒險 戰報｜%s】\n"
                + "Lv.%d %s（同伴：%s）\n"
                + "目前區域：%s（累計探索 %d 格）\n"
                + "今日步數：%d 步｜連續達標：%d 天\n"
                + "今日收益：+%d 金幣、解決 %d 次奇遇｜持有金幣：%d",
            safeToday,
            adventureLevel(progress),
            currentTitle(progress),
            buddy.name,
            biome.name,
            exploredCells(progress),
            todaySteps,
            effectiveStreak(safeToday),
            todayCoins,
            todayEnc,
            progress.coins
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
