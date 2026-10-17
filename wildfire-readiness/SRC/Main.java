import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import javax.swing.SwingUtilities;

public class Main {

    private static final int ROWS = 100;
    private static final int COLS = 100;
    private static final String CSV_FILE =
            "Data/northern_california_100x100_java_grid.csv";
    private static final double DEFAULT_LATITUDE = 40.5862;
    private static final double DEFAULT_LONGITUDE = -122.3914;

    public static void main(String[] args) {
        int[][] vegetationGrid = new int[ROWS][COLS];
        int[][] fuelGrid = new int[ROWS][COLS];
        boolean[][] loaded = new boolean[ROWS][COLS];
        int cellCount = 0;
        double latitude = DEFAULT_LATITUDE;
        double longitude = DEFAULT_LONGITUDE;
        double fallbackWindSpeed = 0.0;
        double fallbackWindDirection = 0.0;

        System.out.println("================================");
        System.out.println("       CALFIRE SIMULATION");
        System.out.println("================================");
        System.out.println("Loading Northern California data...");

        try {
            int positionalCount = 0;
            for (int index = 0; index < args.length; index++) {
                if ("--location".equals(args[index])) {
                    if (index + 2 >= args.length) {
                        throw new IllegalArgumentException(
                                "--location requires latitude and longitude.");
                    }
                    latitude = Double.parseDouble(args[++index]);
                    longitude = Double.parseDouble(args[++index]);
                } else if ("--wind".equals(args[index])) {
                    if (index + 2 >= args.length) {
                        throw new IllegalArgumentException(
                                "--wind requires fallback speed and direction.");
                    }
                    fallbackWindSpeed = Double.parseDouble(args[++index]);
                    fallbackWindDirection = Double.parseDouble(args[++index]);
                } else if (positionalCount == 0) {
                    fallbackWindSpeed = Double.parseDouble(args[index]);
                    positionalCount++;
                } else if (positionalCount == 1) {
                    fallbackWindDirection = Double.parseDouble(args[index]);
                    positionalCount++;
                } else {
                    throw new IllegalArgumentException(
                            "Usage: Main [fallback-speed] [fallback-direction] "
                                    + "[--location latitude longitude] "
                                    + "[--wind speed direction]");
                }
            }
            Wind fallbackWind = new Wind(fallbackWindSpeed, fallbackWindDirection);
            if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0
                    || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException("Weather location coordinates are invalid.");
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(CSV_FILE))) {
                String header = reader.readLine();
                if (header == null) {
                    throw new IOException("CSV file is empty.");
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

                    int row = Integer.parseInt(values[0].trim());
                    int col = Integer.parseInt(values[1].trim());
                    int vegetation = Integer.parseInt(values[4].trim());
                    int fuel = Integer.parseInt(values[5].trim());

                    if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
                        throw new IOException(
                                "Grid position out of bounds: " + row + ", " + col);
                    }
                    if (loaded[row][col]) {
                        throw new IOException("Duplicate grid cell: " + row + ", " + col);
                    }

                    vegetationGrid[row][col] = vegetation;
                    fuelGrid[row][col] = fuel;
                    loaded[row][col] = true;
                    cellCount++;
                }
            }

            System.out.println();
            System.out.println("CSV loaded successfully!");
            System.out.println("Total cells: " + cellCount);
            System.out.println("Grid size: " + ROWS + " x " + COLS);
            if (cellCount != ROWS * COLS) {
                System.out.println("WARNING: Some grid cells are missing.");
            }

            System.out.println();
            System.out.println("Sample cell [0][0]:");
            System.out.println("Vegetation code: " + vegetationGrid[0][0]);
            System.out.println("Fuel model: " + fuelGrid[0][0]);

            FireSimulation simulation = new FireSimulation(
                    vegetationGrid,
                    fuelGrid,
                    30.0,
                    30.0,
                    fallbackWind,
                    20261016L);
            System.out.println();
            System.out.println("CALSAFE data initialization complete.");
            System.out.printf("Configured fallback wind (not used for fire ETAs): "
                            + "%.1f m/s toward %.0f degrees.%n",
                    fallbackWindSpeed, simulation.getWind().getDirectionDegrees());
            System.out.printf(
                    "NWS weather location: %.4f, %.4f; forecasts use reported fire observations "
                            + "and fresh station wind.%n",
                    latitude,
                    longitude);
            System.out.println("Opening CALSAFE fire spread forecast...");
            WeatherMonitor weatherMonitor = new WeatherMonitor(
                    new WeatherApiClient(), latitude, longitude);
            FireObservationMonitor fireMonitor =
                    new FireObservationMonitor(new FireDataApiClient());
            SwingUtilities.invokeLater(() -> FireSimulationGUI.showSimulation(
                    simulation, weatherMonitor, fireMonitor, fallbackWind));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error loading wildfire data: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
