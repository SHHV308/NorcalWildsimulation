import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class FireSpreadForecaster {

    private FireSpreadForecaster() {
    }

    public static double[][] estimateArrivalTimes(
            FireSimulation simulation,
            Wind wind,
            List<int[]> observedFireCells) {
        if (simulation == null || wind == null || observedFireCells == null) {
            throw new IllegalArgumentException(
                    "Simulation, observed wind, and observed fire cells are required.");
        }

        int rows = simulation.getRows();
        int columns = simulation.getColumns();
        double[][] arrivalTimes = new double[rows][columns];
        for (double[] row : arrivalTimes) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }

        PriorityQueue<CellArrival> queue = new PriorityQueue<>(
                Comparator.comparingDouble(arrival -> arrival.seconds));
        for (int[] source : observedFireCells) {
            if (source == null || source.length < 2
                    || source[0] < 0 || source[0] >= rows
                    || source[1] < 0 || source[1] >= columns) {
                continue;
            }
            if (arrivalTimes[source[0]][source[1]] != 0.0) {
                arrivalTimes[source[0]][source[1]] = 0.0;
                queue.add(new CellArrival(source[0], source[1], 0.0));
            }
        }

        while (!queue.isEmpty()) {
            CellArrival current = queue.remove();
            if (current.seconds != arrivalTimes[current.row][current.column]) {
                continue;
            }
            for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                    if (rowOffset == 0 && columnOffset == 0) {
                        continue;
                    }
                    int nextRow = current.row + rowOffset;
                    int nextColumn = current.column + columnOffset;
                    if (nextRow < 0 || nextRow >= rows
                            || nextColumn < 0 || nextColumn >= columns) {
                        continue;
                    }
                    double travelSeconds = simulation.estimateTravelTimeSeconds(
                            nextRow, nextColumn, current.row, current.column, wind);
                    if (!Double.isFinite(travelSeconds)) {
                        continue;
                    }
                    double candidate = current.seconds + travelSeconds;
                    if (candidate < arrivalTimes[nextRow][nextColumn]) {
                        arrivalTimes[nextRow][nextColumn] = candidate;
                        queue.add(new CellArrival(nextRow, nextColumn, candidate));
                    }
                }
            }
        }
        return arrivalTimes;
    }

    public static double[][] summarizeDisplayCells(
            double[][] cellArrivalTimes,
            int displayRows,
            int displayColumns) {
        if (cellArrivalTimes == null || cellArrivalTimes.length == 0
                || cellArrivalTimes[0] == null || cellArrivalTimes[0].length == 0
                || displayRows <= 0 || displayColumns <= 0) {
            throw new IllegalArgumentException("Arrival grid and display dimensions are required.");
        }
        int rows = cellArrivalTimes.length;
        int columns = cellArrivalTimes[0].length;
        double[][] displayTimes = new double[displayRows][displayColumns];
        for (double[] row : displayTimes) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int row = 0; row < rows; row++) {
            if (cellArrivalTimes[row] == null || cellArrivalTimes[row].length != columns) {
                throw new IllegalArgumentException("Arrival grid must be rectangular.");
            }
            int displayRow = Math.min(displayRows - 1, row * displayRows / rows);
            for (int column = 0; column < columns; column++) {
                int displayColumn = Math.min(displayColumns - 1, column * displayColumns / columns);
                displayTimes[displayRow][displayColumn] = Math.min(
                        displayTimes[displayRow][displayColumn],
                        cellArrivalTimes[row][column]);
            }
        }
        return displayTimes;
    }

    private static final class CellArrival {
        private final int row;
        private final int column;
        private final double seconds;

        private CellArrival(int row, int column, double seconds) {
            this.row = row;
            this.column = column;
            this.seconds = seconds;
        }
    }
}
