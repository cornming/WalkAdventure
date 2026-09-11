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

## Build
GitHub Actions 使用 Gradle 8.10 與 Android Gradle Plugin 8.6.1 建置 APK。

```bash
gradle assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`
