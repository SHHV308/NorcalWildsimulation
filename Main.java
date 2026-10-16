
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import javax.swing.SwingUtilities;

public class Main {

    private static final int ROWS = 100;
    private static final int COLS = 100;

    private static final String CSV_FILE =
            "data/northern_california_100x100_java_grid.csv";

    public static void main(String[] args) {

        int[][] vegetationGrid = new int[ROWS][COLS];
        int[][] fuelGrid = new int[ROWS][COLS];

        boolean[][] loaded = new boolean[ROWS][COLS];
        int cellCount = 0;

        System.out.println("================================");
        System.out.println("       CALSAFE SIMULATION");
        System.out.println("================================");
        System.out.println("Loading Northern California data...");

        try (BufferedReader reader =
                new BufferedReader(new FileReader(CSV_FILE))) {

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
                    throw new IOException(
                        "Invalid CSV row: " + line
                    );
                }

                int row = Integer.parseInt(values[0].trim());
                int col = Integer.parseInt(values[1].trim());

                int vegetation =
                        Integer.parseInt(values[4].trim());

                int fuel =
                        Integer.parseInt(values[5].trim());

                if (row < 0 || row >= ROWS ||
                    col < 0 || col >= COLS) {

                    throw new IOException(
                        "Grid position out of bounds: "
                        + row + ", " + col
                    );
                }

                if (loaded[row][col]) {
                    throw new IOException(
                        "Duplicate grid cell: "
                        + row + ", " + col
                    );
                }

                vegetationGrid[row][col] = vegetation;
                fuelGrid[row][col] = fuel;
                loaded[row][col] = true;

                cellCount++;
            }

            System.out.println();
            System.out.println("CSV loaded successfully!");
            System.out.println("Total cells: " + cellCount);
            System.out.println("Grid size: " + ROWS + " x " + COLS);

            if (cellCount != ROWS * COLS) {
                System.out.println(
                    "WARNING: Some grid cells are missing."
                );
            }

            System.out.println();
            System.out.println("Sample cell [0][0]:");
            System.out.println(
                "Vegetation code: " + vegetationGrid[0][0]
            );
            System.out.println(
                "Fuel model: " + fuelGrid[0][0]
            );

            System.out.println();
            System.out.println("CALSAFE data initialization complete.");

            System.out.println("Opening CALSAFE GUI...");

            SwingUtilities.invokeLater(() -> {
            FireSimulationGUI.showGrid(fuelGrid);
        });

        } catch (IOException | NumberFormatException e) {

            System.err.println(
                "Error loading wildfire data: " + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}

