package wildfire;

import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimulationGUI().setVisible(true));
    }
}
