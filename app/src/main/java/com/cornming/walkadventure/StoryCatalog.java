package com.cornming.walkadventure;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 連貫繪本故事奇遇（10 條故事線共 30 話）、5 座島嶼守護巨獸安撫試煉、
 * 24 張分齡線下「親子抬頭任務卡」與四季印章護照（純 Java）。
 */
final class StoryCatalog {
    static final int ARC_COUNT = 10;
    static final int CHAPTERS_PER_ARC = 3;
    static final int BOSS_REQUIRED_STEPS = 2000;
    static final int SEASON_STAMP_COUNT = 8;
    static final int MAX_RECENT_NOTES = 40;
    static final int MAX_MEMORIES = 80;

    static final String AGE_ALL = "ALL";
    static final String AGE_PRESCHOOL = "PRESCHOOL";     // 學齡前～低年級
    static final String AGE_ELEMENTARY = "ELEMENTARY";   // 國小中高年級＋大人

    static final class ChapterDef {
        final int chapterNumber; // 1..3
        final String title;
        final String prompt;
        final String choiceA;
        final String choiceB;
        final String outcomeA;
        final String outcomeB;
        final int coinsA;
        final int starA;
        final int bondA;
        final int coinsB;
        final int starB;
        final int bondB;

        ChapterDef(
            int chapterNumber,
            String title,
            String prompt,
            String choiceA,
            String choiceB,
            String outcomeA,
            String outcomeB,
            int coinsA,
            int starA,
            int bondA,
            int coinsB,
            int starB,
            int bondB
        ) {
            this.chapterNumber = chapterNumber;
            this.title = title;
            this.prompt = prompt;
            this.choiceA = choiceA;
            this.choiceB = choiceB;
            this.outcomeA = outcomeA;
            this.outcomeB = outcomeB;
            this.coinsA = coinsA;
            this.starA = starA;
            this.bondA = bondA;
            this.coinsB = coinsB;
            this.starB = starB;
            this.bondB = bondB;
        }
    }

    static final class ArcDef {
        final int id;
        final String title;
        final String badgeTitle;
        final int rewardEggType; // -1 表示無必掉蛋
        final ChapterDef[] chapters;

        ArcDef(int id, String title, String badgeTitle, int rewardEggType, ChapterDef[] chapters) {
            this.id = id;
            this.title = title;
            this.badgeTitle = badgeTitle;
            this.rewardEggType = rewardEggType;
            this.chapters = chapters;
        }
    }

    static final class BossDef {
        final int biomeIndex;
        final String name;
        final String intro;
        final String calmHint;
        final String clearedStory;
        final int rewardCoins;
        final int rewardStarSand;
        final int rewardEggType;

        BossDef(
            int biomeIndex,
            String name,
            String intro,
            String calmHint,
            String clearedStory,
            int rewardCoins,
            int rewardStarSand,
            int rewardEggType
        ) {
            this.biomeIndex = biomeIndex;
            this.name = name;
            this.intro = intro;
            this.calmHint = calmHint;
            this.clearedStory = clearedStory;
            this.rewardCoins = rewardCoins;
            this.rewardStarSand = rewardStarSand;
            this.rewardEggType = rewardEggType;
        }
    }

    static final class LookUpQuestDef {
        final int id;
        final String category; // 觀察 / 動作 / 對話
        final String ageGroup; // PRESCHOOL / ELEMENTARY / ALL
        final String title;
        final String detail;
        final int starReward;
        final int coinReward;

        LookUpQuestDef(int id, String category, String ageGroup, String title, String detail, int starReward, int coinReward) {
            this.id = id;
            this.category = category;
            this.ageGroup = ageGroup;
            this.title = title;
            this.detail = detail;
            this.starReward = starReward;
            this.coinReward = coinReward;
        }
    }

    static final class LookUpNote {
        final String date;
        final String questTitle;
        final String squadLabel;
        final String note;

        LookUpNote(String date, String questTitle, String squadLabel, String note) {
            this.date = date != null ? date : "";
            this.questTitle = questTitle != null ? questTitle : "";
            this.squadLabel = squadLabel != null ? squadLabel : "";
            this.note = note != null ? note : "";
        }
    }

    /** Phase 2 實境相機尋寶與冒險明信片紀錄模型（含照片、塗鴉圖層、語音便條與當日旅伴快照） */
    static final class MemoryEntry {
        final String id;
        final String date;
        final String title;
        final String squadLabel;
        final String photoPath;
        final String audioPath;
        final String doodlePath;
        final String note;
        final int huntId;
        final String colorName;
        final int dominantHue;
        final int stars;
        final long stepsOnDay;
        final int companionId;
        final int companionStage;
        final boolean companionShiny;
        final int season;

