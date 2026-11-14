package wildfire;

public final class SimulationStatistics {
    private final Grid grid;
    public SimulationStatistics(Grid grid) { this.grid = grid; }
    public int getBurning() { return grid.count(CellState.BURNING); }
    public int getBurned() { return grid.count(CellState.BURNED); }
    public int getUnburned() { return grid.count(CellState.UNBURNED); }
    public int getNonBurnable() { return grid.count(CellState.NON_BURNABLE); }
    public int getAffected() { return getBurning() + getBurned(); }
    public int getSimulationSteps() { return 0; } // The engine owns the global step counter.
    public int getLongestBurnDuration() {
        int longest = 0;
        for (int r = 0; r < grid.getRows(); r++)
            for (int c = 0; c < grid.getCols(); c++)
                longest = Math.max(longest, grid.getCell(r, c).getBurningSteps());
        return longest;
    }
    public double getAverageBurnDuration() {
        int total = 0, cells = 0;
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                Cell cell = grid.getCell(r, c);
                if (cell.getBurningSteps() > 0) { total += cell.getBurningSteps(); cells++; }
            }
        }
        return cells == 0 ? 0 : (double) total / cells;
    }
    public double getAffectedPercent() {
        int total = grid.getRows() * grid.getCols() - getNonBurnable();
        return total == 0 ? 0 : 100.0 * getAffected() / total;
    }
}
