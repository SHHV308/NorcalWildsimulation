package wildfire;

/** Tracks cell-state counts, ignition events and the step counter. */
public class SimulationStatistics {

    private int totalCells;
    private int unburned;
    private int burning;
    private int burnedOut;
    private int totalIgnitions;
    private int step;

    public void reset(Grid grid) {
        totalIgnitions = 0;
        step = 0;
        refresh(grid);
    }

    /** Recounts cell states from the grid. */
    public void refresh(Grid grid) {
        totalCells = grid.getTotalCells();
        int u = 0;
        int b = 0;
        int o = 0;
        for (Cell cell : grid.allCells()) {
            switch (cell.getState()) {
                case UNBURNED -> u++;
                case BURNING -> b++;
                case BURNED_OUT -> o++;
            }
        }
        unburned = u;
        burning = b;
        burnedOut = o;
    }

    public void recordIgnitions(int count) {
        totalIgnitions += count;
    }

    public void advanceStep() {
        step++;
    }

    public int getTotalCells() {
        return totalCells;
    }

    public int getUnburned() {
        return unburned;
    }

    public int getBurning() {
        return burning;
    }

    public int getBurnedOut() {
        return burnedOut;
    }

    public int getTotalIgnitions() {
        return totalIgnitions;
    }

    public int getStep() {
        return step;
    }

    /** Percentage of ALL cells that are burning or burned out. */
    public double getPercentAffected() {
        return totalCells == 0 ? 0.0 : 100.0 * (burning + burnedOut) / totalCells;
    }
}
