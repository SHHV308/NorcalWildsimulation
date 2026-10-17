import java.util.Random;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class FireSimulation {

    private static final double HEAT_OF_COMBUSTION_KJ_PER_KG = 18_000.0;
    private static final double WIND_SPREAD_COEFFICIENT = 0.12;
    private static final double DIRECTIONAL_WIND_COEFFICIENT = 0.06;
    private static final double MIN_DIRECTIONAL_MULTIPLIER = 0.25;
    private static final double MAX_DIRECTIONAL_MULTIPLIER = 3.0;
    private static final double BURN_DURATION_SECONDS = 600.0;

    private final FireCell[][] cells;
    private final double cellSizeMeters;
    private final double timeStepSeconds;
    private Wind wind;
    private Random random;
    private final long randomSeed;
    private double timeSeconds;
    private WeatherObservation weatherObservation;
    private int initialIgnitionRow = -1;
    private int initialIgnitionCol = -1;

    public FireSimulation(
            int[][] vegetationGrid,
            int[][] fuelGrid,
            double cellSizeMeters,
            double timeStepSeconds,
            Wind wind,
            long randomSeed) {
        validateGrid(vegetationGrid, fuelGrid);
        if (!Double.isFinite(cellSizeMeters) || cellSizeMeters <= 0.0) {
            throw new IllegalArgumentException("Cell size must be finite and positive.");
        }
        if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0) {
            throw new IllegalArgumentException("Time step must be finite and positive.");
        }
        if (wind == null) {
            throw new IllegalArgumentException("Wind must not be null.");
        }

        this.cellSizeMeters = cellSizeMeters;
        this.timeStepSeconds = timeStepSeconds;
        this.wind = wind;
        this.randomSeed = randomSeed;
        this.random = new Random(randomSeed);
        this.cells = new FireCell[fuelGrid.length][fuelGrid[0].length];

        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                FuelProfile profile = profileFor(
                        vegetationGrid[row][col],
                        fuelGrid[row][col]);
                cells[row][col] = new FireCell(
                        row,
                        col,
                        vegetationGrid[row][col],
                        fuelGrid[row][col],
                        profile != null);
            }
        }
    }

    public FireCell getCell(int row, int col) {
        return cells[row][col];
    }

    public int getRows() {
        return cells.length;
    }

    public int getColumns() {
        return cells[0].length;
    }

    public double getTimeSeconds() {
        return timeSeconds;
    }

    public double getTimeStepSeconds() {
        return timeStepSeconds;
    }

    public double getCellSizeMeters() {
        return cellSizeMeters;
    }

    public Wind getWind() {
        return wind;
    }

    public void setWind(Wind wind) {
        if (wind == null) {
            throw new IllegalArgumentException("Wind must not be null.");
        }
        this.wind = wind;
    }

    public WeatherObservation getWeatherObservation() {
        return weatherObservation;
    }

    public boolean applyWeatherObservation(
            WeatherObservation observation,
            Instant now,
            Duration maximumAge) {
        if (observation == null || !observation.isFreshAt(now, maximumAge)) {
            return false;
        }
        Optional<Wind> observedWind = observation.toWindToward();
        if (!observedWind.isPresent()) {
            return false;
        }
        wind = observedWind.get();
        weatherObservation = observation;
        return true;
    }

    public void clearWeatherObservation() {
        weatherObservation = null;
    }

    public boolean isComplete() {
        for (FireCell[] row : cells) {
            for (FireCell cell : row) {
                if (cell.getState() == FireState.BURNING) {
                    return false;
                }
            }
        }
        return true;
    }

    public void igniteCell(int row, int col) {
        FireCell cell = cells[row][col];
        FuelProfile profile = profileFor(cell.getVegetationCode(), cell.getFuelModel());
        if (profile == null) {
            throw new IllegalArgumentException(
                    "Cannot ignite non-burnable cell at [" + row + "][" + col + "].");
        }
        if (cell.getState() != FireState.UNBURNED) {
            throw new IllegalStateException(
                    "Cell at [" + row + "][" + col + "] is not unburned.");
        }
        ignite(cell, profile, timeSeconds);
        if (initialIgnitionRow < 0) {
            initialIgnitionRow = row;
            initialIgnitionCol = col;
        }
    }

    public void reset() {
        timeSeconds = 0.0;
        random = new Random(randomSeed);
        weatherObservation = null;
        for (FireCell[] row : cells) {
            for (FireCell cell : row) {
                cell.reset();
            }
        }
        wind = Wind.calm();
        if (initialIgnitionRow >= 0) {
            FireCell initialCell = cells[initialIgnitionRow][initialIgnitionCol];
            FuelProfile profile = profileFor(
                    initialCell.getVegetationCode(), initialCell.getFuelModel());
            ignite(initialCell, profile, 0.0);
        }
    }

    /**
     * Advances one fixed time step. For each eligible target, source-neighbor
     * probabilities are combined as 1 - product(1 - p_i), with
     * p_i = susceptibility * (1 - exp(-dt / travelTime)) * intensityFactor.
     * Each candidate is sampled once per step, so newly ignited cells do not
     * spread until the next step.
     */
    public void advanceOneStep() {
        double nextTimeSeconds = timeSeconds + timeStepSeconds;

        for (FireCell[] row : cells) {
            for (FireCell target : row) {
                if (target.getState() == FireState.UNBURNED) {
                    target.setIgnitionProbability(calculateIgnitionProbability(target));
                } else {
                    target.setIgnitionProbability(0.0);
                }
            }
        }

        boolean[][] igniteNextStep = new boolean[cells.length][cells[0].length];
        for (FireCell[] row : cells) {
            for (FireCell target : row) {
                if (target.getState() == FireState.UNBURNED
                        && target.getIgnitionProbability() > random.nextDouble()) {
                    igniteNextStep[target.getRow()][target.getCol()] = true;
                }
            }
        }

        timeSeconds = nextTimeSeconds;
        for (FireCell[] row : cells) {
            for (FireCell cell : row) {
                if (cell.getState() == FireState.BURNING
                        && timeSeconds - cell.getIgnitionTimeSeconds() >= BURN_DURATION_SECONDS) {
                    cell.markBurned();
                }
            }
        }
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                if (igniteNextStep[row][col]) {
                    FuelProfile profile = profileFor(
                            cells[row][col].getVegetationCode(),
                            cells[row][col].getFuelModel());
                    ignite(cells[row][col], profile, timeSeconds);
                }
            }
        }
    }

    private double calculateIgnitionProbability(FireCell target) {
        FuelProfile targetProfile = profileFor(
                target.getVegetationCode(),
                target.getFuelModel());
        if (targetProfile == null) {
            return 0.0;
        }

        double probabilityNotIgnited = 1.0;
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int colOffset = -1; colOffset <= 1; colOffset++) {
                if (rowOffset == 0 && colOffset == 0) {
                    continue;
                }
                int sourceRow = target.getRow() + rowOffset;
                int sourceCol = target.getCol() + colOffset;
                if (!isInsideGrid(sourceRow, sourceCol)) {
                    continue;
                }

                FireCell source = cells[sourceRow][sourceCol];
                if (source.getState() != FireState.BURNING) {
                    continue;
                }
                double travelTimeSeconds = estimateTravelTimeSeconds(
                        target.getRow(), target.getCol(),
                        sourceRow, sourceCol, wind);

                target.setPredictedArrivalTimeSeconds(timeSeconds + travelTimeSeconds);
                double temporalProbability = 1.0 - Math.exp(-timeStepSeconds / travelTimeSeconds);
                double sourceIntensityFactor = source.getFirelineIntensityKwPerMeter()
                        / (source.getFirelineIntensityKwPerMeter() + 100.0);
                double ignitionProbability = clamp(
                        targetProfile.ignitionSusceptibility
                                * temporalProbability
                                * (0.75 + 0.5 * sourceIntensityFactor),
                        0.0,
                        1.0);
                probabilityNotIgnited *= 1.0 - ignitionProbability;
            }
        }
        return 1.0 - probabilityNotIgnited;
    }

    public double estimateTravelTimeSeconds(
            int targetRow,
            int targetCol,
            int sourceRow,
            int sourceCol,
            Wind forecastWind) {
        if (forecastWind == null || targetRow < 0 || targetRow >= cells.length
                || targetCol < 0 || targetCol >= cells[0].length
                || sourceRow < 0 || sourceRow >= cells.length
                || sourceCol < 0 || sourceCol >= cells[0].length) {
            return Double.POSITIVE_INFINITY;
        }
        int rowOffset = sourceRow - targetRow;
        int colOffset = sourceCol - targetCol;
        if (Math.abs(rowOffset) > 1 || Math.abs(colOffset) > 1
                || rowOffset == 0 && colOffset == 0) {
            return Double.POSITIVE_INFINITY;
        }
        FuelProfile targetProfile = profileFor(
                cells[targetRow][targetCol].getVegetationCode(),
                cells[targetRow][targetCol].getFuelModel());
        if (targetProfile == null) {
            return Double.POSITIVE_INFINITY;
        }

        double diagonalScale = rowOffset != 0 && colOffset != 0 ? Math.sqrt(2.0) : 1.0;
        double distance = cellSizeMeters * diagonalScale;
        double east = -Math.signum(colOffset) / diagonalScale;
        double north = Math.signum(rowOffset) / diagonalScale;
        double alignment = forecastWind.alignmentWith(east, north);
        double directionalMultiplier = clamp(
                Math.exp(DIRECTIONAL_WIND_COEFFICIENT
                        * forecastWind.getSpeedMetersPerSecond() * alignment),
                MIN_DIRECTIONAL_MULTIPLIER,
                MAX_DIRECTIONAL_MULTIPLIER);
        double spreadRate = targetProfile.baseSpreadRateMetersPerSecond
                * (1.0 + WIND_SPREAD_COEFFICIENT * forecastWind.getSpeedMetersPerSecond())
                * directionalMultiplier;
        return distance / spreadRate;
    }

    private void ignite(FireCell cell, FuelProfile profile, double ignitionTimeSeconds) {
        // Empirical rate proxy: R = R0 * (1 + 0.12 * windSpeed); wind direction
        // is applied separately when estimating travel to a particular neighbor.
        double spreadRate = profile.baseSpreadRateMetersPerSecond
                * (1.0 + WIND_SPREAD_COEFFICIENT * wind.getSpeedMetersPerSecond());

        // Byram fireline intensity: I = H * w * R; (kJ/kg)(kg/m^2)(m/s) = kW/m.
        double intensity = HEAT_OF_COMBUSTION_KJ_PER_KG
                * profile.fuelLoadKgPerSquareMeter
                * spreadRate;
        cell.ignite(ignitionTimeSeconds, spreadRate, intensity);
    }

    private boolean isInsideGrid(int row, int col) {
        return row >= 0 && row < cells.length && col >= 0 && col < cells[0].length;
    }

    private static void validateGrid(int[][] vegetationGrid, int[][] fuelGrid) {
        if (vegetationGrid == null || fuelGrid == null
                || vegetationGrid.length == 0 || fuelGrid.length != vegetationGrid.length
                || vegetationGrid[0] == null || fuelGrid[0] == null
                || vegetationGrid[0].length == 0
                || fuelGrid[0].length != vegetationGrid[0].length) {
            throw new IllegalArgumentException("Vegetation and fuel grids must be non-empty and match.");
        }
        int columns = fuelGrid[0].length;
        for (int row = 0; row < fuelGrid.length; row++) {
            if (fuelGrid[row] == null || vegetationGrid[row] == null
                    || fuelGrid[row].length != columns
                    || vegetationGrid[row].length != columns) {
                throw new IllegalArgumentException("Vegetation and fuel grids must be rectangular and match.");
            }
        }
    }

    /**
     * Assigns broad empirical fuel proxies to the source dataset's codes:
     * grass (101-109: 0.35 kg/m^2, 0.08 m/s, 0.60 susceptibility),
     * shrub (121-124 and 141-149: 0.75, 0.035, 0.45), and woody
     * (161-165 and 181-189: 1.00, 0.018, 0.30). The CSV has no fuel
     * moisture, loading, or model legend, so these are illustrative inputs,
     * not calibrated Anderson/Rothermel fuel parameters.
     */
    private static FuelProfile profileFor(int vegetationCode, int fuelModel) {
        if (vegetationCode <= 0) {
            return null;
        }

        // These broad groups are empirical proxies, not calibrated Anderson fuel models.
        if (fuelModel >= 101 && fuelModel <= 109) {
            return new FuelProfile(0.35, 0.08, 0.60);
        }
        if ((fuelModel >= 121 && fuelModel <= 124)
                || (fuelModel >= 141 && fuelModel <= 149)) {
            return new FuelProfile(0.75, 0.035, 0.45);
        }
        if ((fuelModel >= 161 && fuelModel <= 165)
                || (fuelModel >= 181 && fuelModel <= 189)) {
            return new FuelProfile(1.00, 0.018, 0.30);
        }
        return null;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class FuelProfile {
        private final double fuelLoadKgPerSquareMeter;
        private final double baseSpreadRateMetersPerSecond;
        private final double ignitionSusceptibility;

        private FuelProfile(
                double fuelLoadKgPerSquareMeter,
                double baseSpreadRateMetersPerSecond,
                double ignitionSusceptibility) {
            this.fuelLoadKgPerSquareMeter = fuelLoadKgPerSquareMeter;
            this.baseSpreadRateMetersPerSecond = baseSpreadRateMetersPerSecond;
            this.ignitionSusceptibility = ignitionSusceptibility;
        }
    }
}
