import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExternalDataIntegrationTest {

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--live".equals(args[0])) {
            WeatherObservation weather =
                    new WeatherApiClient().fetchLatest(38.58, -121.49);
            System.out.println("Live NWS observation: " + weather.getStationId()
                    + " observed " + weather.getObservedAt()
                    + ", speed " + weather.getWindSpeedMetersPerSecond()
                    + " m/s from " + weather.getWindDirectionFromDegrees() + " degrees.");

            FireDataApiClient fireClient = new FireDataApiClient();
            List<ObservedFire> incidents = fireClient.fetchCalFireIncidents();
            System.out.println("Live CAL FIRE Northern California incidents: "
                    + incidents.size());
            if (fireClient.isFirmsConfigured()) {
                List<ObservedFire> detections = fireClient.fetchFirmsDetections();
                System.out.println("Live NASA FIRMS Northern California detections: "
                        + detections.size());
            } else {
                System.out.println("NASA FIRMS check skipped: NASA_FIRMS_MAP_KEY is not set.");
            }
            return;
        }

        FireSimulation csvGrid = Main.loadSimulation();
        require(csvGrid.getRows() == 20 && csvGrid.getColumns() == 20,
                "CSV-backed simulation is capped at a 20 x 20 grid");
        require(csvGrid.getCell(0, 0).getFuelModel() == 102
                && csvGrid.getCell(19, 19).getFuelModel() == 102,
                "20 x 20 simulation uses the centered CSV window");

        AtomicBoolean failRequests = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, failRequests));
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            HttpClientFixture fixture = new HttpClientFixture();
            GeocodingApiClient geocodingClient = new GeocodingApiClient(
                    fixture.client(), baseUri.resolve("/geocode"));
            GeoLocation location = geocodingClient.search("Redding, CA 96001");
            require(location.getDisplayName().equals("Redding, California, United States"),
                    "location search display name");
            require(location.getLatitude() == 40.5862
                    && location.getLongitude() == -122.3914,
                    "location search coordinates");

            WeatherApiClient weatherClient = new WeatherApiClient(fixture.client(), baseUri);
            WeatherObservation weather = weatherClient.fetchLatest(38.58, -121.49);
            require("KSAC".equals(weather.getStationId()), "NWS station selection");
            require(Math.abs(weather.getWindSpeedMetersPerSecond() - 10.0) < 0.001,
                    "NWS wind speed conversion");
            require(weather.toWindToward().get().getDirectionDegrees() == 90.0,
                    "NWS from-direction converted to model toward-direction");
            require("West (from)".equals(FireSimulationGUI.formatWindFrom(270.0)),
                    "weather panel labels the observed wind direction as from");
            require("22.4 mph toward East".equals(
                    FireSimulationGUI.formatWindToward(weather.toWindToward().get())),
                    "weather summary labels the converted wind direction as toward");
            require(weather.getRelativeHumidityPercent() == 24.0, "NWS humidity parsing");
            require(weather.isFreshAt(Instant.now(), Duration.ofMinutes(90)),
                    "current observation is fresh");
            WeatherObservation staleWeather = new WeatherObservation(
                    "KSAC", "Sacramento", Instant.now().minus(Duration.ofHours(2)),
                    Instant.now(), "Clear", 10.0, 270.0, null, 20.0, 24.0,
                    1017.0, 16.0, null);
            require(!staleWeather.isFreshAt(Instant.now(), Duration.ofMinutes(90)),
                    "old observation is rejected as stale");
            ObservationFeed staleFeed = new ObservationFeed(
                    "CAL FIRE",
                    true,
                    java.util.Collections.emptyList(),
                    Instant.now().minus(Duration.ofHours(1)),
                    null);
            require(staleFeed.isStaleAt(Instant.now(), FireObservationMonitor.MAX_FEED_AGE),
                    "old incident-feed snapshot is marked stale");

            String currentDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
            List<ObservedFire> detections = FireDataApiClient.parseFirmsCsv(
                    "latitude,longitude,acq_date,acq_time,satellite,confidence,frp,bright_ti4\r\n"
                            + "39.1,-121.4," + currentDate
                            + ",1430,Suomi-NPP,high,4.2,320.1\r\n",
                    Instant.now());
            require(detections.size() == 1, "FIRMS detection parsing");
            require(detections.get(0).getSource()
                    == ObservedFire.Source.NASA_FIRMS_DETECTION, "FIRMS source tag");
            require(detections.get(0).getObservedAt() != null,
                    "FIRMS acquisition timestamp");

            FireDataApiClient fireClient = new FireDataApiClient(
                    fixture.client(),
                    baseUri.resolve("/cal/fire/FeatureServer/0"),
                    "test-map-key");
            List<ObservedFire> incidents = fireClient.fetchCalFireIncidents();
            require(incidents.size() == 1, "CAL FIRE incident query");
            require(incidents.get(0).getSource()
                    == ObservedFire.Source.CAL_FIRE_INCIDENT, "CAL FIRE source tag");
            require(incidents.get(0).getObservedAt() != null,
                    "CAL FIRE update timestamp");

            FireSimulation simulation = new FireSimulation(
                    new int[][] { { 1 } },
                    new int[][] { { 101 } },
                    30.0,
                    30.0,
                    Wind.calm(),
                    1L);
            FireState before = simulation.getCell(0, 0).getState();
            ObservedFire incident = new ObservedFire(
                    ObservedFire.Source.CAL_FIRE_INCIDENT,
                    "CA-123",
                    "Observed incident",
                    Instant.now(),
                    Instant.now(),
                    39.1,
                    -121.4,
                    "Acres: 10");
            require(before == FireState.UNBURNED, "simulation starts unburned");
            GeoLocation mapCenter = new GeoLocation("Test location", 39.1, -121.4);
            int[] fireCell = FireGridMapper.toCell(
                    mapCenter, incident, 100, 100, 30.0);
            require(fireCell != null && fireCell[0] == 50 && fireCell[1] == 50,
                    "observed fire at the searched location maps to the grid center");
            ObservedFire distantFire = new ObservedFire(
                    ObservedFire.Source.CAL_FIRE_INCIDENT,
                    "CA-far",
                    "Distant fire",
                    Instant.now(),
                    Instant.now(),
                    39.2,
                    -121.4,
                    null);
            require(FireGridMapper.toCell(
                    mapCenter, distantFire, 100, 100, 30.0) == null,
                    "observed fire outside the map extent is not plotted");
            require(!detections.isEmpty() && incident.getSource()
                    == ObservedFire.Source.CAL_FIRE_INCIDENT,
                    "actual fires have independent source records");
            require(!incidents.isEmpty()
                    && incidents.get(0).getSource() == ObservedFire.Source.CAL_FIRE_INCIDENT,
                    "CAL FIRE records are independent observations");
            require(simulation.getCell(0, 0).getState() == before,
                    "actual observations do not modify simulated cell state");
            require(simulation.getTimeSeconds() == 0.0,
                    "actual observations do not advance simulation time");
            require(simulation.applyWeatherObservation(
                    weather, Instant.now(), WeatherMonitor.MAX_OBSERVATION_AGE),
                    "fresh weather sample accepted by simulation");
            require(simulation.getWeatherObservation() == weather,
                    "weather sample timestamp is retained by simulation");
            require(!simulation.applyWeatherObservation(
                    staleWeather, Instant.now(), WeatherMonitor.MAX_OBSERVATION_AGE),
                    "stale weather sample is rejected by simulation");
            require(simulation.getWeatherObservation() == weather,
                    "rejected stale weather does not replace current input");
            simulation.clearWeatherObservation();
            require(simulation.getWeatherObservation() == null,
                    "stale weather can be cleared from simulation input");

            FireSimulation forecastGrid = new FireSimulation(
                    new int[][] {
                            { 1, 1, 1, 1, 1 },
                            { 1, 1, 1, 1, 1 },
                            { 1, 1, 1, 1, 1 },
                            { 1, 1, 1, 1, 1 },
                            { 1, 1, 1, 1, 1 }
                    },
                    new int[][] {
                            { 101, 101, 101, 101, 101 },
                            { 101, 101, 101, 101, 101 },
                            { 101, 101, 101, 101, 101 },
                            { 101, 101, 101, 101, 101 },
                            { 101, 101, 101, 101, 101 }
                    },
                    30.0,
                    30.0,
                    Wind.calm(),
                    9L);
            Wind eastWind = weather.toWindToward().get();
            require(eastWind.getDirectionDegrees() == 90.0,
                    "fire forecast uses the observed NWS wind-toward direction");
            double[][] forecast = FireSpreadForecaster.estimateArrivalTimes(
                    forecastGrid, eastWind, java.util.Collections.singletonList(new int[] { 2, 2 }));
            require(forecast[2][2] == 0.0, "reported fire cell is forecast at time zero");
            require(forecast[2][3] < forecast[2][1],
                    "current wind makes downwind neighbor arrival earlier");
            double eastNeighborChance = forecastGrid.estimateIgnitionProbability(
                    2, 3, 2, 2, eastWind, 30.0);
            double westNeighborChance = forecastGrid.estimateIgnitionProbability(
                    2, 1, 2, 2, eastWind, 30.0);
            require(eastNeighborChance > westNeighborChance
                    && eastNeighborChance > 0.0 && eastNeighborChance < 1.0,
                    "neighbor ignition chance responds to wind direction");
            Wind southWind = new Wind(8.0, 180.0);
            double[][] southForecast = FireSpreadForecaster.estimateArrivalTimes(
                    forecastGrid, southWind,
                    java.util.Collections.singletonList(new int[] { 2, 2 }));
            double southNeighborChance = forecastGrid.estimateIgnitionProbability(
                    3, 2, 2, 2, southWind, 30.0);
            double northNeighborChance = forecastGrid.estimateIgnitionProbability(
                    1, 2, 2, 2, southWind, 30.0);
            require(southForecast[3][2] < southForecast[1][2]
                    && southNeighborChance > northNeighborChance,
                    "north-to-south wind accelerates the south neighbor");
            try {
                forecastGrid.estimateIgnitionProbability(
                        2, 3, 2, 2, eastWind, 0.0);
                throw new AssertionError("Invalid ignition probability interval was accepted.");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("interval"),
                        "invalid neighbor-probability interval is rejected");
            }
            require(forecastGrid.getCell(2, 2).getState() == FireState.UNBURNED
                    && forecastGrid.getTimeSeconds() == 0.0,
                    "arrival forecast does not ignite cells or advance simulation");
            FireSimulation blockedGrid = new FireSimulation(
                    new int[][] { { 1, 1 } },
                    new int[][] { { 101, 91 } },
                    30.0,
                    30.0,
                    Wind.calm(),
                    2L);
            require(blockedGrid.estimateIgnitionProbability(
                    0, 1, 0, 0, Wind.calm(), 30.0) == 0.0,
                    "non-burnable neighbor has zero ignition probability");
            double[][] displayForecast = FireSpreadForecaster.summarizeDisplayCells(
                    forecast, 1, 1);
            require(displayForecast.length == 1 && displayForecast[0].length == 1
                    && displayForecast[0][0] == 0.0,
                    "coarse map tile reports earliest underlying cell arrival");

            simulation.igniteCell(0, 0);
            simulation.advanceOneStep();
            simulation.reset();
            require(simulation.getTimeSeconds() == 0.0, "reset clears simulation time");
            require(simulation.getCell(0, 0).getState() == FireState.BURNING,
                    "reset restores original simulated ignition");
            simulation.clearFire();
            require(simulation.getCell(0, 0).getState() == FireState.UNBURNED
                    && simulation.getTimeSeconds() == 0.0,
                    "clear fire removes the active ignition and resets the simulation");

            FireSimulationGUI gui = new FireSimulationGUI(
                    simulation, null, null, Wind.calm());
            javax.swing.JButton igniteCellButton = findButton(gui, "Ignite selected cell");
            require(igniteCellButton != null && igniteCellButton.isEnabled(),
                    "cell ignition is enabled without a weather observation");
            igniteCellButton.doClick();
            require(simulation.getCell(0, 0).getState() == FireState.BURNING,
                    "cell ignites using the fallback calm wind");
            javax.swing.JButton clearFireButton = findButton(gui, "Clear fire");
            require(clearFireButton != null, "clear fire control is available");
            clearFireButton.doClick();
            require(simulation.getCell(0, 0).getState() == FireState.UNBURNED,
                    "clear fire stops and resets a fallback-wind ignition");
            gui.setSize(1200, 780);
            gui.doLayout();
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    1200, 780, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            gui.paint(graphics);
            graphics.dispose();
            require(image.getRGB(0, 0) != 0, "Swing interface paints in headless test");
            WeatherMonitor gridWeatherMonitor = new WeatherMonitor(
                    weatherClient, 38.58, -121.49);
            try {
                GridCommunicationGUI gridGui = new GridCommunicationGUI(
                        new GridCellData(50, 50, 2960, 102),
                        new GridCellData(50, 51, 2960, 102),
                        gridWeatherMonitor);
                gridGui.setSize(1100, 760);
                gridGui.doLayout();
                java.awt.image.BufferedImage gridImage = new java.awt.image.BufferedImage(
                        1100, 760, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D gridGraphics = gridImage.createGraphics();
                gridGui.paint(gridGraphics);
                gridGraphics.dispose();
                require(gridImage.getRGB(0, 0) != 0,
                        "two-cell communication view paints in headless test");
                require(containsLabel(gridGui, "Immediate Cell-to-Cell Fire Spread"),
                        "fire-spread view displays the compact cell-to-cell interface");
            } finally {
                gridWeatherMonitor.close();
            }
            require(containsLabel(gui, "Grass / fine fuels"),
                    "selected-cell panel shows an understandable cover class");
            require(!containsLabel(gui, "2966"),
                    "raw vegetation raster code is not shown as the vegetation name");
            require(FuelCover.descriptionForModel(122).equals("Shrub / brush"),
                    "shrub fuel models receive a human-readable cover name");
            require(FuelCover.descriptionForModel(186).equals("Timber / woody fuels"),
                    "woody fuel models receive a human-readable cover name");
            require(FuelCover.descriptionForModel(91)
                    .equals("No burnable cover classified"),
                    "unclassified fuel models are not guessed");
            javax.swing.JLabel coverLabel = findLabel(gui, "Grass / fine fuels");
            require(coverLabel != null && coverLabel.getToolTipText() != null
                    && coverLabel.getToolTipText().contains("code 1 "),
                    "raw raster code remains available as supporting tooltip detail");
            require(containsLabel(gui, "0, 0"),
                    "selected-cell panel shows the selected cell row and column");
            require(containsLabel(gui, "CSV FIRE SIMULATION GRID"),
                    "interface presents the CSV-backed cell grid instead of a map");
            require(containsLabel(gui, "No ETA"),
                    "selected-cell panel explains when no fire ETA is available");
            require(findComboBox(gui) == null,
                    "grid simulation does not show map location controls");
            require(!containsLabel(gui, "Wind Direction (arrow points toward)"),
                    "compass and directional arrow are removed from the controls");

            failRequests.set(true);
            try {
                weatherClient.fetchLatest(38.58, -121.49);
                throw new AssertionError("NWS API failure was silently accepted.");
            } catch (IOException expected) {
                require(expected.getMessage().contains("HTTP 503"),
                        "NWS HTTP failure is surfaced");
            }
            try {
                fireClient.fetchCalFireIncidents();
                throw new AssertionError("CAL FIRE API failure was silently accepted.");
            } catch (IOException expected) {
                require(expected.getMessage().contains("HTTP 503"),
                        "CAL FIRE HTTP failure is surfaced");
            }
            try {
                geocodingClient.search("Redding");
                throw new AssertionError("Geocoding API failure was silently accepted.");
            } catch (IOException expected) {
                require(expected.getMessage().contains("HTTP 503"),
                        "geocoding HTTP failure is surfaced");
            }
            FireDataApiClient noKeyClient = new FireDataApiClient(
                    fixture.client(), baseUri.resolve("/cal/fire/FeatureServer/0"), null);
            require(!noKeyClient.isFirmsConfigured(), "FIRMS reports missing API key");
            try {
                noKeyClient.fetchFirmsDetections();
                throw new AssertionError("FIRMS without a key was silently accepted.");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("NASA_FIRMS_MAP_KEY"),
                        "missing FIRMS API key is explicit");
            }
        } finally {
            server.stop(0);
        }
        System.out.println("External data integration tests passed.");
    }

    private static void respond(HttpExchange exchange, AtomicBoolean failRequests)
            throws IOException {
        String path = exchange.getRequestURI().getPath();
        int status = failRequests.get() ? 503 : 200;
        String body;
        if (path.startsWith("/cal/fire/FeatureServer/0/query")) {
            body = "{\"features\":[{\"attributes\":{\"OBJECTID\":42,"
                    + "\"crbe2_incidentid\":\"CA-42\","
                    + "\"crbe2_incidentname\":\"Test Fire\","
                    + "\"crbe2_size_acres\":25,"
                    + "\"crbe2_pct_containment\":10,"
                    + "\"crbe2_startdate\":"
                    + Instant.now().minusSeconds(600).toEpochMilli() + ","
                    + "\"modifiedon\":" + Instant.now().toEpochMilli()
                    + "},\"geometry\":{\"x\":-121.4,\"y\":39.1}}]}";
        } else if ("/geocode".equals(path)) {
            body = "[{\"lat\":\"40.5862\",\"lon\":\"-122.3914\","
                    + "\"display_name\":\"Redding, California, United States\"}]";
        } else if (path.startsWith("/points/")) {
            body = "{\"properties\":{\"observationStations\":\"http://127.0.0.1:"
                    + exchange.getLocalAddress().getPort() + "/stations\"}}";
        } else if ("/stations".equals(path)) {
            body = "{\"features\":[{\"id\":\"http://127.0.0.1:"
                    + exchange.getLocalAddress().getPort()
                    + "/stations/KSAC\",\"properties\":{\"stationIdentifier\":\"KSAC\","
                    + "\"name\":\"Sacramento Executive\"}}]}";
        } else {
            body = "{\"properties\":{\"stationId\":\"KSAC\","
                    + "\"stationName\":\"Sacramento Executive\","
                    + "\"timestamp\":\"" + Instant.now() + "\","
                    + "\"textDescription\":\"Clear\","
                    + "\"windSpeed\":{\"value\":36},"
                    + "\"windDirection\":{\"value\":270},"
                    + "\"relativeHumidity\":{\"value\":24},"
                    + "\"temperature\":{\"value\":25}}}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/geo+json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed check: " + description);
        }
    }

    private static boolean containsLabel(java.awt.Container parent, String text) {
        for (java.awt.Component component : parent.getComponents()) {
            if (component instanceof javax.swing.JLabel
                    && text.equals(((javax.swing.JLabel) component).getText())) {
                return true;
            }
            if (component instanceof java.awt.Container
                    && containsLabel((java.awt.Container) component, text)) {
                return true;
            }
        }
        return false;
    }

    private static javax.swing.JComboBox<?> findComboBox(java.awt.Container parent) {
        for (java.awt.Component component : parent.getComponents()) {
            if (component instanceof javax.swing.JComboBox<?>) {
                return (javax.swing.JComboBox<?>) component;
            }
            if (component instanceof java.awt.Container) {
                javax.swing.JComboBox<?> match =
                        findComboBox((java.awt.Container) component);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static javax.swing.JLabel findLabel(java.awt.Container parent, String text) {
        for (java.awt.Component component : parent.getComponents()) {
            if (component instanceof javax.swing.JLabel
                    && text.equals(((javax.swing.JLabel) component).getText())) {
                return (javax.swing.JLabel) component;
            }
            if (component instanceof java.awt.Container) {
                javax.swing.JLabel match = findLabel((java.awt.Container) component, text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static javax.swing.JButton findButton(java.awt.Container parent, String text) {
        for (java.awt.Component component : parent.getComponents()) {
            if (component instanceof javax.swing.JButton
                    && text.equals(((javax.swing.JButton) component).getText())) {
                return (javax.swing.JButton) component;
            }
            if (component instanceof java.awt.Container) {
                javax.swing.JButton match = findButton((java.awt.Container) component, text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static final class HttpClientFixture {
        private java.net.http.HttpClient client() {
            return java.net.http.HttpClient.newHttpClient();
        }
    }
}
