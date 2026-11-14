# 走走冒險 WalkAdventure

「你走的每一步，都是遊戲進度。」

這是一款以 Android 真實計步感測器（`TYPE_STEP_COUNTER`）驅動的輕量冒險 RPG：用日常走路累積真實步數、開寶箱賺金幣、邂逅路途奇遇與選擇事件、招募可愛旅伴並升級裝備，還能在「戰報」頁查看近 7 天步數曲線、解鎖稱號徽章與複製每日冒險戰報。

## 遊戲特色與系統

### 1. 冒險分頁（五大主題區域 × 互動奇遇）
- **真實步數計步**：直接讀取硬體 `TYPE_STEP_COUNTER`，`今日真實步數` 與 `累計真實步數` 只採計真實行走，冒險 XP 與裝備／同伴加成採獨立計算、絕不偽造假步數。
- **五大探索章節（每章 8 格探索路徑，無限周目循環）**：
  1. `青草新手村`（村口風車塔）
  2. `迷霧精靈林`（樹靈古壇）
  3. `烈日黃金漠`（沙蟲遺跡）
  4. `星辰水晶原`（極光祭壇）
  5. `龍之赤火山`（炎龍王座）
  - 解鎖第 2 章以上後可隨時切換檢視已走過的章節，主角還會在目前探索節點上漫步待命。
- **雙選項互動奇遇（每 500 步觸發 + 首次出發補給包）**：
  - 路途會遇見守衛小怪（⚔️ 迎戰 / 🍖 餵食安撫）、神秘寶箱（🔑 直接開箱 / ✨ 許願再開）、巡迴流浪商人（🤝 免費祝福 / 🎁 花 25 金幣抽盲盒福袋）與古老神壇（🌟 祈願經驗 / 🪙 祈願財運）。
  - 若尚未處理上一個奇遇就走過下一個觸發點，系統會自動結算上個奇遇並記錄到冒險日誌，獎勵不會遺失。
- **每 1,000 步寶箱**：固定贈送金幣，且受幸運裝備與旅伴加成放大；關閉 App 期間走過的里程碑會在開啟時全部補發。

### 2. 商店分頁（金幣裝備 × 可愛旅伴）
- **永久被動裝備（Lv.0 → Lv.5）**：
  - `疾風探險靴`：走路額外 +10%/Lv 冒險 XP
  - `幸運四葉草`：寶箱與勝利金幣 +15%/Lv
  - `尋寶羅盤`：提高路途雙倍幸運暴擊率與神秘寶箱遇見率
- **可愛旅伴招募（招募後可隨時免費切換，並繪製於主角卡片右側）**：
  - `柴犬阿福`（80 金幣）：每個千步寶箱額外 +8 金幣
  - `小綠史萊姆`（140 金幣）：解決奇遇額外 +20 冒險 XP、+6 金幣
  - `貓頭鷹波波`（220 金幣）：每日任務領獎金幣 +25%
  - `迷你火龍`（380 金幣）：走路冒險 XP +20% 且奇遇必帶 +15 金幣

### 3. 戰報分頁（7 日圖表 × 任務 × 成就 × 日誌）
- **近 7 天步數長條圖**：視覺化呈現過去 7 天真實步數、每日 `1.5k` 暖身基準線與 🔥 連續達標天數（連勝），支援一鍵**複製今日冒險戰報**到剪貼簿。
- **每日步數任務**：每天 `1,500`（暖身起走）／`4,000`（活力探險）／`8,000`（今日健步王）三階段可領金幣與 XP 獎勵，午夜換日後自動重置。
- **10 項成就稱號徽章與 20 筆冒險日誌**：達標即刻發放成就金幣並解鎖對應稱號（同時自動補發從舊版升級玩家的既有達標成就），完整記錄近 20 條開箱、奇遇、任務與招募歷程。

### 4. 本機存檔、更新與無障礙
- SharedPreferences 分檔儲存：冒險進度與 RPG 狀態（`walk_adventure`）隨系統備份保留；硬體計步基準與更新快取（`walk_adventure_device`）不跨機還原，升級覆蓋安裝 100% 向下相容 `v0.1.0–v0.3.0`。
- 內建 GitHub Releases 更新檢查與自動背景下載（核對檔案大小與 SHA-256 校驗碼，透過固定簽章可直接覆蓋安裝）。
- 免 GPS、免登入，主畫布與 7 日圖表皆提供 TalkBack 無障礙朗讀摘要與 Android 8+ 自適應腳印主題圖示。

### 步數怎麼算

手機的計步感測器回報的是「開機以來的累計步數」，App 每次讀到新的數字，就把和上一次讀數的差額加進進度。規則寫在 [`StepProgress.java`](app/src/main/java/com/cornming/walkadventure/StepProgress.java)，RPG 加成、任務與戰報狀態寫在 [`AdventureCatalog.java`](app/src/main/java/com/cornming/walkadventure/AdventureCatalog.java) 與 [`AdventureState.java`](app/src/main/java/com/cornming/walkadventure/AdventureState.java)：

