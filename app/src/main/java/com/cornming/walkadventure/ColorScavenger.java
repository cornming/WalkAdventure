package com.cornming.walkadventure;

/**
 * 現實世界相機尋寶任務目錄與純 Java 本機 HSV 色彩分析引擎（100% 離線、零外部套件）：
 * 支援 7 色「彩虹尋寶收集冊」與 5 項「親子自然觀察拍照任務」，搭配親子裁判打星機制。
 */
final class ColorScavenger {
    static final int COLOR_ANY = -1;
    static final int COLOR_RED = 0;
    static final int COLOR_ORANGE = 1;
    static final int COLOR_YELLOW = 2;
    static final int COLOR_GREEN = 3;
    static final int COLOR_BLUE = 4;
    static final int COLOR_PURPLE = 5;
    static final int COLOR_WHITE = 6;
    static final int RAINBOW_COUNT = 7;
    static final int RAINBOW_COMPLETE_MASK = (1 << RAINBOW_COUNT) - 1;

    static final int MATCH_THRESHOLD_PCT = 6;
    static final int STRONG_MATCH_PCT = 18;

    static final class HuntDef {
        final int id;
        final int targetColor; // 0..6 對應彩虹七色，-1 表示自然主題觀察
        final String title;
        final String prompt;
        final String colorLabel;
        final int badgeRgb;
        final int baseCoins;
        final int baseStarSand;

        HuntDef(
            int id,
            int targetColor,
            String title,
            String prompt,
            String colorLabel,
            int badgeRgb,
            int baseCoins,
            int baseStarSand
        ) {
            this.id = id;
            this.targetColor = targetColor;
            this.title = title;
            this.prompt = prompt;
            this.colorLabel = colorLabel;
            this.badgeRgb = badgeRgb;
            this.baseCoins = baseCoins;
            this.baseStarSand = baseStarSand;
        }

        boolean isRainbowColorHunt() {
            return targetColor >= 0 && targetColor < RAINBOW_COUNT;
        }
    }

    static final class AnalysisResult {
        final int dominantHue;        // 0..359，若以中性色／白色為主則為 -1
        final int dominantBucket;     // 0..6 或 -1
        final String dominantColorName;
        final int targetMatchPercent; // 0..100
        final boolean targetMatched;
        final int suggestedStars;     // 1..3
        final String feedbackText;

        AnalysisResult(
            int dominantHue,
            int dominantBucket,
            String dominantColorName,
            int targetMatchPercent,
            boolean targetMatched,
            int suggestedStars,
            String feedbackText
        ) {
            this.dominantHue = dominantHue;
            this.dominantBucket = dominantBucket;
            this.dominantColorName = dominantColorName;
            this.targetMatchPercent = targetMatchPercent;
            this.targetMatched = targetMatched;
            this.suggestedStars = Math.max(1, Math.min(3, suggestedStars));
            this.feedbackText = feedbackText;
        }
    }

    static final String[] RAINBOW_NAMES = {
        "熱情紅",
        "暖陽橘",
        "向日葵黃",
        "森活綠",
        "晴空藍",
        "魔法紫",
        "雲朵白"
    };

    static final int[] RAINBOW_RGB = {
        0xFFE05252,
        0xFFED8B3B,
        0xFFF2C94C,
        0xFF5FA865,
        0xFF4D8EDB,
        0xFF9B6CD6,
        0xFFF3EFE6
    };

