package tw.parking.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TrainingDatasetService {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final List<String> ROADSIDE_HEADER = List.of(
            "collected_at_utc", "collected_date_taipei", "weekday_taipei", "hour_taipei",
            "minute_bucket_taipei", "source", "area_code", "spot_id", "cell_id", "road_id",
            "road_name", "spot_type", "latitude", "longitude", "parking_status_raw",
            "cell_status_raw", "is_available", "label_rule");

    private static final List<String> OFFSTREET_HEADER = List.of(
            "collected_at_utc", "collected_date_taipei", "weekday_taipei", "hour_taipei",
            "minute_bucket_taipei", "source", "lot_id", "lot_name", "address", "total_car",
            "tw97_x", "tw97_y", "available_car_raw", "available_car", "availability_ratio",
            "is_unknown", "label_rule");

    private final NtpcClient client;
    private final MailExportService mail;
    private final ObjectMapper json;
    private final Path outputDir;
    private final boolean mailEnabled;

    public TrainingDatasetService(NtpcClient client, MailExportService mail, ObjectMapper json,
                                  @Value("${collector.output-dir}") String outputDir,
                                  @Value("${collector.mail-enabled:true}") boolean mailEnabled) {
        this.client = client;
        this.mail = mail;
        this.json = json;
        this.outputDir = Path.of(outputDir);
        this.mailEnabled = mailEnabled;
    }

    public void collectOnce() throws Exception {
        ZonedDateTime collectedAt = Instant.now().atZone(TAIPEI);
        LocalDate date = collectedAt.toLocalDate();

        List<Map<String, Object>> roadside = client.roadside();
        List<List<String>> roadsideRows = roadside.stream()
                .map(row -> roadsideRow(row, collectedAt))
                .toList();
        CsvFiles.appendRows(roadsideFile(date), ROADSIDE_HEADER, roadsideRows);

        Map<String, Map<String, Object>> lots = new HashMap<>();
        for (Map<String, Object> lot : client.xinzhuangLots()) {
            lots.put(NtpcClient.value(lot, "ID"), lot);
        }
        List<List<String>> offstreetRows = new ArrayList<>();
        for (Map<String, Object> row : client.lotAvailability()) {
            Map<String, Object> lot = lots.get(NtpcClient.value(row, "ID"));
            if (lot != null) {
                offstreetRows.add(offstreetRow(row, lot, collectedAt));
            }
        }
        if (offstreetRows.isEmpty()) {
            throw new IllegalStateException("No Xinzhuang offstreet lots matched live availability");
        }
        CsvFiles.appendRows(offstreetFile(date), OFFSTREET_HEADER, offstreetRows);
    }

    public void exportDaily(LocalDate date) throws Exception {
        Path roadside = roadsideFile(date);
        Path offstreet = offstreetFile(date);
        if (Files.notExists(roadside) && Files.notExists(offstreet)) {
            throw new IllegalStateException("No daily CSV files found for " + date);
        }

        Files.createDirectories(outputDir.resolve("exports"));
        Files.createDirectories(outputDir.resolve("manifests"));
        Path manifest = manifestFile(date);
        writeManifest(date, roadside, offstreet, manifest);

        Path zip = outputDir.resolve("exports").resolve("parking-training-" + DATE.format(date) + ".zip");
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zipOut = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            addIfExists(zipOut, roadside, roadside.getFileName().toString());
            addIfExists(zipOut, offstreet, offstreet.getFileName().toString());
            addIfExists(zipOut, manifest, manifest.getFileName().toString());
        }

        if (mailEnabled) {
            mail.sendDailyExport(date, zip);
        }
    }

    private List<String> roadsideRow(Map<String, Object> row, ZonedDateTime collectedAt) {
        String parkingStatus = NtpcClient.value(row, "parkingstatus");
        return List.of(
                collectedAt.toInstant().toString(),
                DATE.format(collectedAt),
                String.valueOf(collectedAt.getDayOfWeek().getValue()),
                String.valueOf(collectedAt.getHour()),
                String.valueOf((collectedAt.getMinute() / 5) * 5),
                "ntpc_roadside",
                value(row, "areacode"),
                value(row, "id"),
                value(row, "cellid"),
                value(row, "roadid"),
                value(row, "roadname"),
                value(row, "name"),
                value(row, "latitude"),
                value(row, "longitude"),
                value(row, "parkingstatus"),
                value(row, "cellstatus"),
                roadsideAvailability(parkingStatus),
                "parkingstatus_0_available_1_unavailable_keep_raw");
    }

    private List<String> offstreetRow(Map<String, Object> availability, Map<String, Object> lot,
                                      ZonedDateTime collectedAt) {
        Integer total = integer(lot, "TOTALCAR");
        Integer available = integer(availability, "AVAILABLECAR");
        boolean unknown = available == null || available < 0;
        String ratio = "";
        if (!unknown && total != null && total > 0) {
            ratio = String.format(java.util.Locale.ROOT, "%.6f", available.doubleValue() / total);
        }
        return List.of(
                collectedAt.toInstant().toString(),
                DATE.format(collectedAt),
                String.valueOf(collectedAt.getDayOfWeek().getValue()),
                String.valueOf(collectedAt.getHour()),
                String.valueOf((collectedAt.getMinute() / 5) * 5),
                "ntpc_offstreet",
                value(availability, "ID"),
                value(lot, "NAME"),
                value(lot, "ADDRESS"),
                total == null ? "" : total.toString(),
                value(lot, "TW97X"),
                value(lot, "TW97Y"),
                value(availability, "AVAILABLECAR"),
                unknown ? "" : available.toString(),
                ratio,
                String.valueOf(unknown),
                "available_car_negative_unknown_keep_raw");
    }

    private void writeManifest(LocalDate date, Path roadside, Path offstreet, Path manifest) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("dataset", "parking-training-data");
        doc.put("dateTaipei", DATE.format(date));
        doc.put("generatedAtUtc", Instant.now().toString());
        doc.put("roadsideFile", roadside.getFileName().toString());
        doc.put("roadsideRows", dataRows(roadside));
        doc.put("offstreetFile", offstreet.getFileName().toString());
        doc.put("offstreetRows", dataRows(offstreet));
        doc.put("timezoneForFileDate", "Asia/Taipei");
        doc.put("version", "0.1");
        Files.writeString(manifest, json.writerWithDefaultPrettyPrinter().writeValueAsString(doc),
                StandardCharsets.UTF_8);
    }

    private void addIfExists(ZipOutputStream zipOut, Path file, String name) throws IOException {
        if (Files.notExists(file)) {
            return;
        }
        zipOut.putNextEntry(new ZipEntry(name));
        Files.copy(file, zipOut);
        zipOut.closeEntry();
    }

    private long dataRows(Path file) throws IOException {
        if (Files.notExists(file)) {
            return 0;
        }
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return Math.max(0, lines.count() - 1);
        }
    }

    private Path roadsideFile(LocalDate date) {
        return outputDir.resolve("daily").resolve("roadside")
                .resolve("roadside_training_samples_" + DATE.format(date) + ".csv");
    }

    private Path offstreetFile(LocalDate date) {
        return outputDir.resolve("daily").resolve("offstreet")
                .resolve("offstreet_training_samples_" + DATE.format(date) + ".csv");
    }

    private Path manifestFile(LocalDate date) {
        return outputDir.resolve("manifests").resolve("manifest_" + DATE.format(date) + ".json");
    }

    private static String roadsideAvailability(String parkingStatus) {
        if ("0".equals(parkingStatus)) {
            return "true";
        }
        if ("1".equals(parkingStatus)) {
            return "false";
        }
        return "";
    }

    private static String value(Map<String, Object> row, String key) {
        String value = NtpcClient.value(row, key);
        return value == null ? "" : value;
    }

    private static Integer integer(Map<String, Object> row, String key) {
        String value = NtpcClient.value(row, key);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }
}