- **App 沒開也算**：關著 App 走的步數，下次打開時會一次補進累積步數；期間跨過的每一個 1000 步寶箱都會補發。
- **今日步數**：每天歸零，從當天第一次讀到感測器（通常是當天第一次打開 App）之後開始算。在那之前走的步數分不出是哪一天的，只算進累積步數。
- **重開機**：感測器會從 0 重新累計，App 會用開機次數分辨，重開機之後走的步數照樣計入，今日步數不會歸零。
- 安裝後第一次讀到感測器時只記下起點，安裝前走的步數不算。
- 裝備、奇遇與每日任務給的是「冒險 XP」與金幣獎勵，不會改動「今日真實步數」與「累計真實步數」。

## 下載、安裝與更新

### 首次安裝
1. 到本 repo 的 [Releases](../../releases) 頁面，下載最新版的 `WalkAdventure-vX.Y.Z.apk`。
2. 在 Android 手機上開啟 APK，依系統提示允許「安裝未知應用程式」。
3. 第一次執行時允許「活動辨識」權限（計步需要）；若曾永久拒絕權限，可點一下主畫面直接前往系統設定開啟。

### App 內自動檢查與下載更新
- App 啟動時會於背景自動查詢最新 Release（約每 3 小時檢查一次，或點擊畫面底部「檢查更新」按鈕手動檢查），發現新版本時會自動下載並驗證 SHA-256 與檔案大小。
- 新版 APK 驗證就緒後，畫面會提示可「立即安裝」（亦可隨時點底部綠色按鈕安裝）；首次在 App 內更新時依系統提示開啟「允許來自此來源的應用程式」後返回即可繼續覆蓋安裝，原本的步數、金幣與冒險進度都會保留。（受限於 Android 系統安全規範，最後仍需由使用者在系統安裝畫面確認「更新」。）
- `INTERNET` 與 `REQUEST_INSTALL_PACKAGES` 權限僅用於檢查與安裝本倉庫 Releases 更新；其餘計步與遊戲資料完全離線運作。

> **舊版 APK 簽章不同，第一次換新版需先解除安裝舊版，本機存檔會消失。**
> 這只會發生一次；從固定簽章的版本開始，之後都可以直接覆蓋安裝。

每個版本的更新內容會整理在該版 Release 說明中。

## 建置與 CI

GitHub Actions 使用 Gradle 8.10 與 Android Gradle Plugin 8.6.1 建置 APK（JDK 17）：
- **PR 驗證**（[`.github/workflows/ci.yml`](.github/workflows/ci.yml)）：Pull Request 會自動檢查 shell 腳本語法、執行單元測試、Android Lint 與 `assembleDebug`。
- **自動發佈**（[`.github/workflows/release.yml`](.github/workflows/release.yml)）：推上 `main` 時計算版號，通過單元測試與 Lint 後自動建置 debug APK 並發佈 GitHub Release；若標籤已存在會直接跳過建置，只改 Markdown 檔不會觸發。

本機建置（需要 JDK 17、Android SDK 與 Gradle 8.10）：

```bash
gradle testDebugUnitTest   # 計步規則的單元測試
gradle lintDebug           # Android Lint 檢查
gradle assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

本機建置沒有設定環境變數時，versionCode 為 `1`、versionName 為 `0.1.0`。

## 版號規則

- **versionName**：由 [`.github/scripts/next-version.sh`](.github/scripts/next-version.sh) 依「上一個版本標籤（`vX.Y.Z`）之後的提交訊息」計算，採用 [Conventional Commits](https://www.conventionalcommits.org/zh-hant/) 格式：

  | 提交內容 | 版號變化 | 範例 |
  | --- | --- | --- |
  | 類型帶 `!`，或內文有 `BREAKING CHANGE` | 主版號 +1（次版號、修訂號歸零） | `feat!: 重做存檔格式` |
  | `feat` | 次版號 +1（修訂號歸零） | `feat: 新增地圖` |
  | 其他（`fix`、`docs`、`chore`⋯⋯） | 修訂號 +1 | `fix: 修正計步重複計算` |

  上一個標籤之後有多筆提交時，取最高的一項。repo 還沒有任何版本標籤時，第一版為 `0.1.0`。
- **versionCode**：使用 GitHub Actions 的 run number，只增不減。Android 不允許用 versionCode 較低的 APK 覆蓋較高的版本，所以新版 versionCode 一定要比舊版大。
- **Release 說明**：由 [`.github/scripts/release-notes.sh`](.github/scripts/release-notes.sh) 把上一個標籤之後的提交，依破壞性變更／feat／fix／其他分組整理，開頭附安裝方式與覆蓋安裝說明。

## 簽章

debug 建置使用 repo 內固定的金鑰 `keystore/debug.keystore`（別名 `androiddebugkey`，密碼 `android`），由 `app/build.gradle` 的 `signingConfigs.debug` 指定。這樣 CI 每次建置的簽章都相同，新版才能直接覆蓋安裝。

這把金鑰是公開的，只適合 debug／個人使用，不可用於正式上架。原因與安全性取捨請見 [`keystore/README.md`](keystore/README.md)。
