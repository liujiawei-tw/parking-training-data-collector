# 本機啟動與驗證

此專案從獨立 `feature/collect-parking-data` 分支開發。需要 Java 21；可從 IntelliJ IDEA 開啟 `pom.xml`，使用內建 Maven。資料庫預設存於本機 `data/`，已由 Git 忽略。HTTP API 預設只綁定 `127.0.0.1:8081`。

在 IntelliJ IDEA 執行 `CollectorApplication`，或用 Maven 執行 `spring-boot:run`。程序啟動後立即擷取一次；`GET http://127.0.0.1:8081/api/v1/collector/health` 應顯示兩個來源的 `SUCCESS`。若停車場來源顯示 `FAILED`，查看錯誤訊息並保持舊資料，不要把失敗當作「零位」。

Windows 背景啟動：先以 IntelliJ 的 Maven 工具執行 `package`，再於 PowerShell 執行 `./scripts/start.ps1`；停止用 `./scripts/stop.ps1`。程序 ID 與日誌會存於被 Git 忽略的 `data/`。腳本只會停止命令列確實指向此專案 JAR 的程序。若 PowerShell 執行原則阻擋腳本，可在 IntelliJ 直接執行主類別。

收集器預設每 5 分鐘執行一次，路外基本資料每 24 小時更新一次。新北市開發指引提醒大量／高頻介接應先與平台聯繫；若要擴大範圍或提高頻率，先確認使用條件。筆電睡眠或程序停止時不會自動補回錯過的即時資料。

新莊區資料首次測試：路邊 2,124 格、路外 149 場。這是 2026-09-23 的觀測值，非固定筆數或品質保證。路外全市即時資料有重複 ID，但首次測試時新莊區對應資料沒有重複；若之後出現本地重複，該次擷取會標為失敗。

## 預定 API

- `GET /api/v1/collector/health`：目前程序與最近成功擷取情況。
- `GET /api/v1/observations/roadside/latest`：新莊區格位最新原始狀態與系統取得時間。
- `GET /api/v1/observations/offstreet/latest`：新莊區公有路外停車場最新剩餘汽車位原始值與系統取得時間。

每個 API 都只供主專案內部使用。對外部署前需補認證、流量限制與主機網路規劃。
