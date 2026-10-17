import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FireObservationMonitor implements AutoCloseable {

    public static final Duration MAX_FEED_AGE = Duration.ofMinutes(30);
    private final FireDataApiClient client;
    private final ScheduledExecutorService executor;
    private final AtomicReference<ObservationFeed> calFire =
            new AtomicReference<>(new ObservationFeed(
                    "CAL FIRE", true, Collections.emptyList(), null, null));
    private final AtomicReference<ObservationFeed> firms;

    public FireObservationMonitor(FireDataApiClient client) {
        this.client = client;
        this.firms = new AtomicReference<>(new ObservationFeed(
                "NASA FIRMS",
                client.isFirmsConfigured(),
                Collections.emptyList(),
                null,
                client.isFirmsConfigured()
                        ? null : "NASA_FIRMS_MAP_KEY is not set; detections are disabled."));
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fire-observation-poller");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::refresh, 0, 15, TimeUnit.MINUTES);
    }

    public ObservationFeed getCalFireFeed() {
        return calFire.get();
    }

    public ObservationFeed getFirmsFeed() {
        return firms.get();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void refresh() {
        refreshCalFire();
        if (client.isFirmsConfigured()) {
            refreshFirms();
        }
    }

    private void refreshCalFire() {
        try {
            calFire.set(new ObservationFeed(
                    "CAL FIRE",
                    true,
                    client.fetchCalFireIncidents(),
                    Instant.now(),
                    null));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            retainFailure(calFire, "CAL FIRE", true, "CAL FIRE request was interrupted.");
        } catch (java.io.IOException | IllegalArgumentException
                | java.time.DateTimeException exception) {
            retainFailure(calFire, "CAL FIRE", true,
                    "CAL FIRE refresh failed: " + exception.getMessage());
        }
    }

    private void refreshFirms() {
        try {
            firms.set(new ObservationFeed(
                    "NASA FIRMS",
                    true,
                    client.fetchFirmsDetections(),
                    Instant.now(),
                    null));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            retainFailure(firms, "NASA FIRMS", true, "NASA FIRMS request was interrupted.");
        } catch (java.io.IOException | IllegalArgumentException
                | java.time.DateTimeException exception) {
            retainFailure(firms, "NASA FIRMS", true,
                    "NASA FIRMS refresh failed: " + exception.getMessage());
        }
    }

    private static void retainFailure(
            AtomicReference<ObservationFeed> destination,
            String sourceName,
            boolean configured,
            String error) {
        ObservationFeed previous = destination.get();
        ObservationFeed updated = new ObservationFeed(
                sourceName,
                configured,
                new ArrayList<>(previous.getObservations()),
                previous.getFetchedAt(),
                error);
        destination.set(updated);
        System.err.println(error);
    }
}
