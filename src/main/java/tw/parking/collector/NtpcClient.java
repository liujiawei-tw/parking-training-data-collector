package tw.parking.collector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NtpcClient {
    private static final String BASE = "https://data.ntpc.gov.tw/api/datasets/";
    private static final String ROADSIDE = "54A507C4-C038-41B5-BF60-BBECB9D052C6";
    private static final String LOTS = "B1464EF0-9C7C-4A6F-ABF7-6BDF32847E68";
    private static final String LOT_AVAILABILITY = "e09b35a5-a738-48cc-b0f5-570b67ad9c78";
    private static final TypeReference<List<Map<String, Object>>> ROWS = new TypeReference<>() {};

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json;
    private final int pageSize;
    private final int maxPages;
    private final String areaCode;
    private final String areaName;

    public NtpcClient(ObjectMapper json,
                      @Value("${collector.page-size}") int pageSize,
                      @Value("${collector.max-pages}") int maxPages,
                      @Value("${collector.area-code}") String areaCode,
                      @Value("${collector.area-name}") String areaName) {
        this.json = json;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        this.areaCode = areaCode;
        this.areaName = areaName;
        if (pageSize < 1 || pageSize > 1000 || maxPages < 1 || maxPages > 100) {
            throw new IllegalArgumentException("Unsafe pagination settings");
        }
    }

    public List<Map<String, Object>> roadside() throws IOException, InterruptedException {
        return fetchPages(ROADSIDE, "areacode eq " + areaCode, "id", "areacode", areaCode);
    }

    public List<Map<String, Object>> xinzhuangLots() throws IOException, InterruptedException {
        return fetchPages(LOTS, "AREA eq " + areaName, "ID", "AREA", areaName);
    }

    public List<Map<String, Object>> lotAvailability() throws IOException, InterruptedException {
        // The citywide feed contains some duplicate IDs outside Xinzhuang.
        // Check duplicates after joining to Xinzhuang's static lot IDs.
        return fetchPages(LOT_AVAILABILITY, null, null, null, null);
    }

    private List<Map<String, Object>> fetchPages(String dataset, String filter, String key,
                                                 String areaField, String expectedArea)
            throws IOException, InterruptedException {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int page = 0; page < maxPages; page++) {
            StringBuilder url = new StringBuilder(BASE).append(dataset).append("/json?page=")
                    .append(page).append("&size=").append(pageSize);
            if (filter != null) {
                url.append("&$filter=").append(URLEncoder.encode(filter, StandardCharsets.UTF_8)
                        .replace("+", "%20"));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "parking-data-collector/0.1")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("NTPC HTTP " + response.statusCode() + " on page " + page);
            }
            List<Map<String, Object>> rows = json.readValue(response.body(), ROWS);
            if (rows == null) {
                throw new IOException("NTPC returned null page " + page);
            }
            for (Map<String, Object> row : rows) {
                if (key != null) {
                    String id = value(row, key);
                    if (id == null || id.isBlank() || !seen.add(id)) {
                        throw new IOException("Missing or duplicate " + key + " on page " + page);
                    }
                }
                if (areaField != null && !expectedArea.equals(value(row, areaField))) {
                    throw new IOException("Unexpected area on page " + page + ": " + value(row, areaField));
                }
                result.add(row);
            }
            if (rows.size() < pageSize) {
                if (result.isEmpty()) {
                    throw new IOException("NTPC returned no rows for " + dataset + " at " + url);
                }
                return result;
            }
        }
        throw new IOException("NTPC pagination exceeded " + maxPages + " pages for " + dataset);
    }

    static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
}
