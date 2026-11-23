package com.cornming.walkadventure;

/**
 * Phase 4 端側純 Java 自然生態視覺鑑定引擎與《自然百科圖鑑》（零外部套件、100% 離線）：
 * - 分析取樣像素的空間分區（天空／中景／地面）、HSV 色彩分布、邊緣紋理密度與明暗對比
 * - 自動鑑定 8 大自然觀察類別，附上親子自然百科小知識、自然學家鑑定章與稀有素材掉落加成
 * - 支援家長與孩子一鍵微調分類，兼顧 AI 驚喜感與零挫折親子共玩體驗！
 */
final class NatureVisionEngine {
    static final int CAT_FLOWER = 0;       // 🌺 花朵綻放
    static final int CAT_TREE_LEAF = 1;    // 🌿 綠樹枝葉
    static final int CAT_SKY_CLOUD = 2;    // ☁️ 藍天白雲
    static final int CAT_ANIMAL = 3;       // 🐾 萌寵動物
    static final int CAT_SUNSET_LIGHT = 4; // 🌅 暖陽夕彩
    static final int CAT_WATER = 5;        // 🌊 水岸倒影
    static final int CAT_CITY_STREET = 6;  // 🚲 街角與交通
    static final int CAT_NIGHT_STAR = 7;   // 🌌 星空夜色
    static final int CATEGORY_COUNT = 8;
    static final int COMPLETE_MASK = (1 << CATEGORY_COUNT) - 1;

    static final class CategoryDef {
        final int id;
        final String emoji;
        final String name;
        final String badgeTitle;
        final String trivia;
        final String dropSummary;
        final int accentRgb;
        final int bonusStarSand;
        final int bonusWood;
        final int bonusPetals;
        final int bonusSnacks;
        final int bonusCoins;

        CategoryDef(
            int id,
            String emoji,
            String name,
            String badgeTitle,
            String trivia,
            String dropSummary,
            int accentRgb,
            int bonusStarSand,
            int bonusWood,
            int bonusPetals,
            int bonusSnacks,
            int bonusCoins
        ) {
            this.id = id;
            this.emoji = emoji;
            this.name = name;
            this.badgeTitle = badgeTitle;
            this.trivia = trivia;
            this.dropSummary = dropSummary;
            this.accentRgb = accentRgb;
            this.bonusStarSand = bonusStarSand;
            this.bonusWood = bonusWood;
            this.bonusPetals = bonusPetals;
            this.bonusSnacks = bonusSnacks;
            this.bonusCoins = bonusCoins;
        }
    }

    static final class Classification {
        final int categoryId;
        final int confidencePercent; // 45..96
        final CategoryDef category;
        final String summaryLine;

        Classification(int categoryId, int confidencePercent, CategoryDef category, String summaryLine) {
            this.categoryId = categoryId;
            this.confidencePercent = confidencePercent;
            this.category = category;
            this.summaryLine = summaryLine;
        }
    }

    static final CategoryDef[] CATEGORIES = {
        new CategoryDef(
            CAT_FLOWER, "🌺", "花朵綻放", "花草學家章",
            "小知識：鮮豔的花瓣就像大自然的大看板，專門邀請蜜蜂和蝴蝶來幫忙傳粉喔！",
            "🌸 花瓣 +3、✨ 星砂 +8", 0xFFE05A7A, 8, 0, 3, 0, 0
        ),
        new CategoryDef(
            CAT_TREE_LEAF, "🌿", "綠樹枝葉", "森林觀察章",
            "小知識：葉子裡的葉綠素能把陽光和水變成樹木的點心，同時放出新鮮氧氣給我們呼吸！",
            "🪵 木頭 +3、✨ 星砂 +8", 0xFF4D965A, 8, 3, 0, 0, 0
        ),
        new CategoryDef(
            CAT_SKY_CLOUD, "☁️", "藍天白雲", "氣象探險章",
            "小知識：白雲其實是由千千萬萬顆超細小的水滴與冰晶集合起來，輕飄飄浮在空中的！",
            "✨ 星砂 +12", 0xFF4D8EDB, 12, 0, 0, 0, 0
        ),
        new CategoryDef(
            CAT_ANIMAL, "🐾", "萌寵動物", "動物之友章",
            "小知識：很多小動物的耳朵和鼻子都比人類靈敏好幾倍，輕聲觀察才不會嚇到牠們喔！",
            "🍪 旅伴點心 +1、✨ 星砂 +8", 0xFFD98A3C, 8, 0, 0, 1, 0
        ),
        new CategoryDef(
            CAT_SUNSET_LIGHT, "🌅", "暖陽夕彩", "追光旅人章",
            "小知識：傍晚陽光斜斜穿過較厚的大氣層，藍光散開後就留下溫暖浪漫的橘紅色夕陽！",
            "✨ 星砂 +12、🪙 金幣 +15", 0xFFEB743B, 12, 0, 0, 0, 15
        ),
        new CategoryDef(
            CAT_WATER, "🌊", "水岸倒影", "水文偵探章",
            "小知識：平靜的水面就像一面天然鏡子，會把天空、雲朵和岸邊的大樹通通映出來！",
            "✨ 星砂 +10、🪙 金幣 +15", 0xFF3E9AB8, 10, 0, 0, 0, 15
        ),
        new CategoryDef(
            CAT_CITY_STREET, "🚲", "街角與交通", "城市漫步章",
            "小知識：用雙腳散步不但能發現平時坐車沒注意的可愛街角，還能幫忙地球減少碳排放！",
            "🪙 金幣 +25、✨ 星砂 +6", 0xFF7B6F99, 6, 0, 0, 0, 25
        ),
        new CategoryDef(
            CAT_NIGHT_STAR, "🌌", "星空夜色", "星夜守望章",
            "小知識：晴朗的夜晚仰望天空，每一顆閃亮的星星幾乎都是一座遠在好幾光年外的太陽！",
            "✨ 星砂 +15", 0xFF45579B, 15, 0, 0, 0, 0
        )
    };

