package com.cornming.walkadventure;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Phase 3 實體藏寶盒（NFC 卡片／離線 QR 碼）與離線家庭 QR 禮物交換系統（純 Java，100% 離線）：
 * - 大人可綁定閒置悠遊卡／NFC 貼紙或產生離線 QR 藏寶碼，寫下謎語讓孩子在家或公園尋寶開箱！
 * - 支援多機免伺服器「離線 QR 家族禮物卡」：含防偽校驗碼與防重複領取機制，收到禮物時對方的旅伴還會來營地串門子一整天！
 */
final class TreasureAndQrState {
    static final int MAX_TREASURES = 24;
    static final int MAX_REDEEMED_GIFTS = 60;

    static final int REWARD_COIN_CHEST = 0;   // 金幣星砂大寶箱（+60金幣、+20星砂）
    static final int REWARD_SNACK_PACK = 1;   // 點心毛毯禮包（+3點心、+1暖蛋毛毯、+12星砂）
    static final int REWARD_MYSTERY_EGG = 2;  // 尋寶限定神祕蛋（+30金幣、+1顆星光／花瓣神祕蛋）

    static final int GIFT_CARE_PACK = 0;      // 暖心點心包（+2點心、+15星砂、+35金幣）
    static final int GIFT_EXPLORER_BOX = 1;   // 冒險補給箱（+65金幣、+20星砂、+1暖蛋毛毯）

    static final String TREASURE_PREFIX = "WATR1:";
    static final String GIFT_PREFIX = "WAGIFT1:";

    static final class HiddenTreasure {
        final String id;
        final String title;
        final String riddle;
        final String nfcUid;      // 大寫 HEX（若為純 QR 尋寶則為空字串）
        final String secretCode;  // 可愛短碼（例：STAR-4821），同步編入 QR 碼
        final int rewardType;
        final String createdDate;
        boolean claimed;
        String claimedDate;

        HiddenTreasure(
            String id,
            String title,
            String riddle,
            String nfcUid,
            String secretCode,
            int rewardType,
            String createdDate,
            boolean claimed,
            String claimedDate
        ) {
            this.id = id != null ? id : "";
            this.title = title != null && !title.trim().isEmpty() ? title.trim() : "神祕藏寶箱";
            this.riddle = riddle != null && !riddle.trim().isEmpty() ? riddle.trim() : "找找看家裡或公園裡藏起來的祕密記號！";
            this.nfcUid = normalizeHexUid(nfcUid);
            this.secretCode = normalizeSecretCode(secretCode);
            this.rewardType = Math.max(0, Math.min(2, rewardType));
            this.createdDate = createdDate != null ? createdDate : "";
            this.claimed = claimed;
            this.claimedDate = claimedDate != null ? claimedDate : "";
        }

        boolean hasNfc() {
            return !nfcUid.isEmpty();
        }

        String toQrPayload() {
            return TREASURE_PREFIX + secretCode + "|" + sanitizePayloadField(title, 18) + "|" + rewardType;
        }
    }

    static final class FamilyGiftCard {
        final String giftId;
        final String senderName;
        final int companionId;
        final int companionStage;
        final boolean companionShiny;
        final int giftType;
        final String createdDate;
        final String encodedString;

        FamilyGiftCard(
            String giftId,
            String senderName,
            int companionId,
            int companionStage,
            boolean companionShiny,
            int giftType,
            String createdDate,
            String encodedString
        ) {
            this.giftId = giftId;
            this.senderName = senderName;
            this.companionId = companionId;
            this.companionStage = Math.max(1, Math.min(3, companionStage));
            this.companionShiny = companionShiny;
            this.giftType = Math.max(0, Math.min(1, giftType));
            this.createdDate = createdDate != null ? createdDate : "";
            this.encodedString = encodedString;
        }
    }

    static final class GuestVisit {
        String senderName = "";
        int companionId = CompanionSystem.ID_NONE;
        int companionStage = 1;
        boolean companionShiny = false;
        String visitDate = "";

        boolean isActiveOn(String today) {
            return companionId != CompanionSystem.ID_NONE
                && today != null
                && !today.isEmpty()
                && today.equals(visitDate);
        }
    }

    static final class ClaimOutcome {
        final boolean ok;
        final String message;
        final HiddenTreasure treasure;
        final int coinsGained;
        final int starSandGained;
        final int snacksGained;
        final int blanketsGained;
        final int eggTypeDropped;

        ClaimOutcome(
            boolean ok,
            String message,
            HiddenTreasure treasure,
            int coinsGained,
            int starSandGained,
            int snacksGained,
            int blanketsGained,
            int eggTypeDropped
        ) {
            this.ok = ok;
            this.message = message;
            this.treasure = treasure;
            this.coinsGained = coinsGained;
            this.starSandGained = starSandGained;
            this.snacksGained = snacksGained;
            this.blanketsGained = blanketsGained;
            this.eggTypeDropped = eggTypeDropped;
        }
    }

