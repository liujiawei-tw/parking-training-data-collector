# 本機啟動與驗證

此專案從獨立 `feature/xinzhuang-collector` 分支開發。需要 Java 21；可從 IntelliJ IDEA 開啟 `pom.xml`，使用內建 Maven。資料庫預設存於本機 `data/`，已由 Git 忽略。HTTP API 預設只綁定 `127.0.0.1:8081`。

收集器預設每 5 分鐘執行一次，路外基本資料每 24 小時更新一次。新北市開發指引提醒大量／高頻介接應先與平台聯繫；若要擴大範圍或提高頻率，先確認使用條件。筆電睡眠或程序停止時不會自動補回錯過的即時資料。

## 預定 API

- `GET /api/v1/collector/health`：目前程序與最近成功擷取情況。
- `GET /api/v1/observations/roadside/latest`：新莊區格位最新原始狀態與系統取得時間。
- `GET /api/v1/observations/offstreet/latest`：新莊區公有路外停車場最新剩餘汽車位原始值與系統取得時間。

每個 API 都只供主專案內部使用。對外部署前需補認證、流量限制與主機網路規劃。
