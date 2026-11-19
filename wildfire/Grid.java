package wildfire;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/** Creates and owns the 2-D array of {@link Cell} objects. */
public class Grid {

    public static final int MIN_DIMENSION = 3;
    /** Upper bound per side; rows (n) and columns (m) are independent, so any n x m up to 100 x 100. */
    public static final int MAX_DIMENSION = 100;

    /** Weighted material mix for random fuel assignment (TEAM ASSUMPTION). Sums to 100. */
    private static final MaterialType[] RANDOM_MIX_MATERIAL = {
        MaterialType.WATER, MaterialType.BARE_LAND, MaterialType.SPARSE_GRASS,
        MaterialType.DENSE_GRASS, MaterialType.SHRUBS, MaterialType.DRY_FOREST
    };
    private static final int[] RANDOM_MIX_WEIGHT = {5, 5, 20, 25, 25, 20};

    private final int rows;
    private final int columns;
    private final Cell[][] cells;

    /** Creates a grid filled with the given material at its default fuel value. */
    public Grid(int rows, int columns, MaterialType fill) {
        if (rows < MIN_DIMENSION || columns < MIN_DIMENSION
                || rows > MAX_DIMENSION || columns > MAX_DIMENSION) {
            throw new IllegalArgumentException("Grid dimensions must be between "
                    + MIN_DIMENSION + " and " + MAX_DIMENSION + ".");
        }
        this.rows = rows;
        this.columns = columns;
        this.cells = new Cell[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                cells[r][c] = new Cell(r, c, fill, fill.defaultFuel());
            }
        }
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getTotalCells() {
        return rows * columns;
    }

    public boolean inBounds(int row, int column) {
        return row >= 0 && row < rows && column >= 0 && column < columns;
    }

    public Cell getCell(int row, int column) {
        if (!inBounds(row, column)) {
            throw new IndexOutOfBoundsException("No cell at (" + row + "," + column + ").");
        }
        return cells[row][column];
    }

    public void forEachCell(Consumer<Cell> action) {
        for (Cell[] line : cells) {
            for (Cell cell : line) {
                action.accept(cell);
            }
        }
    }

    public List<Cell> allCells() {
        List<Cell> list = new ArrayList<>(getTotalCells());
        forEachCell(list::add);
        return list;
    }

    /** Cells that could start a fire: unburned and with fuel. */
    public List<Cell> ignitableCells() {
        List<Cell> list = new ArrayList<>();
        forEachCell(cell -> {
            if (cell.canIgnite()) {
                list.add(cell);
            }
        });
        return list;
    }

    // ----- fuel assignment -----

    /** Random fuel mode: each cell gets a weighted random material and a fuel value in its range. */
    public void assignRandomFuel(Random random) {
        forEachCell(cell -> {
            MaterialType type = pickWeightedMaterial(random);
            cell.setFuel(type, type.randomFuel(random));
        });
    }

    /** Manual fuel mode: paints one cell with a material (default fuel for that material). */
    public void paint(int row, int column, MaterialType type) {
        paint(row, column, type, type.defaultFuel());
    }

    public void paint(int row, int column, MaterialType type, int fuelValue) {
        getCell(row, column).setFuel(type, fuelValue);
    }

    /**
     * A new n x m grid that keeps this grid's layout where the two overlap (same row and column
     * keep their material and fuel). Cells added by growing get random fuel when
     * {@code randomForNewCells} is given, otherwise sparse grass.
     */
    public Grid resized(int newRows, int newColumns, Random randomForNewCells) {
        Grid next = new Grid(newRows, newColumns, MaterialType.SPARSE_GRASS);
        for (int r = 0; r < newRows; r++) {
            for (int c = 0; c < newColumns; c++) {
                Cell target = next.getCell(r, c);
                if (inBounds(r, c)) {
                    Cell old = getCell(r, c);
                    target.setFuel(old.getMaterialType(), old.getFuelValue());
                } else if (randomForNewCells != null) {
                    MaterialType type = pickWeightedMaterial(randomForNewCells);
                    target.setFuel(type, type.randomFuel(randomForNewCells));
                }
            }
        }
        return next;
    }

    /** Restores every cell to unburned with full fuel; the fuel layout is unchanged. */
    public void resetFire() {
        forEachCell(Cell::resetFire);
    }

    private static MaterialType pickWeightedMaterial(Random random) {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (int i = 0; i < RANDOM_MIX_MATERIAL.length; i++) {
            cumulative += RANDOM_MIX_WEIGHT[i];
            if (roll < cumulative) {
                return RANDOM_MIX_MATERIAL[i];
            }
        }
        return MaterialType.DRY_FOREST;
    }
}
