package wildfire;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Calculates ignition probabilities and advances the fire by one discrete step.
 *
 * <p>Professor-stated concept: ignition probability = f(neighbouring fire, target fuel, wind).
 * The concrete formula below is a TEAM ASSUMPTION and is not scientifically validated:
 *
 * <pre>
 *   contribution(n) = clamp01( spreadCoefficient * intensity(n) * distanceWeight(n) * windMultiplier(n -> target) )
 *   fireInfluence   = 1 - product(1 - contribution(n))        over burning neighbours n
 *   fuelFactor      = 0 if remaining fuel = 0, else minFuelFactor + (1 - minFuelFactor) * fuel / maxFuel
 *   P(ignite)       = clamp01( fireInfluence * fuelFactor )
 * </pre>
 *
 * Wind only scales a burning neighbour's contribution, so with no burning neighbour
 * fireInfluence is 0 and wind alone can never ignite a cell.
 *
 * <p>Cell-to-cell communication: each burning neighbour sends the target one
 * {@link IgnitionBreakdown.Signal}. {@link #explainIgnition} builds every signal and the combined
 * result; the simulation step uses that same method, so what the GUI shows is exactly what the
 * model computed.
 */
public class FireSpreadEngine {

    /** Outcome counts of one simulation step. */
    public static final class StepResult {
        private final int newIgnitions;
        private final int burnedOut;

        StepResult(int newIgnitions, int burnedOut) {
            this.newIgnitions = newIgnitions;
            this.burnedOut = burnedOut;
        }

        public int getNewIgnitions() {
            return newIgnitions;
        }

        public int getBurnedOut() {
            return burnedOut;
        }
    }

    private final Grid grid;
    private final SimulationConfig config;
    private final NeighborManager neighbors;
    private StepTrace lastTrace = StepTrace.empty();

    public FireSpreadEngine(Grid grid, SimulationConfig config) {
        this.grid = grid;
        this.config = config;
        this.neighbors = new NeighborManager(grid, config.isIncludeDiagonals());
    }

    public NeighborManager getNeighborManager() {
        return neighbors;
    }

    /** Combined influence of all burning neighbours on {@code target}, in [0, 1]. */
    public double calculateFireInfluence(Cell target, Wind wind) {
        return explainIgnition(target, wind).getFireInfluence();
    }

    /** Ignition probability for one cell given the current grid state; always in [0, 1]. */
    public double calculateIgnitionProbability(Cell target, Wind wind) {
        return explainIgnition(target, wind).getProbability();
    }

    /**
     * The signal a burning {@code source} sends to an adjacent {@code target}: every factor of
     * {@code contribution = clamp01(spreadCoefficient * intensity * distanceWeight * windMultiplier)}.
     */
    public IgnitionBreakdown.Signal signalBetween(Cell source, Cell target, Wind wind) {
        int rowOffset = target.getRow() - source.getRow();
        int columnOffset = target.getColumn() - source.getColumn();
        double distanceWeight = NeighborManager.isDiagonal(source, target)
                ? config.getDiagonalWeight() : 1.0;
        double alignment = wind.alignment(rowOffset, columnOffset);
        double windMultiplier = wind.spreadMultiplier(rowOffset, columnOffset, config.getWindGain());
        double raw = config.getSpreadCoefficient() * source.getFireIntensity() * distanceWeight * windMultiplier;
        return new IgnitionBreakdown.Signal(source, target, source.getFireIntensity(), distanceWeight,
                alignment, windMultiplier, raw, clamp01(raw));
    }

    /**
     * Term-by-term explanation of the target's ignition probability from the current grid state.
     * The simulation step uses this same method, so the explanation always matches the outcome.
     */
    public IgnitionBreakdown explainIgnition(Cell target, Wind wind) {
        List<IgnitionBreakdown.Signal> signals = new ArrayList<>(8);
        double notIgnitedByAny = 1.0;
        for (Cell source : neighbors.burningNeighborsOf(target)) {
            IgnitionBreakdown.Signal signal = signalBetween(source, target, wind);
            signals.add(signal);
            notIgnitedByAny *= (1.0 - signal.getContribution());
        }
        double influence = clamp01(1.0 - notIgnitedByAny);
        boolean eligible = target.canIgnite(); // false: no fuel, already burning, or burned out
        double fuelFactor = eligible ? config.fuelFactorFor(target.getRemainingFuel()) : 0.0;
        double probability = eligible ? clamp01(influence * fuelFactor) : 0.0;
        return new IgnitionBreakdown(target, signals, influence, fuelFactor, probability, eligible);
    }

    /** Cell-by-cell record of the most recent {@link #step}. */
    public StepTrace getLastTrace() {
        return lastTrace;
    }

    /** Forgets the last step's record (used on reset). */
    public void clearTrace() {
        lastTrace = StepTrace.empty();
    }

    /**
     * Advances the simulation by one step.
     *
     * <ol>
     *   <li>Phase 1 (read-only): from the grid state at the start of the step, calculate every
     *       eligible cell's probability and sample ignitions. No cell state changes yet, so
     *       processing order cannot bias the outcome.</li>
     *   <li>Phase 2: cells that were burning at the start of the step burn (consume fuel,
     *       update intensity, possibly burn out).</li>
     *   <li>Phase 3: the cells chosen in phase 1 ignite together. Each records its strongest
     *       contributing neighbour as the cell that passed the fire on.</li>
     * </ol>
     */
    public StepResult step(Wind wind, int stepNumber, Random random) {
        List<Cell> toIgnite = new ArrayList<>();
        List<IgnitionBreakdown.Signal> igniters = new ArrayList<>();
        List<Cell> burning = new ArrayList<>();
        List<StepTrace.Evaluation> evaluations = new ArrayList<>();

        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getColumns(); c++) {
                Cell cell = grid.getCell(r, c);
                if (cell.isBurning()) {
                    burning.add(cell);
                } else if (cell.canIgnite()) {
                    IgnitionBreakdown breakdown = explainIgnition(cell, wind);
                    double probability = breakdown.getProbability();
                    cell.setIgnitionProbability(probability);
                    if (probability > 0.0) {
                        double roll = random.nextDouble();
                        boolean ignites = roll < probability;
                        cell.setLastRoll(roll);
                        evaluations.add(new StepTrace.Evaluation(breakdown, roll, ignites));
                        if (ignites) {
                            toIgnite.add(cell);
                            igniters.add(breakdown.getStrongestSignal());
                        }
                    }
                }
            }
        }

        List<Cell> burnedOutCells = new ArrayList<>();
        for (Cell cell : burning) {
            cell.burn(config.getFuelConsumptionPerStep());
            if (cell.getState() == FireState.BURNED_OUT) {
                burnedOutCells.add(cell);
            }
        }
        for (int i = 0; i < toIgnite.size(); i++) {
            Cell cell = toIgnite.get(i);
            IgnitionBreakdown.Signal via = igniters.get(i);
            cell.ignite(stepNumber, config.peakIntensityFor(cell.getFuelValue()), via.getSource());
            cell.setIgnitionWindAlignment(via.getWindAlignment());
        }
        lastTrace = new StepTrace(stepNumber, evaluations, burnedOutCells);
        return new StepResult(toIgnite.size(), burnedOutCells.size());
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