    static final HuntDef[] HUNTS = {
        new HuntDef(1, COLOR_RED, "尋找「熱情紅」", "找路邊最亮眼的紅色！花朵、招牌、消防栓或果實都可以。", "熱情紅", RAINBOW_RGB[0], 35, 16),
        new HuntDef(2, COLOR_ORANGE, "尋找「暖陽橘」", "拍下溫暖的橘色風景：夕陽、橘貓、橘色看板或小花。", "暖陽橘", RAINBOW_RGB[1], 35, 16),
        new HuntDef(3, COLOR_YELLOW, "尋找「向日葵黃」", "尋找像太陽一樣的黃色：花瓣、計程車、黃色標誌或落葉。", "向日葵黃", RAINBOW_RGB[2], 35, 16),
        new HuntDef(4, COLOR_GREEN, "尋找「森活綠」", "拍下公園裡最有生命力的綠樹、草地或嫩芽！", "森活綠", RAINBOW_RGB[3], 30, 14),
        new HuntDef(5, COLOR_BLUE, "尋找「晴空藍」", "抬頭找找藍天、水面倒影、藍色郵筒或可愛招牌。", "晴空藍", RAINBOW_RGB[4], 35, 16),
        new HuntDef(6, COLOR_PURPLE, "尋找「魔法紫」", "街角隱藏的紫色最稀有！紫色花朵、裝飾或彩霞都算。", "魔法紫", RAINBOW_RGB[5], 45, 20),
        new HuntDef(7, COLOR_WHITE, "尋找「雲朵白」", "拍一朵蓬蓬白雲、小白花或乾淨明亮的白色建築！", "雲朵白", RAINBOW_RGB[6], 35, 16),
        new HuntDef(8, COLOR_ANY, "雲朵動物園", "拍下一朵形狀像小動物、恐龍或點心的天空雲朵！", "自然觀察", 0xFF7FB3E6, 38, 18),
        new HuntDef(9, COLOR_ANY, "最特別的葉子或花", "近拍一片形狀最奇特的落葉或路邊綻放的小花。", "自然觀察", 0xFF70AF74, 38, 18),
        new HuntDef(10, COLOR_ANY, "遇見動物好鄰居", "輕輕遠拍散步途中遇見的小狗、貓咪、小鳥或松鼠。", "動物觀察", 0xFFE09652, 42, 20),
        new HuntDef(11, COLOR_ANY, "光影魔術師", "拍下陽光穿過樹葉或欄杆形成的有趣光影圖案！", "光影發現", 0xFFE8B94A, 40, 18),
        new HuntDef(12, COLOR_ANY, "全家散步紀念合照", "留下今天出隊成員一起微笑或做鬼臉的美好瞬間！", "親子回憶", 0xFFD65C7A, 45, 22)
    };

    private ColorScavenger() {}

    static HuntDef huntById(int id) {
        for (HuntDef h : HUNTS) {
            if (h.id == id) return h;
        }
        return HUNTS[0];
    }

