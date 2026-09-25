const CONFIG = {
  rootFolderId: '1c4KBGVdLXDi_FlmP8Q8WAq0TSZCuoA6u',
  exportMailTo: 'x0976117735@gmail.com',
  areaCode: '65000050',
  areaName: '新莊區',
  pageSize: 1000,
  maxPages: 10,
  freshnessThresholdMinutes: 90,
  baseUrl: 'https://data.ntpc.gov.tw/api/datasets/',
  datasets: {
    roadside: '54A507C4-C038-41B5-BF60-BBECB9D052C6',
    lots: 'B1464EF0-9C7C-4A6F-ABF7-6BDF32847E68',
    lotAvailability: 'e09b35a5-a738-48cc-b0f5-570b67ad9c78'
  },
  sourceMetadata: {
    roadside: {
      datasetId: '54A507C4-C038-41B5-BF60-BBECB9D052C6',
      datasetName: '新北市路邊停車空位查詢',
      updateFrequency: '每2分鐘'
    },
    offstreetAvailability: {
      datasetId: 'e09b35a5-a738-48cc-b0f5-570b67ad9c78',
      datasetName: '新北市公有路外停車場即時賸餘車位數',
      updateFrequency: '每3分鐘'
    },
    offstreetLots: {
      datasetId: 'B1464EF0-9C7C-4A6F-ABF7-6BDF32847E68',
      datasetName: '新北市路外公共停車場資訊',
      updateFrequency: '每日'
    }
  }
};

const ROADSIDE_HEADER = [
  'collected_at_utc', 'collected_date_taipei', 'weekday_taipei', 'hour_taipei',
  'minute_bucket_taipei', 'source', 'area_code', 'spot_id', 'cell_id', 'road_id',
  'road_name', 'spot_type', 'latitude', 'longitude', 'parking_status_raw',
  'cell_status_raw', 'is_available', 'label_rule', 'source_dataset_id',
  'source_dataset_name', 'source_update_frequency', 'source_api_response_at_utc'
];

const OFFSTREET_HEADER = [
  'collected_at_utc', 'collected_date_taipei', 'weekday_taipei', 'hour_taipei',
  'minute_bucket_taipei', 'source', 'lot_id', 'lot_name', 'address', 'total_car',
  'tw97_x', 'tw97_y', 'available_car_raw', 'available_car', 'availability_ratio',
  'is_unknown', 'label_rule', 'source_dataset_id', 'source_dataset_name',
  'source_update_frequency', 'source_api_response_at_utc'
];

function collectParkingData() {
  withPipelineLock_(() => {
    collectParkingDataUnlocked_();
  });
}

function monitorAndRecover() {
  withPipelineLock_(() => {
    if (isFresh_()) {
      console.log('Parking CSV files are fresh.');
      return;
    }
    console.warn('Parking CSV files are stale. Running one recovery collection.');
    collectParkingDataUnlocked_();
    if (!isFresh_()) {
      throw new Error('Parking CSV files are still stale after recovery collection.');
    }
  });
}

function exportDailyZip() {
  withPipelineLock_(() => {
    const date = addDays_(taipeiDateString_(new Date()), -1);
    exportDailyZipForDate_(date);
  });
}

