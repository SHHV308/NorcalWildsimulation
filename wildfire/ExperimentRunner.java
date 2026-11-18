package wildfire;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs many seeded, headless simulations to answer the professor's question: "under which
 * conditions does the fire spread, and how many initial fires are needed for a big fire?"
 *
 * <p>Each run uses a fresh random landscape and random ignition points. A run counts as a
 * "major fire" when more than {@value #MAJOR_FIRE_FRACTION_PERCENT}% of the cells that had
 * any fuel were burned (TEAM ASSUMPTION for the threshold).
 */
public class ExperimentRunner {

    public static final int MAJOR_FIRE_FRACTION_PERCENT = 50;

    /** Aggregated outcome of all runs for one parameter value. */
    public static final class Row {
        private final String parameter;
        private final int runs;
        private final double averagePercentOfBurnable;
        private final double minPercentOfBurnable;
        private final double maxPercentOfBurnable;
        private final double majorFirePercentOfRuns;
        private final double averageSteps;

        Row(String parameter, int runs, double avg, double min, double max, double major, double steps) {
            this.parameter = parameter;
            this.runs = runs;
            this.averagePercentOfBurnable = avg;
            this.minPercentOfBurnable = min;
            this.maxPercentOfBurnable = max;
            this.majorFirePercentOfRuns = major;
            this.averageSteps = steps;
        }

        public String getParameter() {
            return parameter;
        }

        public int getRuns() {
            return runs;
        }

        public double getAveragePercentOfBurnable() {
            return averagePercentOfBurnable;
        }

        public double getMinPercentOfBurnable() {
            return minPercentOfBurnable;
        }

        public double getMaxPercentOfBurnable() {
            return maxPercentOfBurnable;
        }

        public double getMajorFirePercentOfRuns() {
            return majorFirePercentOfRuns;
        }

        public double getAverageSteps() {
            return averageSteps;
        }
    }

    private final int rows;
    private final int columns;
    private final SimulationConfig config;
    private final long baseSeed;

    public ExperimentRunner(int rows, int columns, SimulationConfig config, long baseSeed) {
        this.rows = rows;
        this.columns = columns;
        this.config = config;
        this.baseSeed = baseSeed;
    }

    /** Varies the number of initial fires from 1 to {@code maxFires}, wind held fixed. */
    public List<Row> sweepInitialFires(int maxFires, int runsPerValue, Wind wind) {
        List<Row> result = new ArrayList<>();
        for (int fires = 1; fires <= maxFires; fires++) {
            result.add(runSeries(fires + (fires == 1 ? " fire" : " fires"), fires, runsPerValue, wind));
        }
        return result;
    }

    /** Wind speeds (mph) swept by {@link #sweepWindSpeed}: calm, F4, F6, F10, F12 on the Beaufort scale. */
    public static final double[] SWEEP_MPH = {0, 15, 28, 58, 73};

    /** Varies wind speed over {@link #SWEEP_MPH} with fixed direction and number of fires. */
    public List<Row> sweepWindSpeed(int fires, int runsPerValue, double directionDegrees) {
        List<Row> result = new ArrayList<>();
        for (double mph : SWEEP_MPH) {
            result.add(runSeries(String.format("%2.0f mph F%d", mph, BeaufortScale.of(mph).getForce()),
                    fires, runsPerValue, Wind.ofMph(directionDegrees, mph)));
        }
        return result;
    }

    private Row runSeries(String label, int fires, int runs, Wind wind) {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = 0;
        int major = 0;
        long steps = 0;
        int counted = 0;
        for (int run = 0; run < runs; run++) {
            SimulationController controller = new SimulationController(
                    rows, columns, config, baseSeed + run);
            int burnable = (int) controller.getGrid().allCells().stream()
                    .filter(Cell::canIgnite).count();
            if (burnable < fires) {
                continue; // cannot place that many fires on this landscape
            }
            controller.getWind().setDirectionDegrees(wind.getDirectionDegrees());
            controller.getWind().setSpeedMph(wind.getSpeedMph());
            controller.randomizeIgnitions(fires);
            controller.start();
            while (controller.step()) {
                // run to completion
            }
            SimulationStatistics stats = controller.getStatistics();
            double percent = 100.0 * (stats.getBurning() + stats.getBurnedOut()) / burnable;
            sum += percent;
            min = Math.min(min, percent);
            max = Math.max(max, percent);
            if (percent > MAJOR_FIRE_FRACTION_PERCENT) {
                major++;
            }
            steps += stats.getStep();
            counted++;
        }
        if (counted == 0) {
            return new Row(label, 0, 0, 0, 0, 0, 0);
        }
        return new Row(label, counted, sum / counted, min, max, 100.0 * major / counted,
                (double) steps / counted);
    }

    /** Plain-text table for display. */
    public static String format(String title, List<Row> rows) {
        StringBuilder sb = new StringBuilder(title).append('\n');
        sb.append(String.format("%-12s %5s %10s %8s %8s %12s %8s%n",
                "Setting", "Runs", "Avg burned", "Min", "Max", "Major fire", "Avg steps"));
        for (Row row : rows) {
            sb.append(String.format("%-12s %5d %9.1f%% %7.1f%% %7.1f%% %11.0f%% %8.1f%n",
                    row.getParameter(), row.getRuns(), row.getAveragePercentOfBurnable(),
                    row.getMinPercentOfBurnable(), row.getMaxPercentOfBurnable(),
                    row.getMajorFirePercentOfRuns(), row.getAverageSteps()));
        }
        return sb.toString();
    }
}
