import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class GeocodingApiClient {

    private static final URI NOMINATIM_SEARCH_URI =
            URI.create("https://nominatim.openstreetmap.org/search");
    private static final long MIN_REQUEST_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private final HttpClient httpClient;
    private final URI searchUri;
    private long lastRequestNanos;

    public GeocodingApiClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(8))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                NOMINATIM_SEARCH_URI);
    }

    GeocodingApiClient(HttpClient httpClient, URI searchUri) {
        if (httpClient == null || searchUri == null) {
            throw new IllegalArgumentException("HTTP client and geocoding URI are required.");
        }
        this.httpClient = httpClient;
        this.searchUri = searchUri;
    }

    public synchronized GeoLocation search(String query) throws IOException, InterruptedException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a city, address, or ZIP code to search.");
        }

        long elapsed = System.nanoTime() - lastRequestNanos;
        long waitNanos = MIN_REQUEST_INTERVAL_NANOS - elapsed;
        if (lastRequestNanos != 0L && waitNanos > 0L) {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        }

        String separator = searchUri.toString().contains("?") ? "&" : "?";
        URI requestUri = URI.create(searchUri + separator
                + "format=jsonv2&limit=1&q="
                + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(java.time.Duration.ofSeconds(12))
                .header("User-Agent", "CALSAFE-Wildfire-Readiness/1.0")
                .header("Accept", "application/json")
                .GET()
                .build();
        lastRequestNanos = System.nanoTime();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Location search returned HTTP " + response.statusCode() + ".");
        }

        try {
            List<Object> results = JsonParser.array(JsonParser.parse(response.body()));
            if (results.isEmpty()) {
                throw new IOException("No matching location was found. Try a city or ZIP code.");
            }
            Map<String, Object> result = JsonParser.object(results.get(0));
            String name = JsonParser.string(result.get("display_name"));
            String latitude = JsonParser.string(result.get("lat"));
            String longitude = JsonParser.string(result.get("lon"));
            if (name == null || latitude == null || longitude == null) {
                throw new IOException("Location search returned an incomplete result.");
            }
            return new GeoLocation(name, Double.parseDouble(latitude), Double.parseDouble(longitude));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Location search returned invalid location data.", exception);
        }
    }
}
