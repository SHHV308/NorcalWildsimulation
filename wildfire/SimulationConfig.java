package wildfire;

/**
 * Tunable parameters of the model. Every value is a TEAM IMPLEMENTATION ASSUMPTION;
 * none are scientifically calibrated. Defaults were chosen so that fires visibly spread
 * through dense fuel, struggle through sparse fuel and stop at water/bare land.
 */
public class SimulationConfig {

    /** Upper end of the fuel scale (dry forest = 10). Used to normalise fuel to 0..1. */
    private int maxFuel = 10;
    /** Fuel units removed from a burning cell per step. */
    private double fuelConsumptionPerStep = 1.0;
    /** Intensity of a just-ignited cell with the smallest (non-zero) fuel. */
    private double minPeakIntensity = 0.5;
    /** Intensity of a just-ignited cell with maxFuel. */
    private double maxPeakIntensity = 1.0;
    /** Scales a burning neighbour's intensity into an ignition contribution. */
    private double spreadCoefficient = 0.5;
    /** Fuel factor for the smallest non-zero fuel; rises linearly to 1 at maxFuel. */
    private double minFuelFactor = 0.2;
    /** Whether the 4 diagonal neighbours count (8-neighbourhood) or only N/E/S/W. */
    private boolean includeDiagonals = true;
    /** Influence weight of a diagonal neighbour (1/sqrt(2): it is farther away). */
    private double diagonalWeight = Math.sqrt(0.5);
    /** How much a full-strength, perfectly aligned wind changes spread (multiplier 1 +/- windGain). */
    private double windGain = 1.0;
    /**
     * Simulated minutes represented by one step (TEAM ASSUMPTION, not calibrated). Used only to
     * express step counts as time; the spread rules themselves work in steps.
     */
    private double minutesPerStep = 1.0;
    /** Hard safety cap so a run can never loop forever. */
    private int maxSteps = 10_000;

    public int getMaxFuel() {
        return maxFuel;
    }

    public void setMaxFuel(int maxFuel) {
        if (maxFuel < 1) {
            throw new IllegalArgumentException("maxFuel must be at least 1.");
        }
        this.maxFuel = maxFuel;
    }

    public double getFuelConsumptionPerStep() {
        return fuelConsumptionPerStep;
    }

    public void setFuelConsumptionPerStep(double value) {
        if (!(value > 0)) {
            throw new IllegalArgumentException("Fuel consumption per step must be positive.");
        }
        this.fuelConsumptionPerStep = value;
    }

    public double getMinPeakIntensity() {
        return minPeakIntensity;
    }

    public double getMaxPeakIntensity() {
        return maxPeakIntensity;
    }

    public void setPeakIntensityRange(double min, double max) {
        if (min <= 0 || max > 1 || min > max) {
            throw new IllegalArgumentException("Peak intensities must satisfy 0 < min <= max <= 1.");
        }
        this.minPeakIntensity = min;
        this.maxPeakIntensity = max;
    }

    public double getSpreadCoefficient() {
        return spreadCoefficient;
    }

    public void setSpreadCoefficient(double value) {
        if (value < 0) {
            throw new IllegalArgumentException("Spread coefficient must not be negative.");
        }
        this.spreadCoefficient = value;
    }

    public double getMinFuelFactor() {
        return minFuelFactor;
    }

    public void setMinFuelFactor(double value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("Minimum fuel factor must be within [0, 1].");
        }
        this.minFuelFactor = value;
    }

    public boolean isIncludeDiagonals() {
        return includeDiagonals;
    }

    public void setIncludeDiagonals(boolean includeDiagonals) {
        this.includeDiagonals = includeDiagonals;
    }

    public double getDiagonalWeight() {
        return diagonalWeight;
    }

    public double getWindGain() {
        return windGain;
    }

    public void setWindGain(double value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("Wind gain must be within [0, 1] so multipliers stay >= 0.");
        }
        this.windGain = value;
    }

    public double getMinutesPerStep() {
        return minutesPerStep;
    }

    public void setMinutesPerStep(double minutes) {
        if (!(minutes > 0)) {
            throw new IllegalArgumentException("Minutes per step must be positive.");
        }
        this.minutesPerStep = minutes;
    }

    /** "5 min" or "1 h 05 min" for a number of steps. */
    public String formatSteps(double steps) {
        double minutes = steps * minutesPerStep;
        if (minutes < 60) {
            return minutes == Math.rint(minutes) ? String.format("%.0f min", minutes) : String.format("%.1f min", minutes);
        }
        long whole = Math.round(minutes);
        return String.format("%d h %02d min", whole / 60, whole % 60);
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be at least 1.");
        }
        this.maxSteps = maxSteps;
    }

    /** Peak intensity for a cell with the given fuel: higher fuel burns hotter. */
    public double peakIntensityFor(int fuelValue) {
        double fraction = Math.min(1.0, Math.max(0.0, (double) fuelValue / maxFuel));
        return minPeakIntensity + (maxPeakIntensity - minPeakIntensity) * fraction;
    }

    /** Fuel availability factor in (0, 1] for fuel > 0, and 0 for no fuel. */
    public double fuelFactorFor(double fuel) {
        if (fuel <= 0) {
            return 0.0;
        }
        double fraction = Math.min(1.0, fuel / maxFuel);
        return minFuelFactor + (1.0 - minFuelFactor) * fraction;
    }
}
