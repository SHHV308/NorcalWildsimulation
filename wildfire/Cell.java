package wildfire;

/**
 * One independent patch of landscape. Owns its fuel, fire state and lifecycle rules.
 *
 * <p>The numeric {@code fuelValue} drives the model; {@code materialType} is a label only.
 */
public class Cell {

    private final int row;
    private final int column;

    private MaterialType materialType;
    private int fuelValue;

    private double remainingFuel;
    private FireState state = FireState.UNBURNED;
    private double fireIntensity;
    private double peakIntensity;
    private int fireDuration;
    private double ignitionProbability;
    private int ignitionTime = -1;
    /** Burning neighbour that contributed most to this cell's ignition; null for start fires. */
    private Cell ignitedBy;
    /** Random draw from this cell's latest ignition test, or NaN if never tested. */
    private double lastRoll = Double.NaN;
    /** Wind alignment (cos) of the move that brought fire here: +1 downwind, 0 crosswind, -1 upwind. */
    private double ignitionWindAlignment = Double.NaN;

    public Cell(int row, int column, MaterialType materialType, int fuelValue) {
        this.row = row;
        this.column = column;
        setFuel(materialType, fuelValue);
    }

    // ----- configuration -----

    /** Assigns material and fuel and restores the cell to a fresh unburned state. */
    public void setFuel(MaterialType materialType, int fuelValue) {
        if (materialType == null) {
            throw new IllegalArgumentException("Material type is required.");
        }
        if (fuelValue < 0) {
            throw new IllegalArgumentException("Fuel value must not be negative.");
        }
        this.materialType = materialType;
        this.fuelValue = fuelValue;
        resetFire();
    }

    /** Restores the unburned, fully fuelled starting state (fuel layout is kept). */
    public void resetFire() {
        state = FireState.UNBURNED;
        remainingFuel = fuelValue;
        fireIntensity = 0.0;
        peakIntensity = 0.0;
        fireDuration = 0;
        ignitionProbability = 0.0;
        ignitionTime = -1;
        ignitedBy = null;
        lastRoll = Double.NaN;
        ignitionWindAlignment = Double.NaN;
    }

    // ----- behaviour -----

    /** True only for an unburned cell that still has fuel. Burned-out cells never re-ignite. */
    public boolean canIgnite() {
        return state == FireState.UNBURNED && remainingFuel > 0;
    }

    /**
     * Sets the cell on fire.
     *
     * @param step          simulation step at which ignition happens
     * @param peakIntensity intensity at ignition, in (0, 1]
     * @throws IllegalStateException if the cell cannot ignite (no fuel, already burning/burned)
     */
    public void ignite(int step, double peakIntensity) {
        ignite(step, peakIntensity, null);
    }

    /**
     * Sets the cell on fire and records which burning neighbour passed the fire on.
     *
     * @param source strongest contributing neighbour, or null for an initial (user-placed) fire
     */
    public void ignite(int step, double peakIntensity, Cell source) {
        if (!canIgnite()) {
            throw new IllegalStateException("Cell (" + row + "," + column + ") cannot ignite.");
        }
        state = FireState.BURNING;
        this.peakIntensity = peakIntensity;
        fireIntensity = peakIntensity;
        fireDuration = 0;
        ignitionTime = step;
        ignitedBy = source;
    }

    /**
     * Advances one step of burning: increases duration, consumes fuel, updates intensity
     * (peak intensity scaled by the fraction of fuel left) and burns out at zero fuel.
     * Does nothing unless the cell is BURNING.
     */
    public void burn(double fuelConsumption) {
        if (state != FireState.BURNING) {
            return;
        }
        fireDuration++;
        remainingFuel = Math.max(0.0, remainingFuel - fuelConsumption);
        if (remainingFuel <= 0.0) {
            state = FireState.BURNED_OUT;
            fireIntensity = 0.0;
        } else {
            fireIntensity = peakIntensity * (remainingFuel / fuelValue);
        }
    }

    // ----- accessors -----

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    /** Unique identifier derived from position, e.g. "R3C7". */
    public String getId() {
        return "R" + row + "C" + column;
    }

    public MaterialType getMaterialType() {
        return materialType;
    }

    public int getFuelValue() {
        return fuelValue;
    }

    public double getRemainingFuel() {
        return remainingFuel;
    }

    public FireState getState() {
        return state;
    }

    public boolean isBurning() {
        return state == FireState.BURNING;
    }

    public double getFireIntensity() {
        return fireIntensity;
    }

    public int getFireDuration() {
        return fireDuration;
    }

    public double getIgnitionProbability() {
        return ignitionProbability;
    }

    /** Records the most recently calculated ignition probability (display only). */
    public void setIgnitionProbability(double probability) {
        this.ignitionProbability = Math.max(0.0, Math.min(1.0, probability));
    }

    /** Burning neighbour that contributed most to this cell's ignition, or null. */
    public Cell getIgnitedBy() {
        return ignitedBy;
    }

    /** Random draw from the latest ignition test (ignites when below P), or NaN if never tested. */
    public double getLastRoll() {
        return lastRoll;
    }

    void setLastRoll(double roll) {
        this.lastRoll = roll;
    }

    /**
     * Steps the fire took to travel from {@link #getIgnitedBy()} to this cell
     * (this cell's ignition step minus the source's), or -1 for start fires and unburned cells.
     */
    public int getTravelSteps() {
        if (ignitedBy == null || ignitionTime < 0) {
            return -1;
        }
        return ignitionTime - ignitedBy.getIgnitionTime();
    }

    /** Wind alignment of the move that ignited this cell (+1 downwind ... -1 upwind), NaN if none. */
    public double getIgnitionWindAlignment() {
        return ignitionWindAlignment;
    }

    void setIgnitionWindAlignment(double alignment) {
        this.ignitionWindAlignment = alignment;
    }

    /** Step at which the cell ignited, or -1 if it never has. */
    public int getIgnitionTime() {
        return ignitionTime;
    }
}
