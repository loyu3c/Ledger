# LoyuLedger

## Language

務必使用**繁體中文**與使用者對話,無論使用者用什麼語言提問。程式碼、commit message、PR 標題與內文維持英文即可,只有對使用者說話時用繁體中文。

## Project

- Android 原生記帳 App(MVP / v0.1),Kotlin + Jetpack Compose + Room,MVVM + Repository,local-first(無登入、無雲端同步、無 AI)
- 目標平台版本:`compileSdk`/`targetSdk` 37、`minSdk` 26、Gradle 9.4.1、AGP 9.2.0、Kotlin 2.3.20、KSP 2.3.11
- CI:`.github/workflows/build-apk.yml`,push 到 `main` 或手動 `workflow_dispatch` 時建置 debug APK,artifact 名稱 `LoyuLedger-debug-apk`
- 開發分支規範:所有變更請 commit 到 `claude/loyuledger-android-app-9xa8dp`,不要直接推 `main`;要合併請走 PR
- **開發階段的合併節奏**:目前專案還在開發階段,一個功能改完、CI 建置驗證通過後,**直接開 PR 並合併到 `main`,不需要每次都停下來問使用者要不要合併**。使用者的測試方式是合併後自己去 GitHub Actions 下載 APK 裝到手機上測,所以盡快合併讓最新版本可以下載反而是使用者要的節奏。真的需要暫停確認的情況只有:改動有明顯風險或不確定使用者是否要這個方向時。
- **批次交付**:使用者提過「往返測試的頻率太高」,所以拿到一批功能待辦時,盡量一次做完多個小功能(各自 commit)後再一起請使用者下載測試,不要做完一個小功能就馬上打斷使用者。真的卡住或方向不確定時才中途詢問。
- **Debug 簽章金鑰已固定**:`debug.keystore`(commit 在 repo 根目錄)+ `app/build.gradle.kts` 裡的 `signingConfigs { named("debug") {...} }`。原因:CI 每次都是全新機器,若不固定簽章金鑰,AGP 會每次自動產生新的隨機 debug 金鑰,導致使用者每次安裝新版 APK 都要先移除舊版(簽章不一致無法覆蓋安裝)。**不要刪除或忽略這個 keystore 檔案**,它只是本機測試用、非正式上架金鑰,不是敏感機密。
- **語音輸入是「無 AI」原則的唯一例外**:新增記帳頁面的語音輸入按鈕,會呼叫使用者自己申請的 Groq API(`GroqClient.kt`)做語意解析,自動判斷分類/金額/商家/備註。這是使用者明確要求的選配功能,金鑰只存在裝置本機 `SharedPreferences`(`SettingsRepository.kt`),不會內建或上傳。沒有設定金鑰、或呼叫失敗時,會靜默降級成把辨識文字塞進備註欄。除此功能外,App 其餘部分維持 local-first、無雲端、無 AI。

## Known gotchas (2026 toolchain)

- AGP 9.0+ 已內建 Kotlin 支援,**不要**加回 `org.jetbrains.kotlin.android` plugin
- 依賴版本升級前務必確認 Maven 上真的存在該版本號(曾發生 Kotlin `2.4.0`、`compose-bom 2026.04.00` 這類編出來但不存在的版本號)
- KSP 2.3.11 對「類別屬性層級的解構宣告」(如 `private val (a, b) = foo()`)有已知的 Internal KSP Error,遇到請改寫成一般屬性寫法
