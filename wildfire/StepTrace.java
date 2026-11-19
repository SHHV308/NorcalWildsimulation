package wildfire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What happened in one simulation step, cell by cell: every ignition test that was made
 * (probability, random draw, outcome) and every cell that burned out. Used for display only.
 */
public final class StepTrace {

    /** One ignition test on one target cell during phase 1 of a step. */
    public static final class Evaluation {
        private final IgnitionBreakdown breakdown;
        private final double roll;
        private final boolean ignited;

        Evaluation(IgnitionBreakdown breakdown, double roll, boolean ignited) {
            this.breakdown = breakdown;
            this.roll = roll;
            this.ignited = ignited;
        }

        public IgnitionBreakdown getBreakdown() {
            return breakdown;
        }

        public Cell getTarget() {
            return breakdown.getTarget();
        }

        public double getProbability() {
            return breakdown.getProbability();
        }

        /** The uniform [0, 1) random number drawn; the cell ignites when roll &lt; probability. */
        public double getRoll() {
            return roll;
        }

        public boolean isIgnited() {
            return ignited;
        }
    }

    private final int step;
    private final List<Evaluation> evaluations;
    private final List<Cell> burnedOut;

    StepTrace(int step, List<Evaluation> evaluations, List<Cell> burnedOut) {
        this.step = step;
        this.evaluations = Collections.unmodifiableList(new ArrayList<>(evaluations));
        this.burnedOut = Collections.unmodifiableList(new ArrayList<>(burnedOut));
    }

    static StepTrace empty() {
        return new StepTrace(0, List.of(), List.of());
    }

    public int getStep() {
        return step;
    }

    /** Ignition tests with probability &gt; 0, in grid order. */
    public List<Evaluation> getEvaluations() {
        return evaluations;
    }

    public List<Cell> getBurnedOut() {
        return burnedOut;
    }

    /** The test made on {@code cell} in this step, or null if it was not tested. */
    public Evaluation evaluationFor(Cell cell) {
        for (Evaluation e : evaluations) {
            if (e.getTarget() == cell) {
                return e;
            }
        }
        return null;
    }
}
