package tw.parking.collector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CollectorApi {
    private final JdbcTemplate db;

    public CollectorApi(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/collector/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("scope", "新北市新莊區");
        health.put("sources", db.queryForList("SELECT source AS \"source\", status AS \"status\", "
                + "started_at AS \"startedAt\", finished_at AS \"finishedAt\", "
                + "item_count AS \"itemCount\", message AS \"message\" "
                + "FROM collection_run WHERE id IN "
                + "(SELECT id FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY source ORDER BY started_at DESC) "
                + "AS rn FROM collection_run) WHERE rn=1) ORDER BY source"));
        return health;
    }

    @GetMapping("/observations/roadside/latest")
    public List<Map<String, Object>> roadside() {
        return db.queryForList("SELECT spot_id AS \"spotId\", cell_id AS \"cellId\", "
                + "road_id AS \"roadId\", road_name AS \"roadName\", spot_type AS \"spotType\", "
                + "area_code AS \"areaCode\", latitude AS \"latitude\", longitude AS \"longitude\", "
                + "parking_status AS \"parkingStatusRaw\", cell_status AS \"cellStatusRaw\", "
                + "last_seen_at AS \"collectedAt\", last_changed_at AS \"lastChangedAt\" "
                + "FROM roadside_spot WHERE last_seen_at=(SELECT MAX(last_seen_at) FROM roadside_spot) "
                + "ORDER BY spot_id");
    }

    @GetMapping("/observations/offstreet/latest")
    public List<Map<String, Object>> offstreet() {
        return db.queryForList("SELECT lot_id AS \"lotId\", lot_name AS \"lotName\", "
                + "address AS \"address\", total_car AS \"totalCar\", tw97_x AS \"tw97X\", "
                + "tw97_y AS \"tw97Y\", available_car_raw AS \"availableCarRaw\", "
                + "last_seen_at AS \"collectedAt\", last_changed_at AS \"lastChangedAt\" "
                + "FROM offstreet_lot WHERE last_seen_at=(SELECT MAX(last_seen_at) FROM offstreet_lot) "
                + "ORDER BY lot_id");
    }
}
