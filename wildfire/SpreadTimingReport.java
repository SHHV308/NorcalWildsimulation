package wildfire;

import java.util.EnumMap;
import java.util.Map;

/**
 * How long fire took to travel from one cell to the next, grouped by the vegetation it moved
 * into and by how the move lined up with the wind. Built from the cells' recorded ignitions,
 * so it reflects exactly what happened in the current run.
 *
 * <p>Travel time = target ignition step - source ignition step (the source is the burning
 * neighbour with the strongest signal at the moment of ignition). Start fires are not counted.
 */
public final class SpreadTimingReport {

    /** Direction of a cell-to-cell move relative to the wind, by cosine alignment. */
    public enum WindRelation {
        DOWNWIND("Downwind (with the wind)"),
        CROSSWIND("Crosswind"),
        UPWIND("Upwind (against the wind)");

        private final String label;

        WindRelation(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** cos >= 0.38 (within ~67.5 deg of the wind) is downwind, cos <= -0.38 upwind, else crosswind. */
        public static WindRelation of(double alignment) {
            if (alignment >= 0.38) {
                return DOWNWIND;
            }
            if (alignment <= -0.38) {
                return UPWIND;
            }
            return CROSSWIND;
        }
    }

    /** Count, mean, min and max travel time (in steps) for one group. */
    public static final class Stat {
        private int count;
        private long total;
        private int min = Integer.MAX_VALUE;
        private int max;

        void add(int steps) {
            count++;
            total += steps;
            min = Math.min(min, steps);
            max = Math.max(max, steps);
        }

        public int getCount() {
            return count;
        }

        public double getAverage() {
            return count == 0 ? 0.0 : (double) total / count;
        }

        public int getMin() {
            return count == 0 ? 0 : min;
        }

        public int getMax() {
            return max;
        }
    }

    private final Stat overall = new Stat();
    private final Map<MaterialType, Stat> byMaterial = new EnumMap<>(MaterialType.class);
    private final Map<WindRelation, Stat> byWind = new EnumMap<>(WindRelation.class);
    /** Calm wind makes the direction groups meaningless; tracked separately. */
    private final Stat calm = new Stat();

    /**
     * @param calmWind true when the wind is calm (0 mph): direction groups are then meaningless,
     *                 so moves are only grouped by vegetation
     */
    public static SpreadTimingReport of(Grid grid, boolean calmWind) {
        SpreadTimingReport report = new SpreadTimingReport();
        for (Cell cell : grid.allCells()) {
            int steps = cell.getTravelSteps();
            if (steps < 0) {
                continue;
            }
            report.overall.add(steps);
            report.byMaterial.computeIfAbsent(cell.getMaterialType(), m -> new Stat()).add(steps);
            if (calmWind) {
                report.calm.add(steps);
            } else {
                report.byWind.computeIfAbsent(WindRelation.of(cell.getIgnitionWindAlignment()), w -> new Stat())
                        .add(steps);
            }
        }
        return report;
    }

    public Stat getOverall() {
        return overall;
    }

    /** Stat for a material, or an empty one when no fire moved into that material. */
    public Stat forMaterial(MaterialType type) {
        return byMaterial.getOrDefault(type, new Stat());
    }

    public Stat forWind(WindRelation relation) {
        return byWind.getOrDefault(relation, new Stat());
    }

    /** Plain-text table, with each average also shown as simulated time. */
    public String format(SimulationConfig config, Wind wind) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CELL-TO-CELL TRAVEL TIME  (1 step = %s simulated; start fires excluded)%n",
                config.formatSteps(1)));
        if (overall.getCount() == 0) {
            sb.append("  No cell has been ignited by a neighbour yet. Press Step or Start.\n");
            return sb.toString();
        }
        sb.append(String.format("  All moves: %d, average %.2f steps (%s), fastest %d, slowest %d%n%n",
                overall.getCount(), overall.getAverage(), config.formatSteps(overall.getAverage()),
                overall.getMin(), overall.getMax()));

        sb.append("  BY VEGETATION OF THE CELL THE FIRE MOVED INTO\n");
        sb.append(String.format("  %-22s %5s %9s %11s %5s %5s%n", "Vegetation", "moves", "avg steps", "avg time",
                "min", "max"));
        for (MaterialType type : MaterialType.values()) {
            Stat s = forMaterial(type);
            if (type.getMaxFuel() == 0) {
                sb.append(String.format("  %-22s %5s   never burns (fuel 0)%n", type.getLabel(), "-"));
            } else if (s.getCount() == 0) {
                sb.append(String.format("  %-22s %5d %9s%n", type.getLabel(), 0, "-"));
            } else {
                sb.append(String.format("  %-22s %5d %9.2f %11s %5d %5d%n", type.getLabel(), s.getCount(),
                        s.getAverage(), config.formatSteps(s.getAverage()), s.getMin(), s.getMax()));
            }
        }

        BeaufortScale beaufort = wind.getBeaufort();
        sb.append(String.format("%n  BY DIRECTION RELATIVE TO THE WIND  (wind blowing toward %s, %.0f mph, %s)%n",
                wind.compassName(), wind.getSpeedMph(), beaufort));
        if (calm.getCount() > 0 && byWind.isEmpty()) {
            sb.append(String.format("  Calm air: no direction effect. %d moves, average %.2f steps.%n",
                    calm.getCount(), calm.getAverage()));
        } else {
            sb.append(String.format("  %-26s %5s %9s %11s%n", "Move direction", "moves", "avg steps", "avg time"));
            for (WindRelation relation : WindRelation.values()) {
                Stat s = forWind(relation);
                sb.append(s.getCount() == 0
                        ? String.format("  %-26s %5d %9s%n", relation.getLabel(), 0, "-")
                        : String.format("  %-26s %5d %9.2f %11s%n", relation.getLabel(), s.getCount(),
                                s.getAverage(), config.formatSteps(s.getAverage())));
            }
        }
        sb.append("\n  Why it differs: the target's fuel sets its fuel factor, the source's fuel sets its\n"
                + "  heat (intensity), and the wind multiplies each signal by 1 + strength x cos(angle).\n"
                + "  Expected wait for a cell is about 1 / P steps.\n");
        return sb.toString();
    }
}
