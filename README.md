# Parking Training Data Collector

This project collects New Taipei City parking data for machine-learning training datasets.
It does not run a public parking API and does not use a database.

## Current Design

- GitHub Actions runs the collector every 5 minutes.
- Each run fetches Xinzhuang roadside and offstreet parking data from New Taipei City Open Data.
- Rows are converted into ML-ready daily CSV files.
- CSV files are stored in Google Drive through rclone.
- A daily workflow builds a ZIP for the previous Taiwan calendar day and emails it.

Google Drive is the long-term data store. GitHub stores only code and workflow definitions.

## Google Drive Layout

The rclone remote should point at the `parking-training-data` folder as its root.

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

## GitHub Secrets

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

## Dataset Notes

The daily CSV files keep raw source status fields and add basic training features:

- UTC collection timestamp
- Taiwan calendar date
- Taiwan weekday, hour, and 5-minute bucket
- source identifiers
- parking status raw fields
- simple availability labels

Roadside `is_available` is derived from `parkingstatus` with the current rule:

```text
0 -> true
1 -> false
other -> blank
```

The raw fields are preserved so the label rule can be corrected later without losing source data.

Offstreet `available_car_raw` is preserved. Negative values are treated as unknown for numeric training columns.

## rclone Setup Summary

Use rclone on your own computer once to authorize Google Drive.

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