    static final class DexState {
        int unlockedMask = 0;
        final int[] categoryCounts = new int[CATEGORY_COUNT];
        int encyclopediaCompletedSets = 0;

        boolean isUnlocked(int catId) {
            if (catId < 0 || catId >= CATEGORY_COUNT) return false;
            return (unlockedMask & (1 << catId)) != 0;
        }

        int unlockedCount() {
            return Integer.bitCount(unlockedMask & COMPLETE_MASK);
        }

        int countFor(int catId) {
            if (catId < 0 || catId >= CATEGORY_COUNT) return 0;
            return categoryCounts[catId];
        }

        /** 記錄一次鑑定，回傳是否為「首次解鎖此自然類別」。 */
        boolean recordCategory(int catId) {
            if (catId < 0 || catId >= CATEGORY_COUNT) return false;
            boolean firstTime = !isUnlocked(catId);
            unlockedMask |= (1 << catId);
            categoryCounts[catId]++;
            return firstTime;
        }

        boolean isComplete() {
            return (unlockedMask & COMPLETE_MASK) == COMPLETE_MASK;
        }
    }

    private NatureVisionEngine() {}

    static CategoryDef category(int catId) {
        if (catId < 0 || catId >= CATEGORIES.length) return CATEGORIES[CAT_CITY_STREET];
        return CATEGORIES[catId];
    }

