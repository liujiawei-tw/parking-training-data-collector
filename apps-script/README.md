# Google Apps Script Runner

This folder contains the Google Apps Script version of the parking training data pipeline.

Use this when GitHub Actions scheduling is not reliable enough. GitHub should still keep the source code, but Apps Script can run the collection, monitoring, Drive storage, ZIP export, and email steps inside the Google account.

## What Runs In Apps Script

- `collectParkingData()`: fetches New Taipei City parking data and appends rows to daily CSV files in Google Drive.
- `monitorAndRecover()`: checks whether the latest roadside and offstreet CSV files were updated within 90 minutes. If not, it runs one recovery collection and checks again.
- `exportDailyZip()`: builds yesterday's ZIP file and emails it to `x0976117735@gmail.com`.
- `setupTriggers()`: creates the time-driven triggers.

## Source Freshness Fields

The Apps Script runner appends source metadata to every new CSV row:

```text
source_dataset_id
source_dataset_name
source_update_frequency
source_api_response_at_utc
```

It also writes both collection timestamps:

```text
collected_at_utc
collected_at_taipei
```

`collected_at_utc` uses UTC, so it appears 8 hours behind Taiwan local time. Use `collected_at_taipei` for quick manual inspection.

CSV files are written with a UTF-8 BOM. This keeps Chinese text readable when ZIP exports are extracted and opened directly in Windows Excel or common text editors.

New Taipei City publishes update frequency for these sources:

```text
roadside availability: 每2分鐘
offstreet live availability: 每3分鐘
offstreet lot metadata: 每日
```

The row APIs do not expose an official per-row update timestamp. `source_api_response_at_utc` records the official API response `Date` header when available, and falls back to the collection time if the header is unavailable.

## Google Drive Folder

The script writes to this existing folder:

```text
parking-training-data
1c4KBGVdLXDi_FlmP8Q8WAq0TSZCuoA6u
```

Folder layout:

```text
parking-training-data/
  daily/
    roadside/
    offstreet/
  exports/
  manifests/
```

## Setup Steps

1. Open [Google Apps Script](https://script.google.com/).
2. Create a new standalone project named `parking-training-data-collector`.
3. Set the project time zone to `Asia/Taipei`.
4. In project settings, enable `Show "appsscript.json" manifest file in editor`.
5. Copy `appsscript.json` into the manifest file.
6. Copy `Code.gs` into the Apps Script editor.
7. Save the project.
8. Run `collectParkingData()` manually once and approve permissions.
9. Confirm CSV files were updated in Google Drive.
10. Run `monitorAndRecover()` manually once.
11. Run `exportDailyZip()` manually once if you want to test ZIP email immediately.
12. Run `setupTriggers()` once to create the production triggers.

## Trigger Plan

Apps Script triggers are created by `setupTriggers()`:

```text
collectParkingData   every 30 minutes
monitorAndRecover    every 30 minutes
exportDailyZip       every day near 00:10 Asia/Taipei
```

Apps Script time-driven triggers are managed by Google and can run slightly away from the requested minute. This is expected.

## Cutover From GitHub Actions

Do not disable GitHub Actions until these manual checks pass:

```text
collectParkingData() creates or updates both CSV files
monitorAndRecover() completes successfully
exportDailyZip() creates ZIP, manifest, and email
```

After Apps Script is confirmed, disable GitHub Actions schedules or leave workflows as manual-only backup jobs.

## Quota Notes

For a consumer Gmail account, Apps Script has daily quotas. This pipeline is designed to stay small:

- 48 scheduled collection runs per day.
- One daily ZIP email.
- Recovery collection only when data is stale.

If API responses become too large or collection takes more than the Apps Script per-execution limit, move the runner to Cloud Run or a VPS instead.
