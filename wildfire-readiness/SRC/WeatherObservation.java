import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class WeatherObservation {

    private final String stationId;
    private final String stationName;
    private final Instant observedAt;
    private final Instant fetchedAt;
    private final String description;
    private final Double windSpeedMetersPerSecond;
    private final Double windDirectionFromDegrees;
    private final Double windGustMetersPerSecond;
    private final Double temperatureCelsius;
    private final Double relativeHumidityPercent;
    private final Double pressureHectopascals;
    private final Double visibilityKilometers;
    private final Double precipitationLast3HoursMillimeters;

    public WeatherObservation(
            String stationId,
            String stationName,
            Instant observedAt,
            Instant fetchedAt,
            String description,
            Double windSpeedMetersPerSecond,
            Double windDirectionFromDegrees,
            Double windGustMetersPerSecond,
            Double temperatureCelsius,
            Double relativeHumidityPercent,
            Double pressureHectopascals,
            Double visibilityKilometers,
            Double precipitationLast3HoursMillimeters) {
        this.stationId = stationId;
        this.stationName = stationName;
        this.observedAt = observedAt;
        this.fetchedAt = fetchedAt;
        this.description = description;
        this.windSpeedMetersPerSecond = windSpeedMetersPerSecond;
        this.windDirectionFromDegrees = windDirectionFromDegrees;
        this.windGustMetersPerSecond = windGustMetersPerSecond;
        this.temperatureCelsius = temperatureCelsius;
        this.relativeHumidityPercent = relativeHumidityPercent;
        this.pressureHectopascals = pressureHectopascals;
        this.visibilityKilometers = visibilityKilometers;
        this.precipitationLast3HoursMillimeters = precipitationLast3HoursMillimeters;
    }

    public String getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getDescription() {
        return description;
    }

    public Double getWindSpeedMetersPerSecond() {
        return windSpeedMetersPerSecond;
    }

    public Double getWindDirectionFromDegrees() {
        return windDirectionFromDegrees;
    }

    public Double getWindGustMetersPerSecond() {
        return windGustMetersPerSecond;
    }

    public Double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public Double getRelativeHumidityPercent() {
        return relativeHumidityPercent;
    }

    public Double getPressureHectopascals() {
        return pressureHectopascals;
    }

    public Double getVisibilityKilometers() {
        return visibilityKilometers;
    }

    public Double getPrecipitationLast3HoursMillimeters() {
        return precipitationLast3HoursMillimeters;
    }

    public boolean isFreshAt(Instant now, Duration maxAge) {
        Duration age = Duration.between(observedAt, now);
        return !age.isNegative() && age.compareTo(maxAge) <= 0;
    }

    public Optional<Wind> toWindToward() {
        // Calm air: NWS often omits the direction when the speed is zero.
        if (windSpeedMetersPerSecond != null && windSpeedMetersPerSecond == 0.0
                && windDirectionFromDegrees == null) {
            return Optional.of(Wind.calm());
        }
        if (windSpeedMetersPerSecond == null || windDirectionFromDegrees == null
                || !Double.isFinite(windSpeedMetersPerSecond)
                || windSpeedMetersPerSecond < 0.0
                || !Double.isFinite(windDirectionFromDegrees)) {
            return Optional.empty();
        }
        return Optional.of(new Wind(
                windSpeedMetersPerSecond,
                windDirectionFromDegrees + 180.0));
    }
}
