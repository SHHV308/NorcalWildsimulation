package wildfire;

import java.util.Random;

/**
 * Descriptive vegetation/material label for a cell, plus the illustrative fuel range
 * used when fuel is assigned automatically (TEAM ASSUMPTION, not professor-prescribed).
 *
 * <p>The label is information only: the spread algorithm reads {@link Cell#getFuelValue()}.
 */
public enum MaterialType {
    WATER("Water", 0, 0),
    BARE_LAND("Bare land", 0, 0),
    SPARSE_GRASS("Sparse grass", 1, 2),
    DENSE_GRASS("Dense grass", 3, 4),
    SHRUBS("Shrubs", 5, 6),
    DRY_FOREST("Dry forest vegetation", 7, 10);

    private final String label;
    private final int minFuel;
    private final int maxFuel;

    MaterialType(String label, int minFuel, int maxFuel) {
        this.label = label;
        this.minFuel = minFuel;
        this.maxFuel = maxFuel;
    }

    public String getLabel() {
        return label;
    }

    public int getMinFuel() {
        return minFuel;
    }

    public int getMaxFuel() {
        return maxFuel;
    }

    /** A fuel value drawn uniformly from this material's illustrative range. */
    public int randomFuel(Random random) {
        return minFuel + random.nextInt(maxFuel - minFuel + 1);
    }

    /** The midpoint of this material's fuel range (used for manual painting). */
    public int defaultFuel() {
        return (minFuel + maxFuel + 1) / 2;
    }

    @Override
    public String toString() {
        return label;
    }
}
