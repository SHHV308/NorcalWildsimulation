public final class GeoLocation {

    private final String displayName;
    private final double latitude;
    private final double longitude;

    public GeoLocation(String displayName, double latitude, double longitude) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Location name must not be empty.");
        }
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0
                || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Location coordinates are invalid.");
        }
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