        MemoryEntry(
            String id,
            String date,
            String title,
            String squadLabel,
            String photoPath,
            String audioPath,
            int dominantHue,
            int stars
        ) {
            this(id, date, title, squadLabel, photoPath, audioPath, "", "", 0, "", dominantHue, stars, 0L, 0, 1, false, 0);
        }

        MemoryEntry(
            String id,
            String date,
            String title,
            String squadLabel,
            String photoPath,
            String audioPath,
            String doodlePath,
            String note,
            int huntId,
            String colorName,
            int dominantHue,
            int stars,
            long stepsOnDay,
            int companionId,
            int companionStage,
            boolean companionShiny,
            int season
        ) {
            this.id = id != null ? id : "";
            this.date = date != null ? date : "";
            this.title = title != null ? title : "";
            this.squadLabel = squadLabel != null ? squadLabel : "";
            this.photoPath = photoPath != null ? photoPath : "";
            this.audioPath = audioPath != null ? audioPath : "";
            this.doodlePath = doodlePath != null ? doodlePath : "";
            this.note = note != null ? note : "";
            this.huntId = huntId;
            this.colorName = colorName != null ? colorName : "";
            this.dominantHue = dominantHue;
            this.stars = Math.max(1, Math.min(3, stars <= 0 ? 1 : stars));
            this.stepsOnDay = Math.max(0L, stepsOnDay);
            this.companionId = Math.max(0, companionId);
            this.companionStage = Math.max(1, Math.min(3, companionStage));
            this.companionShiny = companionShiny;
            this.season = Math.max(0, Math.min(3, season));
        }
    }

    static final class SeasonStampDef {
        final int index;
        final String title;
        final String condition;

        SeasonStampDef(int index, String title, String condition) {
            this.index = index;
            this.title = title;
            this.condition = condition;
        }
    }

