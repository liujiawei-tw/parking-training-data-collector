# Data Contract v0.4

The exported files are daily CSV training datasets.
File dates use `Asia/Taipei`; collection timestamps are stored in UTC.
Apps Script CSV files are written with a UTF-8 BOM to keep Chinese text readable after ZIP extraction on Windows.

The New Taipei City source pages expose official dataset update frequency, such as `每2分鐘` or `每3分鐘`.
The row APIs do not expose an official per-row update timestamp, so this contract records the official API response time as `source_api_response_at_utc` and the official published frequency as `source_update_frequency`.

## Roadside File

File name:

```text
roadside_training_samples_yyyy-MM-dd.csv
```

Columns:

```text
collected_at_utc
collected_date_taipei
weekday_taipei
hour_taipei
minute_bucket_taipei
source
area_code
spot_id
cell_id
road_id
road_name
spot_type
latitude
longitude
parking_status_raw
cell_status_raw
is_available
availability_state
not_confirmed_reason
label_rule
source_dataset_id
source_dataset_name
source_update_frequency
source_api_response_at_utc
collected_at_taipei
```

Rows are filtered to Xinzhuang by `areacode=65000050`.

Current roadside label rule:

```text
label_rule ntpc_roadside_confirmed_available_status_2_else_false_v1
parkingstatus 1 -> availability_state OCCUPIED, is_available false
parkingstatus 2 -> availability_state AVAILABLE, is_available true
parkingstatus 3 -> availability_state RESTRICTED_OR_CLOSED, is_available false, not_confirmed_reason restricted_or_closed_status
parkingstatus 5 -> availability_state UNKNOWN_OR_SPECIAL, is_available false, not_confirmed_reason unknown_or_special_status
other values -> availability_state UNKNOWN, is_available false, not_confirmed_reason unverified_parkingstatus
```

The raw status fields are preserved because the rule may need to be corrected after validating official status values. This binary label means "confirmed generally available"; `false` includes occupied, restricted, special, unknown, and unverified statuses, so it must not be described as a guaranteed physical no-space observation.

## Offstreet File

File name:

```text
offstreet_training_samples_yyyy-MM-dd.csv
```

Columns:

```text
collected_at_utc
collected_date_taipei
weekday_taipei
hour_taipei
minute_bucket_taipei
source
lot_id
lot_name
address
total_car
tw97_x
tw97_y
available_car_raw
available_car
availability_ratio
is_unknown
label_rule
source_dataset_id
source_dataset_name
source_update_frequency
source_api_response_at_utc
collected_at_taipei
```

Rows are built by matching Xinzhuang public offstreet lot metadata with the citywide live availability feed.

`available_car_raw` is preserved.
Negative values are treated as unknown for numeric training columns:

```text
available_car = blank
availability_ratio = blank
is_unknown = true
```

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

The manifest records the dataset date, generation time, file names, row counts, timezone, and schema version.
