import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public class StationFallbackTest {
    static final AtomicReference<String> scenario = new AtomicReference<>("A");
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", StationFallbackTest::handle);
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            WeatherApiClient client = new WeatherApiClient(HttpClient.newHttpClient(), base);
            for (String s : new String[] {"A", "B", "C", "D", "E"}) {
                scenario.set(s);
                try {
                    WeatherObservation o = client.fetchLatest(39.7, -121.97);
                    System.out.println(s + ": station=" + o.getStationId()
                            + " speed=" + o.getWindSpeedMetersPerSecond()
                            + " dirFrom=" + o.getWindDirectionFromDegrees()
                            + " fresh=" + o.isFreshAt(Instant.now(), Duration.ofMinutes(90))
                            + " windToward=" + o.toWindToward()
                                    .map(w -> w.getSpeedMetersPerSecond() + "@" + w.getDirectionDegrees())
                                    .orElse("none"));
                } catch (IOException e) {
                    System.out.println(s + ": IOException " + e.getMessage());
                }
            }
        } finally { server.stop(0); }
    }
    static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        int port = ex.getLocalAddress().getPort();
        String sc = scenario.get();
        int status = 200; String body;
        String now = Instant.now().toString();
        String old = Instant.now().minus(Duration.ofHours(3)).toString();
        if (path.startsWith("/points/")) {
            body = "{\"properties\":{\"observationStations\":\"http://127.0.0.1:" + port + "/stations\"}}";
        } else if (path.equals("/stations")) {
            body = "{\"features\":["
                + feat(port,"AAA1") + "," + feat(port,"BBB2") + "," + feat(port,"CCC3") + "]}";
        } else {
            String st = path.split("/")[2];
            // A: AAA1 has speed but no direction (not calm) -> should fall through to BBB2
            // B: AAA1 is calm (speed 0, no direction) -> usable
            // C: every station is stale -> fall back to the first one, marked not fresh
            // D: AAA1 returns HTTP 503 -> BBB2 used
            // E: everything returns HTTP 503 -> IOException
            String ts = now, spd = "36", dir = "270";
            if (sc.equals("A") && st.equals("AAA1")) { spd = "36"; dir = "null"; }
            if (sc.equals("B") && st.equals("AAA1")) { spd = "0"; dir = "null"; }
            if (sc.equals("C")) { ts = old; }
            if ((sc.equals("D") && st.equals("AAA1")) || sc.equals("E")) { status = 503; }
            body = "{\"properties\":{\"stationId\":\"" + st + "\",\"timestamp\":\"" + ts + "\","
                + "\"windSpeed\":{\"value\":" + spd + ",\"unitCode\":\"wmoUnit:km_h-1\"},"
                + "\"windDirection\":{\"value\":" + dir + "},"
                + "\"temperature\":{\"value\":25},\"relativeHumidity\":{\"value\":24}}}";
        }
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/geo+json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b); ex.close();
    }
    static String feat(int port, String id) {
        return "{\"id\":\"http://127.0.0.1:" + port + "/stations/" + id
            + "\",\"properties\":{\"stationIdentifier\":\"" + id + "\",\"name\":\"" + id + "\"}}";
    }
}