    static final ArcDef[] ARCS = {
        new ArcDef(0, "迷路小松鼠的三顆橡果", "橡果好友", CompanionSystem.EGG_MIST, new ChapterDef[]{
            new ChapterDef(1, "草叢裡的嘆氣聲", "一隻抱著空背包的小松鼠在路邊發愁：『我把過冬的橡果藏在哪裡了呢？』",
                "🌰 陪牠沿路找線索", "🍪 先分牠一塊餅乾打氣",
                "你們在樹根旁發現第一顆亮晶晶的橡果！", "小松鼠啃著餅乾眼睛發亮，立刻想起藏寶方向！",
                20, 6, 15, 12, 8, 25),
            new ChapterDef(2, "風吹來的樹葉地圖", "一片葉脈像迷宮一樣的大葉子飄到腳邊，似乎畫著松鼠的秘密記號！",
                "🗺️ 仔細比對樹影方向", "🐾 請旅伴幫忙聞氣味",
                "順著樹影找到第二顆黃金橡果，還撿到不少木頭！", "旅伴咻一下就在花叢邊挖出第二顆橡果！",
                26, 8, 15, 18, 8, 28),
            new ChapterDef(3, "大樹洞的感謝茶會", "終於找回最後一顆橡果！小松鼠端出暖呼呼的堅果茶招待全家人。",
                "🎉 舉杯慶祝收下謝禮", "🥚 帶走牠珍藏的神祕蛋",
                "松鼠一家為你們拍手歡呼，送出滿滿金幣與星砂！", "松鼠悄悄送上一顆霧林神祕蛋作為友誼見證！",
                45, 14, 25, 25, 12, 30)
        }),
        new ArcDef(1, "會唱歌的郵差信箱", "童話送信員", -1, new ChapterDef[]{
            new ChapterDef(1, "叮噹響的藍色信箱", "巷口出現一個長著小翅膀的郵筒，裡面卡著一封沒寫門牌的信。",
                "✉️ 幫忙查看信封郵戳", "🎵 輕輕敲三下跟牠打招呼",
                "郵戳寫著『送給最愛散步的一家人』，真神祕！", "郵筒唱起輕快小調，抖出幾枚幸運金幣！",
                22, 6, 12, 26, 6, 18),
            new ChapterDef(2, "屋頂上的白雲暗號", "信封背面提示：『抬頭看雲的人，才能讀懂下一行字。』",
                "☁️ 大家一起抬頭找雲", "✨ 用星砂照亮信紙",
                "哇！在陽光下浮現出通往老風車塔的路線！", "星光字跡亮了起來，旅伴高興地原地轉圈！",
                24, 9, 20, 20, 10, 22),
            new ChapterDef(3, "寫給未來的明信片", "找到收件人竟是未來的你們自己！郵差鳥蓋下閃亮郵戳。",
                "💌 收進故事手札紀念", "🌟 許下全家下次出遊願望",
                "獲得童話郵差徽章與星砂祝福！", "郵差鳥把願望折成紙飛機送向天空！",
                40, 15, 25, 30, 18, 30)
        }),
        new ArcDef(2, "營火旁的星空麵包師", "星光烘焙師", CompanionSystem.EGG_LEAF, new ChapterDef[]{
            new ChapterDef(1, "香噴噴的麵粉腳印", "空氣裡飄著奶油香，地上留著一串白白的小腳印通往營地。",
                "🥐 循著香味去探險", "👣 數數看有幾個腳印",
                "遇見戴著高高廚師帽的刺蝟麵包師！", "一路笑著數完腳印，剛好走到麵包師的小推車旁！",
                22, 7, 15, 18, 9, 20),
            new ChapterDef(2, "尋找發光莓果", "麵包師說只差一點點『散步才採得到的星星莓』就能烤出雲朵麵包。",
                "🫐 幫忙採集路邊露珠", "🔥 幫忙搧風把烤箱升溫",
                "露珠滴進麵團立刻蓬起來，好香啊！", "全家人輪流搧風，烤爐發出溫暖的劈啪聲！",
                25, 10, 18, 22, 10, 25),
            new ChapterDef(3, "剛出爐的雲朵麵包", "叮！大麵包裂開冒出金色熱氣，全家與旅伴一起分著吃。",
                "😋 大口分享烤麵包", "🧺 打包當旅伴的點心",
                "美味得像咬到真正的雲！星砂與金幣滿滿進帳！", "旅伴吃得滿臉麵包屑，羈絆瞬間大提升！",
                42, 14, 25, 28, 12, 40)
        }),
        new ArcDef(3, "雨傘精靈的彩虹橋", "彩虹建築師", CompanionSystem.EGG_BLOOM, new ChapterDef[]{
            new ChapterDef(1, "倒過來的透明小傘", "草地上停著一把會發光的小雨傘，正把水珠收集成七彩玻璃珠。",
                "🌈 幫忙收集光點水珠", "💧 輕輕搖晃雨傘聽水聲",
                "收集到紅、黃、藍三顆透亮的小水珠！", "雨傘奏出像木琴一樣清脆的雨滴交響曲！",
                20, 8, 15, 24, 7, 18),
            new ChapterDef(2, "缺了一色的半道彩虹", "天邊的迷你彩虹少了一段溫暖的橘色，雨傘精靈急得團團轉。",
                "🧡 分享旅伴的溫暖笑容", "🌻 撒下一把陽光花瓣",
                "笑聲變成最耀眼的橘光補上彩虹！", "花瓣飛上天空化作閃閃發光的拱橋！",
                26, 10, 22, 22, 12, 20),
            new ChapterDef(3, "走過彩虹橋的約定", "彩虹橋落成！精靈邀請全家人走過它，留下彩色腳印。",
                "✨ 收集橋尾彩虹寶盒", "🌸 帶回一顆花瓣奇蹟蛋",
                "寶盒裡跳出大量金幣與星砂！", "暖洋洋的花瓣蛋滾到腳邊，準備一起孵化！",
                46, 15, 22, 26, 14, 28)
        }),
        new ArcDef(4, "夜晚螢火蟲的音樂會", "星夜指揮家", CompanionSystem.EGG_NIGHT, new ChapterDef[]{
            new ChapterDef(1, "閃爍的節拍燈", "樹林邊有五隻小螢火蟲一明一滅，好像在邀請大家一起打拍子。",
                "👏 跟著節奏拍手", "🔦 輕聲哼一段晚安歌",
                "螢火蟲排成愛心形狀在空中跳舞！", "歌聲引來了更多提著小燈籠的夜行夥伴！",
                20, 9, 18, 18, 10, 24),
            new ChapterDef(2, "找回落單的小鼓手", "節奏突然少了一個低音，原來是青蛙鼓手害羞躲在荷葉下。",
                "🐸 溫柔邀請青蛙上台", "🥁 用腳步聲幫牠伴奏",
                "青蛙鼓起勇氣跳出來『呱呱』敲出完美節拍！", "大家的腳步聲變成最棒的大鼓伴奏！",
                24, 11, 20, 26, 10, 22),
            new ChapterDef(3, "滿天星光安可曲", "森林音樂會大成功！螢火蟲送出夜空的禮物。",
                "🌟 收下星光指揮棒祝福", "🥚 帶回螢火夜行蛋",
                "星砂如流星般落進背包裡！", "一顆微微發亮的夜行蛋輕輕落在手心！",
                38, 20, 25, 24, 15, 30)
        }),
        new ArcDef(5, "沙漠裡的冰涼綠洲茶館", "綠洲貴賓", CompanionSystem.EGG_GOLD, new ChapterDef[]{
            new ChapterDef(1, "帳篷裡搖鈴的駱駝", "沙丘後方冒出彩色旗幟，一隻戴墨鏡的駱駝招呼大家喝涼茶。",
                "🧋 點一杯薄荷仙人掌茶", "🧊 幫茶館搬運涼水壺",
                "喝完暑氣全消，旅伴舒服地打了個飽嗝！", "駱駝老闆誇獎全家人真熱心，大方給出小費！",
                20, 8, 22, 32, 7, 15),
            new ChapterDef(2, "沙畫說書時間", "茶館老闆在沙盤上畫出古代旅人的腳印，邀請大家接力畫下一筆。",
                "✏️ 畫一座親子秘密城堡", "🐢 畫一隻載滿寶藏的巨龜",
                "沙畫竟然發光浮在空中，賓客們紛紛鼓掌！", "沙盤裡滾出一枚閃亮的黃金星幣！",
                24, 11, 22, 34, 9, 18),
            new ChapterDef(3, "沙漠日落派對", "金色夕陽染紅沙丘，老闆送上一張『永久免費喝茶券』與神秘包裹。",
                "🪙 收下綠洲金幣大禮", "🥚 帶回黃金沙丘蛋",
                "滿袋金幣叮噹作響！", "熱呼呼的黃金沙丘蛋準備陪你們走下一段路！",
                50, 14, 20, 28, 14, 25)
        }),
        new ArcDef(6, "老風車塔的秘密齒輪", "小小工程師", -1, new ChapterDef[]{
            new ChapterDef(1, "停下來的木頭扇葉", "山頂的老風車轉不動了，原來是一顆星星形狀的木齒輪滾進草叢。",
                "⚙️ 分頭在草坡找齒輪", "🌬️ 全家一起用力吹氣試試",
                "眼尖的隊員一眼就找到閃亮木齒輪！", "雖然沒吹動風車，但大家笑到肚子痛，還意外發現隱藏金幣！",
                26, 8, 16, 22, 9, 24),
            new ChapterDef(2, "卡滋卡滋裝回去", "把齒輪放回風車軸心，需要一起喊『一、二、三！』轉動把手。",
                "💪 一二三齊心轉動", "🔧 塗上一點花蜜潤滑油",
                "轟隆一聲，巨大風車迎風轉起來，撒下滿天蒲公英！", "風車轉得又順又香，傳來好聽的八音盒音樂！",
                28, 10, 22, 24, 12, 20),
            new ChapterDef(3, "塔頂的千里望遠鏡", "爬上風車陽台，透過望遠鏡能看到你們的秘密營地正閃閃發光！",
                "🔭 標記下一段探險方向", "🌟 收集塔頂風之星砂",
                "獲得工程師稱讚與滿滿經驗加乘！", "風兒把大量星砂吹進了背包裡！",
                45, 16, 25, 32, 20, 25)
        }),
        new ArcDef(7, "雪地企鵝的滑冰大賽", "極地夥伴", CompanionSystem.EGG_STAR, new ChapterDef[]{
            new ChapterDef(1, "跌進雪堆的小選手", "一隻戴著大圍巾的小企鵝滑得太快一頭栽進雪裡，只露出兩隻小腳。",
                "🐧 趕緊把牠拉出來拍拍雪", "⛄ 順便幫牠堆個加油雪人",
                "小企鵝站起來敬了個可愛的禮，謝謝大家！", "雪人戴上松果眼睛，全場企鵝都來圍觀！",
                22, 8, 22, 20, 10, 24),
            new ChapterDef(2, "冰晶接力棒", "企鵝隊少了一位接力幫手，邀請你們用散步步數幫牠們熱身。",
                "🏃 幫企鵝隊喊加油口號", "🧣 借出溫暖圍巾護航",
                "全場響起熱烈加油聲，企鵝隊衝刺破紀錄！", "披著暖圍巾的小企鵝飛快滑過終點線！",
                28, 10, 20, 24, 12, 26),
            new ChapterDef(3, "極光下的頒獎台", "夜空出現綠色極光！企鵝隊頒給全家人『最佳友誼獎』。",
                "🏅 領取極地金牌金幣", "🥚 收下星雪奇蹟蛋",
                "滿滿金幣與星砂在極光下閃閃發亮！", "冰涼涼的星雪蛋發出溫柔藍光！",
                48, 16, 24, 28, 15, 28)
        }),
        new ArcDef(8, "雲端鯨魚的許願氣球", "雲海旅人", -1, new ChapterDef[]{
            new ChapterDef(1, "飄下來的透明泡泡", "天空飄來一顆不會破的大泡泡，裡面寫著一張雲端邀請函。",
                "🫧 輕輕戳開泡泡讀信", "🎈 寫上全家的名字吹回天空",
                "邀請函寫著：『今天走滿步數，就能聽見鯨魚唱歌！』", "泡泡載著大家的名字越飛越高，閃出星星火花！",
                24, 9, 18, 20, 11, 24),
            new ChapterDef(2, "收集微笑氣球", "雲端小鯨魚喜歡收集地上的好心情，問問大家今天最開心的事。",
                "😄 分享今天最棒的小事", "🎶 一起哼一段快樂旋律",
                "氣球瞬間充滿溫暖光彩，升空點亮雲層！", "小鯨魚開心地噴出一道迷你彩虹水柱！",
                26, 12, 26, 28, 11, 22),
            new ChapterDef(3, "星辰雨祝福", "大鯨魚從雲朵間探出頭朝你們揮揮鰭，降下一場溫柔的星砂雨。",
                "✨ 伸出雙手接住星砂", "💖 把祝福分享給旅伴",
                "星砂滿滿掉進袋子裡！", "旅伴被星光包圍，開心得一直跳躍！",
                40, 22, 24, 34, 16, 38)
        }),
        new ArcDef(9, "火山溫泉的棉花糖派對", "暖焰好友", CompanionSystem.EGG_FLAME, new ChapterDef[]{
            new ChapterDef(1, "咕嚕冒泡的暖石池", "火山腳下有一座不會燙的天然溫泉，幾隻小火蜥正拿樹枝烤棉花糖。",
                "🍡 一起加入烤棉花糖", "♨️ 坐下來泡腳休息一下",
                "烤得外酥內軟的棉花糖讓大家讚不絕口！", "暖呼呼的泉水讓走了一整天的雙腳瞬間恢復活力！",
                24, 9, 22, 28, 9, 20),
            new ChapterDef(2, "尋找黃金烤糖秘方", "小火蜥說加一點點『勇敢者的笑聲』棉花糖就會變成金色！",
                "😂 互相說一個冷笑話", "🔥 請旅伴幫忙控制火候",
                "大家笑成一團，棉花糖真的發出金光！", "旅伴完美掌握小火，烤出大師級黃金棉花糖！",
                26, 12, 25, 30, 10, 24),
            new ChapterDef(3, "暖心營火紀念禮", "派對結束前，火蜥村長送上最珍貴的火山紀念品。",
                "🪙 收下暖焰寶藏袋", "🥚 帶回火山熔岩蛋",
                "沉甸甸的寶藏袋裝滿金幣與星砂！", "暖烘烘的火山蛋正在輕輕跳動！",
                52, 16, 24, 30, 16, 30)
        })
    };

