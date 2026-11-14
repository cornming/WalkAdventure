package com.cornming.walkadventure;

import java.util.Random;

/**
 * 走走冒險的遊戲內容設定表：五大主題區域、商店裝備、同伴夥伴、每日任務、成就與奇遇池。
 * 純 Java 無 Android 相依，平衡與內容可直接納入單元測試。
 */
final class AdventureCatalog {
    static final int BIOME_COUNT = 5;
    static final int CELLS_PER_BIOME = 8;
    static final int GEAR_COUNT = 3;
    static final int GEAR_MAX_LEVEL = 5;
    static final int COMPANION_COUNT = 5;
    static final int DAILY_QUEST_COUNT = 3;
    static final int ACHIEVEMENT_COUNT = 10;

    static final int GEAR_BOOTS = 0;
    static final int GEAR_CHARM = 1;
    static final int GEAR_COMPASS = 2;

    static final int COMPANION_NONE = 0;
    static final int COMPANION_SHIBA = 1;
    static final int COMPANION_SLIME = 2;
    static final int COMPANION_OWL = 3;
    static final int COMPANION_DRAGON = 4;

    static final int ENCOUNTER_MONSTER = 0;
    static final int ENCOUNTER_CHEST = 1;
    static final int ENCOUNTER_MERCHANT = 2;
    static final int ENCOUNTER_SHRINE = 3;
    static final int ENCOUNTER_STARTER = 4;

    private AdventureCatalog() {}

    static final class BiomeDef {
        final int index;
        final String name;
        final String landmark;
        final String description;
        final int accentRgb;
        final int tintRgb;

        BiomeDef(int index, String name, String landmark, String description, int accentRgb, int tintRgb) {
            this.index = index;
            this.name = name;
            this.landmark = landmark;
            this.description = description;
            this.accentRgb = accentRgb;
            this.tintRgb = tintRgb;
        }
    }

    static final class CompanionDef {
        final int id;
        final String name;
        final String role;
        final String perkText;
        final int cost;

