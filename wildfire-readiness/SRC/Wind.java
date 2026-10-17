public final class Wind {

    private final double speedMetersPerSecond;
    private final double directionDegrees;

    /**
     * @param speedMetersPerSecond wind speed
     * @param directionDegrees direction the wind blows toward, clockwise from north
     */
    public Wind(double speedMetersPerSecond, double directionDegrees) {
        if (!Double.isFinite(speedMetersPerSecond) || speedMetersPerSecond < 0.0) {
            throw new IllegalArgumentException("Wind speed must be finite and non-negative.");
        }
        if (!Double.isFinite(directionDegrees)) {
            throw new IllegalArgumentException("Wind direction must be finite.");
        }

        this.speedMetersPerSecond = speedMetersPerSecond;
        this.directionDegrees = ((directionDegrees % 360.0) + 360.0) % 360.0;
    }

    public static Wind calm() {
        return new Wind(0.0, 0.0);
    }

    public double getSpeedMetersPerSecond() {
        return speedMetersPerSecond;
    }

    public double getDirectionDegrees() {
        return directionDegrees;
    }

    double alignmentWith(double eastComponent, double northComponent) {
        double radians = Math.toRadians(directionDegrees);
        double windEast = Math.sin(radians);
        double windNorth = Math.cos(radians);
        return windEast * eastComponent + windNorth * northComponent;
    }
}
