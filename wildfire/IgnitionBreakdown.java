package wildfire;

import java.util.Collections;
import java.util.List;

/**
 * A full, term-by-term explanation of how the neighbours of one target cell "talk" to it.
 *
 * <p>Each burning neighbour sends a {@link Signal} to the target. The engine combines all signals
 * into {@code fireInfluence}, scales it by the target's {@code fuelFactor} and gets the ignition
 * probability. This object holds every intermediate number so the GUI can show exactly why a cell
 * did or did not catch fire. It is produced by {@link FireSpreadEngine#explainIgnition(Cell, Wind)},
 * which is the same code path the simulation itself uses, so the numbers always match.
 */
public final class IgnitionBreakdown {

    /** One message from a burning source cell to a neighbouring target cell. */
    public static final class Signal {
        private final Cell source;
        private final Cell target;
        private final double sourceIntensity;
        private final double distanceWeight;
        private final double windAlignment;
        private final double windMultiplier;
        private final double rawContribution;
        private final double contribution;

        Signal(Cell source, Cell target, double sourceIntensity, double distanceWeight,
               double windAlignment, double windMultiplier, double rawContribution, double contribution) {
            this.source = source;
            this.target = target;
            this.sourceIntensity = sourceIntensity;
            this.distanceWeight = distanceWeight;
            this.windAlignment = windAlignment;
            this.windMultiplier = windMultiplier;
            this.rawContribution = rawContribution;
            this.contribution = contribution;
        }

        public Cell getSource() {
            return source;
        }

        public Cell getTarget() {
            return target;
        }

        /** Row offset source -> target (+1 = target is south of source). */
        public int getRowOffset() {
            return target.getRow() - source.getRow();
        }

        /** Column offset source -> target (+1 = target is east of source). */
        public int getColumnOffset() {
            return target.getColumn() - source.getColumn();
        }

        /** Compass direction the signal travels in (source -> target), e.g. "SE". */
        public String getDirectionName() {
            return directionName(getRowOffset(), getColumnOffset());
        }

        public double getSourceIntensity() {
            return sourceIntensity;
        }

        public double getDistanceWeight() {
            return distanceWeight;
        }

        /** Cosine between wind and the source -> target direction, in [-1, 1]. */
        public double getWindAlignment() {
            return windAlignment;
        }

        public double getWindMultiplier() {
            return windMultiplier;
        }

        /** spreadCoefficient * intensity * distanceWeight * windMultiplier, before clamping. */
        public double getRawContribution() {
            return rawContribution;
        }

        /** The clamped [0, 1] chance that this source alone would ignite the target. */
        public double getContribution() {
            return contribution;
        }
    }

    private final Cell target;
    private final List<Signal> signals;
    private final double fireInfluence;
    private final double fuelFactor;
    private final double probability;
    private final boolean eligible;

    IgnitionBreakdown(Cell target, List<Signal> signals, double fireInfluence,
                      double fuelFactor, double probability, boolean eligible) {
        this.target = target;
        this.signals = Collections.unmodifiableList(signals);
        this.fireInfluence = fireInfluence;
        this.fuelFactor = fuelFactor;
        this.probability = probability;
        this.eligible = eligible;
    }

    public Cell getTarget() {
        return target;
    }

    /** Signals from every burning neighbour, in neighbour order (N, E, S, W, then diagonals). */
    public List<Signal> getSignals() {
        return signals;
    }

    /** 1 - product(1 - contribution) over all signals. */
    public double getFireInfluence() {
        return fireInfluence;
    }

    public double getFuelFactor() {
        return fuelFactor;
    }

    /** Final ignition probability (0 if the target cannot ignite). */
    public double getProbability() {
        return probability;
    }

    /** False when the target is burning, burned out, or has no fuel. */
    public boolean isEligible() {
        return eligible;
    }

    /** The signal with the largest contribution, or null when no neighbour is burning. */
    public Signal getStrongestSignal() {
        Signal best = null;
        for (Signal s : signals) {
            if (best == null || s.getContribution() > best.getContribution()) {
                best = s;
            }
        }
        return best;
    }

    static String directionName(int rowOffset, int columnOffset) {
        String ns = rowOffset < 0 ? "N" : rowOffset > 0 ? "S" : "";
        String ew = columnOffset > 0 ? "E" : columnOffset < 0 ? "W" : "";
        String name = ns + ew;
        return name.isEmpty() ? "-" : name;
    }
}
