import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FireDataApiClient {

    private static final String CAL_FIRE_LAYER =
            "https://services1.arcgis.com/jUJYIo9tSA7EHvfZ/arcgis/rest/services/"
                    + "Power_Intel_CRM_Incidents_view/FeatureServer/0";
    private static final String FIRMS_BASE =
            "https://firms.modaps.eosdis.nasa.gov/api/area/csv/";
    private static final String NORTHERN_CALIFORNIA_BOUNDS = "-124,36.5,-119,42";
    private final HttpClient httpClient;
    private final URI calFireLayerUri;
    private final String firmsMapKey;

    public FireDataApiClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(8))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                URI.create(System.getenv().getOrDefault("CAL_FIRE_LAYER_URL", CAL_FIRE_LAYER)),
                System.getenv("NASA_FIRMS_MAP_KEY"));
    }

    FireDataApiClient(HttpClient httpClient, URI calFireLayerUri, String firmsMapKey) {
        this.httpClient = httpClient;
        this.calFireLayerUri = calFireLayerUri;
        this.firmsMapKey = firmsMapKey == null || firmsMapKey.trim().isEmpty()
                ? null : firmsMapKey.trim();
    }

    public boolean isFirmsConfigured() {
        return firmsMapKey != null;
    }

    public List<ObservedFire> fetchCalFireIncidents() throws IOException, InterruptedException {
        String query = "/query?where=1%3D1"
                + "&outFields=OBJECTID,crbe2_incidentid,crbe2_incidentname,"
                + "crbe2_size_acres,crbe2_pct_containment,crbe2_startdate,modifiedon"
                + "&outSR=4326"
                + "&geometry=" + encodePath(NORTHERN_CALIFORNIA_BOUNDS)
                + "&geometryType=esriGeometryEnvelope&inSR=4326"
                + "&spatialRel=esriSpatialRelIntersects&returnGeometry=true&f=json";
        Map<String, Object> response = getJson(
                URI.create(calFireLayerUri.toString().replaceAll("/+$", "") + query),
                "CAL FIRE");
        if (response.containsKey("error")) {
            throw new IOException("CAL FIRE ArcGIS API returned an error: "
                    + response.get("error"));
        }
        Object featuresValue = response.get("features");
        if (featuresValue == null) {
            throw new IOException("CAL FIRE response did not contain incident features.");
        }

        Instant fetchedAt = Instant.now();
        List<ObservedFire> incidents = new ArrayList<>();
        for (Object featureValue : JsonParser.array(featuresValue)) {
            Map<String, Object> feature = JsonParser.object(featureValue);
            Map<String, Object> attributes = JsonParser.object(feature.get("attributes"));
            Map<String, Object> geometry = feature.get("geometry") == null
                    ? new HashMap<>() : JsonParser.object(feature.get("geometry"));
            Double latitude = JsonParser.number(geometry.get("y"));
            Double longitude = JsonParser.number(geometry.get("x"));
            if (!isNorthernCalifornia(latitude, longitude)) {
                continue;
            }

            String id = firstNonEmpty(
                    JsonParser.string(attributes.get("crbe2_incidentid")),
                    stringValue(attributes.get("OBJECTID")));
            String name = firstNonEmpty(
                    JsonParser.string(attributes.get("crbe2_incidentname")),
                    "CAL FIRE incident " + id);
            Instant observedAt = firstInstant(
                    attributes.get("modifiedon"),
                    attributes.get("crbe2_startdate"));
            String details = formatDetails(
                    "Acres", attributes.get("crbe2_size_acres"),
                    "containment %", attributes.get("crbe2_pct_containment"));
            incidents.add(new ObservedFire(
                    ObservedFire.Source.CAL_FIRE_INCIDENT,
                    id,
                    name,
                    observedAt,
                    fetchedAt,
                    latitude,
                    longitude,
                    details));
        }
        incidents.sort(Comparator.comparing(
                ObservedFire::getObservedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return incidents;
    }

    public List<ObservedFire> fetchFirmsDetections() throws IOException, InterruptedException {
        if (firmsMapKey == null) {
            throw new IllegalStateException(
                    "NASA_FIRMS_MAP_KEY is not set; FIRMS detections were not requested.");
        }

        URI uri = URI.create(FIRMS_BASE
                + encodePath(firmsMapKey)
                + "/VIIRS_SNPP_NRT/"
                + encodePath(NORTHERN_CALIFORNIA_BOUNDS)
                + "/1");
        String csv = getText(uri, "NASA FIRMS");
        return parseFirmsCsv(csv, Instant.now());
    }

    static List<ObservedFire> parseFirmsCsv(String csv, Instant fetchedAt) throws IOException {
        List<List<String>> rows = parseCsv(csv);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = rows.get(0);
        for (int index = 0; index < header.size(); index++) {
            columns.put(header.get(index).trim().toLowerCase(Locale.ROOT), index);
        }
        requireColumns(columns, "latitude", "longitude", "acq_date", "acq_time");

        List<ObservedFire> detections = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            try {
                Double latitude = parseDouble(column(row, columns, "latitude"));
                Double longitude = parseDouble(column(row, columns, "longitude"));
                if (!isNorthernCalifornia(latitude, longitude)) {
                    continue;
                }

                Instant acquiredAt = parseAcquisitionTime(
                        column(row, columns, "acq_date"),
                        column(row, columns, "acq_time"));
                String satellite = firstNonEmpty(
                        column(row, columns, "satellite"),
                        column(row, columns, "instrument"),
                        "VIIRS");
                String id = satellite + "-" + acquiredAt + "-"
                        + latitude + "-" + longitude;
                String details = formatDetails(
                        "confidence", column(row, columns, "confidence"),
                        "FRP MW", column(row, columns, "frp"),
                        "brightness K", column(row, columns, "bright_ti4"));
                detections.add(new ObservedFire(
                        ObservedFire.Source.NASA_FIRMS_DETECTION,
                        id,
                        satellite + " thermal anomaly",
                        acquiredAt,
                        fetchedAt,
                        latitude,
                        longitude,
                        details));
            } catch (java.time.DateTimeException | NumberFormatException exception) {
                throw new IOException(
                        "NASA FIRMS returned invalid CSV row " + (rowIndex + 1) + ".",
                        exception);
            }
        }
        detections.sort(Comparator.comparing(
                ObservedFire::getObservedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return detections;
    }

    private Map<String, Object> getJson(URI uri, String source)
            throws IOException, InterruptedException {
        String body = getText(uri, source);
        try {
            return JsonParser.object(JsonParser.parse(body));
        } catch (IllegalArgumentException exception) {
            throw new IOException(source + " returned invalid JSON.", exception);
        }
    }

    private String getText(URI uri, String source) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(12))
                .header("User-Agent", "CALSAFE-Wildfire-Readiness/1.0")
                .header("Accept", "application/json, text/csv, text/plain")
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(source + " API returned HTTP " + response.statusCode()
                    + " for " + uri.getPath());
        }
        return response.body();
    }

    private static List<List<String>> parseCsv(String csv) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char current = csv.charAt(index);
            if (quoted) {
                if (current == '"' && index + 1 < csv.length()
                        && csv.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else if (current == '"') {
                    quoted = false;
                } else {
                    field.append(current);
                }
            } else if (current == '"' && field.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (current == '\n' || current == '\r') {
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
                if (current == '\r' && index + 1 < csv.length()
                        && csv.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IOException("NASA FIRMS returned a CSV with an unterminated quoted field.");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static Instant parseAcquisitionTime(String date, String time) {
        LocalDate acquisitionDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        String normalizedTime = String.format(Locale.ROOT, "%04d", Integer.parseInt(time.trim()));
        LocalTime acquisitionTime = LocalTime.parse(normalizedTime, DateTimeFormatter.ofPattern("HHmm"));
        return acquisitionDate.atTime(acquisitionTime).toInstant(ZoneOffset.UTC);
    }

    private static Instant firstInstant(Object first, Object second) {
        Instant result = epochMillis(first);
        return result == null ? epochMillis(second) : result;
    }

    private static Instant epochMillis(Object value) {
        Double number = JsonParser.number(value);
        return number == null ? null : Instant.ofEpochMilli(number.longValue());
    }

    private static void requireColumns(Map<String, Integer> columns, String... required)
            throws IOException {
        for (String column : required) {
            if (!columns.containsKey(column)) {
                throw new IOException("NASA FIRMS CSV is missing required column: " + column);
            }
        }
    }

    private static String column(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private static Double parseDouble(String value) {
        return value == null || value.isEmpty() ? null : Double.valueOf(value);
    }

    private static String formatDetails(Object... labelledValues) {
        List<String> parts = new ArrayList<>();
        for (int index = 0; index + 1 < labelledValues.length; index += 2) {
            Object value = labelledValues[index + 1];
            if (value != null && !value.toString().isEmpty()) {
                parts.add(labelledValues[index] + ": " + value);
            }
        }
        return String.join(", ", parts);
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second : first;
    }

    private static String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null || second.isEmpty() ? third : second;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
                .replace("%2C", ",");
    }

    private static boolean isNorthernCalifornia(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && latitude >= 36.5 && latitude <= 42.0
                && longitude >= -124.0 && longitude <= -119.0;
    }
}
