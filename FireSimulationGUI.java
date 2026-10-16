
import javax.swing.*;
import java.awt.*;

public class FireSimulationGUI extends JPanel {

    private final int[][] fuelGrid;
    private static final int CELL_SIZE = 10;

    public FireSimulationGUI(int[][] fuelGrid) {
        this.fuelGrid = fuelGrid;

        setPreferredSize(new Dimension(
            fuelGrid[0].length * CELL_SIZE,
            fuelGrid.length * CELL_SIZE
        ));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int row = 0; row < fuelGrid.length; row++) {
            for (int col = 0; col < fuelGrid[row].length; col++) {

                int fuel = fuelGrid[row][col];

                if (fuel >= 101 && fuel <= 109) {
                    g.setColor(new Color(180, 215, 120));
                } else if (fuel >= 121 && fuel <= 124) {
                    g.setColor(new Color(210, 185, 95));
                } else if (fuel >= 141 && fuel <= 149) {
                    g.setColor(new Color(120, 175, 85));
                } else if (fuel >= 161 && fuel <= 165) {
                    g.setColor(new Color(75, 135, 65));
                } else if (fuel >= 181 && fuel <= 189) {
                    g.setColor(new Color(45, 100, 55));
                } else {
                    g.setColor(Color.GRAY);
                }

                g.fillRect(
                    col * CELL_SIZE,
                    row * CELL_SIZE,
                    CELL_SIZE,
                    CELL_SIZE
                );

                // Draw a border around every grid cell
                g.setColor(new Color(0, 0, 0, 65));

                g.drawRect(
                col * CELL_SIZE,
                row * CELL_SIZE,
                CELL_SIZE,
                CELL_SIZE
        );
            }
        }
    }

    public static void showGrid(int[][] fuelGrid) {
        JFrame frame = new JFrame("CALSAFE - Northern California Fuel Grid");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new FireSimulationGUI(fuelGrid));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
