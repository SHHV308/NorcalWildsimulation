package wildfire;

/**
 * Wind direction and strength.
 *
 * <p>Direction is the compass bearing the wind blows TOWARD, in degrees
 * (0 = north/up, 90 = east/right, 180 = south/down, 270 = west/left).
 * Strength is in [0, 1]; 0 means no wind effect.
 *
 * <p>TEAM ASSUMPTION - the spread multiplier is {@code 1 + gain * strength * alignment},
 * where {@code alignment = cos(angle between wind and spread direction)} in [-1, 1].
 * Wind toward the target gives up to 1 + gain, crosswind gives exactly 1,
 * wind blowing away gives down to 1 - gain.
 *
 * <p>Wind speed is entered in miles per hour (mph) and classified on the {@link BeaufortScale}.
 * TEAM ASSUMPTION - model strength = min(1, speedMph / {@value #FULL_EFFECT_MPH}): it grows linearly
 * with speed and reaches full effect at hurricane force (Force 12, 73 mph).
 */
public class Wind {

    /** Wind speed (mph) at which the model strength reaches 1 - the Force 12 hurricane threshold. */
    public static final double FULL_EFFECT_MPH = 73.0;

    private double directionDegrees;
    private double strength;
    private double speedMph;

    public Wind(double directionDegrees, double strength) {
        setDirectionDegrees(directionDegrees);
        setStrength(strength);
    }

    public static Wind calm() {
        return new Wind(0.0, 0.0);
    }

    public double getDirectionDegrees() {
        return directionDegrees;
    }

    public void setDirectionDegrees(double degrees) {
        if (Double.isNaN(degrees) || Double.isInfinite(degrees)) {
            throw new IllegalArgumentException("Wind direction must be a finite number.");
        }
        this.directionDegrees = ((degrees % 360.0) + 360.0) % 360.0;
    }

    public double getStrength() {
        return strength;
    }

    public void setStrength(double strength) {
        if (Double.isNaN(strength) || strength < 0.0 || strength > 1.0) {
            throw new IllegalArgumentException("Wind strength must be within [0, 1].");
        }
        this.strength = strength;
        this.speedMph = strength * FULL_EFFECT_MPH;
    }

    /** Wind with direction (degrees, blowing toward) and speed in mph. */
    public static Wind ofMph(double directionDegrees, double speedMph) {
        Wind wind = calm();
        wind.setDirectionDegrees(directionDegrees);
        wind.setSpeedMph(speedMph);
        return wind;
    }

    /** Wind speed in miles per hour. */
    public double getSpeedMph() {
        return speedMph;
    }

    /** Sets the speed in mph; model strength becomes min(1, mph / 73). Speeds above 73 mph keep full effect. */
    public void setSpeedMph(double mph) {
        if (Double.isNaN(mph) || Double.isInfinite(mph) || mph < 0.0) {
            throw new IllegalArgumentException("Wind speed must be a non-negative number of mph.");
        }
        this.speedMph = mph;
        this.strength = Math.min(1.0, mph / FULL_EFFECT_MPH);
    }

    /** Beaufort force for the current speed. */
    public BeaufortScale getBeaufort() {
        return BeaufortScale.of(speedMph);
    }

    /**
     * Cosine alignment between the wind and a spread step from a burning source to a target,
     * where the step is given in grid offsets (rows grow downward = south).
     *
     * @return value in [-1, 1]; 1 = wind blows exactly from source toward target
     */
    public double alignment(int rowOffset, int columnOffset) {
        double length = Math.hypot(rowOffset, columnOffset);
        if (length == 0.0) {
            return 0.0;
        }
        double radians = Math.toRadians(directionDegrees);
        double windRow = -Math.cos(radians); // north = decreasing row
        double windColumn = Math.sin(radians); // east = increasing column
        double dot = (windRow * rowOffset + windColumn * columnOffset) / length;
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    /**
     * Multiplier applied to a neighbour's influence when fire spreads from source to target.
     *
     * @param rowOffset    target row minus source row
     * @param columnOffset target column minus source column
     * @param gain         maximum fractional change at full strength (see {@link SimulationConfig})
     * @return multiplier in [1 - gain, 1 + gain], never negative
     */
    public double spreadMultiplier(int rowOffset, int columnOffset, double gain) {
        return Math.max(0.0, 1.0 + gain * strength * alignment(rowOffset, columnOffset));
    }

    /** Eight-point compass name of the direction the wind blows toward. */
    public String compassName() {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.round(directionDegrees / 45.0) % 8];
    }
}
