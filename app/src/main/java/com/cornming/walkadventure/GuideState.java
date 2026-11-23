package com.cornming.walkadventure;

/**
 * 繪本風「說明引導」狀態與內容目錄（純 Java，100% 可單測）：
 * 1) 4 頁互動新手繪本導覽（首次啟動自動呈現，亦可隨時點「？」重看）
 * 2) 四分頁情境式小幫手提示（每頁首次造訪顯示一次，關閉後不再打擾）
 * 3) 8 項功能快速說明手冊（步數規則、雙軌模式、營火盒、孵蛋、菜園、相機明信片、巨獸與備份）
 */
final class GuideState {
    static final int TIP_ADVENTURE = 0;
    static final int TIP_CAMP = 1;
    static final int TIP_JOURNAL = 2;
    static final int TIP_SQUAD = 3;
    static final int TIP_COUNT = 4;

    static final class OnboardingPage {
        final int index;
        final String emoji;
        final String title;
        final String subtitle;
        final String body;
        final int accentRgb;

        OnboardingPage(int index, String emoji, String title, String subtitle, String body, int accentRgb) {
            this.index = index;
            this.emoji = emoji;
            this.title = title;
            this.subtitle = subtitle;
            this.body = body;
            this.accentRgb = accentRgb;
        }
    }

    static final class TabTip {
        final int bitIndex;
        final String title;
        final String message;

        TabTip(int bitIndex, String title, String message) {
            this.bitIndex = bitIndex;
            this.title = title;
            this.message = message;
        }
    }

    static final class HelpTopic {
        final String icon;
        final String title;
        final String body;

        HelpTopic(String icon, String title, String body) {
            this.icon = icon;
            this.title = title;
            this.body = body;
        }
    }

    static final OnboardingPage[] ONBOARDING_PAGES = {
        new OnboardingPage(
            0,
            "☀️🎈",
            "平日蓄積 × 假日親子共玩",
            "同一台手機，大人孩子一起寫冒險故事",
            "平日上班通勤走滿 1,000 步，寶箱與神祕蛋會自動收進「🔥 營火盒」；晚上回家或假日出門，只要點頂部切換「🎈 親子出任務」，就能全家一起開箱享加成！",
            0xFFE07A3F
        ),
        new OnboardingPage(
            1,
            "⛺🥚",
            "秘密營地 × 孵蛋進化 × 菜園",
            "每一步都同時在孵蛋、澆水與蓋基地",
            "到「營地」頁可以直接點擊畫布互動：點 🔥 營火開箱、點 🐾 旅伴摸摸冒愛心、點 🥚 蛋巢幫蛋暖暖身加速破殼、點 🌱 花圃播種，走路就會自動幫花圃澆水採收點心！",
            0xFF5FA865
        ),
        new OnboardingPage(
            2,
            "📸📮",
            "實境相機尋寶 × 冒險明信片",
            "把散步風景、孩子塗鴉與童言錄音珍藏下來",
            "帶孩子出門時找找現實世界的「🌈 彩虹七色」或可愛雲朵，拍完照一起打星、讓孩子手指塗鴉並錄 15 秒語音便條，立刻合成繪本明信片！所有照片與錄音全存本機絕不上傳雲端。",
            0xFF4D8EDB
        ),
        new OnboardingPage(
            3,
            "🎒🌿",
            "口袋散步不低頭・準備出發！",
            "牽手看世界，抵達休息點再一起看驚喜",
            "出門前點頂部「🎒 口袋」收進口袋安心散步，途中所有步數、孵蛋與寶箱都會自動收集。記得也可以到「小隊」頁幫爸媽與孩子建專屬小隊護照（填生日還有慶生驚喜喔）！",
            0xFF8C63B8
        )
    };

    static final TabTip[] TAB_TIPS = {
        new TabTip(
            TIP_ADVENTURE,
            "💡 探險小幫手",
            "點頂部可隨時切換「平日模式／親子出任務」；往下滑有實境相機尋寶、島嶼守護巨獸試煉、連貫繪本故事與線下抬頭任務卡！"
        ),
        new TabTip(
            TIP_CAMP,
            "💡 營地互動指南",
            "上方的繪本營地可以直接點擊互動喔！點 🔥 營火開箱、點 🐾 旅伴摸摸賺羈絆、點 🥚 蛋巢暖蛋破殼、點 🌱 空花圃播種（走路就會自動澆水）。"
        ),
        new TabTip(
            TIP_JOURNAL,
            "💡 手札與彩虹圖鑑指南",
            "點彩虹七色的任一顆色珠就能立刻開拍該色尋寶，集滿七色送花瓣奇蹟蛋！拍好的明信片點擊即可全螢幕賞圖、聽孩子錄音或分享給家人。"
        ),
        new TabTip(
            TIP_SQUAD,
            "💡 家庭小隊護照指南",
            "同一台手機可建立 1～6 位家人小隊卡並勾選今天一起出門的成員；填寫生日當天營地會掛滿彩旗送生日禮！最下方也支援離線 JSON 備份。"
        )
    };

