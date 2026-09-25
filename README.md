# Parking Training Data Collector

This project collects New Taipei City parking data and converts it into daily CSV datasets for machine-learning training.

The main goal is to build historical training material for a future model that predicts parking availability near a destination at the time a driver arrives. The collector focuses on live parking availability signals, time features, location identifiers, and raw source status fields. It is not a public parking API, not a parking reservation system, and not a database-backed web service.

## Purpose

The dataset is intended for downstream projects that need to train or evaluate parking availability models.

Example model goals:

- Predict how many nearby offstreet parking spaces may remain when the user reaches a destination.
- Learn availability patterns by weekday, hour, and 5-minute time bucket.
- Compare roadside parking status and offstreet lot availability around Xinzhuang.
- Preserve raw government source values so labeling rules can be improved later.

The project intentionally keeps the pipeline simple:

```text
Google Apps Script time-driven trigger
  -> fetch New Taipei City parking data
  -> transform rows into ML-ready CSV
  -> append daily CSV files in Google Drive
  -> build daily ZIP export
  -> email the ZIP
```

## Current Design

- Google Apps Script is the preferred long-running scheduler because GitHub Actions schedules can be delayed or skipped.
- Apps Script runs the collector every 30 minutes.
- Each run fetches Xinzhuang roadside and offstreet parking data from New Taipei City Open Data.
- Rows are appended to Taiwan-date daily CSV files.
- CSV files are stored directly in Google Drive.
- A daily Apps Script trigger builds a ZIP for the previous Taiwan calendar day and emails it.
- A monitor trigger checks Google Drive freshness every 30 minutes and runs one recovery collection when data is stale.
- GitHub stores source code and backup workflow definitions only; generated data belongs in Google Drive.

The previous GitHub Actions workflows are still kept as backup/manual jobs during migration. Disable their schedules only after the Apps Script runner is manually verified.

## Data Sources

The collector uses these New Taipei City Open Data datasets:

- Xinzhuang roadside parking availability, filtered by `areacode=65000050`.
- Public offstreet parking lot metadata, filtered by `AREA=新莊區`.
- Citywide public offstreet parking live availability, matched back to Xinzhuang lots by `ID`.

## Google Drive Layout

The Apps Script `CONFIG.rootFolderId` points at the `parking-training-data` folder.
Legacy GitHub Actions jobs can still use an rclone remote that points at the same folder as its root.

```text
parking-training-data/
  daily/
    roadside/
      roadside_training_samples_2026-09-23.csv
    offstreet/
      offstreet_training_samples_2026-09-23.csv
  exports/
    parking-training-2026-09-23.zip
  manifests/
    manifest_2026-09-23.json
```

## Output Files

Daily files use the `Asia/Taipei` calendar date in the file name.
Collection timestamps inside the files are stored in UTC.

| File | Description |
| --- | --- |
| `daily/roadside/roadside_training_samples_yyyy-MM-dd.csv` | Roadside parking spot training rows for one Taiwan calendar day. |
| `daily/offstreet/offstreet_training_samples_yyyy-MM-dd.csv` | Offstreet parking lot training rows for one Taiwan calendar day. |
| `manifests/manifest_yyyy-MM-dd.json` | Row counts, generated time, file names, timezone, and schema version. |
| `exports/parking-training-yyyy-MM-dd.zip` | Daily ZIP containing both CSV files and the manifest. |

## Roadside CSV Columns

File name:

```text
roadside_training_samples_yyyy-MM-dd.csv
```

Rows are filtered to Xinzhuang by `areacode=65000050`.

| Column | Meaning | ML usage |
| --- | --- | --- |
| `collected_at_utc` | UTC time captured when `collectOnce()` starts, before source API requests. | Time index; do not treat as official source update time. |
| `collected_date_taipei` | Taiwan calendar date for the collection time. | Partition key and date feature. |
| `weekday_taipei` | Day of week in Taiwan time, `1=Monday` to `7=Sunday`. | Time feature. |
| `hour_taipei` | Hour of day in Taiwan time, `0` to `23`. | Time feature. |
| `minute_bucket_taipei` | Minute rounded down to the nearest 5-minute bucket. | Time feature. |
| `source` | Constant value `ntpc_roadside`. | Source identifier. |
| `area_code` | Official area code from the source row. | Area filter/check. |
| `spot_id` | Roadside parking spot ID from source field `id`. | Entity key. |
| `cell_id` | Source field `cellid`. | Optional entity/grouping key. |
| `road_id` | Source field `roadid`. | Road grouping feature. |
| `road_name` | Source field `roadname`. | Human-readable road name. |
| `spot_type` | Source field `name`, usually describing the parking spot type. | Categorical feature. |
| `latitude` | Source latitude. | Location feature. |
| `longitude` | Source longitude. | Location feature. |
| `parking_status_raw` | Raw source field `parkingstatus`. | Preserve raw label source. |
| `cell_status_raw` | Raw source field `cellstatus`. | Preserve raw status/context. |
| `is_available` | Derived availability label. Current rule: `0=true`, `1=false`, other values blank. | Training label candidate. |
| `label_rule` | Text description of the rule used to derive `is_available`. | Data lineage. |

Current roadside label rule:

```text
parkingstatus 0 -> is_available true
parkingstatus 1 -> is_available false
other values -> blank
```

The raw status fields are preserved so the label rule can be corrected later without losing original data.

## Offstreet CSV Columns

File name:

```text
offstreet_training_samples_yyyy-MM-dd.csv
```

Rows are built by matching Xinzhuang public offstreet lot metadata with the citywide live availability feed.