    static final class GiftRedeemOutcome {
        final boolean ok;
        final String message;
        final FamilyGiftCard card;

        GiftRedeemOutcome(boolean ok, String message, FamilyGiftCard card) {
            this.ok = ok;
            this.message = message;
            this.card = card;
        }
    }

    private static final String[] CODE_WORDS = {
        "STAR", "LEAF", "FIRE", "BEAR", "CLOUD", "MOON", "RAIN", "CAMP"
    };

    final List<HiddenTreasure> treasures = new ArrayList<>();
    final Set<String> redeemedGiftIds = new LinkedHashSet<>();
    final GuestVisit guestVisit = new GuestVisit();
    int totalTreasuresClaimed = 0;
    int totalGiftsExchanged = 0;

    static String rewardLabel(int rewardType) {
        if (rewardType == REWARD_SNACK_PACK) return "🍪 點心毛毯組（點心×3＋毛毯×1＋星砂+12）";
        if (rewardType == REWARD_MYSTERY_EGG) return "🥚 尋寶限定神祕蛋（星光蛋＋金幣+30）";
        return "🪙 金幣星砂寶箱（金幣+60＋星砂+20）";
    }

    static String giftTypeLabel(int giftType) {
        if (giftType == GIFT_EXPLORER_BOX) return "🎁 冒險補給箱（金幣+65、星砂+20、毛毯×1）";
        return "🍪 暖心點心包（點心×2、金幣+35、星砂+15）";
    }

