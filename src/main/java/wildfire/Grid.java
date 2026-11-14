package wildfire;

import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rectangular grid. Default material/fuel values are illustrative team assumptions. */
public final class Grid {
    private final Cell[][] cells;

    public Grid(int rows, int cols, long seed) {
        if (rows < 2 || cols < 2) throw new IllegalArgumentException("Grid must be at least 2 x 2.");
        cells = new Cell[rows][cols];
        Random random = new Random(seed);
        String[] materials = {"Grass", "Shrub", "Dry forest", "Sparse grass", "Bare ground", "Water"};
        double[] fuelRanges = {3.0, 5.5, 8.5, 2.0, 0.0, 0.0};
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int type = random.nextInt(materials.length);
                double fuel = fuelRanges[type];
                if (fuel > 0) fuel = Math.min(10, Math.max(1, fuel + (random.nextDouble() * 2 - 1)));
                cells[r][c] = new Cell(r, c, materials[type], fuel);
            }
        }
    }

    public int getRows() { return cells.length; }
    public int getCols() { return cells[0].length; }
    public Cell getCell(int row, int col) { return cells[row][col]; }

    public void reset() {
        for (Cell[] row : cells) for (Cell cell : row) cell.reset();
    }

    public int count(CellState state) {
        int count = 0;
        for (Cell[] row : cells) for (Cell cell : row) if (cell.getState() == state) count++;
        return count;
    }

    public void igniteRandom(int count, Random random, int step) {
        if (count < 1) throw new IllegalArgumentException("Ignition count must be at least 1.");
        List<Cell> candidates = new ArrayList<>();
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                if (cell.getState() == CellState.UNBURNED) candidates.add(cell);
            }
        }
        if (count > candidates.size()) {
            throw new IllegalStateException("Requested " + count + " ignition points, but only "
                    + candidates.size() + " burnable cells remain.");
        }
        Collections.shuffle(candidates, random);
        for (int i = 0; i < count; i++) candidates.get(i).ignite(step, 5.0);
    }
}
