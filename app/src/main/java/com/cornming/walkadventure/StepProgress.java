package com.cornming.walkadventure;

import java.util.Random;

/**
 * 步數與冒險進度的規則。不碰任何 Android API，方便用一般的 JUnit 測試。
 *
 * 進度由 TYPE_STEP_COUNTER 的讀數推進：感測器回報的是「開機以來的累計步數」，
 * 所以每次讀數都跟上一次的讀數相減，把差額加進累積步數與今日步數。
 */
final class StepProgress {
    static final int STEPS_PER_CHEST = 1000;
    static final int STEPS_PER_EVENT = 500;
    static final int CHEST_COINS_MIN = 20;
    static final int CHEST_COINS_MAX = 50;
    /** 地圖一排有 8 格，走完一輪（第 9 個 1000 步）後重新點亮。 */
    static final int MAP_CELLS = 8;
    /** 還沒讀過感測器、或不知道開機次數時的值。 */
    static final long UNKNOWN = -1;

    /** 累積冒險步數：經驗值、等級、地圖與寶箱都由它推算。 */
    long lifetime;
    /** 今日步數所屬的日期（yyyy-MM-dd），用來判斷換日。 */
    String day;
    long todaySteps;
    long coins;
    /** 已經領過寶箱的最後一個里程碑（步數，1000 的倍數）。 */
    long claimedChest;
    /** 已經觸發過隨機事件的最後一個區間（累積步數 ÷ 500）。 */
    long eventBucket;
    /** 上一次讀到的感測器步數。 */
    long lastSensorSteps = UNKNOWN;
    /** 上一次讀數時的開機次數，用來分辨手機有沒有重開機。 */
    long lastBootCount = UNKNOWN;

    /** 一次感測器讀數帶來的變化，交給畫面顯示訊息。 */
    static final class Update {
        /** 這次加進累積步數的步數。 */
        long steps;
        /** 這次開了幾個寶箱。 */
        int chests;
        /** 這次寶箱一共給了多少金幣。 */
        int chestCoins;
        /** 這次有沒有觸發隨機事件。 */
        boolean event;

        boolean changed() {
            return steps > 0;
        }
    }

    /**
     * 處理一次感測器讀數。
     *
     * @param sensorSteps 感測器回報的開機以來累計步數
     * @param today       現在的日期（yyyy-MM-dd）
     * @param bootCount   目前的開機次數；系統不提供時傳 {@link #UNKNOWN}
     * @param random      寶箱金幣的亂數來源
     */
    Update onSensorReading(long sensorSteps, String today, long bootCount, Random random) {
        Update update = new Update();
        if (sensorSteps < 0 || today == null || random == null) return update;

        // 換日：今日步數歸零，從換日後的第一次讀數開始算。
        // 這一次的差額跨了兩天、分不出各走了多少，所以只算進累積步數。
        boolean newDay = !today.equals(day);
        if (newDay) {
            day = today;
            todaySteps = 0;
        }

        long delta;
        if (lastSensorSteps < 0) {
            // 第一次讀到感測器：沒有上一次的讀數可以比，安裝前走的步數不算。
            delta = 0;
        } else if (rebooted(bootCount) || sensorSteps < lastSensorSteps) {
            // 重開機後感測器從 0 重新累計，目前的讀數就是重開機之後走的步數。
            delta = sensorSteps;
        } else {
            delta = sensorSteps - lastSensorSteps;
        }
        lastSensorSteps = sensorSteps;
        if (bootCount >= 0) lastBootCount = bootCount;

        if (delta <= 0) return update;
        update.steps = delta;
        lifetime += delta;
        if (!newDay) todaySteps += delta;

        // 每 1000 步一個寶箱。App 沒開的時候一口氣走過好幾個里程碑，回來時全部補發。
        long milestone = (lifetime / STEPS_PER_CHEST) * STEPS_PER_CHEST;
        while (claimedChest < milestone) {
            claimedChest += STEPS_PER_CHEST;
            update.chests++;
            update.chestCoins += CHEST_COINS_MIN + random.nextInt(CHEST_COINS_MAX - CHEST_COINS_MIN + 1);
        }
        coins += update.chestCoins;

        // 每 500 步一次隨機事件；一次跨過好幾個區間也只觸發一次（事件只是一句訊息）。
        long bucket = lifetime / STEPS_PER_EVENT;
        if (bucket > eventBucket) {
            eventBucket = bucket;
            update.event = true;
        }
        return update;
    }

    /**
     * 將同一次讀數觸發的寶箱與隨機事件合併為單一提示，避免事件訊息立刻蓋掉寶箱獎勵。
     */
    static String buildAnnouncement(Update update, String eventMessage) {
        if (update == null) return null;
        String chestText = null;
        if (update.chests > 0) {
            chestText = update.chests == 1
                ? "🎁 寶箱！獲得 " + update.chestCoins + " 金幣"
                : "🎁 寶箱 ×" + update.chests + "！獲得 " + update.chestCoins + " 金幣";
        }
        String eventText = (update.event && eventMessage != null && !eventMessage.isEmpty())
            ? eventMessage
            : null;
        if (chestText != null && eventText != null) {
            return chestText + "\n" + eventText;
        }
        return chestText != null ? chestText : eventText;
    }

    private boolean rebooted(long bootCount) {
        return bootCount >= 0 && lastBootCount >= 0 && bootCount != lastBootCount;
    }

    /**
     * 舊版（v0.1.0）沒有存今日步數，而是每次用「感測器讀數 − 當天的基準值」算出來。
     * 升級後第一次啟動時，用舊資料換算出今天已經走的步數，換日或資料不完整就從 0 開始。
     */
    static long todayStepsFromLegacy(String savedDay, String today, float baseline, long lastSensorSteps) {
        if (savedDay == null || today == null || !savedDay.equals(today)) return 0;
        if (baseline < 0 || lastSensorSteps < baseline) return 0;
        return (long) (lastSensorSteps - baseline);
    }

    /** 畫面上要顯示的今日步數：存檔是前幾天留下的（換日後還沒有新讀數）就顯示 0。 */
    long todayStepsOn(String today) {
        return today != null && today.equals(day) ? todaySteps : 0;
    }

    int level() {
        return (int) Math.min(Integer.MAX_VALUE, (lifetime / STEPS_PER_CHEST) + 1);
    }

    /** 目前等級內的經驗值（0 到 999）。 */
    long xp() {
        return lifetime % STEPS_PER_CHEST;
    }

    /** 已探索的地圖格數（每 1000 步一格）。 */
    long exploredCells() {
        return lifetime / STEPS_PER_CHEST;
    }

    /** 地圖上要點亮幾格（0 到 8）。 */
    int litCells() {
        return (int) (exploredCells() % (MAP_CELLS + 1));
    }

    /** 下一個寶箱在累積第幾步。 */
    long nextChestAt() {
        return (exploredCells() + 1) * STEPS_PER_CHEST;
    }
}
