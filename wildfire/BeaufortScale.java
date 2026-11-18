package wildfire;

/**
 * Beaufort wind force scale, with wind speed in miles per hour (mph).
 *
 * <p>Each force covers {@code [minMph, nextForce.minMph)}. Speeds are whole mph as published;
 * a fractional speed belongs to the force whose range it falls in (e.g. 3.5 mph is Force 1).
 * Force 12 starts at 73 mph (64 knots = 73.6 mph), the standard hurricane threshold.
 */
public enum BeaufortScale {
    CALM(0, "Calm", 0, "< 1 mph", "< 1 knot", "Smoke rises vertically; sea like a mirror."),
    LIGHT_AIR(1, "Light Air", 1, "1-3 mph", "1-3 knots", "Direction shown by smoke drift, not vanes; rippled water."),
    LIGHT_BREEZE(2, "Light Breeze", 4, "4-7 mph", "4-6 knots", "Wind felt on face; leaves rustle."),
    GENTLE_BREEZE(3, "Gentle Breeze", 8, "8-12 mph", "7-10 knots",
            "Leaves and small twigs in constant motion; light flags extend."),
    MODERATE_BREEZE(4, "Moderate Breeze", 13, "13-18 mph", "11-16 knots",
            "Raises dust and loose paper; small branches move."),
    FRESH_BREEZE(5, "Fresh Breeze", 19, "19-24 mph", "17-21 knots", "Small trees in leaf begin to sway."),
    STRONG_BREEZE(6, "Strong Breeze", 25, "25-31 mph", "22-27 knots",
            "Large branches move; whistling in wires; difficulty using umbrellas."),
    NEAR_GALE(7, "Near Gale", 32, "32-38 mph", "28-33 knots",
            "Whole trees in motion; walking against the wind is difficult."),
    GALE(8, "Gale", 39, "39-46 mph", "34-40 knots", "Twigs break off trees; impedes progress."),
    STRONG_GALE(9, "Strong Gale", 47, "47-54 mph", "41-47 knots",
            "Slight structural damage (chimney pots and slates removed)."),
    STORM(10, "Storm", 55, "55-63 mph", "48-55 knots", "Trees uprooted; considerable structural damage."),
    VIOLENT_STORM(11, "Violent Storm", 64, "64-72 mph", "56-63 knots",
            "Widespread damage; exceptionally high waves."),
    HURRICANE(12, "Hurricane", 73, "73+ mph", "64+ knots",
            "Air filled with foam and spray; severe, devastating destruction.");

    private final int force;
    private final String label;
    private final double minMph;
    private final String mphRange;
    private final String knotRange;
    private final String description;

    BeaufortScale(int force, String label, double minMph, String mphRange, String knotRange, String description) {
        this.force = force;
        this.label = label;
        this.minMph = minMph;
        this.mphRange = mphRange;
        this.knotRange = knotRange;
        this.description = description;
    }

    /** The force whose mph range contains {@code mph} (negative speeds count as calm). */
    public static BeaufortScale of(double mph) {
        BeaufortScale result = CALM;
        for (BeaufortScale b : values()) {
            if (mph >= b.minMph) {
                result = b;
            }
        }
        return result;
    }

    public int getForce() {
        return force;
    }

    public String getLabel() {
        return label;
    }

    public double getMinMph() {
        return minMph;
    }

    public String getMphRange() {
        return mphRange;
    }

    public String getKnotRange() {
        return knotRange;
    }

    public String getDescription() {
        return description;
    }

    /** e.g. "Force 4 - Moderate Breeze (13-18 mph)". */
    @Override
    public String toString() {
        return "Force " + force + " - " + label + " (" + mphRange + ")";
    }
}