    static String bytesToHexUid(byte[] uidBytes) {
        if (uidBytes == null || uidBytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(uidBytes.length * 2);
        for (byte b : uidBytes) {
            sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        }
        return sb.toString();
    }

    static String normalizeHexUid(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim().toUpperCase(Locale.US).replaceAll("[^0-9A-F]", "");
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    static String normalizeSecretCode(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase(Locale.US);
        if (s.startsWith(TREASURE_PREFIX)) {
            int bar = s.indexOf('|');
            s = bar > TREASURE_PREFIX.length()
                ? s.substring(TREASURE_PREFIX.length(), bar)
                : s.substring(TREASURE_PREFIX.length());
        }
        return s.replaceAll("[^A-Z0-9-]", "");
    }

    static String generateSecretCode(Random random) {
        Random r = random != null ? random : new Random();
        String word = CODE_WORDS[r.nextInt(CODE_WORDS.length)];
        int num = 1000 + r.nextInt(9000);
        return word + "-" + num;
    }

    int activeTreasureCount() {
        int c = 0;
        for (HiddenTreasure t : treasures) {
            if (!t.claimed) c++;
        }
        return c;
    }

    List<HiddenTreasure> activeTreasures() {
        List<HiddenTreasure> out = new ArrayList<>();
        for (HiddenTreasure t : treasures) {
            if (!t.claimed) out.add(t);
        }
        return out;
    }

    HiddenTreasure createTreasure(
        String title,
        String riddle,
        String nfcUid,
        int rewardType,
        String today,
        Random random
    ) {
        String cleanUid = normalizeHexUid(nfcUid);
        // 若同一張 NFC 卡片先前綁定尚未領取的舊寶藏，更新成新寶藏以方便重複利用同一張悠遊卡！
        if (!cleanUid.isEmpty()) {
            for (int i = treasures.size() - 1; i >= 0; i--) {
                HiddenTreasure existing = treasures.get(i);
                if (!existing.claimed && cleanUid.equals(existing.nfcUid)) {
                    treasures.remove(i);
                }
            }
        }
        String code = generateUniqueCode(random);
        String id = "tr_" + System.currentTimeMillis() + "_" + (random != null ? random.nextInt(999) : 1);
        HiddenTreasure created = new HiddenTreasure(
            id, title, riddle, cleanUid, code, rewardType, today, false, ""
        );
        treasures.add(0, created);
        while (treasures.size() > MAX_TREASURES) {
            treasures.remove(treasures.size() - 1);
        }
        return created;
    }

    boolean removeTreasure(String id) {
        if (id == null) return false;
        for (int i = 0; i < treasures.size(); i++) {
            if (id.equals(treasures.get(i).id)) {
                treasures.remove(i);
                return true;
            }
        }
        return false;
    }

    HiddenTreasure findActiveByNfcUid(String rawUid) {
        String uid = normalizeHexUid(rawUid);
        if (uid.isEmpty()) return null;
        for (HiddenTreasure t : treasures) {
            if (!t.claimed && uid.equals(t.nfcUid)) return t;
        }
        return null;
    }

    HiddenTreasure findActiveByCodeOrQr(String rawInput) {
        String code = normalizeSecretCode(rawInput);
        if (code.isEmpty()) return null;
        for (HiddenTreasure t : treasures) {
            if (!t.claimed && code.equals(t.secretCode)) return t;
        }
        return null;
    }

    /** 產生可編入 QR 碼與文字複製的「離線家族禮物卡」（含 4 位 HEX 防偽校驗碼）。 */
    FamilyGiftCard createFamilyGiftCard(
        String senderName,
        int companionId,
        int companionStage,
        boolean companionShiny,
        int giftType,
        String today,
        Random random
    ) {
        Random r = random != null ? random : new Random();
        String safeSender = sanitizePayloadField(senderName == null || senderName.trim().isEmpty() ? "探險家人" : senderName, 10);
        int cid = Math.max(0, companionId);
        int stage = Math.max(1, Math.min(3, companionStage));
        int gType = Math.max(0, Math.min(1, giftType));
        String safeDate = today != null ? today : "";
        String nonce = String.format(Locale.US, "%04X", r.nextInt(0x10000));
        String giftId = safeDate.replace("-", "") + "_" + nonce;
        String body = giftId
            + "|" + safeSender
            + "|" + cid
            + "|" + stage
            + "|" + (companionShiny ? "1" : "0")
            + "|" + gType
            + "|" + safeDate;
        String crc = computeShortChecksum(body);
        String encoded = GIFT_PREFIX + body + "|" + crc;
        totalGiftsExchanged++;
        return new FamilyGiftCard(giftId, safeSender, cid, stage, companionShiny, gType, safeDate, encoded);
    }

    /** 解析並驗證離線家族禮物卡字串（校驗碼不符回傳 null）。 */
    static FamilyGiftCard parseGiftCard(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int idx = s.indexOf(GIFT_PREFIX);
        if (idx < 0) return null;
        s = s.substring(idx + GIFT_PREFIX.length()).trim();
        // 允許結尾夾雜換行或空白
        int nl = s.indexOf('\n');
        if (nl >= 0) s = s.substring(0, nl).trim();
        String[] parts = s.split("\\|");
        if (parts.length != 8) return null;

        String body = parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + parts[3]
            + "|" + parts[4] + "|" + parts[5] + "|" + parts[6];
        String expectedCrc = computeShortChecksum(body);
        if (!expectedCrc.equalsIgnoreCase(parts[7])) {
            return null;
        }
        try {
            int cid = Integer.parseInt(parts[2]);
            int stage = Integer.parseInt(parts[3]);
            boolean shiny = "1".equals(parts[4]);
            int gType = Integer.parseInt(parts[5]);
            return new FamilyGiftCard(
                parts[0], parts[1], cid, stage, shiny, gType, parts[6], GIFT_PREFIX + s
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    boolean hasRedeemedGift(String giftId) {
        return giftId != null && redeemedGiftIds.contains(giftId);
    }

    void recordRedeemedGift(FamilyGiftCard card, String today) {
        if (card == null) return;
        redeemedGiftIds.add(card.giftId);
        while (redeemedGiftIds.size() > MAX_REDEEMED_GIFTS) {
            String first = redeemedGiftIds.iterator().next();
            redeemedGiftIds.remove(first);
        }
        totalGiftsExchanged++;
        if (card.companionId != CompanionSystem.ID_NONE) {
            guestVisit.senderName = card.senderName;
            guestVisit.companionId = card.companionId;
            guestVisit.companionStage = card.companionStage;
            guestVisit.companionShiny = card.companionShiny;
            guestVisit.visitDate = today != null ? today : "";
        }
    }

    private String generateUniqueCode(Random random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String c = generateSecretCode(random);
            if (findActiveByCodeOrQr(c) == null) return c;
        }
        return "STAR-" + (System.currentTimeMillis() % 9000 + 1000);
    }

    private static String sanitizePayloadField(String raw, int maxLen) {
        if (raw == null) return "";
        String clean = raw.trim().replace("|", " ").replace("\n", " ").replace("\r", " ");
        return clean.length() > maxLen ? clean.substring(0, maxLen) : clean;
    }

    /** 輕量 16-bit FNV-1a 校驗碼，防止手動打字輸入錯誤或偽造禮物字串。 */
    static String computeShortChecksum(String input) {
        byte[] bytes = (input == null ? "" : input).getBytes(StandardCharsets.UTF_8);
        int hash = 0x811C9DC5;
        for (byte b : bytes) {
            hash ^= (b & 0xFF);
            hash *= 0x01000193;
        }
        int folded = ((hash >>> 16) ^ (hash & 0xFFFF)) & 0xFFFF;
        return String.format(Locale.US, "%04X", folded);
    }
}
