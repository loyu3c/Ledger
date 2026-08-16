# LoyuLedger

## Language

務必使用**繁體中文**與使用者對話,無論使用者用什麼語言提問。程式碼、commit message、PR 標題與內文維持英文即可,只有對使用者說話時用繁體中文。

## Project

- Android 原生記帳 App(MVP / v0.1),Kotlin + Jetpack Compose + Room,MVVM + Repository,local-first(無登入、無雲端同步、無 AI)
- 目標平台版本:`compileSdk`/`targetSdk` 37、`minSdk` 26、Gradle 9.4.1、AGP 9.2.0、Kotlin 2.3.20、KSP 2.3.11
- CI:`.github/workflows/build-apk.yml`,push 到 `main` 或手動 `workflow_dispatch` 時建置 debug APK,artifact 名稱 `LoyuLedger-debug-apk`
- 開發分支規範:所有變更請 commit 到 `claude/loyuledger-android-app-9xa8dp`,不要直接推 `main`;要合併請走 PR

## Known gotchas (2026 toolchain)

- AGP 9.0+ 已內建 Kotlin 支援,**不要**加回 `org.jetbrains.kotlin.android` plugin
- 依賴版本升級前務必確認 Maven 上真的存在該版本號(曾發生 Kotlin `2.4.0`、`compose-bom 2026.04.00` 這類編出來但不存在的版本號)
- KSP 2.3.11 對「類別屬性層級的解構宣告」(如 `private val (a, b) = foo()`)有已知的 Internal KSP Error,遇到請改寫成一般屬性寫法
