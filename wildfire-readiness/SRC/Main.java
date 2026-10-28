import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    private static final String CSV_FILE =
            "Data/northern_california_100x100_java_grid.csv";
    private static final int GRID_DIMENSION = 20;
    private static final double CELL_SIZE_METERS = 30.0;
    private static final double TIME_STEP_SECONDS = 30.0;
    private static final double WEATHER_LATITUDE = 40.5862;
    private static final double WEATHER_LONGITUDE = -122.3914;

    public static void main(String[] args) {
        if (args.length != 0) {
            System.err.println("Usage: Main");
            return;
        }
        try {
            FireSimulation simulation = loadSimulation();
            System.out.println("Loaded " + simulation.getRows() + " x "
                    + simulation.getColumns() + " fuel cells from " + CSV_FILE + ".");
            System.out.println("Fetching current wind from the nearest NWS station...");
            WeatherMonitor weatherMonitor = new WeatherMonitor(
                    new WeatherApiClient(), WEATHER_LATITUDE, WEATHER_LONGITUDE);
            FireSimulationGUI.showSimulation(
                    simulation, weatherMonitor, null, simulation.getWind());
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Unable to start wildfire grid: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    static FireSimulation loadSimulation() throws IOException {
        List<GridCellData> cells = new ArrayList<>();
        int maximumRow = -1;
        int maximumColumn = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_FILE))) {
            String header = reader.readLine();
            if (!"row,col,X,Y,vegetation_code,fuel_model".equals(header)) {
                throw new IOException("CSV header does not match the expected grid format.");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] values = line.split(",", -1);
                if (values.length < 6) {
                    throw new IOException("Invalid CSV row: " + line);
                }
                int row, column, vegetation, fuel;
                try {
                    row = Integer.parseInt(values[0].trim());
                    column = Integer.parseInt(values[1].trim());
                    vegetation = Integer.parseInt(values[4].trim());
                    fuel = Integer.parseInt(values[5].trim());
                } catch (NumberFormatException exception) {
                    throw new IOException("Invalid CSV cell data: " + line, exception);
                }
                if (row < 0 || column < 0) {
                    throw new IOException("CSV cell coordinates must be non-negative: " + line);
                }
                cells.add(new GridCellData(row, column, vegetation, fuel));
                maximumRow = Math.max(maximumRow, row);
                maximumColumn = Math.max(maximumColumn, column);
            }
        }

        int sourceRows = maximumRow + 1;
        int sourceColumns = maximumColumn + 1;
        if (sourceRows < GRID_DIMENSION || sourceColumns < GRID_DIMENSION) {
            throw new IOException("CSV grid must contain at least "
                    + GRID_DIMENSION + " rows and columns.");
        }
        if ((long) sourceRows * sourceColumns != cells.size()) {
            throw new IOException("CSV grid contains missing or duplicate cells.");
        }

        boolean[][] sourcePopulated = new boolean[sourceRows][sourceColumns];
        for (GridCellData cell : cells) {
            int row = cell.getRow();
            int column = cell.getColumn();
            if (sourcePopulated[row][column]) {
                throw new IOException("Duplicate CSV cell at row " + row
                        + ", column " + column + ".");
            }
            sourcePopulated[row][column] = true;
        }

        int sourceStartRow = (sourceRows - GRID_DIMENSION) / 2;
        int sourceStartColumn = (sourceColumns - GRID_DIMENSION) / 2;
        int[][] vegetation = new int[GRID_DIMENSION][GRID_DIMENSION];
        int[][] fuel = new int[GRID_DIMENSION][GRID_DIMENSION];
        boolean[][] populated = new boolean[GRID_DIMENSION][GRID_DIMENSION];
        for (int row = 0; row < GRID_DIMENSION; row++) {
            Arrays.fill(vegetation[row], -1);
        }
        for (GridCellData cell : cells) {
            int row = cell.getRow() - sourceStartRow;
            int column = cell.getColumn() - sourceStartColumn;
            if (row < 0 || row >= GRID_DIMENSION
                    || column < 0 || column >= GRID_DIMENSION) {
                continue;
            }
            if (populated[row][column]) {
                throw new IOException("Duplicate CSV cell at row " + row
                        + ", column " + column + ".");
            }
            populated[row][column] = true;
            vegetation[row][column] = cell.getVegetationCode();
            fuel[row][column] = cell.getFuelModel();
        }
        for (int row = 0; row < GRID_DIMENSION; row++) {
            for (int column = 0; column < GRID_DIMENSION; column++) {
                if (!populated[row][column]) {
                    throw new IOException("CSV is missing cell at row "
                            + (row + sourceStartRow) + ", column "
                            + (column + sourceStartColumn) + ".");
                }
            }
        }
        return new FireSimulation(
                vegetation, fuel, CELL_SIZE_METERS, TIME_STEP_SECONDS, Wind.calm(), 20261026L);
    }
}