    static final BossDef[] ISLAND_BOSSES = {
        new BossDef(0, "打瞌睡的風車石巨人",
            "巨大石巨人擋在村口風車橋上打呼嚕，用溫柔的方式叫醒牠吧！",
            "完成「累計散步 2,000 步」或「完成 1 次親子抬頭任務」即可喚醒安撫牠",
            "石巨人伸了個大懶腰，笑著讓出通往迷霧森林的小橋並送上禮物！",
            80, 24, CompanionSystem.EGG_LEAF),
        new BossDef(1, "迷路的樹靈鹿王",
            "身上掛滿燈籠的鹿王忘記回古壇的路，需要一家人替牠帶路。",
            "完成「累計散步 2,000 步」或「完成 1 次親子抬頭任務」即可替牠引路",
            "樹靈鹿王點亮整片森林螢火，向全家人深深鞠躬致謝！",
            100, 28, CompanionSystem.EGG_MIST),
        new BossDef(2, "愛聽故事的沙丘巨龜",
            "古代巨龜把遺跡入口當枕頭，只要聽一段冒險故事就會挪開背殼！",
            "完成「累計散步 2,000 步」或「完成 1 次親子抬頭任務」分享今日故事",
            "巨龜聽得津津有味，大方開啟黃金寶庫並贈送沙丘神祕蛋！",
            120, 32, CompanionSystem.EGG_GOLD),
        new BossDef(3, "怕黑的極光雪鯨",
            "水晶原上的小雪鯨正尋找溫暖的星光陪伴牠度過極夜。",
            "完成「累計散步 2,000 步」或「完成 1 次親子抬頭任務」點亮星光",
            "極光雪鯨躍出雲海灑下銀河星砂，留下一顆珍稀星雪奇蹟蛋！",
            140, 38, CompanionSystem.EGG_STAR),
        new BossDef(4, "嘴饞的暖焰龍王",
            "火山王座上的龍王其實只是想找人一起開暖呼呼的點心派對！",
            "完成「累計散步 2,000 步」或「完成 1 次親子抬頭任務」一起同樂",
            "暖焰龍王放起金色煙火慶祝，封全家人為『傳奇散步勇者團』！",
            180, 45, CompanionSystem.EGG_FLAME)
    };