    static HuntDef rainbowHuntForColor(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= RAINBOW_COUNT) return HUNTS[0];
        return HUNTS[colorIndex];
    }

    static String colorName(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= RAINBOW_COUNT) return "自然暖色";
        return RAINBOW_NAMES[colorIndex];
    }

    /** 挑選下一個尚未收集到的彩虹色任務；若七色皆滿則輪播全部 12 題。 */
    static HuntDef recommendedHunt(int rainbowMask, int seedNumber) {
        for (int i = 0; i < RAINBOW_COUNT; i++) {
            if ((rainbowMask & (1 << i)) == 0) {
                return HUNTS[i];
            }
        }
        int idx = Math.abs(seedNumber) % HUNTS.length;
        return HUNTS[idx];
    }

    /**
     * 純 Java RGB 轉 HSV：out[0]=hue (0..360), out[1]=saturation (0..1), out[2]=value (0..1)。
     */
    static void rgbToHsv(int r, int g, int b, float[] out) {
        float rf = (r & 0xFF) / 255f;
        float gf = (g & 0xFF) / 255f;
        float bf = (b & 0xFF) / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float h;
        if (delta < 0.0001f) {
            h = 0f;
        } else if (max == rf) {
            h = 60f * (((gf - bf) / delta) % 6f);
        } else if (max == gf) {
            h = 60f * (((bf - rf) / delta) + 2f);
        } else {
            h = 60f * (((rf - gf) / delta) + 4f);
        }
        if (h < 0f) h += 360f;

        float s = max < 0.0001f ? 0f : (delta / max);
        out[0] = h;
        out[1] = s;
        out[2] = max;
    }

    /** 將單一 HSV 像素歸類到彩虹 7 色桶（0..6），中性灰／暗黑回傳 -1。 */
    static int classifyHsvBucket(float h, float s, float v) {
        if (s <= 0.18f && v >= 0.72f) {
            return COLOR_WHITE;
        }
        if (s < 0.18f || v < 0.16f) {
            return -1;
        }
        float hue = ((h % 360f) + 360f) % 360f;
        if (hue >= 338f || hue < 18f) {
            return (s >= 0.28f && v >= 0.22f) ? COLOR_RED : -1;
        }
        if (hue < 44f) {
            return (s >= 0.30f && v >= 0.24f) ? COLOR_ORANGE : -1;
        }
        if (hue < 72f) {
            return (s >= 0.26f && v >= 0.28f) ? COLOR_YELLOW : -1;
        }
        if (hue < 168f) {
            return COLOR_GREEN;
        }
        if (hue < 255f) {
            return COLOR_BLUE;
        }
        return COLOR_PURPLE;
    }

    /**
     * 分析取樣 ARGB 像素陣列的主色分布並比對目標色。
     */
    static AnalysisResult analyzeRgbPixels(int[] argbPixels, int targetColor) {
        if (argbPixels == null || argbPixels.length == 0) {
            return new AnalysisResult(-1, -1, "自然色", 0, targetColor == COLOR_ANY, 2,
                "已收下這張探險照片！請和孩子一起評選星等吧！");
        }

        int[] bucketCounts = new int[RAINBOW_COUNT];
        int[] hueBins = new int[36]; // 每 10 度一格
        int chromaticCount = 0;
        float[] hsv = new float[3];

        for (int argb : argbPixels) {
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            rgbToHsv(r, g, b, hsv);
            int bucket = classifyHsvBucket(hsv[0], hsv[1], hsv[2]);
            if (bucket >= 0 && bucket < RAINBOW_COUNT) {
                bucketCounts[bucket]++;
            }
            if (hsv[1] >= 0.22f && hsv[2] >= 0.20f) {
                int bin = Math.min(35, Math.max(0, (int) (hsv[0] / 10f)));
                hueBins[bin]++;
                chromaticCount++;
            }
        }

        int total = argbPixels.length;
        int bestBucket = -1;
        int bestBucketCount = 0;
        for (int i = 0; i < RAINBOW_COUNT; i++) {
            if (bucketCounts[i] > bestBucketCount) {
                bestBucketCount = bucketCounts[i];
                bestBucket = i;
            }
        }

        int dominantHue = -1;
        if (chromaticCount > total / 12) {
            int bestBin = 0;
            for (int i = 1; i < hueBins.length; i++) {
                if (hueBins[i] > hueBins[bestBin]) bestBin = i;
            }
            dominantHue = bestBin * 10 + 5;
        }
        String domName = bestBucket >= 0 ? RAINBOW_NAMES[bestBucket] : "自然暖色";

        if (targetColor < 0 || targetColor >= RAINBOW_COUNT) {
            String fb = "✨ 照片主色像「" + domName + "」！請大人小孩一起給這次發現評分吧！";
            return new AnalysisResult(dominantHue, bestBucket, domName, 100, true, 3, fb);
        }

        int targetPct = Math.round((bucketCounts[targetColor] * 100f) / total);
        boolean matched = targetPct >= MATCH_THRESHOLD_PCT;
        int stars;
        String fb;
        String targetName = RAINBOW_NAMES[targetColor];
        if (targetPct >= STRONG_MATCH_PCT) {
            stars = 3;
            fb = "🌈 超級精準！偵測到滿滿的「" + targetName + "」（" + targetPct + "%），推薦 ★★★！";
        } else if (matched) {
            stars = 2;
            fb = "🌈 找到了！照片裡藏著「" + targetName + "」（" + targetPct + "%），也歡迎親子手動調星！";
        } else {
            stars = 1;
            fb = "🔎 相機覺得主色偏「" + domName + "」（" + targetName + " " + targetPct
                + "%），但只要你們覺得找到了，一樣可以給滿星喔！";
        }
        return new AnalysisResult(dominantHue, bestBucket, domName, targetPct, matched, stars, fb);
    }
}
