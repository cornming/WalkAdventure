# 固定的 debug 簽章金鑰

`debug.keystore` 是這個專案固定使用的 debug 簽章金鑰，由 `app/build.gradle` 的 `signingConfigs.debug` 指定。

| 項目 | 值 |
| --- | --- |
| 檔案 | `keystore/debug.keystore`（PKCS12） |
| 別名 | `androiddebugkey` |
| 金鑰庫密碼 | `android` |
| 金鑰密碼 | `android` |

## 為什麼要固定

Android 要求「覆蓋安裝」時，新舊 APK 的簽章必須相同。

如果不指定金鑰，Android Gradle Plugin 會在建置機器上自動產生一把新的 debug 金鑰。GitHub Actions 每次執行都是全新的環境，所以每一版 APK 的簽章都不一樣，手機就會拒絕覆蓋安裝，只能先解除安裝（本機存檔也會跟著消失）。

把金鑰放進 repo、每次都用同一把，之後的版本就能直接覆蓋安裝。

## 安全性取捨

**這把金鑰是公開的**：它放在公開 repo 中，密碼也寫在 `app/build.gradle` 與本文件裡，任何人都能取得。

- 任何人都可以用這把金鑰簽出「看起來與官方版本同一個簽章」的 APK。對方只要誘導你安裝，那個 APK 就能覆蓋你手機上的走走冒險。因此**請只從本 repo 的 Releases 下載 APK**。
- 這把金鑰只適合 debug／個人使用，**不可以用來上架 Google Play 或做正式發行**。正式發行請另外建立 release 金鑰，並放在 GitHub Secrets 或其他安全的地方，不要提交到 repo。
- 本 App 目前只有本機存檔、不需要登入、不連網，被冒名覆蓋的風險限於本機遊戲資料；若之後加入帳號、付費或連網功能，應改用不公開的金鑰。

## 更換金鑰會怎樣

簽章一旦改變，所有已安裝的舊版都無法覆蓋安裝，需要先解除安裝（本機存檔會消失）。除非必要，請不要更換這個檔案。
