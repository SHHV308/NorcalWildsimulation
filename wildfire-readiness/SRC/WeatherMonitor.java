import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class WeatherMonitor implements AutoCloseable {

    public static final Duration MAX_OBSERVATION_AGE = Duration.ofMinutes(90);
    private final WeatherApiClient client;
    private volatile double latitude;
    private volatile double longitude;
    private final ScheduledExecutorService executor;
    private final AtomicReference<WeatherObservation> latest = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public WeatherMonitor(WeatherApiClient client, double latitude, double longitude) {
        this.client = client;
        this.latitude = latitude;
        this.longitude = longitude;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "nws-weather-poller");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::refresh, 0, 10, TimeUnit.MINUTES);
    }

    public WeatherObservation getLatest() {
        return latest.get();
    }

    public void setLocation(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0
                || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Weather location coordinates are invalid.");
        }
        latest.set(null);
        lastError.set(null);
        this.latitude = latitude;
        this.longitude = longitude;
        executor.execute(this::refresh);
    }

    public String getLastError() {
        return lastError.get();
    }

    public boolean isFreshAt(Instant now) {
        WeatherObservation observation = latest.get();
        return observation != null && observation.isFreshAt(now, MAX_OBSERVATION_AGE);
    }

    public Optional<Wind> getFreshWindAt(Instant now) {
        WeatherObservation observation = latest.get();
        if (observation == null || !observation.isFreshAt(now, MAX_OBSERVATION_AGE)) {
            return Optional.empty();
        }
        return observation.toWindToward();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void refresh() {
        try {
            WeatherObservation observation = client.fetchLatest(latitude, longitude);
            latest.set(observation);
            lastError.set(null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            lastError.set("NWS request was interrupted.");
        } catch (IOException | IllegalArgumentException exception) {
            String message = "NWS refresh failed: " + exception.getMessage();
            lastError.set(message);
            System.err.println(message);
        }
    }
}