    static final LookUpQuestDef[] LOOKUP_QUESTS = {
        new LookUpQuestDef(1, "觀察", AGE_PRESCHOOL, "尋找太陽黃", "一起在路上找一個像太陽一樣亮眼的黃色東西！", 12, 20),
        new LookUpQuestDef(2, "觀察", AGE_PRESCHOOL, "雲朵動物園", "抬頭看看天空，找一朵像小動物或食物的雲。", 12, 20),
        new LookUpQuestDef(3, "觀察", AGE_PRESCHOOL, "特別的樹葉", "找一片掉在地上的葉子，看看它有幾條葉脈或像什麼形狀。", 12, 22),
        new LookUpQuestDef(4, "觀察", AGE_PRESCHOOL, "三個圓圈圈", "沿路一起找三個圓形的事物（輪子、招牌、人孔蓋都可以！）。", 12, 20),
        new LookUpQuestDef(5, "動作", AGE_PRESCHOOL, "學旅伴打招呼", "到達下一個路口前，學現在同行的旅伴擺一個最可愛的動作！", 14, 24),
        new LookUpQuestDef(6, "動作", AGE_PRESCHOOL, "小小企鵝步", "大人小孩一起用企鵝搖擺步走 10 步，看看誰先笑出來。", 14, 24),
        new LookUpQuestDef(7, "對話", AGE_PRESCHOOL, "怪獸的晚餐", "問問孩子：如果前方轉角住著一隻友善小怪獸，牠最愛吃什麼？", 15, 22),
        new LookUpQuestDef(8, "觀察", AGE_PRESCHOOL, "路上遇見毛小孩", "數數看今天散步途中看到幾隻小狗、小貓或小鳥！", 12, 20),

        new LookUpQuestDef(9, "觀察", AGE_ELEMENTARY, "小小彩虹獵人", "在這次散步中依序找到紅、黃、綠、藍四種顏色的風景或招牌。", 16, 28),
        new LookUpQuestDef(10, "觀察", AGE_ELEMENTARY, "街角光影偵探", "找一處陽光穿過樹葉或建築物形成的有趣影子，一起幫它取名字。", 15, 26),
        new LookUpQuestDef(11, "觀察", AGE_ELEMENTARY, "尋找隱藏數字", "找出路牌、門牌或車牌上藏著『今日日期』或幸運數字的地方。", 15, 26),
        new LookUpQuestDef(12, "動作", AGE_ELEMENTARY, "十秒探險默契", "不用手機看時間，大家一起在心裡默數 10 秒同時舉手看誰最準！", 16, 28),
        new LookUpQuestDef(13, "對話", AGE_ELEMENTARY, "秘密基地大改造", "討論如果真的有一座屬於家人的秘密營地，最想在裡面蓋什麼設施？", 16, 26),
        new LookUpQuestDef(14, "對話", AGE_ELEMENTARY, "今日冒險新聞標題", "幫今天的散步想一個超酷的頭條新聞標題！", 16, 26),
        new LookUpQuestDef(15, "觀察", AGE_ELEMENTARY, "聲音地圖採集", "停下來閉上眼睛 10 秒，說出你聽到的三種現實世界聲音。", 16, 28),
        new LookUpQuestDef(16, "觀察", AGE_ELEMENTARY, "對稱美學發現", "在公園或街道上找一個左右對稱的建築、花朵或標誌。", 15, 26),

        new LookUpQuestDef(17, "對話", AGE_ALL, "今天最開心的一件事", "散步時每個人輪流說一件今天讓自己微笑的小事。", 15, 24),
        new LookUpQuestDef(18, "對話", AGE_ALL, "給旅伴取外號", "大家一起幫目前同行旅伴想一個超搞笑的秘密外號！", 14, 22),
        new LookUpQuestDef(19, "動作", AGE_ALL, "擊掌加速口令", "走滿下一個路口時全員擊掌，一起喊出『走走冒險出發！』", 14, 24),
        new LookUpQuestDef(20, "觀察", AGE_ALL, "發現微笑臉龐", "找一個看起來像在對你微笑的物品（樹洞、車頭、窗戶或雲朵）。", 15, 24),
        new LookUpQuestDef(21, "觀察", AGE_ALL, "新路線小探險", "刻意走一條平常少轉進去的小巷或公園步道，看看有什麼新發現。", 18, 30),
        new LookUpQuestDef(22, "對話", AGE_ALL, "謝謝今天的小隊員", "對一起出門的家人說一句具體的稱讚或感謝！", 16, 26),
        new LookUpQuestDef(23, "動作", AGE_ALL, "深呼吸充電站", "找一棵大樹或寬敞處停下來，全隊一起做三次大大的深呼吸。", 14, 22),
        new LookUpQuestDef(24, "對話", AGE_ALL, "週末夢想清單", "一起許願下次天氣好時，最想去哪一個公園或步道收集步數？", 16, 26)
    };

