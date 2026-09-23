package tw.parking.collector;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CollectionService {
    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);
    private final NtpcClient client;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private List<Map<String, Object>> lotMetadata;
    private OffsetDateTime metadataFetchedAt;

    public CollectionService(NtpcClient client, JdbcTemplate db, PlatformTransactionManager txManager,
                             @Value("${collector.enabled}") boolean enabled) {
        this.client = client;
        this.db = db;
        this.tx = new TransactionTemplate(txManager);
        this.enabled = enabled;
    }

    @Scheduled(initialDelayString = "0", fixedDelayString = "${collector.interval-ms}")
    public void collectOnSchedule() {
        if (!enabled) {
            return;
        }
        collectRoadside();
        collectOffstreet();
    }

    public void collectRoadside() {
        String runId = startRun("roadside");
        try {
            List<Map<String, Object>> rows = client.roadside();
            OffsetDateTime collectedAt = now();
            tx.executeWithoutResult(status -> saveRoadside(runId, rows, collectedAt));
            log.info("Roadside collection completed: {} spots", rows.size());
        } catch (Exception failure) {
            failRun(runId, failure);
        }
    }

    public void collectOffstreet() {
        String runId = startRun("offstreet");
        try {
            OffsetDateTime fetchedAt = now();
            if (lotMetadata == null || metadataFetchedAt == null
                    || Duration.between(metadataFetchedAt, fetchedAt).toHours() >= 24) {
                List<Map<String, Object>> fresh = client.xinzhuangLots();
                lotMetadata = fresh;
                metadataFetchedAt = fetchedAt;
            }
            List<Map<String, Object>> availability = client.lotAvailability();
            Map<String, Map<String, Object>> localLots = new HashMap<>();
            for (Map<String, Object> lot : lotMetadata) {
                localLots.put(NtpcClient.value(lot, "ID"), lot);
            }
            List<Map<String, Object>> localAvailability = new ArrayList<>();
            Set<String> matchedIds = new HashSet<>();
            for (Map<String, Object> row : availability) {
                String id = NtpcClient.value(row, "ID");
                if (localLots.containsKey(id)) {
                    if (!matchedIds.add(id)) {
                        throw new IllegalStateException("Duplicate Xinzhuang lot ID: " + id);
                    }
                    localAvailability.add(row);
                }
            }
            if (localAvailability.isEmpty()) {
                throw new IllegalStateException("No Xinzhuang lots matched live availability");
            }
            OffsetDateTime collectedAt = now();
            tx.executeWithoutResult(status -> saveOffstreet(runId, localLots, localAvailability, collectedAt));
            log.info("Offstreet collection completed: {} lots", localAvailability.size());
        } catch (Exception failure) {
            failRun(runId, failure);
        }
    }

    private void saveRoadside(String runId, List<Map<String, Object>> rows, OffsetDateTime at) {
        Map<String, RoadsideState> prior = new HashMap<>();
        db.query("SELECT spot_id, parking_status, cell_status, last_changed_at FROM roadside_spot", rs -> {
            prior.put(rs.getString("spot_id"), new RoadsideState(rs.getString("parking_status"),
                    rs.getString("cell_status"), rs.getObject("last_changed_at", OffsetDateTime.class)));
        });
        List<Object[]> spots = new ArrayList<>(rows.size());
        List<Object[]> events = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String id = NtpcClient.value(row, "id");
            String parkingStatus = NtpcClient.value(row, "parkingstatus");
            String cellStatus = NtpcClient.value(row, "cellstatus");
            RoadsideState old = prior.get(id);
            boolean changed = old == null || !Objects.equals(old.parkingStatus(), parkingStatus)
                    || !Objects.equals(old.cellStatus(), cellStatus);
            OffsetDateTime changedAt = changed ? at : old.changedAt();
            spots.add(new Object[]{id, NtpcClient.value(row, "cellid"), NtpcClient.value(row, "roadid"),
                    NtpcClient.value(row, "roadname"), NtpcClient.value(row, "name"),
                    NtpcClient.value(row, "areacode"), number(row, "latitude"), number(row, "longitude"),
                    parkingStatus, cellStatus, at, changedAt});
            if (changed) {
                events.add(new Object[]{id, at, parkingStatus, cellStatus, runId});
            }
        }
        db.batchUpdate("MERGE INTO roadside_spot (spot_id, cell_id, road_id, road_name, spot_type, "
                + "area_code, latitude, longitude, parking_status, cell_status, last_seen_at, last_changed_at) "
                + "KEY (spot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", spots);
        if (!events.isEmpty()) {
            db.batchUpdate("INSERT INTO roadside_state_event "
                    + "(spot_id, observed_at, parking_status, cell_status, run_id) VALUES (?, ?, ?, ?, ?)", events);
        }
        finishRun(runId, rows.size());
    }

    private void saveOffstreet(String runId, Map<String, Map<String, Object>> lots,
                               List<Map<String, Object>> availability, OffsetDateTime at) {
        Map<String, LotState> prior = new HashMap<>();
        db.query("SELECT lot_id, available_car_raw, last_changed_at FROM offstreet_lot", rs -> {
            prior.put(rs.getString("lot_id"), new LotState(rs.getString("available_car_raw"),
                    rs.getObject("last_changed_at", OffsetDateTime.class)));
        });
        List<Object[]> current = new ArrayList<>(availability.size());
        List<Object[]> events = new ArrayList<>();
        for (Map<String, Object> row : availability) {
            String id = NtpcClient.value(row, "ID");
            Map<String, Object> lot = lots.get(id);
            String availableRaw = NtpcClient.value(row, "AVAILABLECAR");
            LotState old = prior.get(id);
            boolean changed = old == null || !Objects.equals(old.availableRaw(), availableRaw);
            OffsetDateTime changedAt = changed ? at : old.changedAt();
            current.add(new Object[]{id, NtpcClient.value(lot, "NAME"), NtpcClient.value(lot, "ADDRESS"),
                    integer(lot, "TOTALCAR"), NtpcClient.value(lot, "TW97X"),
                    NtpcClient.value(lot, "TW97Y"), availableRaw, at, changedAt});
            if (changed) {
                events.add(new Object[]{id, at, availableRaw, runId});
            }
        }
        db.batchUpdate("MERGE INTO offstreet_lot (lot_id, lot_name, address, total_car, tw97_x, tw97_y, "
                + "available_car_raw, last_seen_at, last_changed_at) KEY (lot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", current);
        if (!events.isEmpty()) {
            db.batchUpdate("INSERT INTO offstreet_state_event (lot_id, observed_at, available_car_raw, run_id) "
                    + "VALUES (?, ?, ?, ?)", events);
        }
        finishRun(runId, availability.size());
    }

    private String startRun(String source) {
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO collection_run (id, source, started_at, status) VALUES (?, ?, ?, 'RUNNING')",
                id, source, now());
        return id;
    }

    private void finishRun(String runId, int count) {
        db.update("UPDATE collection_run SET finished_at=?, status='SUCCESS', item_count=? WHERE id=?",
                now(), count, runId);
    }

    private void failRun(String runId, Exception failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        String message = failure.toString();
        if (message.length() > 900) {
            message = message.substring(0, 900);
        }
        db.update("UPDATE collection_run SET finished_at=?, status='FAILED', message=? WHERE id=?",
                now(), message, runId);
        log.error("Collection failed for run {}", runId, failure);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static Double number(Map<String, Object> row, String key) {
        String raw = NtpcClient.value(row, key);
        return raw == null || raw.isBlank() ? null : Double.valueOf(raw);
    }

    private static Integer integer(Map<String, Object> row, String key) {
        String raw = NtpcClient.value(row, key);
        return raw == null || raw.isBlank() ? null : Integer.valueOf(raw);
    }

    private record RoadsideState(String parkingStatus, String cellStatus, OffsetDateTime changedAt) {}
    private record LotState(String availableRaw, OffsetDateTime changedAt) {}
}
