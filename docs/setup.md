# Setup

This project is migrating from a GitHub Actions data pipeline to a Google Apps Script runner because GitHub Actions schedules can be delayed or skipped.

## Repository

Use:

```text
https://github.com/liujiawei-tw/parking-training-data-collector
```

The repository stores source code and workflows only. Do not commit generated CSV, ZIP, logs, or secrets.

## Google Drive

The target folder is:

```text
parking-training-data
```

The folder ID used by rclone is:

```text
1c4KBGVdLXDi_FlmP8Q8WAq0TSZCuoA6u
```

rclone should use this folder as its root, so workflow paths can be simple:

```text
gdrive:daily
gdrive:exports
gdrive:manifests
```

## Google Apps Script Runner

Use the files in:

```text
apps-script/
```

Setup summary:

```text
create a standalone Apps Script project
copy apps-script/appsscript.json into the Apps Script manifest
copy apps-script/Code.gs into the editor
run collectParkingData() manually and approve permissions
run monitorAndRecover() manually
run exportDailyZip() manually if you want to test email
run setupTriggers() once to create production triggers
```

See `apps-script/README.md` for the full setup checklist.

## GitHub Actions Backup

GitHub Actions workflows are kept as manual backup jobs only. Their automatic schedules are disabled; production collection, monitoring, and daily export are handled by Google Apps Script.

## GitHub Actions Backup Secrets

These secrets are only needed if you manually run the legacy GitHub Actions backup workflows.

Add repository secrets under:

`Settings` -> `Secrets and variables` -> `Actions`

Required secrets:

```text
GDRIVE_RCLONE_CONFIG
GDRIVE_REMOTE_NAME
SMTP_HOST
SMTP_PORT
SMTP_USERNAME
SMTP_PASSWORD
EXPORT_MAIL_FROM
EXPORT_MAIL_TO
```

Suggested values:

```text
GDRIVE_REMOTE_NAME=gdrive
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=x0976117735@gmail.com
EXPORT_MAIL_FROM=x0976117735@gmail.com
EXPORT_MAIL_TO=x0976117735@gmail.com
```

`SMTP_PASSWORD` must be a Gmail App Password, not the normal Google password.
`GDRIVE_RCLONE_CONFIG` must be the full rclone config file content.

## Legacy GitHub Workflows

`collect.yml` is manual-only:

```text
download daily CSV from Google Drive
run collector once
upload updated daily CSV to Google Drive
```

`export-daily.yml` is manual-only:

```text
download daily CSV from Google Drive
build previous day's ZIP
email ZIP
upload ZIP and manifest to Google Drive
```

`monitor.yml` is manual-only:

```text
list latest roadside/offstreet CSV files in Google Drive
run one recovery collection if either source has not been updated within 90 minutes
fail only if the recovery collection does not refresh the stale data
```