    static final SeasonStampDef[] SEASON_STAMPS = {
        new SeasonStampDef(0, "本季起走", "在本季累積走路超過 1,500 步"),
        new SeasonStampDef(1, "親子出動", "切換親子模式並完成 1 次抬頭任務"),
        new SeasonStampDef(2, "暖心營火", "完成 1 次營火開箱儀式"),
        new SeasonStampDef(3, "破殼新生", "成功孵化 1 顆神祕蛋"),
        new SeasonStampDef(4, "奇幻園丁", "在奇幻小菜園採收 1 次作物"),
        new SeasonStampDef(5, "營地匠人", "建造或升級營地建築達總等級 4 級"),
        new SeasonStampDef(6, "繪本讀者", "完成 1 條完整的 3 話連貫故事"),
        new SeasonStampDef(7, "星夜漫步", "在清晨或黃昏／夜晚出門散步留下足跡")
    };

    // 執行期狀態（隨 AdventureState 存檔）
    int activeArcId = 0;
    int activeChapter = 1; // 1..3
    int completedArcMask = 0;
    int bossClearedMask = 0;
    int bossStepProgress = 0;
    boolean bossLookUpDone = false;

    String lookUpDate = "";
    String lookUpAgeFilter = AGE_ALL;
    final List<Integer> offeredLookUpIds = new ArrayList<>();
    final List<Integer> completedLookUpIds = new ArrayList<>();
    int totalLookUpCompleted = 0;
    final List<LookUpNote> recentNotes = new ArrayList<>();
    final List<MemoryEntry> memories = new ArrayList<>();

