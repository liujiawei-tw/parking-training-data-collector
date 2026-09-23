# 新莊停車資料收集器

此獨立 repository 用新北市政府資料開放平臺的即時資料，累積新莊區路邊格位狀態與公有路外停車場剩餘汽車位數，並提供主專案讀取最新觀測的內部 API。

## 專案邊界

- 收集並保存來源原始狀態碼、格位／停車場 ID、取得時間；不在此服務預測或宣稱保證有位。
- 路邊只保留新莊區（`areacode=65000050`）；路外以新北市路外公共停車場基本資料的 `AREA=新莊區` 對應即時資料的 `ID`。
- 來源 API 以分頁取得，收集失敗時保留既有資料，記錄失敗。相同狀態只更新最後看見時間；狀態改變時留下事件，供後續重建歷史。
- 第一版採 Java 21、Spring Boot、Spring JDBC 與本機 H2 檔案資料庫，以便立即開始累積。未來部署時再評估 MySQL 與持續運行環境。

## 官方來源

- [新北市路邊停車空位查詢](https://data.ntpc.gov.tw/datasets/54A507C4-C038-41B5-BF60-BBECB9D052C6)
- [新北市公有路外停車場即時賸餘車位數](https://data.ntpc.gov.tw/datasets/e09b35a5-a738-48cc-b0f5-570b67ad9c78)
- [新北市路外公共停車場資訊](https://data.ntpc.gov.tw/datasets/B1464EF0-9C7C-4A6F-ABF7-6BDF32847E68)
- [新北市資料開放平臺開發指引](https://data.ntpc.gov.tw/applications)

## 開發流程

`main` 保存已驗證的基底；`feature/xinzhuang-collector` 開發及檢查收集器，再經自我審查合併。資料檔與本機設定不提交 Git。此 repository 目前只有本機 Git，沒有 GitHub 遠端。

啟動與 API 使用方式見 `docs/setup.md`；欄位與失敗處理見 `docs/data-contract.md`。