| Column | Meaning | ML usage |
| --- | --- | --- |
| `collected_at_utc` | UTC time captured when `collectOnce()` starts, before source API requests. | Time index; do not treat as official source update time. |
| `collected_date_taipei` | Taiwan calendar date for the collection time. | Partition key and date feature. |
| `weekday_taipei` | Day of week in Taiwan time, `1=Monday` to `7=Sunday`. | Time feature. |
| `hour_taipei` | Hour of day in Taiwan time, `0` to `23`. | Time feature. |
| `minute_bucket_taipei` | Minute rounded down to the nearest 5-minute bucket. | Time feature. |
| `source` | Constant value `ntpc_offstreet`. | Source identifier. |
| `lot_id` | Offstreet lot ID, matched from source field `ID`. | Entity key. |
| `lot_name` | Parking lot name from metadata field `NAME`. | Human-readable lot name. |
| `address` | Parking lot address from metadata field `ADDRESS`. | Location/context feature. |
| `total_car` | Total car capacity from metadata field `TOTALCAR`. | Capacity feature. |
| `tw97_x` | TWD97 X coordinate from metadata field `TW97X`. | Location feature. |
| `tw97_y` | TWD97 Y coordinate from metadata field `TW97Y`. | Location feature. |
| `available_car_raw` | Raw source field `AVAILABLECAR`. | Preserve raw label source. |
| `available_car` | Numeric available car spaces. Blank when raw value is unknown/negative. | Training target candidate. |
| `availability_ratio` | `available_car / total_car`, blank when unavailable. | Normalized training target candidate. |
| `is_unknown` | `true` when `available_car_raw` is missing, invalid, or negative. | Data quality flag. |
| `label_rule` | Text description of the rule used to derive numeric fields. | Data lineage. |

Offstreet raw values are preserved.
Negative `available_car_raw` values are treated as unknown for numeric training columns:

```text
available_car = blank
availability_ratio = blank
is_unknown = true
```

## Manifest

File name:

```text
manifest_yyyy-MM-dd.json
```

The manifest records:

- dataset name
- Taiwan dataset date
- UTC generation time
- roadside CSV file name and row count
- offstreet CSV file name and row count
- timezone used for file dates
- schema version

## Daily ZIP

File name:

```text
parking-training-yyyy-MM-dd.zip
```

Contents:

```text
roadside_training_samples_yyyy-MM-dd.csv
offstreet_training_samples_yyyy-MM-dd.csv
manifest_yyyy-MM-dd.json
```

## Apps Script Runner

The Apps Script implementation lives in:

```text
apps-script/
  Code.gs
  appsscript.json
  README.md
```

Main functions:

| Function | Purpose |
| --- | --- |
| `collectParkingData()` | Fetch one batch and append it to today's roadside/offstreet CSV files. |
| `monitorAndRecover()` | Check freshness; if data is stale, run one recovery collection and check again. |
| `exportDailyZip()` | ZIP yesterday's CSV files and manifest, then email the ZIP. |
| `setupTriggers()` | Create production time-driven triggers. |

Manual setup instructions are in `apps-script/README.md`.

## Schedule

Collection trigger:

```text
every 30 minutes
```

Daily export trigger:

```text
near Asia/Taipei 00:10 every day
```

The daily export sends the previous Taiwan calendar day's ZIP.

Freshness monitor trigger:

```text
every 30 minutes
```

The monitor runs one recovery collection if either roadside or offstreet CSV files have not been updated within 90 minutes.
After recovery, the monitor checks freshness again and fails only if the data is still stale.

## Commands

Collect one batch and append it to today's Taiwan-date CSV files:

```powershell
java -jar target/parking-data-collector-0.1.0-SNAPSHOT.jar collect
```

Build and email the previous Taiwan-date export:

```powershell
java -jar target/parking-data-collector-0.1.0-SNAPSHOT.jar export-daily yesterday
```

Build and email a specific date:

```powershell
java -jar target/parking-data-collector-0.1.0-SNAPSHOT.jar export-daily 2026-09-23
```

## GitHub Actions Backup Secrets

These secrets are only needed for the legacy GitHub Actions backup workflows.

Create these in the GitHub repository:

`Settings` -> `Secrets and variables` -> `Actions` -> `New repository secret`

For each row below, put the left value into GitHub's `Name` field and the right value into GitHub's `Secret` field.
Do not paste the whole table row.

| Secret | Value |
| --- | --- |
| `GDRIVE_RCLONE_CONFIG` | Full contents of `rclone.conf`. Do not paste it into chat or commit it. |
| `GDRIVE_REMOTE_NAME` | `gdrive` |
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | Gmail address used to send mail. |
| `SMTP_PASSWORD` | Gmail App Password, not the normal Google account password. |
| `EXPORT_MAIL_FROM` | Gmail address used to send mail. |
| `EXPORT_MAIL_TO` | `x0976117735@gmail.com` |

The current target Google Drive folder is `parking-training-data`.
Use the actual folder ID shown in the folder URL after opening it:

```text
1c4KBGVdLXDi_FlmP8Q8WAq0TSZCuoA6u
```

The rclone config should already include this as `root_folder_id`.

## rclone Setup Summary For GitHub Actions Backup

Use rclone on your own computer once to authorize Google Drive if you keep the legacy GitHub Actions backup workflows enabled.

```powershell
rclone config
```

Recommended answers:

- New remote name: `gdrive`
- Storage: Google Drive
- Scope: full Drive access
- Service account file: leave blank
- Advanced config: yes
- Root folder ID: `1c4KBGVdLXDi_FlmP8Q8WAq0TSZCuoA6u`
- Auto config: yes

After authorization, test:

```powershell
rclone lsd gdrive:
```

Then find the config file:

```powershell
rclone config file
```

Copy the full `rclone.conf` content into GitHub Secret `GDRIVE_RCLONE_CONFIG`.
