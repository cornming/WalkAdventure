# 走走冒險 WalkAdventure

「你走的每一步，都是遊戲進度。」

V0.1 是一款以 Android 實際步數驅動的輕量冒險 RPG：走路取得經驗、升級、探索地圖、開寶箱、取得金幣，並觸發 NPC／怪物／道具／特殊事件。

## V0.1
- Android `TYPE_STEP_COUNTER` 實際步數
- 今日步數與累積冒險步數
- XP / 等級 / 地圖探索
- 每 1000 步寶箱與金幣
- 每 500 步隨機事件
- SharedPreferences 本機存檔
- 不需要 GPS、不需要登入

## 下載與安裝

1. 到本 repo 的 [Releases](../../releases) 頁面，下載最新版的 `WalkAdventure-vX.Y.Z.apk`。
2. 在 Android 手機上開啟 APK，依系統提示允許「安裝未知應用程式」。
3. 第一次執行時允許「活動辨識」權限（計步需要）。

之後的新版 APK 使用同一把固定簽章，**可直接覆蓋安裝**，本機存檔會保留。

> **舊版 APK 簽章不同，第一次換新版需先解除安裝，本機存檔會消失。**
> 這只會發生一次；從固定簽章的版本開始，之後都可以直接覆蓋安裝。

每個版本的更新內容會整理在該版 Release 說明中。

## 建置

GitHub Actions 使用 Gradle 8.10 與 Android Gradle Plugin 8.6.1 建置 APK（JDK 17）。每次推上 `main` 就會自動建置 debug APK 並發佈 GitHub Release；只改 Markdown 檔不會觸發。流程定義在 [`.github/workflows/release.yml`](.github/workflows/release.yml)。

本機建置（需要 JDK 17、Android SDK 與 Gradle 8.10）：

```bash
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
- **Release 說明**：由 [`.github/scripts/release-notes.sh`](.github/scripts/release-notes.sh) 把上一個標籤之後的提交，依 feat／fix／其他分組整理，開頭附安裝方式與覆蓋安裝說明。

## 簽章

debug 建置使用 repo 內固定的金鑰 `keystore/debug.keystore`（別名 `androiddebugkey`，密碼 `android`），由 `app/build.gradle` 的 `signingConfigs.debug` 指定。這樣 CI 每次建置的簽章都相同，新版才能直接覆蓋安裝。

這把金鑰是公開的，只適合 debug／個人使用，不可用於正式上架。原因與安全性取捨請見 [`keystore/README.md`](keystore/README.md)。