function exportDailyZipForDate_(dateTaipei) {
  const root = rootFolder_();
  const daily = childFolder_(root, 'daily');
  const roadsideFolder = childFolder_(daily, 'roadside');
  const offstreetFolder = childFolder_(daily, 'offstreet');
  const manifestsFolder = childFolder_(root, 'manifests');
  const exportsFolder = childFolder_(root, 'exports');

  const roadsideName = `roadside_training_samples_${dateTaipei}.csv`;
  const offstreetName = `offstreet_training_samples_${dateTaipei}.csv`;
  const manifestName = `manifest_${dateTaipei}.json`;
  const zipName = `parking-training-${dateTaipei}.zip`;

  const roadsideFile = findFile_(roadsideFolder, roadsideName);
  const offstreetFile = findFile_(offstreetFolder, offstreetName);
  if (!roadsideFile && !offstreetFile) {
    throw new Error(`No daily CSV files found for ${dateTaipei}`);
  }

  const manifest = {
    dataset: 'parking-training-data',
    dateTaipei,
    generatedAtUtc: new Date().toISOString(),
    roadsideFile: roadsideName,
    roadsideRows: roadsideFile ? dataRows_(roadsideFile) : 0,
    offstreetFile: offstreetName,
    offstreetRows: offstreetFile ? dataRows_(offstreetFile) : 0,
    timezoneForFileDate: 'Asia/Taipei',
    version: '0.1'
  };
  const manifestFile = upsertTextFile_(
    manifestsFolder,
    manifestName,
    JSON.stringify(manifest, null, 2),
    'application/json'
  );

  const blobs = [];
  if (roadsideFile) blobs.push(roadsideFile.getBlob().setName(roadsideName));
  if (offstreetFile) blobs.push(offstreetFile.getBlob().setName(offstreetName));
  blobs.push(manifestFile.getBlob().setName(manifestName));

  const zipBlob = Utilities.zip(blobs, zipName);
  const oldZip = findFile_(exportsFolder, zipName);
  if (oldZip) oldZip.setTrashed(true);
  const zipFile = exportsFolder.createFile(zipBlob);

  MailApp.sendEmail({
    to: CONFIG.exportMailTo,
    subject: `Parking training data export - ${dateTaipei}`,
    body: `Parking training data export is attached.\n\nDate: ${dateTaipei}\nFile: ${zipName}\n`,
    attachments: [zipFile.getBlob()]
  });
}

function setupTriggers() {
  deleteProjectTriggers_();
  ScriptApp.newTrigger('collectParkingData')
    .timeBased()
    .everyMinutes(30)
    .create();
  ScriptApp.newTrigger('monitorAndRecover')
    .timeBased()
    .everyMinutes(30)
    .create();
  ScriptApp.newTrigger('exportDailyZip')
    .timeBased()
    .atHour(0)
    .nearMinute(10)
    .everyDays(1)
    .inTimezone('Asia/Taipei')
    .create();
}

function deleteProjectTriggers_() {
  for (const trigger of ScriptApp.getProjectTriggers()) {
    ScriptApp.deleteTrigger(trigger);
  }
}

function collectParkingDataUnlocked_() {
  const collectedAt = new Date();
  const dateTaipei = taipeiDateString_(collectedAt);
  const root = rootFolder_();
  const dailyFolder = childFolder_(root, 'daily');
  const roadsideFolder = childFolder_(dailyFolder, 'roadside');
  const offstreetFolder = childFolder_(dailyFolder, 'offstreet');

  const roadside = fetchRoadside_();
  const roadsideRows = roadside.rows.map(row => roadsideRow_(row, collectedAt, roadside.sourceApiResponseAtUtc));
  appendCsvRows_(
    roadsideFolder,
    `roadside_training_samples_${dateTaipei}.csv`,
    ROADSIDE_HEADER,
    roadsideRows
  );

  const lotsById = {};
  for (const lot of fetchXinzhuangLots_().rows) {
    lotsById[value_(lot, 'ID')] = lot;
  }
  const availabilityRows = fetchLotAvailability_();
  const offstreetRows = [];
  for (const availability of availabilityRows.rows) {
    const lot = lotsById[value_(availability, 'ID')];
    if (lot) offstreetRows.push(offstreetRow_(availability, lot, collectedAt, availabilityRows.sourceApiResponseAtUtc));
  }
  if (offstreetRows.length === 0) {
    throw new Error('No Xinzhuang offstreet lots matched live availability');
  }
  appendCsvRows_(
    offstreetFolder,
    `offstreet_training_samples_${dateTaipei}.csv`,
    OFFSTREET_HEADER,
    offstreetRows
  );
}

function fetchRoadside_() {
  return fetchPages_(CONFIG.datasets.roadside, `areacode eq ${CONFIG.areaCode}`, 'id', 'areacode', CONFIG.areaCode);
}

function fetchXinzhuangLots_() {
  return fetchPages_(CONFIG.datasets.lots, `AREA eq ${CONFIG.areaName}`, 'ID', 'AREA', CONFIG.areaName);
}

function fetchLotAvailability_() {
  return fetchPages_(CONFIG.datasets.lotAvailability, null, null, null, null);
}