    static final HelpTopic[] HELP_TOPICS = {
        new HelpTopic("👣", "真實步數怎麼計算？",
            "App 直接讀取手機硬體計步感測器（TYPE_STEP_COUNTER），關著 App 走路也會在下次開啟時一次補發寶箱、孵蛋與菜園澆水進度；裝備與旅伴提供的是「冒險 XP」加成，絕不灌水真實步數。"),
        new HelpTopic("🔥", "平日模式與「營火開箱」有什麼差別？",
            "在「平日模式」下（且開啟自動鎖存），每 1,000 步寶箱與撿到的蛋會暫存到營火盒；切換成「🎈 親子出任務」再點營火開箱，可獲得 +10%～+34% 金幣與額外星砂加成，適合晚上或假日跟孩子一起戳破驚喜！"),
        new HelpTopic("🥚", "12 位旅伴怎麼取得與進化？",
            "除了 4 位元老旅伴可直接招募，散步開寶箱、讀完 3 話連貫故事、安撫島嶼巨獸與收成彩虹蘿蔔都會撿到不同生態／時段神祕蛋。走路＋每日點「💛 親子暖蛋」可孵化；餵食菜園點心提升羈絆至 Lv.4 與 Lv.8 會進化成更帥氣的 2／3 階段形態！"),
        new HelpTopic("🌱", "奇幻小菜園怎麼澆水？",
            "升級奇幻小菜園最多可開 3 格花圃。用金幣買種子種下後，你每走一步就會自動替所有花圃澆水；成熟採收可獲得大量旅伴點心與星砂，彩虹蘿蔔還有機會挖出花瓣神祕蛋。"),
        new HelpTopic("📸", "相機尋寶與隱私安全嗎？",
            "呼叫手機內建相機拍照（免申請相機權限）或從相簿挑圖，本機以 HSV 演算法分析顏色並交由大人小孩一起評 1～3 顆星。所有照片、孩子手指塗鴉與 15 秒語音便條皆只存在手機本機 App 私有目錄，絕不上傳伺服器。"),
        new HelpTopic("🐲", "島嶼守護巨獸怎麼安撫？",
            "每座島都有一位可愛守護巨獸，只要「累積散步 2,000 步」或「完成 1 次親子抬頭任務／相機尋寶」二選一即可溫柔安撫，領取大量金幣、星砂與該島稀有神祕蛋！"),
        new HelpTopic("🎒", "「口袋模式」有什麼用？",
            "怕孩子出門一直盯著手機看？點頂部「🎒 口袋」啟動防低頭遮罩後把手機放進口袋，安心牽手看風景；抵達公園長椅或回到家再點結束，一次揭曉剛剛收集了幾步！"),
        new HelpTopic("🎁", "NFC／離線 QR 實體藏寶怎麼玩？",
            "到「手札」頁點「實體藏寶盒」，大人可拿家裡閒置悠遊卡／NFC 貼紙靠背感應，或直接產生可列印的離線 QR 藏寶卡並寫下謎語；把卡片藏在家裡或公園，孩子找到後「嗶！」一下 NFC 或輸入短碼就能炸開寶箱！"),
        new HelpTopic("💌", "離線 QR 家族禮物卡與足跡迷霧怎麼用？",
            "在「小隊」頁可產生離線 QR 禮物卡分享給另一支家人手機，領取後對方的進化旅伴還會飛來你的營地串門子一整天！開啟「足跡迷霧」則只用 200m 粗略相對格記錄新公園首探寶箱，也能手動打卡命名家庭秘密基地。"),
        new HelpTopic("🔬", "自然百科圖鑑與年度長圖冊是什麼？",
            "每次相機尋寶除了偵測彩虹色，端側視覺引擎還會自動鑑定花朵、樹木、藍天白雲、動物、夕陽、水岸、街景與星空 8 大自然類別，贈送自然小知識與稀有素材；到「手札」頁一鍵即可合成全家年度回憶長圖冊分享收藏！"),
        new HelpTopic("💾", "換手機怎麼保留資料？",
            "在「小隊」分頁最下方可一鍵複製 2.0 營地備份 JSON，貼上到新手機即可還原家庭成員、營地建築、旅伴與故事紀錄。")
    };

    boolean onboardingCompleted = false;
    int seenTipsMask = 0;

    boolean shouldShowOnboarding() {
        return !onboardingCompleted;
    }

    void markOnboardingCompleted() {
        onboardingCompleted = true;
    }

    boolean shouldShowTabTip(int tipIndex) {
        if (tipIndex < 0 || tipIndex >= TIP_COUNT) return false;
        return (seenTipsMask & (1 << tipIndex)) == 0;
    }

    void dismissTabTip(int tipIndex) {
        if (tipIndex < 0 || tipIndex >= TIP_COUNT) return;
        seenTipsMask |= (1 << tipIndex);
    }

    void resetAllGuides() {
        onboardingCompleted = false;
        seenTipsMask = 0;
    }

    static TabTip tabTip(int tipIndex) {
        if (tipIndex < 0 || tipIndex >= TAB_TIPS.length) return TAB_TIPS[0];
        return TAB_TIPS[tipIndex];
    }
}
