import java.time.Instant;

public final class ObservedFire {

    public enum Source {
        CAL_FIRE_INCIDENT,
        NASA_FIRMS_DETECTION
    }

    private final Source source;
    private final String id;
    private final String name;
    private final Instant observedAt;
    private final Instant fetchedAt;
    private final Double latitude;
    private final Double longitude;
    private final String details;

    public ObservedFire(
            Source source,
            String id,
            String name,
            Instant observedAt,
            Instant fetchedAt,
            Double latitude,
            Double longitude,
            String details) {
        this.source = source;
        this.id = id;
        this.name = name;
        this.observedAt = observedAt;
        this.fetchedAt = fetchedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.details = details;
    }

    public Source getSource() {
        return source;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getDetails() {
        return details;
    }
}
