import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WeatherApiClient {

    private static final String USER_AGENT = "CALSAFE-Wildfire-Readiness/1.0";
    private static final int MAX_STATIONS_TO_TRY = 5;
    private final HttpClient httpClient;
    private final URI baseUri;

    public WeatherApiClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                URI.create("https://api.weather.gov"));
    }

    WeatherApiClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = httpClient;
        this.baseUri = baseUri;
    }

    public WeatherObservation fetchLatest(double latitude, double longitude)
            throws IOException, InterruptedException {
        validateCoordinates(latitude, longitude);
        String coordinates = String.format(java.util.Locale.ROOT, "%.4f,%.4f", latitude, longitude);
        Map<String, Object> point = getObject(baseUri.resolve("/points/" + coordinates));
        Map<String, Object> pointProperties = JsonParser.object(point.get("properties"));
        String stationsUrl = JsonParser.string(pointProperties.get("observationStations"));
        if (stationsUrl == null) {
            throw new IOException("NWS point response did not include observation stations.");
        }

        Map<String, Object> stations = getObject(URI.create(stationsUrl));
        List<Object> features = JsonParser.array(stations.get("features"));
        if (features.isEmpty()) {
            throw new IOException("NWS returned no observation stations for this location.");
        }

        // Try the nearest few stations. Some stations report no wind direction
        // or have an old reading, so keep going until one is usable.
        WeatherObservation firstReading = null;
        IOException lastError = null;
        int tried = 0;
        for (Object feature : features) {
            if (tried >= MAX_STATIONS_TO_TRY) {
                break;
            }
            tried++;
            try {
                WeatherObservation observation =
                        fetchStationObservation(JsonParser.object(feature));
                if (isUsable(observation)) {
                    return observation;
                }
                if (firstReading == null) {
                    firstReading = observation;
                }
            } catch (IOException exception) {
                lastError = exception;
            }
        }
        if (firstReading != null) {
            return firstReading;
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("NWS returned no usable observation for this location.");
    }

    /** Fresh reading that has a wind speed and either a direction or calm air. */
    private static boolean isUsable(WeatherObservation observation) {
        if (!observation.isFreshAt(Instant.now(), WeatherMonitor.MAX_OBSERVATION_AGE)) {
            return false;
        }
        Double speed = observation.getWindSpeedMetersPerSecond();
        return speed != null
                && (observation.getWindDirectionFromDegrees() != null || speed == 0.0);
    }

    private WeatherObservation fetchStationObservation(Map<String, Object> stationFeature)
            throws IOException, InterruptedException {
        String stationUrl = JsonParser.string(stationFeature.get("id"));
        Map<String, Object> stationProperties =
                JsonParser.object(stationFeature.get("properties"));
        if (stationUrl == null) {
            throw new IOException("NWS observation station has no URL.");
        }

        Map<String, Object> latest = getObject(
                URI.create(stationUrl.replaceAll("/+$", "") + "/observations/latest"));
        Map<String, Object> properties = JsonParser.object(latest.get("properties"));
        Instant fetchedAt = Instant.now();
        Instant observedAt = parseInstant(JsonParser.string(properties.get("timestamp")));
        if (observedAt == null) {
            throw new IOException("NWS latest observation has no valid timestamp.");
        }

        Double windSpeed = value(properties.get("windSpeed"), 1.0 / 3.6);
        Double windGust = value(properties.get("windGust"), 1.0 / 3.6);
        Double direction = value(properties.get("windDirection"), 1.0);
        Double pressure = value(properties.get("barometricPressure"), 1.0 / 100.0);
        Double visibility = value(properties.get("visibility"), 1.0 / 1000.0);
        Double precipitation = value(properties.get("precipitationLast3Hours"), 1.0);

        return new WeatherObservation(
                firstNonEmpty(
                        JsonParser.string(properties.get("stationId")),
                        JsonParser.string(stationProperties.get("stationIdentifier"))),
                firstNonEmpty(
                        JsonParser.string(properties.get("stationName")),
                        JsonParser.string(stationProperties.get("name"))),
                observedAt,
                fetchedAt,
                JsonParser.string(properties.get("textDescription")),
                windSpeed,
                direction,
                windGust,
                value(properties.get("temperature"), 1.0),
                value(properties.get("relativeHumidity"), 1.0),
                pressure,
                visibility,
                precipitation);
    }

    private Map<String, Object> getObject(URI uri) throws IOException, InterruptedException {
        if (!baseUri.getScheme().equalsIgnoreCase(uri.getScheme())
                || !uri.getHost().equalsIgnoreCase(baseUri.getHost())) {
            throw new IOException("NWS returned an unexpected API URL: " + uri);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/geo+json")
                .GET()
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("NWS API returned HTTP " + response.statusCode()
                    + " for " + uri.getPath());
        }
        try {
            return JsonParser.object(JsonParser.parse(response.body()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("NWS API returned invalid JSON for " + uri.getPath(), exception);
        }
    }

    private static Double value(Object measurement, double conversionFactor) {
        if (!(measurement instanceof Map)) {
            return null;
        }
        Double raw = JsonParser.number(JsonParser.object(measurement).get("value"));
        return raw == null ? null : raw * conversionFactor;
    }

    private static Instant parseInstant(String timestamp) throws IOException {
        if (timestamp == null) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (java.time.DateTimeException exception) {
            throw new IOException("NWS returned an invalid observation timestamp.", exception);
        }
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second : first;
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0
                || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Weather location coordinates are invalid.");
        }
    }
}