    String seasonKey = "";
    int seasonStampsMask = 0;

    static ArcDef arc(int id) {
        if (id < 0 || id >= ARCS.length) return ARCS[0];
        return ARCS[id];
    }

    static BossDef bossForBiome(int biomeIndex) {
        int safe = Math.max(0, Math.min(ISLAND_BOSSES.length - 1, biomeIndex));
        return ISLAND_BOSSES[safe];
    }

    static LookUpQuestDef lookUpQuestById(int id) {
        for (LookUpQuestDef q : LOOKUP_QUESTS) {
            if (q.id == id) return q;
        }
        return LOOKUP_QUESTS[0];
    }

    ChapterDef currentStoryChapter() {
        ArcDef arc = arc(activeArcId);
        int idx = Math.max(0, Math.min(CHAPTERS_PER_ARC - 1, activeChapter - 1));
        return arc.chapters[idx];
    }

    boolean isArcCompleted(int arcId) {
        if (arcId < 0 || arcId >= ARC_COUNT) return false;
        return (completedArcMask & (1 << arcId)) != 0;
    }

    int completedArcCount() {
        return Integer.bitCount(completedArcMask);
    }

    boolean isBossCleared(int biomeIndex) {
        if (biomeIndex < 0 || biomeIndex >= ISLAND_BOSSES.length) return false;
        return (bossClearedMask & (1 << biomeIndex)) != 0;
    }

    boolean canCalmCurrentBoss(int biomeIndex) {
        if (isBossCleared(biomeIndex)) return false;
        return bossStepProgress >= BOSS_REQUIRED_STEPS || bossLookUpDone;
    }

    void advanceBossSteps(long steps, int frontierBiomeIndex) {
        if (steps <= 0 || isBossCleared(frontierBiomeIndex)) return;
        bossStepProgress = (int) Math.min(BOSS_REQUIRED_STEPS, bossStepProgress + steps);
    }

    /** 前進至下一話或下一條未讀完的故事線。回傳此話是否為該故事線的大結局。 */
    boolean advanceStoryArc() {
        boolean finishedArc = (activeChapter >= CHAPTERS_PER_ARC);
        if (finishedArc) {
            completedArcMask |= (1 << activeArcId);
            activeChapter = 1;
            activeArcId = pickNextArcId(activeArcId);
            return true;
        }
        activeChapter++;
        return false;
    }

    private int pickNextArcId(int current) {
        for (int step = 1; step <= ARC_COUNT; step++) {
            int candidate = (current + step) % ARC_COUNT;
            if (!isArcCompleted(candidate)) return candidate;
        }
        return (current + 1) % ARC_COUNT;
    }

    void syncSeasonKey(WorldClock.Snapshot snap) {
        if (snap == null) return;
        if (seasonKey == null || !seasonKey.equals(snap.seasonKey)) {
            seasonKey = snap.seasonKey;
            seasonStampsMask = 0;
        }
    }

    boolean unlockSeasonStamp(int index) {
        if (index < 0 || index >= SEASON_STAMP_COUNT) return false;
        int bit = 1 << index;
        if ((seasonStampsMask & bit) != 0) return false;
        seasonStampsMask |= bit;
        return true;
    }

