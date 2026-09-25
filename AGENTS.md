# AI Working Instructions

## Goal

This repository collects New Taipei City parking data and turns it into daily CSV datasets for machine-learning training.

The long-term product goal is to train models that estimate parking availability near a destination when a driver arrives. This repo is the lightweight data collection pipeline only. It is not a public parking API, not a web service, and not a database-backed application.

## Current Architecture

- Google Apps Script is the preferred long-running scheduler.
- Apps Script runs `collectParkingData()` every 30 minutes.
- Each collect run fetches Xinzhuang roadside and offstreet parking data.
- The collector appends ML-ready rows to Taiwan-date CSV files.
- Apps Script writes daily CSV files directly to Google Drive.
- Apps Script `monitorAndRecover()` performs one recovery collection if Google Drive CSV files are stale.
- Apps Script `exportDailyZip()` runs daily near `00:10 Asia/Taipei`, builds the previous Taiwan day's ZIP, writes the ZIP/manifest to Google Drive, and emails the ZIP.
- Google Drive is the long-term data store. GitHub stores source code and backup workflow definitions only.
- GitHub Actions workflows are legacy backup/manual jobs during migration; do not remove them until Apps Script is verified.

## Constraints

- Do not add MySQL, H2, SQLite, JPA, JDBC storage, or a long-running server unless the user explicitly changes direction.
- Do not reintroduce the old Spring web API or database schema.
- Do not commit generated CSV, ZIP, logs, local data files, rclone config, Gmail app passwords, tokens, or other secrets.
- Keep generated data under `data/` locally; this path is Git-ignored.
- Keep workflow secrets in GitHub Repository Secrets only.
- Preserve source raw fields. Do not replace raw source status values with derived labels.
- Official feeds do not expose per-row source update timestamps. `collected_at_utc` is the collector's observation time, not an official update time.
- `AVAILABLECAR` negative values must be treated as unknown, not zero.
- Roadside availability labels are provisional. Current rule is `parkingstatus=0 -> true`, `parkingstatus=1 -> false`, other values blank. Keep `parking_status_raw` and `cell_status_raw` so the rule can be corrected later.
- Roadside rows must remain filtered to Xinzhuang by `areacode=65000050`.
- Offstreet rows must be built by matching Xinzhuang lot metadata where `AREA=新莊區` against live availability by `ID`.
- Source API reads must remain paginated and bounded by safe page limits.

## Source of Truth

- `README.md`: project purpose, data flow, Google Drive layout, workflow schedule, secrets, and full output column descriptions.
- `docs/data-contract.md`: concise machine-readable data contract for CSV, ZIP, and manifest files.
- `docs/setup.md`: setup notes for Apps Script, GitHub Actions backup, Google Drive, rclone, and secrets.
- `apps-script/Code.gs`: Google Apps Script runner for collection, monitoring, recovery, ZIP export, and email.
- `apps-script/appsscript.json`: Apps Script manifest and OAuth scopes.
- `apps-script/README.md`: manual Apps Script setup checklist.
- `.github/workflows/collect.yml`: legacy 30-minute collection workflow.
- `.github/workflows/export-daily.yml`: legacy daily ZIP/email workflow.
- `.github/workflows/monitor.yml`: legacy 30-minute Google Drive freshness monitor that can run one recovery collection before failing when daily CSV files have not been updated within 90 minutes.

## Maintenance Notes

- Prefer small, explicit changes. This pipeline should stay simple.
- If adding new ML features, keep the old raw fields and document every new derived column in both `README.md` and `docs/data-contract.md`.
- If label logic changes, update the `label_rule` value and document the version/meaning clearly.
- If workflow behavior changes, verify with a manual `workflow_dispatch` run before relying on scheduled runs.
- If Apps Script behavior changes, verify manually in Apps Script with `collectParkingData()`, `monitorAndRecover()`, and `exportDailyZip()` before relying on time-driven triggers.
- The default branch is `main`; scheduled workflows run from the default branch.
