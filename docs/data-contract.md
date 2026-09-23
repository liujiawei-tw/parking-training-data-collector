# Data Contract v0.2

The exported files are daily CSV training datasets.
File dates use `Asia/Taipei`; collection timestamps are stored in UTC.

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
label_rule
```

Rows are filtered to Xinzhuang by `areacode=65000050`.

Current roadside label rule:

```text
parkingstatus 0 -> is_available true
parkingstatus 1 -> is_available false
other values -> blank
```

The raw status fields are preserved because the rule may need to be corrected after validating the official status values.

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
