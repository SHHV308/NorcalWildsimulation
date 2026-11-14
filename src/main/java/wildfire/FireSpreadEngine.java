package wildfire;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Discrete-step fire spread model. Formula coefficients are illustrative team
 * assumptions for a classroom simulation, not calibrated wildfire physics.
 */
public final class FireSpreadEngine {
    private final Grid grid;
    private final Random random;
    private Wind wind;
    private int step;
    private int initialIgnitionCount;

    public FireSpreadEngine(Grid grid, Wind wind, long seed) {
        this.grid = grid;
        this.wind = wind == null ? Wind.calm() : wind;
        this.random = new Random(seed);
    }

    public void setWind(Wind wind) {
        if (wind == null) throw new IllegalArgumentException("Wind cannot be null.");
        this.wind = wind;
    }

    public Wind getWind() { return wind; }
    public int getStep() { return step; }
    public int getInitialIgnitionCount() { return initialIgnitionCount; }

    public void ignite(int row, int col) {
        Cell cell = grid.getCell(row, col);
        boolean wasUnburned = cell.getState() == CellState.UNBURNED;
        cell.ignite(step, 5.0);
        if (wasUnburned && cell.getState() == CellState.BURNING) initialIgnitionCount++;
    }

    public void igniteRandom(int count) {
        if (count < 1) throw new IllegalArgumentException("Ignition count must be at least 1.");
        grid.igniteRandom(count, random, step);
        initialIgnitionCount += count;
    }

    public double calculateIgnitionProbability(int row, int col) {
        Cell target = grid.getCell(row, col);
        if (target.getState() != CellState.UNBURNED || target.getRemainingFuel() <= 0) return 0;
        double probabilityNotIgnited = 1.0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr, nc = col + dc;
                if (nr < 0 || nr >= grid.getRows() || nc < 0 || nc >= grid.getCols()) continue;
                Cell source = grid.getCell(nr, nc);
                if (source.getState() != CellState.BURNING) continue;
                double distanceScale = (dr != 0 && dc != 0) ? 0.75 : 1.0;
                double alignment = wind.alignment(-dc, dr);
                double windFactor = 1.0 + Math.min(0.8, wind.getSpeed() / 20.0) * alignment;
                windFactor = Math.max(0.2, windFactor);
                double sourceFactor = 0.35 + source.getIntensity() / 20.0;
                double fuelFactor = target.getRemainingFuel() / 10.0;
                double p = 0.16 * distanceScale * windFactor * sourceFactor * fuelFactor;
                p = Math.max(0, Math.min(0.85, p));
                probabilityNotIgnited *= (1.0 - p);
            }
        }
        return Math.max(0, Math.min(1, 1.0 - probabilityNotIgnited));
    }

    /** Uses the current grid snapshot to decide all new ignitions before applying them. */
    public void advanceOneStep() {
        double[][] probabilities = new double[grid.getRows()][grid.getCols()];
        List<Cell> newlyIgnited = new ArrayList<>();
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                Cell cell = grid.getCell(r, c);
                double p = calculateIgnitionProbability(r, c);
                cell.setIgnitionProbability(p);
                probabilities[r][c] = p;
            }
        }
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getCols(); c++) {
                Cell cell = grid.getCell(r, c);
                if (cell.getState() == CellState.UNBURNED && random.nextDouble() < probabilities[r][c]) {
                    newlyIgnited.add(cell);
                }
            }
        }
        for (int r = 0; r < grid.getRows(); r++)
            for (int c = 0; c < grid.getCols(); c++)
                grid.getCell(r, c).burnOneStep();
        step++;
        for (Cell cell : newlyIgnited) cell.ignite(step, 5.0);
    }

    public void reset() {
        grid.reset();
        step = 0;
        initialIgnitionCount = 0;
    }
}