function fetchPages_(dataset, filter, key, areaField, expectedArea) {
  const result = [];
  const seen = {};
  let sourceApiResponseAtUtc = new Date().toISOString();
  for (let page = 0; page < CONFIG.maxPages; page++) {
    let url = `${CONFIG.baseUrl}${dataset}/json?page=${page}&size=${CONFIG.pageSize}`;
    if (filter) url += `&$filter=${encodeURIComponent(filter).replace(/\+/g, '%20')}`;
    const response = UrlFetchApp.fetch(url, {
      method: 'get',
      headers: {
        Accept: 'application/json',
        'User-Agent': 'parking-data-collector-apps-script/0.1'
      },
      muteHttpExceptions: true
    });
    const status = response.getResponseCode();
    if (status !== 200) throw new Error(`NTPC HTTP ${status} on page ${page}`);
    sourceApiResponseAtUtc = responseDate_(response);
    const rows = JSON.parse(response.getContentText('UTF-8'));
    if (!Array.isArray(rows)) throw new Error(`NTPC returned non-array page ${page}`);
    for (const row of rows) {
      if (key) {
        const id = value_(row, key);
        if (!id || seen[id]) throw new Error(`Missing or duplicate ${key} on page ${page}`);
        seen[id] = true;
      }
      if (areaField && value_(row, areaField) !== expectedArea) {
        throw new Error(`Unexpected area on page ${page}: ${value_(row, areaField)}`);
      }
      result.push(row);
    }
    if (rows.length < CONFIG.pageSize) {
      if (result.length === 0) throw new Error(`NTPC returned no rows for ${dataset}`);
      return { rows: result, sourceApiResponseAtUtc };
    }
  }
  throw new Error(`NTPC pagination exceeded ${CONFIG.maxPages} pages for ${dataset}`);
}

function responseDate_(response) {
  const headers = response.getHeaders();
  const value = headers.Date || headers.date;
  if (!value) return new Date().toISOString();
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? new Date().toISOString() : parsed.toISOString();
}

function roadsideRow_(row, collectedAt, sourceApiResponseAtUtc) {
  const parkingStatus = value_(row, 'parkingstatus');
  const meta = CONFIG.sourceMetadata.roadside;
  return [
    collectedAt.toISOString(),
    taipeiDateString_(collectedAt),
    taipeiNumber_(collectedAt, 'u'),
    taipeiNumber_(collectedAt, 'H'),
    String(Math.floor(taipeiNumber_(collectedAt, 'm') / 5) * 5),
    'ntpc_roadside',
    value_(row, 'areacode'),
    value_(row, 'id'),
    value_(row, 'cellid'),
    value_(row, 'roadid'),
    value_(row, 'roadname'),
    value_(row, 'name'),
    value_(row, 'latitude'),
    value_(row, 'longitude'),
    value_(row, 'parkingstatus'),
    value_(row, 'cellstatus'),
    roadsideAvailability_(parkingStatus),
    'parkingstatus_0_available_1_unavailable_keep_raw',
    meta.datasetId,
    meta.datasetName,
    meta.updateFrequency,
    sourceApiResponseAtUtc
  ];
}

function offstreetRow_(availability, lot, collectedAt, sourceApiResponseAtUtc) {
  const total = integer_(lot, 'TOTALCAR');
  const available = integer_(availability, 'AVAILABLECAR');
  const unknown = available === null || available < 0;
  const meta = CONFIG.sourceMetadata.offstreetAvailability;
  let ratio = '';
  if (!unknown && total !== null && total > 0) {
    ratio = (available / total).toFixed(6);
  }
  return [
    collectedAt.toISOString(),
    taipeiDateString_(collectedAt),
    taipeiNumber_(collectedAt, 'u'),
    taipeiNumber_(collectedAt, 'H'),
    String(Math.floor(taipeiNumber_(collectedAt, 'm') / 5) * 5),
    'ntpc_offstreet',
    value_(availability, 'ID'),
    value_(lot, 'NAME'),
    value_(lot, 'ADDRESS'),
    total === null ? '' : String(total),
    value_(lot, 'TW97X'),
    value_(lot, 'TW97Y'),
    value_(availability, 'AVAILABLECAR'),
    unknown ? '' : String(available),
    ratio,
    String(unknown),
    'available_car_negative_unknown_keep_raw',
    meta.datasetId,
    meta.datasetName,
    meta.updateFrequency,
    sourceApiResponseAtUtc
  ];
}

