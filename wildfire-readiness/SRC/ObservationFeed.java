import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ObservationFeed {

    private final String sourceName;
    private final boolean configured;
    private final List<ObservedFire> observations;
    private final Instant fetchedAt;
    private final String lastError;

    public ObservationFeed(
            String sourceName,
            boolean configured,
            List<ObservedFire> observations,
            Instant fetchedAt,
            String lastError) {
        this.sourceName = sourceName;
        this.configured = configured;
        this.observations = Collections.unmodifiableList(new ArrayList<>(observations));
        this.fetchedAt = fetchedAt;
        this.lastError = lastError;
    }

    public String getSourceName() {
        return sourceName;
    }

    public boolean isConfigured() {
        return configured;
    }

    public List<ObservedFire> getObservations() {
        return observations;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isStaleAt(Instant now, Duration maxAge) {
        if (fetchedAt == null) {
            return configured;
        }
        Duration age = Duration.between(fetchedAt, now);
        return age.isNegative() || age.compareTo(maxAge) > 0;
    }
}
