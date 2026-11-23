package com.cornming.walkadventure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 旅伴養成、孵蛋巢、三階段進化、閃亮異色與可愛飾品系統（純 Java）：
 * ID 1..4 完全對應舊版 v0.4.0 的 4 位元老旅伴，擴充至 12 位跨生態／時段／季節旅伴。
 */
final class CompanionSystem {
    static final int MAX_EGG_SLOTS = 2;
    static final int DAILY_WARM_STEPS = 300;
    static final int BLANKET_WARM_STEPS = 1000;
    static final int SNACK_BOND_XP = 40;
    static final int MAX_BOND_LEVEL = 10;
    static final int STAGE_2_BOND_XP = 240; // Lv.4 -> 成長形態
    static final int STAGE_3_BOND_XP = 720; // Lv.8 -> 傳說大師形態
    static final int BASE_SHINY_CHANCE_PCT = 8;

    // 12 位旅伴 ID（0=獨自出發，1..4 向下相容舊版）
    static final int ID_NONE = 0;
    static final int ID_SHIBA = 1;       // 柴犬阿福（元老）
    static final int ID_SLIME = 2;       // 小綠史萊姆（元老）
    static final int ID_OWL = 3;         // 貓頭鷹波波（元老）
    static final int ID_DRAGON = 4;      // 迷你火龍（元老）
    static final int ID_SHEEP = 5;       // 雲朵羊綿綿
    static final int ID_SQUIRREL = 6;    // 橡果松鼠球球
    static final int ID_FOX = 7;         // 螢火狐狸亮亮
    static final int ID_TORTOISE = 8;    // 沙丘陸龜咚咚
    static final int ID_PENGUIN = 9;     // 冰晶企鵝嘟嘟
    static final int ID_OTTER = 10;      // 波浪小水獺泡泡
    static final int ID_DEER = 11;       // 櫻花小鹿花花
    static final int ID_WHALE = 12;      // 星光獨角鯨嚕嚕

    // 7 種神祕蛋類型
    static final int EGG_LEAF = 0;       // 草葉蛋（阿福、史萊姆、綿綿）
    static final int EGG_MIST = 1;       // 霧林蛋（波波、球球、綿綿）
    static final int EGG_GOLD = 2;       // 黃金蛋（咚咚、泡泡、阿福）
    static final int EGG_STAR = 3;       // 星雪蛋（嘟嘟、嚕嚕、波波）
    static final int EGG_FLAME = 4;      // 火山蛋（火龍、亮亮、咚咚）
    static final int EGG_NIGHT = 5;      // 螢火夜蛋（亮亮、波波、嚕嚕）
    static final int EGG_BLOOM = 6;      // 花瓣菜園蛋（花花、泡泡、球球）
    static final int EGG_STARTER_MINI = 7; // 新手暖身蛋（僅 500 步，必出草葉夥伴）

    // 飾品類型（探險工坊解鎖或金幣製作）
    static final int ACC_NONE = 0;
    static final int ACC_STRAW_HAT = 1;  // 冒險草帽
    static final int ACC_RED_SCARF = 2;  // 暖心圍巾
    static final int ACC_STAR_CROWN = 3; // 星光王冠
    static final int ACC_COUNT = 4;

    static final class SpeciesDef {
        final int id;
        final String name;
        final String role;
        final String perkSummary;
        final String obtainHint;
        final String stage1Title;
        final String stage2Title;
        final String stage3Title;
        final int primaryRgb;
        final int shinyRgb;
        final String bondStory;

        SpeciesDef(
            int id,
            String name,
            String role,
            String perkSummary,
            String obtainHint,
            String stage1Title,
            String stage2Title,
            String stage3Title,
            int primaryRgb,
            int shinyRgb,
            String bondStory
        ) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.perkSummary = perkSummary;
            this.obtainHint = obtainHint;
            this.stage1Title = stage1Title;
            this.stage2Title = stage2Title;
            this.stage3Title = stage3Title;
            this.primaryRgb = primaryRgb;
            this.shinyRgb = shinyRgb;
            this.bondStory = bondStory;
        }