function appendCsvRows_(folder, name, header, rows) {
  const file = findFile_(folder, name);
  const existing = file ? file.getBlob().getDataAsString('UTF-8') : '';
  const normalized = normalizeExistingCsv_(existing, header);
  const prefix = normalized ? normalized.replace(/\s*$/, '\n') : `${csvRow_(header)}\n`;
  const body = rows.map(csvRow_).join('\n');
  const content = body ? `${prefix}${body}\n` : prefix;
  upsertTextFile_(folder, name, content, 'text/csv');
}

function normalizeExistingCsv_(existing, header) {
  if (!existing || existing.trim().length === 0) return '';
  const lines = existing.replace(/\s*$/, '').split(/\r?\n/);
  const expectedHeader = csvRow_(header);
  if (lines[0] === expectedHeader) return lines.join('\n');
  const oldHeaderCount = lines[0].split(',').length;
  const newHeaderCount = header.length;
  if (oldHeaderCount > newHeaderCount) {
    throw new Error(`Existing CSV has more columns than expected: ${oldHeaderCount} > ${newHeaderCount}`);
  }
  const padding = ','.repeat(newHeaderCount - oldHeaderCount);
  const paddedRows = lines.slice(1).map(line => `${line}${padding}`);
  return [expectedHeader].concat(paddedRows).join('\n');
}

function upsertTextFile_(folder, name, content, mimeType) {
  const file = findFile_(folder, name);
  if (file) {
    file.setContent(content);
    return file;
  }
  return folder.createFile(name, content, mimeType);
}

function findFile_(folder, name) {
  const files = folder.getFilesByName(name);
  return files.hasNext() ? files.next() : null;
}

function childFolder_(parent, name) {
  const folders = parent.getFoldersByName(name);
  return folders.hasNext() ? folders.next() : parent.createFolder(name);
}

function rootFolder_() {
  return DriveApp.getFolderById(CONFIG.rootFolderId);
}

function isFresh_() {
  const root = rootFolder_();
  const daily = childFolder_(root, 'daily');
  const roadsideFresh = folderFresh_(childFolder_(daily, 'roadside'));
  const offstreetFresh = folderFresh_(childFolder_(daily, 'offstreet'));
  return roadsideFresh && offstreetFresh;
}

function folderFresh_(folder) {
  const latest = latestCsv_(folder);
  if (!latest) {
    console.warn(`No CSV files found in ${folder.getName()}`);
    return false;
  }
  const ageMinutes = (Date.now() - latest.getLastUpdated().getTime()) / 60000;
  console.log(`${latest.getName()} updated ${ageMinutes.toFixed(1)} minutes ago`);
  return ageMinutes <= CONFIG.freshnessThresholdMinutes;
}

function latestCsv_(folder) {
  const files = folder.getFiles();
  let latest = null;
  while (files.hasNext()) {
    const file = files.next();
    if (!file.getName().endsWith('.csv')) continue;
    if (!latest || file.getLastUpdated() > latest.getLastUpdated()) latest = file;
  }
  return latest;
}

function dataRows_(file) {
  const content = file.getBlob().getDataAsString('UTF-8').trim();
  if (!content) return 0;
  return Math.max(0, content.split(/\r?\n/).length - 1);
}

function csvRow_(values) {
  return values.map(csvCell_).join(',');
}

function csvCell_(value) {
  if (value === null || value === undefined) return '';
  const text = String(value);
  const escaped = text.replace(/"/g, '""');
  return /[",\r\n]/.test(text) ? `"${escaped}"` : escaped;
}

function value_(row, key) {
  const value = row[key];
  return value === null || value === undefined ? '' : String(value);
}

function integer_(row, key) {
  const text = value_(row, key);
  if (!text) return null;
  const value = Number.parseInt(text, 10);
  return Number.isNaN(value) ? null : value;
}

function roadsideAvailability_(parkingStatus) {
  if (parkingStatus === '0') return 'true';
  if (parkingStatus === '1') return 'false';
  return '';
}

function taipeiDateString_(date) {
  return Utilities.formatDate(date, 'Asia/Taipei', 'yyyy-MM-dd');
}

function taipeiNumber_(date, pattern) {
  return Number(Utilities.formatDate(date, 'Asia/Taipei', pattern));
}

function addDays_(dateString, days) {
  const parts = dateString.split('-').map(Number);
  const date = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2] + days, 12, 0, 0));
  return Utilities.formatDate(date, 'Asia/Taipei', 'yyyy-MM-dd');
}

function withPipelineLock_(fn) {
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}
