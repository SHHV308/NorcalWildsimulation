package wildfire;

import java.util.ArrayList;
import java.util.List;

/** Finds a cell's neighbours and reports which ones are burning. */
public class NeighborManager {

    private static final int[][] ORTHOGONAL = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
    private static final int[][] DIAGONAL = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};

    private final Grid grid;
    private final boolean includeDiagonals;

    public NeighborManager(Grid grid, boolean includeDiagonals) {
        this.grid = grid;
        this.includeDiagonals = includeDiagonals;
    }

    /** All in-bounds neighbours (4 or 8, fewer on edges). */
    public List<Cell> neighborsOf(Cell cell) {
        List<Cell> result = new ArrayList<>(8);
        collect(cell, ORTHOGONAL, result);
        if (includeDiagonals) {
            collect(cell, DIAGONAL, result);
        }
        return result;
    }

    /** Neighbours whose state is BURNING right now. */
    public List<Cell> burningNeighborsOf(Cell cell) {
        List<Cell> burning = new ArrayList<>(8);
        for (Cell neighbor : neighborsOf(cell)) {
            if (neighbor.isBurning()) {
                burning.add(neighbor);
            }
        }
        return burning;
    }

    /** True if the offset from one cell to the other is diagonal. */
    public static boolean isDiagonal(Cell from, Cell to) {
        return from.getRow() != to.getRow() && from.getColumn() != to.getColumn();
    }

    private void collect(Cell cell, int[][] offsets, List<Cell> out) {
        for (int[] offset : offsets) {
            int r = cell.getRow() + offset[0];
            int c = cell.getColumn() + offset[1];
            if (grid.inBounds(r, c)) {
                out.add(grid.getCell(r, c));
            }
        }
    }
}