        String stageTitle(int stage) {
            if (stage >= 3) return stage3Title;
            if (stage == 2) return stage2Title;
            return stage1Title;
        }
    }

    static final class EggTypeDef {
        final int type;
        final String name;
        final int requiredSteps;
        final int accentRgb;
        final int[] candidateIds;

        EggTypeDef(int type, String name, int requiredSteps, int accentRgb, int[] candidateIds) {
            this.type = type;
            this.name = name;
            this.requiredSteps = requiredSteps;
            this.accentRgb = accentRgb;
            this.candidateIds = candidateIds;
        }
    }

    static final class AccessoryDef {
        final int id;
        final String name;
        final int coinCost;
        final int starCost;
        final int minWorkshopLevel;

        AccessoryDef(int id, String name, int coinCost, int starCost, int minWorkshopLevel) {
            this.id = id;
            this.name = name;
            this.coinCost = coinCost;
            this.starCost = starCost;
            this.minWorkshopLevel = minWorkshopLevel;
        }
    }

    static final class OwnedCompanion {
        final int id;
        int bondXp;
        int stage; // 1..3
        boolean shiny;
        int accessory;
        String unlockedDate;
        String lastPetDate;

        OwnedCompanion(int id, int bondXp, int stage, boolean shiny, int accessory, String unlockedDate, String lastPetDate) {
            this.id = id;
            this.bondXp = Math.max(0, bondXp);
            this.stage = Math.max(computeStage(this.bondXp), Math.max(1, Math.min(3, stage)));
            this.shiny = shiny;
            this.accessory = Math.max(0, Math.min(ACC_COUNT - 1, accessory));
            this.unlockedDate = unlockedDate != null ? unlockedDate : "";
            this.lastPetDate = lastPetDate != null ? lastPetDate : "";
        }

        int bondLevel() {
            // 每 120 XP 一級，Lv.1 (0) -> Lv.10 (1080+)
            return Math.min(MAX_BOND_LEVEL, (bondXp / 120) + 1);
        }

        int xpToNextStage() {
            if (stage == 1) return Math.max(0, STAGE_2_BOND_XP - bondXp);
            if (stage == 2) return Math.max(0, STAGE_3_BOND_XP - bondXp);
            return 0;
        }

        boolean addBondXp(int delta) {
            if (delta <= 0) return false;
            int prevStage = stage;
            bondXp = Math.min(100_000, bondXp + delta);
            int nextStage = computeStage(bondXp);
            if (nextStage > stage) {
                stage = nextStage;
                return true;
            }
            return stage > prevStage;
        }
    }

    static final class EggSlot {
        final int slot;
        int eggType;
        int stepsWalked;
        int stepsRequired;
        String lastWarmDate;
        int biomeOrigin;

        EggSlot(int slot, int eggType, int stepsWalked, int stepsRequired, String lastWarmDate, int biomeOrigin) {
            this.slot = slot;
            this.eggType = eggType;
            this.stepsWalked = Math.max(0, stepsWalked);
            this.stepsRequired = Math.max(100, stepsRequired);
            this.lastWarmDate = lastWarmDate != null ? lastWarmDate : "";
            this.biomeOrigin = biomeOrigin;
        }

        boolean isReadyToHatch() {
            return stepsWalked >= stepsRequired;
        }

        float progressRatio() {
            return Math.min(1f, stepsWalked / (float) stepsRequired);
        }
    }

    static final class HatchResult {
        final boolean ok;
        final int companionId;
        final boolean isNewSpecies;
        final boolean isShiny;
        final int bonusStarSand;
        final String message;

        HatchResult(boolean ok, int companionId, boolean isNewSpecies, boolean isShiny, int bonusStarSand, String message) {
            this.ok = ok;
            this.companionId = companionId;
            this.isNewSpecies = isNewSpecies;
            this.isShiny = isShiny;
            this.bonusStarSand = bonusStarSand;
            this.message = message;
        }
    }

    static final SpeciesDef[] SPECIES = {
        new SpeciesDef(
            ID_SHIBA, "柴犬阿福", "散步小隊",
            "千步寶箱額外 +8/+12/+18 金幣（隨進化提升）",
            "元老夥伴 / 草葉蛋孵化",
            "毛球幼犬", "領巾探險柴", "披風黃金柴",
            0xFFE19242, 0xFFF2C85B,
            "阿福總會在路口回頭等你，只要搖尾巴就能聞出金幣的方向。"
        ),
        new SpeciesDef(
            ID_SLIME, "小綠史萊姆", "彈跳助威",
            "解決奇遇額外 +20/+35/+50 XP 與金幣",
            "元老夥伴 / 草葉蛋孵化",
            "露珠史萊姆", "彈跳果凍", "皇冠大史萊姆",
            0xFF62BD6C, 0xFF56C5D0,
            "每走一步就會跟著彈一下，最喜歡把撿到的小花藏在頭頂。"
        ),
        new SpeciesDef(
            ID_OWL, "貓頭鷹波波", "夜視偵察",
            "每日任務金幣 +25%/+32%/+40%，夜晚散步額外 +星砂",
            "元老夥伴 / 霧林蛋與夜蛋孵化",
            "絨毛雛鷹", "偵察貓頭鷹", "星冠智者鷹",
            0xFF8C6C56, 0xFFDCE6F2,
            "天黑以後眼睛亮晶晶的，會在樹梢指引大家看見最亮的星星。"
        ),
        new SpeciesDef(
            ID_DRAGON, "迷你火龍", "傳說夥伴",
            "步數 XP +20%/+25%/+30% 且奇遇額外 +15 金幣",
            "元老夥伴 / 火山蛋孵化",
            "蛋殼小火龍", "展翼焰龍", "金焰傳說龍",
            0xFFDA5C3A, 0xFF6B5CE7,
            "打噴嚏時會冒出暖呼呼的小火星，是全家烤棉花糖的最佳幫手。"
        ),
        new SpeciesDef(
            ID_SHEEP, "雲朵羊綿綿", "花園助手",
            "奇幻菜園走路澆水效率 +25%，採收更有活力",
            "草葉蛋 / 清晨散步蛋孵化",
            "小毛球羊", "蓬蓬雲朵羊", "彩虹雲神羊",
            0xFFF6F2E9, 0xFFFFD8E8,
            "身上的毛軟得像剛出爐的雲朵，只要經過菜園，小花就會立刻抬頭。"
        ),
        new SpeciesDef(
            ID_SQUIRREL, "橡果松鼠球球", "採集達人",
            "每次奇遇有機率多帶回營地木頭與花瓣",
            "霧林蛋 / 花瓣蛋孵化",
            "抱果小松鼠", "背包採集鼠", "森林守護松鼠",
            0xFFC47A42, 0xFFE6A567,
            "臉頰兩邊塞滿了替大家留的秘密橡果，最愛在公園大樹下尋寶。"
        ),
        new SpeciesDef(
            ID_FOX, "螢火狐狸亮亮", "星夜嚮導",
            "黃昏與夜晚的散步奇遇星砂收益加倍",
            "螢火夜蛋（黃昏／夜晚散步獲得）",
            "微光小幼狐", "提燈螢火狐", "星火九尾狐",
            0xFFE8863B, 0xFF6AD5E8,
            "尾巴尖端閃著溫柔的暖光，牽著大人孩子的手就不怕傍晚黑漆漆。"
        ),
        new SpeciesDef(
            ID_TORTOISE, "沙丘陸龜咚咚", "寶庫護衛",
            "營火開箱總金幣額外 +15%（不急不徐最穩重）",
            "黃金蛋 / 火山蛋孵化",
            "迷你小陸龜", "綠洲背殼龜", "移動寶庫巨龜",
            0xFF7B9E5B, 0xFFD9B24C,
            "雖然走得慢吞吞，但從不放棄，背殼裡裝著全家人滿滿的旅行回憶。"
        ),
        new SpeciesDef(
            ID_PENGUIN, "冰晶企鵝嘟嘟", "連勝啦啦隊",
            "保持連續達標時每日額外送出 1 份旅伴點心",
            "星雪蛋孵化（冬季出現機率提升）",
            "圓滾雛企鵝", "圍巾滑行企鵝", "極光艦長企鵝",
            0xFF4F7EA8, 0xFF84C6E8,
            "最喜歡和孩子比賽搖搖擺擺走路，誰先笑出聲就得跟牠擊掌！"
        ),
        new SpeciesDef(
            ID_OTTER, "波浪水獺泡泡", "親子開心果",
            "完成親子抬頭任務額外獲得 +15 星砂與羈絆",
            "黃金蛋 / 花瓣蛋 / 抬頭任務獎勵蛋",
            "抱貝殼幼獺", "泳鏡探險獺", "浪花船長獺",
            0xFF8F6B50, 0xFFB8A392,
            "口袋裡永遠藏著一顆光滑的幸運石，喜歡聽孩子說今天看到什麼好玩的事。"
        ),
        new SpeciesDef(
            ID_DEER, "櫻花小鹿花花", "豐收精靈",
            "採收奇幻菜園時額外多收成 1 份旅伴點心",
            "花瓣菜園蛋（菜園採收或春季散步）",
            "小斑點花鹿", "開花鹿角鹿", "四季森靈鹿",
            0xFFE48FA4, 0xFFFFD178,
            "鹿角會隨著季節長出櫻花、新葉與小雪球，是秘密營地最優雅的鄰居。"
        ),
        new SpeciesDef(
            ID_WHALE, "星光獨角鯨嚕嚕", "孵蛋領航員",
            "全隊走路孵蛋速度額外 +15%",
            "星雪蛋 / 安撫島嶼守護巨獸獲得",
            "泡泡小獨角鯨", "銀角星光鯨", "銀河守護鯨",
            0xFF5D7ED6, 0xFFB18CF0,
            "會漂浮在營地夜空裡哼著輕輕的搖籃曲，守護每顆正在長大的神祕蛋。"
        )
    };

    static final EggTypeDef[] EGG_TYPES = {
        new EggTypeDef(EGG_LEAF, "草葉神祕蛋", 2000, 0xFF70AF74, new int[]{ID_SHIBA, ID_SLIME, ID_SHEEP}),
        new EggTypeDef(EGG_MIST, "霧林神祕蛋", 2800, 0xFF478B62, new int[]{ID_OWL, ID_SQUIRREL, ID_SHEEP}),
        new EggTypeDef(EGG_GOLD, "黃金沙丘蛋", 3500, 0xFFD99536, new int[]{ID_TORTOISE, ID_OTTER, ID_SHIBA}),
        new EggTypeDef(EGG_STAR, "星雪奇蹟蛋", 4200, 0xFF548FD6, new int[]{ID_PENGUIN, ID_WHALE, ID_OWL}),
        new EggTypeDef(EGG_FLAME, "火山熔岩蛋", 4800, 0xFFD65A38, new int[]{ID_DRAGON, ID_FOX, ID_TORTOISE}),
        new EggTypeDef(EGG_NIGHT, "螢火夜行蛋", 3200, 0xFF5C6AC4, new int[]{ID_FOX, ID_OWL, ID_WHALE}),
        new EggTypeDef(EGG_BLOOM, "花瓣花園蛋", 3400, 0xFFE07A96, new int[]{ID_DEER, ID_OTTER, ID_SQUIRREL}),
        new EggTypeDef(EGG_STARTER_MINI, "迎新小葉蛋", 500, 0xFF7EC87E, new int[]{ID_SHEEP, ID_SHIBA, ID_SLIME})
    };

    static final AccessoryDef[] ACCESSORIES = {
        new AccessoryDef(ACC_NONE, "無飾品", 0, 0, 0),
        new AccessoryDef(ACC_STRAW_HAT, "冒險草帽", 120, 10, 1),
        new AccessoryDef(ACC_RED_SCARF, "暖心紅圍巾", 180, 16, 2),
        new AccessoryDef(ACC_STAR_CROWN, "星光小王冠", 280, 28, 3)
    };

    int equippedId = ID_NONE;
    int unlockedAccessoriesMask = 1; // bit 0 = 無飾品
    final Map<Integer, OwnedCompanion> owned = new LinkedHashMap<>();
    final List<EggSlot> eggs = new ArrayList<>();

    static SpeciesDef species(int id) {
        for (SpeciesDef def : SPECIES) {
            if (def.id == id) return def;
        }
        return null;
    }

    static EggTypeDef eggTypeDef(int eggType) {
        if (eggType < 0 || eggType >= EGG_TYPES.length) return EGG_TYPES[0];
        return EGG_TYPES[eggType];
    }

    static AccessoryDef accessoryDef(int accId) {
        if (accId < 0 || accId >= ACCESSORIES.length) return ACCESSORIES[0];
        return ACCESSORIES[accId];
    }

    static int computeStage(int bondXp) {
        if (bondXp >= STAGE_3_BOND_XP) return 3;
        if (bondXp >= STAGE_2_BOND_XP) return 2;
        return 1;
    }

    static int pickEggTypeForContext(int biomeIndex, WorldClock.Snapshot clock, Random random) {
        if (clock != null) {
            if (clock.isNightOrDusk() && (random == null || random.nextInt(100) < 45)) {
                return EGG_NIGHT;
            }
            if (clock.season == WorldClock.SEASON_SPRING && (random == null || random.nextInt(100) < 35)) {
                return EGG_BLOOM;
            }
            if (clock.season == WorldClock.SEASON_WINTER && (random == null || random.nextInt(100) < 35)) {
                return EGG_STAR;
            }
        }
        switch (Math.max(0, Math.min(4, biomeIndex))) {
            case 0: return EGG_LEAF;
            case 1: return EGG_MIST;
            case 2: return EGG_GOLD;
            case 3: return EGG_STAR;
            case 4:
            default: return EGG_FLAME;
        }
    }

    boolean isOwned(int id) {
        if (id == ID_NONE) return true;
        return owned.containsKey(id);
    }

    OwnedCompanion getOwned(int id) {
        return owned.get(id);
    }

    int ownedCount() {
        return owned.size();
    }

    int shinyCount() {
        int c = 0;
        for (OwnedCompanion oc : owned.values()) {
            if (oc.shiny) c++;
        }
        return c;
    }

    int maxStageAchieved() {
        int max = 0;
        for (OwnedCompanion oc : owned.values()) {
            if (oc.stage > max) max = oc.stage;
        }
        return max;
    }

    int equippedStage() {
        OwnedCompanion oc = owned.get(equippedId);
        return oc != null ? oc.stage : 1;
    }

    String equippedName() {
        SpeciesDef def = species(equippedId);
        return def != null ? def.name : "獨自出發";
    }

    boolean unlockDirect(int id, int initialBondXp, boolean shiny, String today) {
        if (species(id) == null) return false;
        if (owned.containsKey(id)) return false;
        int safeXp = Math.max(0, initialBondXp);
        owned.put(id, new OwnedCompanion(id, safeXp, computeStage(safeXp), shiny, ACC_NONE, today, ""));
        return true;
    }

    AdventureState.ActionResult equip(int id) {
        if (id == ID_NONE) {
            equippedId = ID_NONE;
            return new AdventureState.ActionResult(true, "已切換為獨自冒險模式");
        }
        SpeciesDef def = species(id);
        if (def == null || !owned.containsKey(id)) {
            return new AdventureState.ActionResult(false, "還沒遇見這位旅伴，繼續孵蛋或探險吧！");
        }
        equippedId = id;
        return new AdventureState.ActionResult(true, "🐾 已請「" + def.name + "」一起同行！");
    }

    boolean hasFreeEggSlot() {
        return eggs.size() < MAX_EGG_SLOTS;
    }

    boolean addEgg(int eggType, int biomeOrigin) {
        if (!hasFreeEggSlot()) return false;
        EggTypeDef def = eggTypeDef(eggType);
        int nextSlot = findFreeSlotIndex();
        eggs.add(new EggSlot(nextSlot, def.type, 0, def.requiredSteps, "", biomeOrigin));
        return true;
    }

    private int findFreeSlotIndex() {
        boolean slot0Used = false;
        for (EggSlot e : eggs) {
            if (e.slot == 0) slot0Used = true;
        }
        return slot0Used ? 1 : 0;
    }

    /** 走路推進所有孵蛋巢進度，回傳新準備破殼的蛋數。 */
    int advanceEggsByWalk(long steps) {
        if (steps <= 0 || eggs.isEmpty()) return 0;
        long effective = (equippedId == ID_WHALE) ? (steps + (steps * 15L) / 100L) : steps;
        int newlyReady = 0;
        for (EggSlot egg : eggs) {
            boolean wasReady = egg.isReadyToHatch();
            long next = Math.min((long) egg.stepsRequired, egg.stepsWalked + effective);
            egg.stepsWalked = (int) next;
            if (!wasReady && egg.isReadyToHatch()) {
                newlyReady++;
            }
        }
        return newlyReady;
    }

    int readyEggCount() {
        int c = 0;
        for (EggSlot egg : eggs) {
            if (egg.isReadyToHatch()) c++;
        }
        return c;
    }

    AdventureState.ActionResult warmEggDaily(int eggListIndex, String today) {
        if (eggListIndex < 0 || eggListIndex >= eggs.size()) {
            return new AdventureState.ActionResult(false, "找不到這顆神祕蛋");
        }
        EggSlot egg = eggs.get(eggListIndex);
        if (egg.isReadyToHatch()) {
            return new AdventureState.ActionResult(false, "這顆蛋已經在搖晃了，快點擊破殼吧！");
        }
        if (today != null && today.equals(egg.lastWarmDate)) {
            return new AdventureState.ActionResult(false, "今天已經幫這顆蛋暖過身囉，明天再來抱抱牠！");
        }
        egg.lastWarmDate = today != null ? today : "";
        egg.stepsWalked = Math.min(egg.stepsRequired, egg.stepsWalked + DAILY_WARM_STEPS);
        String suffix = egg.isReadyToHatch() ? " 蛋殼裂開了，快點擊孵化！" : "";
        return new AdventureState.ActionResult(true, "💛 親子暖蛋成功！孵蛋進度 +" + DAILY_WARM_STEPS + " 步！" + suffix);
    }

    AdventureState.ActionResult useWarmBlanket(int eggListIndex, CampState camp) {
        if (camp == null || camp.warmBlankets <= 0) {
            return new AdventureState.ActionResult(false, "目前沒有暖蛋毛毯，可在營地工坊用星砂兌換");
        }
        if (eggListIndex < 0 || eggListIndex >= eggs.size()) {
            return new AdventureState.ActionResult(false, "找不到這顆神祕蛋");
        }
        EggSlot egg = eggs.get(eggListIndex);
        if (egg.isReadyToHatch()) {
            return new AdventureState.ActionResult(false, "這顆蛋已經可以孵化囉！");
        }
        camp.warmBlankets--;
        egg.stepsWalked = Math.min(egg.stepsRequired, egg.stepsWalked + BLANKET_WARM_STEPS);
        return new AdventureState.ActionResult(true, "🧣 蓋上暖蛋毛毯！孵蛋進度 +" + BLANKET_WARM_STEPS + " 步！");
    }

    HatchResult hatchEgg(int eggListIndex, int shinyBonusPct, CampState camp, String today, Random random) {
        if (eggListIndex < 0 || eggListIndex >= eggs.size()) {
            return new HatchResult(false, ID_NONE, false, false, 0, "找不到這顆神祕蛋");
        }
        EggSlot egg = eggs.get(eggListIndex);
        if (!egg.isReadyToHatch()) {
            int remain = Math.max(0, egg.stepsRequired - egg.stepsWalked);
            return new HatchResult(false, ID_NONE, false, false, 0, "再走 " + remain + " 步就會破殼囉！");
        }
        eggs.remove(eggListIndex);
        EggTypeDef def = eggTypeDef(egg.eggType);
        Random rng = random != null ? random : new Random();

        // 優先提高未解鎖旅伴機率，減少重複挫折感
        List<Integer> unownedPool = new ArrayList<>();
        for (int cid : def.candidateIds) {
            if (!owned.containsKey(cid)) {
                unownedPool.add(cid);
            }
        }
        int hatchedId;
        if (!unownedPool.isEmpty() && rng.nextInt(100) < 75) {
            hatchedId = unownedPool.get(rng.nextInt(unownedPool.size()));
        } else {
            hatchedId = def.candidateIds[rng.nextInt(def.candidateIds.length)];
        }

        int shinyChance = Math.min(30, BASE_SHINY_CHANCE_PCT + Math.max(0, shinyBonusPct));
        boolean isShiny = rng.nextInt(100) < shinyChance;
        SpeciesDef sp = species(hatchedId);
        if (sp == null) sp = SPECIES[0];

        if (!owned.containsKey(hatchedId)) {
            owned.put(hatchedId, new OwnedCompanion(hatchedId, 0, 1, isShiny, ACC_NONE, today, ""));
            if (equippedId == ID_NONE) {
                equippedId = hatchedId;
            }
            String tag = isShiny ? "✨【閃亮異色】" : "";
            return new HatchResult(
                true,
                hatchedId,
                true,
                isShiny,
                0,
                "🐣 破殼成功！" + tag + "新旅伴「" + sp.name + "」加入我們的小隊！"
            );
        }

        // 重複孵化：轉化為大量羈絆 XP + 星砂（若抽到異色則升級為閃亮外觀！）
        OwnedCompanion existing = owned.get(hatchedId);
        boolean evolved = existing.addBondXp(80);
        boolean gainedShiny = false;
        if (isShiny && !existing.shiny) {
            existing.shiny = true;
            gainedShiny = true;
        }
        int bonusStar = 20;
        if (camp != null) {
            camp.starSand += bonusStar;
        }
        String extra = (gainedShiny ? " 覺醒✨閃亮異色外觀！" : "")
            + (evolved ? " 並且進化成「" + sp.stageTitle(existing.stage) + "」！" : "");
        return new HatchResult(
            true,
            hatchedId,
            false,
            existing.shiny,
            bonusStar,
            "🐣 孵出「" + sp.name + "」的好朋友祝福！羈絆 +80 XP、星砂 +" + bonusStar + "！" + extra
        );
    }

    AdventureState.ActionResult feedSnack(int companionId, CampState camp) {
        if (camp == null || camp.snacks <= 0) {
            return new AdventureState.ActionResult(false, "沒有冒險點心了，去奇幻菜園收成或工坊買一點吧！");
        }
        OwnedCompanion oc = owned.get(companionId);
        SpeciesDef sp = species(companionId);
        if (oc == null || sp == null) {
            return new AdventureState.ActionResult(false, "請先選擇已同行的旅伴");
        }
        camp.snacks--;
        boolean evolved = oc.addBondXp(SNACK_BOND_XP);
        if (evolved) {
            return new AdventureState.ActionResult(
                true,
                "🌟 太棒了！「" + sp.name + "」吃下點心後進化為【" + sp.stageTitle(oc.stage) + "】！"
            );
        }
        return new AdventureState.ActionResult(
            true,
            "🍪 餵食「" + sp.name + "」成功！羈絆 +" + SNACK_BOND_XP + " XP（Lv." + oc.bondLevel() + "）"
        );
    }

    AdventureState.ActionResult petInCamp(int companionId, int bondXpGain, String today) {
        OwnedCompanion oc = owned.get(companionId);
        SpeciesDef sp = species(companionId);
        if (oc == null || sp == null) {
            return new AdventureState.ActionResult(false, "營地裡還沒有這位旅伴");
        }
        if (today != null && today.equals(oc.lastPetDate)) {
            return new AdventureState.ActionResult(true, "💕 「" + sp.name + "」開心地蹭了蹭你的手！（今日摸摸羈絆已領取）");
        }
        oc.lastPetDate = today != null ? today : "";
        boolean evolved = oc.addBondXp(Math.max(10, bondXpGain));
        if (evolved) {
            return new AdventureState.ActionResult(
                true,
                "💖 摸摸「" + sp.name + "」時牠發光進化成【" + sp.stageTitle(oc.stage) + "】了！"
            );
        }
        return new AdventureState.ActionResult(
            true,
            "💖 摸了摸「" + sp.name + "」，牠超開心！羈絆 +" + bondXpGain + " XP"
        );
    }

    boolean isAccessoryUnlocked(int accId) {
        if (accId == ACC_NONE) return true;
        if (accId < 0 || accId >= ACC_COUNT) return false;
        return (unlockedAccessoriesMask & (1 << accId)) != 0;
    }

    AdventureState.ActionResult buyOrEquipAccessory(int companionId, int accId, StepProgress progress, CampState camp) {
        OwnedCompanion oc = owned.get(companionId);
        SpeciesDef sp = species(companionId);
        if (oc == null || sp == null) {
            return new AdventureState.ActionResult(false, "請先選擇已解鎖的旅伴");
        }
        if (accId < 0 || accId >= ACC_COUNT) {
            return new AdventureState.ActionResult(false, "無效的飾品");
        }
        AccessoryDef def = accessoryDef(accId);
        if (isAccessoryUnlocked(accId)) {
            oc.accessory = accId;
            return new AdventureState.ActionResult(true, "🎀 已替「" + sp.name + "」換上「" + def.name + "」！");
        }
        if (camp == null || progress == null) {
            return new AdventureState.ActionResult(false, "目前無法製作飾品");
        }
        if (camp.buildingLevel(CampState.BLD_WORKSHOP) < def.minWorkshopLevel) {
            return new AdventureState.ActionResult(false, "需要「探險工坊」達 Lv." + def.minWorkshopLevel + " 才能製作「" + def.name + "」");
        }
        if (progress.coins < def.coinCost) {
            return new AdventureState.ActionResult(false, "金幣不足，製作需要 " + def.coinCost + " 金幣");
        }
        if (camp.starSand < def.starCost) {
            return new AdventureState.ActionResult(false, "星砂不足，製作需要 " + def.starCost + " 顆星砂");
        }
        progress.coins -= def.coinCost;
        camp.starSand -= def.starCost;
        unlockedAccessoriesMask |= (1 << accId);
        oc.accessory = accId;
        return new AdventureState.ActionResult(true, "✨ 工坊製作完成！「" + sp.name + "」戴上了「" + def.name + "」！");
    }

    /** 依進化階段計算阿福的每箱額外金幣：Lv1=+8, Lv2=+12, Lv3=+18（與舊版 Lv1=+8 100% 相容） */
    int shibaChestBonusPerChest() {
        if (equippedId != ID_SHIBA) return 0;
        int st = equippedStage();
        if (st >= 3) return 18;
        if (st == 2) return 12;
        return 8;
    }

    /** 依進化階段計算波波每日任務加乘百分比：25 / 32 / 40 */
    int owlDailyQuestBonusPercent() {
        if (equippedId != ID_OWL) return 0;
        int st = equippedStage();
        if (st >= 3) return 40;
        if (st == 2) return 32;
        return 25;
    }

    /** 依進化階段計算火龍走路 XP 加乘百分比：20 / 25 / 30 */
    int dragonXpBonusPercent() {
        if (equippedId != ID_DRAGON) return 0;
        int st = equippedStage();
        if (st >= 3) return 30;
        if (st == 2) return 25;
        return 20;
    }

    /** 依進化階段計算史萊姆奇遇額外 XP：20 / 35 / 50 */
    int slimeEncounterBonusXp() {
        if (equippedId != ID_SLIME) return 0;
        int st = equippedStage();
        if (st >= 3) return 50;
        if (st == 2) return 35;
        return 20;
    }

    List<OwnedCompanion> ownedList() {
        return Collections.unmodifiableList(new ArrayList<>(owned.values()));
    }
}
