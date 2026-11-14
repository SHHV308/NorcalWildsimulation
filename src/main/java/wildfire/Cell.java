package wildfire;

/** One cell in the artificial simulation grid. Fuel is a numeric 0-10 value. */
public final class Cell {
    private final int row;
    private final int col;
    private final String material;
    private final double initialFuel;
    private double remainingFuel;
    private CellState state;
    private double intensity;
    private int burningSteps;
    private double ignitionProbability;
    private int ignitionStep = -1;

    public Cell(int row, int col, String material, double fuel) {
        if (row < 0 || col < 0) throw new IllegalArgumentException("Cell coordinates must be non-negative.");
        if (!Double.isFinite(fuel) || fuel < 0 || fuel > 10) throw new IllegalArgumentException("Fuel must be between 0 and 10.");
        this.row = row;
        this.col = col;
        this.material = material == null ? "Unclassified" : material;
        this.initialFuel = fuel;
        reset();
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public String getMaterial() { return material; }
    public double getInitialFuel() { return initialFuel; }
    public double getRemainingFuel() { return remainingFuel; }
    public CellState getState() { return state; }
    public double getIntensity() { return intensity; }
    public int getBurningSteps() { return burningSteps; }
    public double getIgnitionProbability() { return ignitionProbability; }
    public int getIgnitionStep() { return ignitionStep; }

    void setIgnitionProbability(double value) { ignitionProbability = clamp(value, 0, 1); }

    void ignite(int step, double startingIntensity) {
        if (state != CellState.UNBURNED || remainingFuel <= 0) return;
        state = CellState.BURNING;
        ignitionStep = step;
        intensity = clamp(startingIntensity, 1, 10);
        burningSteps = 0;
    }

    void burnOneStep() {
        if (state != CellState.BURNING) return;
        burningSteps++;
        remainingFuel = Math.max(0, remainingFuel - 0.8);
        intensity = Math.max(0, intensity - 0.25);
        if (remainingFuel <= 0) {
            state = CellState.BURNED;
            intensity = 0;
        }
    }

    void reset() {
        remainingFuel = initialFuel;
        state = initialFuel <= 0 ? CellState.NON_BURNABLE : CellState.UNBURNED;
        intensity = 0;
        burningSteps = 0;
        ignitionProbability = 0;
        ignitionStep = -1;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
