# AI Working Instructions

## Goal

以新北市官方免費即時資料持續累積新莊區停車觀測，提供主專案讀取最新資料。使用者是初階後端工程師與 Sponsor；解釋設計與驗證時包含面試官角度。

## Constraints

- 保留來源原始狀態碼與取得時間；官方來源未提供更新時間時，不把系統取得時間冒充來源更新時間。
- `AVAILABLECAR=-9` 代表來源未提供即時剩餘位，不得解讀為零位。
- 路邊是否可合法停放的狀態碼尚未核對，不自行把 `parkingstatus` 或 `cellstatus` 映射成有位。
- 以 `areacode=65000050` 限定路邊新莊區；路外以基本資料 `AREA=新莊區` 的 ID 篩選。
- 來源 API 必須分頁；失敗頁不可儲存為完整快照。限制擷取頻率，遵守官方高頻介接提醒。
- 不把原始資料、H2 資料庫、金鑰或本機設定提交 Git。

## Source of Truth

- `docs/data-contract.md`：資料欄位與狀態處理。
- `docs/setup.md`：啟動、限制及驗證。
- 主專案 `taiwan-parking-forecast` 的產品需求與預測定義仍由其文件管理；此 repo 不自行改動產品目標。