    /**
     * 純 Java 多特徵端側影像鑑定：
     * 分析空間分區（上／中／下）、HSV 色域占比、中心主體突顯度與簡化 Sobel 邊緣密度。
     */
    static Classification classifyPixels(int[] argbPixels, int width, int height) {
        if (argbPixels == null || argbPixels.length == 0 || width <= 0 || height <= 0) {
            CategoryDef fallback = CATEGORIES[CAT_CITY_STREET];
            return new Classification(fallback.id, 55, fallback, "🔬 自然鑑定：" + fallback.emoji + " " + fallback.name);
        }

        int total = Math.min(argbPixels.length, width * height);
        float[] hsv = new float[3];

        int darkCount = 0;
        int brightHighlightCount = 0;
        int greenCount = 0;
        int floralCount = 0;
        int floralCenterCount = 0;
        int topBlueOrWhite = 0;
        int topTotal = 0;
        int bottomBlueOrCyan = 0;
        int bottomTotal = 0;
        int warmSunsetCount = 0;
        int warmFurCount = 0;
        int neutralGrayCount = 0;
        int centerTotal = 0;

        float[] luma = new float[total];
        int topLimit = Math.max(1, height / 3);
        int bottomStart = Math.max(1, (height * 2) / 3);
        int cx0 = width / 4;
        int cx1 = (width * 3) / 4;
        int cy0 = height / 4;
        int cy1 = (height * 3) / 4;

        for (int y = 0; y < height; y++) {
            int rowOff = y * width;
            for (int x = 0; x < width; x++) {
                int idx = rowOff + x;
                if (idx >= total) break;
                int argb = argbPixels[idx];
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                luma[idx] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

                ColorScavenger.rgbToHsv(r, g, b, hsv);
                float h = hsv[0];
                float s = hsv[1];
                float v = hsv[2];
                boolean inCenter = x >= cx0 && x < cx1 && y >= cy0 && y < cy1;
                if (inCenter) centerTotal++;

                if (v < 0.22f) darkCount++;
                if (v > 0.86f && s < 0.35f) brightHighlightCount++;

                // 綠樹枝葉
                if (h >= 72f && h <= 165f && s >= 0.22f && v >= 0.18f) {
                    greenCount++;
                }

                // 鮮豔花瓣（粉紅／紅／洋紅／亮黃／紫）
                boolean isFloral = ((h >= 305f || h < 20f) && s >= 0.38f && v >= 0.35f)
                    || (h >= 265f && h < 305f && s >= 0.32f && v >= 0.32f)
                    || (h >= 46f && h < 68f && s >= 0.45f && v >= 0.48f);
                if (isFloral) {
                    floralCount++;
                    if (inCenter) floralCenterCount++;
                }

                // 暖陽夕彩（橘金暖色高亮度）
                if (h >= 12f && h <= 48f && s >= 0.36f && v >= 0.45f) {
                    warmSunsetCount++;
                }

                // 暖褐／米棕／毛髮色調（常見於貓狗鳥類動物）
                if (h >= 16f && h <= 46f && s >= 0.18f && s <= 0.62f && v >= 0.25f && v <= 0.78f && inCenter) {
                    warmFurCount++;
                }

                // 中性灰白建築／柏油路
                if (s < 0.18f && v >= 0.22f && v <= 0.78f) {
                    neutralGrayCount++;
                }

                // 上方天空區
                if (y < topLimit) {
                    topTotal++;
                    boolean skyBlue = h >= 185f && h <= 245f && s >= 0.20f && v >= 0.42f;
                    boolean cloudWhite = s <= 0.20f && v >= 0.74f;
                    if (skyBlue || cloudWhite) topBlueOrWhite++;
                }

                // 下方水岸區
                if (y >= bottomStart) {
                    bottomTotal++;
                    boolean waterHue = h >= 170f && h <= 240f && s >= 0.18f && v >= 0.24f;
                    if (waterHue) bottomBlueOrCyan++;
                }
            }
        }

        // 計算邊緣紋理密度（分辨自然平滑天空 vs 樹葉微細紋理 vs 幾何街景）
        float edgeSum = 0f;
        int edgeSamples = 0;
        for (int y = 1; y < height - 1; y++) {
            int rowOff = y * width;
            for (int x = 1; x < width - 1; x++) {
                int idx = rowOff + x;
                if (idx + width >= total) break;
                float gx = Math.abs(luma[idx + 1] - luma[idx - 1]);
                float gy = Math.abs(luma[idx + width] - luma[idx - width]);
                edgeSum += (gx + gy);
                edgeSamples++;
            }
        }
        float avgEdge = edgeSamples > 0 ? (edgeSum / edgeSamples) : 0f;

        float fDark = darkCount / (float) total;
        float fHighlight = brightHighlightCount / (float) total;
        float fGreen = greenCount / (float) total;
        float fFloral = floralCount / (float) total;
        float fFloralCenter = centerTotal > 0 ? (floralCenterCount / (float) centerTotal) : 0f;
        float fTopSky = topTotal > 0 ? (topBlueOrWhite / (float) topTotal) : 0f;
        float fBottomWater = bottomTotal > 0 ? (bottomBlueOrCyan / (float) bottomTotal) : 0f;
        float fSunset = warmSunsetCount / (float) total;
        float fWarmFur = centerTotal > 0 ? (warmFurCount / (float) centerTotal) : 0f;
        float fNeutral = neutralGrayCount / (float) total;

        float[] scores = new float[CATEGORY_COUNT];
        scores[CAT_NIGHT_STAR] = fDark * 1.65f + (fDark > 0.42f ? fHighlight * 2.0f : 0f);
        scores[CAT_TREE_LEAF] = fGreen * 1.55f + (fGreen > 0.20f ? Math.min(0.25f, avgEdge) : 0f);
        scores[CAT_FLOWER] = fFloral * 1.45f + fFloralCenter * 0.95f + (fGreen > 0.12f && fFloral > 0.10f ? 0.35f : 0f);
        scores[CAT_SKY_CLOUD] = fTopSky * 1.15f + (avgEdge < 0.14f ? 0.25f : 0f) - fBottomWater * 0.4f;
        scores[CAT_WATER] = fBottomWater * 1.75f + (fTopSky > 0.35f && fBottomWater > 0.22f ? 0.35f : 0f);
        scores[CAT_SUNSET_LIGHT] = fSunset * 1.65f + (fTopSky < 0.30f && fSunset > 0.25f ? 0.25f : 0f);
        scores[CAT_ANIMAL] = fWarmFur * 1.55f + (fWarmFur > 0.24f && avgEdge > 0.08f ? 0.28f : 0f);
        scores[CAT_CITY_STREET] = fNeutral * 1.10f + Math.min(0.45f, avgEdge * 1.4f);

        int bestCat = CAT_CITY_STREET;
        float bestScore = -1f;
        float secondScore = -1f;
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            if (scores[i] > bestScore) {
                secondScore = bestScore;
                bestScore = scores[i];
                bestCat = i;
            } else if (scores[i] > secondScore) {
                secondScore = scores[i];
            }
        }

        float margin = Math.max(0f, bestScore - Math.max(0f, secondScore));
        int confidence = Math.max(58, Math.min(96, Math.round(62f + bestScore * 22f + margin * 18f)));
        CategoryDef def = CATEGORIES[bestCat];
        String line = "🔬 自然鑑定：" + def.emoji + " " + def.name + "（信心度 " + confidence + "%）";
        return new Classification(bestCat, confidence, def, line);
    }
}
