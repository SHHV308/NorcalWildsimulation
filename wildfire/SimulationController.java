package wildfire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Coordinates grid, wind, ignition setup, stepping and statistics. Free of Swing: the GUI
 * drives it by calling {@link #step()} from a {@code javax.swing.Timer}.
 */
public class SimulationController {

    public enum Status { IDLE, RUNNING, PAUSED, FINISHED }

    private final SimulationConfig config;
    private final Wind wind = Wind.calm();
    private final SimulationStatistics statistics = new SimulationStatistics();
    private final List<Runnable> listeners = new ArrayList<>();
    /** Insertion-ordered set of chosen start cells, stored as "row,col" keys -> cell. */
    private final Set<Cell> ignitionCells = new LinkedHashSet<>();

    private final long seed;
    private final Random fuelRandom;
    private Random random;

    private Grid grid;
    private FireSpreadEngine engine;
    private Status status = Status.IDLE;

    public SimulationController(int rows, int columns, SimulationConfig config, long seed) {
        this.config = config;
        this.seed = seed;
        this.fuelRandom = new Random(seed);
        this.random = new Random(seed);
        installGrid(new Grid(rows, columns, MaterialType.SPARSE_GRASS));
        grid.assignRandomFuel(fuelRandom);
        statistics.reset(grid);
    }

    // ----- observers -----

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    // ----- accessors -----

    public Grid getGrid() {
        return grid;
    }

    public Wind getWind() {
        return wind;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public SimulationStatistics getStatistics() {
        return statistics;
    }

    public Status getStatus() {
        return status;
    }

    public List<Cell> getIgnitionCells() {
        return Collections.unmodifiableList(new ArrayList<>(ignitionCells));
    }

    /** Term-by-term ignition explanation for a cell, from the current grid state. */
    public IgnitionBreakdown explainIgnition(Cell cell) {
        return engine.explainIgnition(cell, wind);
    }

    /** The signal a burning cell currently sends to a neighbour. */
    public IgnitionBreakdown.Signal signalBetween(Cell source, Cell target) {
        return engine.signalBetween(source, target, wind);
    }

    public NeighborManager getNeighborManager() {
        return engine.getNeighborManager();
    }

    /** Cell-by-cell record of the most recent step. */
    public StepTrace getLastTrace() {
        return engine.getLastTrace();
    }

    public boolean isFinished() {
        return status == Status.FINISHED;
    }

    // ----- landscape setup (only while IDLE) -----

    /** Replaces the grid with a new random-fuel grid; clears chosen ignition points. */
    public void generateGrid(int rows, int columns, boolean randomFuel) {
        requireIdle();
        installGrid(new Grid(rows, columns, MaterialType.SPARSE_GRASS));
        if (randomFuel) {
            grid.assignRandomFuel(fuelRandom);
        }
        ignitionCells.clear();
        statistics.reset(grid);
        fireChanged();
    }

    /**
     * Changes the grid to {@code rows x columns} in place: cells that still fit keep their fuel and
     * stay ignition points; cells cut off are dropped; new cells get random fuel when
     * {@code randomFuelForNewCells} is true, otherwise sparse grass.
     *
     * @return false if the size did not change
     */
    public boolean resizeGrid(int rows, int columns, boolean randomFuelForNewCells) {
        requireIdle();
        if (rows == grid.getRows() && columns == grid.getColumns()) {
            return false;
        }
        Grid next = grid.resized(rows, columns, randomFuelForNewCells ? fuelRandom : null);
        List<Cell> kept = new ArrayList<>();
        for (Cell cell : ignitionCells) {
            if (next.inBounds(cell.getRow(), cell.getColumn())) {
                Cell moved = next.getCell(cell.getRow(), cell.getColumn());
                if (moved.canIgnite()) {
                    kept.add(moved);
                }
            }
        }
        installGrid(next);
        ignitionCells.clear();
        ignitionCells.addAll(kept);
        statistics.reset(grid);
        fireChanged();
        return true;
    }

    /** Re-rolls random fuel on the current grid (ignition points that lose fuel are dropped). */
    public void randomizeFuel() {
        requireIdle();
        grid.assignRandomFuel(fuelRandom);
        ignitionCells.removeIf(cell -> !cell.canIgnite());
        statistics.reset(grid);
        fireChanged();
    }

    /** Manual fuel mode: paints one cell. Ignition on that cell is dropped if it has no fuel. */
    public void paintCell(int row, int column, MaterialType type) {
        requireIdle();
        grid.paint(row, column, type);
        ignitionCells.removeIf(cell -> !cell.canIgnite());
        fireChanged();
    }

    // ----- ignition configuration (only while IDLE) -----

    /**
     * Adds a manual ignition point.
     *
     * @throws IllegalArgumentException if out of bounds, without fuel, or already selected
     */
    public void addIgnition(int row, int column) {
        requireIdle();
        if (!grid.inBounds(row, column)) {
            throw new IllegalArgumentException("Cell (" + row + "," + column + ") is outside the grid.");
        }
        Cell cell = grid.getCell(row, column);
        if (!cell.canIgnite()) {
            throw new IllegalArgumentException(
                    "Cell " + cell.getId() + " has no fuel and cannot be an ignition point.");
        }
        if (!ignitionCells.add(cell)) {
            throw new IllegalArgumentException("Cell " + cell.getId() + " is already an ignition point.");
        }
        fireChanged();
    }

    public void removeIgnition(int row, int column) {
        requireIdle();
        if (grid.inBounds(row, column) && ignitionCells.remove(grid.getCell(row, column))) {
            fireChanged();
        }
    }

    public void clearIgnitions() {
        requireIdle();
        ignitionCells.clear();
        fireChanged();
    }

    /**
     * Replaces the ignition points with {@code count} distinct random cells that have fuel.
     *
     * @throws IllegalArgumentException if count < 1 or exceeds the number of ignitable cells
     */
    public void randomizeIgnitions(int count) {
        requireIdle();
        List<Cell> candidates = grid.ignitableCells();
        if (count < 1) {
            throw new IllegalArgumentException("At least one ignition point is required.");
        }
        if (count > candidates.size()) {
            throw new IllegalArgumentException("Only " + candidates.size()
                    + " cells have fuel; cannot place " + count + " fires.");
        }
        Collections.shuffle(candidates, fuelRandom);
        ignitionCells.clear();
        ignitionCells.addAll(candidates.subList(0, count));
        fireChanged();
    }

    // ----- run control -----

    /** Ignites the chosen start cells (step 0) and begins running. */
    public void start() {
        beginIfIdle();
        if (status != Status.FINISHED) {
            status = Status.RUNNING;
        }
        fireChanged();
    }

    public void pause() {
        if (status == Status.RUNNING) {
            status = Status.PAUSED;
            fireChanged();
        }
    }

    public void resume() {
        if (status == Status.PAUSED) {
            status = Status.RUNNING;
            fireChanged();
        }
    }

    /**
     * Advances one step (starting the run first if IDLE, leaving it PAUSED so the user can
     * single-step). Returns false when the simulation is finished and nothing was done.
     */
    public boolean step() {
        if (status == Status.IDLE) {
            beginIfIdle();
            status = Status.PAUSED;
        }
        if (status == Status.FINISHED) {
            return false;
        }
        statistics.advanceStep();
        FireSpreadEngine.StepResult result = engine.step(wind, statistics.getStep(), random);
        statistics.recordIgnitions(result.getNewIgnitions());
        statistics.refresh(grid);
        if (statistics.getBurning() == 0 || statistics.getStep() >= config.getMaxSteps()) {
            status = Status.FINISHED;
        }
        fireChanged();
        return true;
    }

    /** Restores the initial state: unburned full-fuel cells, zeroed statistics, same random seed. */
    public void reset() {
        grid.resetFire();
        engine.clearTrace();
        random = new Random(seed);
        statistics.reset(grid);
        status = Status.IDLE;
        fireChanged();
    }

    // ----- internals -----

    private void beginIfIdle() {
        if (status != Status.IDLE) {
            return;
        }
        if (ignitionCells.isEmpty()) {
            throw new IllegalStateException("Select at least one ignition point before starting.");
        }
        for (Cell cell : ignitionCells) {
            cell.ignite(0, config.peakIntensityFor(cell.getFuelValue()));
        }
        statistics.reset(grid);
        statistics.recordIgnitions(ignitionCells.size());
        if (statistics.getBurning() == 0) {
            status = Status.FINISHED;
        }
    }

    private void requireIdle() {
        if (status != Status.IDLE) {
            throw new IllegalStateException("Reset the simulation before changing the setup.");
        }
    }

    private void installGrid(Grid newGrid) {
        this.grid = newGrid;
        this.engine = new FireSpreadEngine(newGrid, config);
    }
}
