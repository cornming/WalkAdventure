package com.cornming.walkadventure;

import java.util.ArrayList;
import java.util.List;

/**
 * 營火開箱暫存盒：
 * 大人平日累積的千步寶箱、神祕蛋與特殊大獎可暫存於此，留到晚上或假日點燃營火和孩子一起開箱並享受親子加成！
 */
final class CampfireVault {
    static final int MAX_ITEMS = 30;

    static final String KIND_CHEST = "CHEST";
    static final String KIND_EGG = "EGG";
    static final String KIND_STORY_GIFT = "STORY_GIFT";
    static final String KIND_BOSS_GIFT = "BOSS_GIFT";

    static final class VaultItem {
        final String id;
        final String kind;
        final String title;
        final int coins;
        final int starSand;
        final int eggType; // -1 表示非蛋類
        final String createdDate;

        VaultItem(
            String id,
            String kind,
            String title,
            int coins,
            int starSand,
            int eggType,
            String createdDate
        ) {
            this.id = id != null ? id : "";
            this.kind = kind != null ? kind : KIND_CHEST;
            this.title = title != null ? title : "冒險包裹";
            this.coins = Math.max(0, coins);
            this.starSand = Math.max(0, starSand);
            this.eggType = eggType;
            this.createdDate = createdDate != null ? createdDate : "";
        }
    }

    static final class OpenOutcome {
        final boolean ok;
        final int coinsGained;
        final int starSandGained;
        final boolean eggPlaced;
        final String message;

        OpenOutcome(boolean ok, int coinsGained, int starSandGained, boolean eggPlaced, String message) {
            this.ok = ok;
            this.coinsGained = coinsGained;
            this.starSandGained = starSandGained;
            this.eggPlaced = eggPlaced;
            this.message = message;
        }
    }

    final List<VaultItem> items = new ArrayList<>();
    private int nextSeq = 1;

    boolean isEmpty() {
        return items.isEmpty();
    }

    int count() {
        return items.size();
    }

    int eggCount() {
        int c = 0;
        for (VaultItem item : items) {
            if (item.eggType >= 0) c++;
        }
        return c;
    }

    int chestCount() {
        return count() - eggCount();
    }

    /**
     * 存入一筆待開箱包裹；若超出上限 MAX_ITEMS，會回傳被擠出的最舊包裹由呼叫端自動結算，確保獎勵絕不蒸發。
     */
    VaultItem enqueue(String kind, String title, int coins, int starSand, int eggType, String date) {
        VaultItem overflow = null;
        if (items.size() >= MAX_ITEMS) {
            overflow = items.remove(0);
        }
        String id = "v" + (nextSeq++);
        items.add(new VaultItem(id, kind, title, coins, starSand, eggType, date));
        return overflow;
    }

    void restoreItem(VaultItem item) {
        if (item == null || items.size() >= MAX_ITEMS) return;
        items.add(item);
        if (item.id.startsWith("v")) {
            try {
                int n = Integer.parseInt(item.id.substring(1));
                if (n >= nextSeq) nextSeq = n + 1;
            } catch (NumberFormatException ignored) {
                // 略過非序號 ID
            }
        }
    }

    VaultItem removeById(String id) {
        if (id == null) return null;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                return items.remove(i);
            }
        }
        return null;
    }

    VaultItem pollFirst() {
        if (items.isEmpty()) return null;
        return items.remove(0);
    }

    /**
     * 計算營火開箱加成後的金幣與星砂收益。
     * @param campfireLevel 營火廣場等級 (1..5)
     * @param isFamilyMode  是否處於親子共玩模式
     * @param hasTortoise   是否同行沙丘陸龜咚咚
     */
    static int computeBonusCoins(int baseCoins, int campfireLevel, boolean isFamilyMode, boolean hasTortoise) {
        if (baseCoins <= 0) return 0;
        int pct = isFamilyMode ? (10 + Math.max(0, campfireLevel - 1) * 6) : 0; // 10% -> 34%
        if (hasTortoise) pct += 15;
        return baseCoins + (baseCoins * pct) / 100;
    }

    static int computeBonusStarSand(int baseStarSand, int campfireLevel, boolean isFamilyMode) {
        int extra = isFamilyMode ? Math.max(2, campfireLevel) : 0;
        return Math.max(0, baseStarSand) + extra;
    }
}
