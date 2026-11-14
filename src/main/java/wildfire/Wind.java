package wildfire;

/** Wind direction is the direction the wind blows TOWARD, clockwise from north. */
public final class Wind {
    private final double speed;
    private final double directionDegrees;

    public Wind(double speed, double directionDegrees) {
        if (!Double.isFinite(speed) || speed < 0) {
            throw new IllegalArgumentException("Wind speed must be non-negative.");
        }
        if (!Double.isFinite(directionDegrees)) {
            throw new IllegalArgumentException("Wind direction must be finite.");
        }
        this.speed = speed;
        this.directionDegrees = ((directionDegrees % 360) + 360) % 360;
    }

    public double getSpeed() { return speed; }
    public double getDirectionDegrees() { return directionDegrees; }

    /** Returns alignment in [-1, 1] between wind travel direction and a neighbor direction. */
    public double alignment(double east, double north) {
        double radians = Math.toRadians(directionDegrees);
        double windEast = Math.sin(radians);
        double windNorth = Math.cos(radians);
        double length = Math.hypot(east, north);
        if (length == 0) return 0;
        return (windEast * east + windNorth * north) / length;
    }

    public static Wind calm() { return new Wind(0, 0); }
}