    boolean isSeasonStampUnlocked(int index) {
        if (index < 0 || index >= SEASON_STAMP_COUNT) return false;
        return (seasonStampsMask & (1 << index)) != 0;
    }

    int seasonStampCount() {
        return Integer.bitCount(seasonStampsMask);
    }

    void ensureDailyLookUpOffers(String today, Random random) {
        if (today == null) return;
        if (!today.equals(lookUpDate) || offeredLookUpIds.isEmpty()) {
            lookUpDate = today;
            completedLookUpIds.clear();
            rollLookUpOffers(random);
        }
    }

    void setAgeFilterAndReroll(String filter, String today, Random random) {
        if (AGE_PRESCHOOL.equals(filter) || AGE_ELEMENTARY.equals(filter)) {
            lookUpAgeFilter = filter;
        } else {
            lookUpAgeFilter = AGE_ALL;
        }
        if (today != null && !today.equals(lookUpDate)) {
            lookUpDate = today;
            completedLookUpIds.clear();
        }
        rollLookUpOffers(random);
    }

    void rollLookUpOffers(Random random) {
        List<LookUpQuestDef> pool = new ArrayList<>();
        for (LookUpQuestDef q : LOOKUP_QUESTS) {
            boolean matchAge = AGE_ALL.equals(lookUpAgeFilter)
                || q.ageGroup.equals(lookUpAgeFilter)
                || AGE_ALL.equals(q.ageGroup);
            if (matchAge && !completedLookUpIds.contains(q.id)) {
                pool.add(q);
            }
        }
        if (pool.size() < 3) {
            pool.clear();
            Collections.addAll(pool, LOOKUP_QUESTS);
        }
        Random rng = random != null ? random : new Random();
        Collections.shuffle(pool, rng);
        offeredLookUpIds.clear();
        for (int i = 0; i < Math.min(3, pool.size()); i++) {
            offeredLookUpIds.add(pool.get(i).id);
        }
    }

    boolean isLookUpCompletedToday(int questId, String today) {
        return today != null && today.equals(lookUpDate) && completedLookUpIds.contains(questId);
    }

    void recordNote(String date, String questTitle, String squadLabel, String note) {
        String cleanNote = note != null ? note.trim() : "";
        if (cleanNote.length() > 80) {
            cleanNote = cleanNote.substring(0, 80);
        }
        recentNotes.add(0, new LookUpNote(
            date,
            questTitle,
            squadLabel,
            cleanNote.isEmpty() ? "全隊開心完成了這次探索！" : cleanNote
        ));
        while (recentNotes.size() > MAX_RECENT_NOTES) {
            recentNotes.remove(recentNotes.size() - 1);
        }
    }

    /** 新增一張實境冒險明信片；若超過上限 MAX_MEMORIES，回傳被移除的最舊明信片供清理媒體檔。 */
    MemoryEntry addMemory(MemoryEntry entry) {
        if (entry == null) return null;
        memories.add(0, entry);
        if (memories.size() > MAX_MEMORIES) {
            return memories.remove(memories.size() - 1);
        }
        return null;
    }

    MemoryEntry removeMemoryById(String id) {
        if (id == null) return null;
        for (int i = 0; i < memories.size(); i++) {
            if (id.equals(memories.get(i).id)) {
                return memories.remove(i);
            }
        }
        return null;
    }

    /**
     * 回憶時光機：優先找出「去年的今天（±3 天）」跨年紀念明信片；若無則找出 7 天以上的過往珍藏回憶。
     */
    List<MemoryEntry> findThrowbackMemories(String todayIso) {
        List<MemoryEntry> onThisDay = new ArrayList<>();
        List<MemoryEntry> olderFlashbacks = new ArrayList<>();
        LocalDate todayDate = parseIsoSafe(todayIso);
        if (todayDate == null) return Collections.emptyList();

        for (MemoryEntry m : memories) {
            LocalDate md = parseIsoSafe(m.date);
            if (md == null) continue;
            if (md.getYear() < todayDate.getYear()) {
                int dayDiff = Math.abs(md.getDayOfYear() - todayDate.getDayOfYear());
                if (dayDiff <= 3 || dayDiff >= 362) {
                    onThisDay.add(m);
                    continue;
                }
            }
            if (!md.isAfter(todayDate.minusDays(7))) {
                olderFlashbacks.add(m);
            }
        }
        if (!onThisDay.isEmpty()) {
            return Collections.unmodifiableList(onThisDay);
        }
        if (!olderFlashbacks.isEmpty()) {
            return Collections.unmodifiableList(olderFlashbacks.subList(0, Math.min(2, olderFlashbacks.size())));
        }
        return Collections.emptyList();
    }

    private static LocalDate parseIsoSafe(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