        CompanionDef(int id, String name, String role, String perkText, int cost) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.perkText = perkText;
            this.cost = cost;
        }
    }

    static final class DailyQuestDef {
        final int index;
        final String title;
        final long targetSteps;
        final int coinReward;
        final int xpReward;

        DailyQuestDef(int index, String title, long targetSteps, int coinReward, int xpReward) {
            this.index = index;
            this.title = title;
            this.targetSteps = targetSteps;
            this.coinReward = coinReward;
            this.xpReward = xpReward;
        }
    }

    static final class AchievementDef {
        final int index;
        final String title;
        final String conditionText;
        final int coinReward;

        AchievementDef(int index, String title, String conditionText, int coinReward) {
            this.index = index;
            this.title = title;
            this.conditionText = conditionText;
            this.coinReward = coinReward;
        }
    }

    static final class PendingEncounter {
        final int type;
        final int biomeIndex;
        final String title;
        final String prompt;
        final String primaryLabel;
        final String secondaryLabel;

        PendingEncounter(
            int type,
            int biomeIndex,
            String title,
            String prompt,
            String primaryLabel,
            String secondaryLabel
        ) {
            this.type = type;
            this.biomeIndex = biomeIndex;
            this.title = title;
            this.prompt = prompt;
            this.primaryLabel = primaryLabel;
            this.secondaryLabel = secondaryLabel;
        }
    }

    static final BiomeDef[] BIOMES = {
        new BiomeDef(0, "青草新手村", "村口風車塔", "微風吹拂的起點，適合暖身踏出第一步", 0xFF70AF74, 0xFFE9F5E3),
        new BiomeDef(1, "迷霧精靈林", "樹靈古壇", "螢火蟲與老樹守護的秘密林道", 0xFF478B62, 0xFFE2F1EA),
        new BiomeDef(2, "烈日黃金漠", "沙蟲遺跡", "金黃沙丘下藏著閃閃發光的古代寶庫", 0xFFD99536, 0xFFFBECD2),
        new BiomeDef(3, "星辰水晶原", "極光祭壇", "夜晚會映出繁星與冰晶的北方原野", 0xFF548FD6, 0xFFE5EFFA),
        new BiomeDef(4, "龍之赤火山", "炎龍王座", "勇者試煉最終地，每一步都冒出金焰", 0xFFD65A38, 0xFFFCE7DF)
    };

    private static final String[] BIOME_MONSTERS = {
        "貪睡小史萊姆",
        "迷路樹葉妖",
        "仙人掌刺刺獸",
        "水晶小雪狼",
        "熔岩小火蜥"
    };

    static final CompanionDef[] COMPANIONS = {
        new CompanionDef(COMPANION_NONE, "獨自出發", "自由旅人", "無額外旅伴加成", 0),
        new CompanionDef(COMPANION_SHIBA, "柴犬阿福", "散步小隊", "每個千步寶箱額外 +8 金幣", 80),
        new CompanionDef(COMPANION_SLIME, "小綠史萊姆", "彈跳助威", "解決奇遇額外 +20 XP、+6 金幣", 140),
        new CompanionDef(COMPANION_OWL, "貓頭鷹波波", "夜視偵察", "每日任務領獎金幣 +25%", 220),
        new CompanionDef(COMPANION_DRAGON, "迷你火龍", "傳說夥伴", "步數 XP +20% 且奇遇必暴擊 +15 金幣", 380)
    };

    static final DailyQuestDef[] DAILY_QUESTS = {
        new DailyQuestDef(0, "暖身起走", 1500, 25, 40),
        new DailyQuestDef(1, "活力探險", 4000, 55, 110),
        new DailyQuestDef(2, "今日健步王", 8000, 100, 250)
    };

    static final AchievementDef[] ACHIEVEMENTS = {
        new AchievementDef(0, "初入冒險", "累計真實步數達 1,000 步", 20),
        new AchievementDef(1, "五千行者", "累計真實步數達 5,000 步", 40),
        new AchievementDef(2, "兩萬健將", "累計真實步數達 20,000 步", 80),
        new AchievementDef(3, "傳奇腳印", "累計真實步數達 50,000 步", 150),
        new AchievementDef(4, "精靈林旅人", "探索解鎖第 2 章「迷霧精靈林」", 50),
        new AchievementDef(5, "極地探險家", "探索解鎖第 4 章「星辰水晶原」", 120),
        new AchievementDef(6, "寶箱獵人", "累計開啟 5 個步數寶箱", 50),
        new AchievementDef(7, "奇遇高手", "完成 5 次路途奇遇互動", 50),
        new AchievementDef(8, "毛茸茸小隊", "招募任一位同伴一起冒險", 40),
        new AchievementDef(9, "三日火種", "達成連續 3 天完成暖身目標", 60)
    };

    static BiomeDef biome(int index) {
        if (index < 0 || index >= BIOMES.length) return BIOMES[0];
        return BIOMES[index];
    }

    static CompanionDef companion(int id) {
        if (id < 0 || id >= COMPANIONS.length) return COMPANIONS[0];
        return COMPANIONS[id];
    }

    static String gearName(int gearIndex) {
        switch (gearIndex) {
            case GEAR_BOOTS: return "疾風探險靴";
            case GEAR_CHARM: return "幸運四葉草";
            case GEAR_COMPASS: return "尋寶羅盤";
            default: return "冒險道具";
        }
    }

    static String gearEffectSummary(int gearIndex, int level) {
        switch (gearIndex) {
            case GEAR_BOOTS:
                return "Lv." + level + "/" + GEAR_MAX_LEVEL + " · 走路額外 +" + (level * 10) + "% 冒險 XP";
            case GEAR_CHARM:
                return "Lv." + level + "/" + GEAR_MAX_LEVEL + " · 寶箱／勝利金幣 +" + (level * 15) + "%";
            case GEAR_COMPASS:
                return "Lv." + level + "/" + GEAR_MAX_LEVEL + " · 雙倍驚喜機率 " + (15 + level * 12) + "%";
            default:
                return "";
        }
    }

    static int gearUpgradeCost(int gearIndex, int currentLevel) {
        if (currentLevel >= GEAR_MAX_LEVEL) return 0;
        int base;
        switch (gearIndex) {
            case GEAR_BOOTS: base = 50; break;
            case GEAR_CHARM: base = 60; break;
            case GEAR_COMPASS: base = 70; break;
            default: base = 60;
        }
        return base + currentLevel * 45;
    }

    static PendingEncounter createStarterEncounter() {
        return new PendingEncounter(
            ENCOUNTER_STARTER,
            0,
            "村長的出發補給包",
            "歡迎展開全新章節！村長準備了一袋起始金幣與乾糧送給你。",
            "打開補給包 (+30 金幣)",
            "先做暖身 (+60 XP)"
        );
    }

    static PendingEncounter generateEncounter(int biomeIndex, int compassLevel, Random random) {
        int safeBiome = Math.max(0, Math.min(BIOME_COUNT - 1, biomeIndex));
        int roll = random.nextInt(100);
        int treasureBias = Math.min(20, compassLevel * 4);
        if (roll < 35) {
            String monster = BIOME_MONSTERS[safeBiome];
            return new PendingEncounter(
                ENCOUNTER_MONSTER,
                safeBiome,
                "遭遇 " + monster + "！",
                "牠在「" + BIOMES[safeBiome].name + "」的路上擋住去路，要怎麼應對？",
                "⚔️ 迎戰 (+金幣與XP)",
                "🍖 餵食安撫 (+穩健XP)"
            );
        }
        if (roll < 62 + treasureBias) {
            return new PendingEncounter(
                ENCOUNTER_CHEST,
                safeBiome,
                "發現路邊神秘寶箱",
                "石碑旁有個微光閃爍的寶箱，羅盤指針也跟著轉個不停。",
                "🔑 直接打開",
                "✨ 許願再開 (+幸運XP)"
            );
        }
        if (roll < 84) {
            return new PendingEncounter(
                ENCOUNTER_MERCHANT,
                safeBiome,
                "遇見巡迴流浪商人",
                "商人笑著招呼你：想免費領祝福，還是花 25 金幣抽盲盒福袋？",
                "🤝 免費祝福 (+18 金幣)",
                "🎁 抽福袋 (-25 → 35~90金幣)"
            );
        }
        return new PendingEncounter(
            ENCOUNTER_SHRINE,
            safeBiome,
            "路過古老祈願神壇",
            "刻滿星紋的小石碑正散發著暖光，你可以向它許下一個願望。",
            "🌟 祈願經驗 (+90 XP)",
            "🪙 祈願財運 (+30 金幣)"
        );
    }
}
