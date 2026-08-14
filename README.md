# Loyu 記帳 — Android MVP v0.1

第一版目標：用最少操作完成個人記帳，資料本機優先保存。

## 已完成
- Kotlin + Jetpack Compose
- Room 本機資料庫
- MVVM / Repository 基礎分層
- 首次啟動自動建立預設帳戶與分類
- 新增支出 / 收入
- 金額、分類、帳戶、商家、備註
- 本月收入 / 支出 / 結餘
- 最近交易明細

## 第一版暫不做
- 轉帳 UI
- 編輯 / 刪除交易
- 預算
- 統計圖表
- 雲端同步 / 登入
- AI 自動分類

## 建議下一個迭代 v0.2
1. 明細頁與日期篩選
2. 編輯 / 刪除
3. 帳戶管理
4. 分類管理
5. 月份切換
6. 基本統計圖表

## 開啟方式
使用新版 Android Studio 開啟專案根目錄，等待 Gradle Sync 後執行 `app`。

最低 Android：API 26（Android 8.0）
Target / Compile SDK：37
