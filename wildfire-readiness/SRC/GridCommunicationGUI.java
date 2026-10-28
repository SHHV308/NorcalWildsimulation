import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

public final class GridCommunicationGUI extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final double CELL_SIZE_METERS = 30.0;
    private static final double TIME_STEP_SECONDS = 30.0;
    private static final int SOURCE_ROW = 1;
    private static final int SOURCE_COLUMN = 1;
    private static final int TARGET_ROW = 1;
    private static final int TARGET_COLUMN = 2;
    private static final DateTimeFormatter OBSERVED_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final GridCellData sourceData;
    private final GridCellData targetData;
    private final WeatherMonitor weatherMonitor;
    private final JLabel windLabel = new JLabel("Wind: waiting for fresh NWS observation");
    private final JLabel windDetailLabel = new JLabel("No wind value is being assumed.");
    private final JLabel sourceDetailsLabel = new JLabel();
    private final JLabel targetDetailsLabel = new JLabel();
    private final JLabel probabilityLabel = new JLabel("--");
    private final JLabel travelTimeLabel = new JLabel("--");
    private final JLabel elapsedLabel = new JLabel("Elapsed: 0 sec");
    private final JLabel statusLabel = new JLabel(
            "Select Start fire to model communication between these adjacent cells.");
    private final PairPanel pairPanel = new PairPanel();
    private final Timer observationTimer;
    private FireSimulation simulation;
    private Wind observedWind;
    private int sourceCsvRow;
    private int sourceCsvColumn;
    private int targetCsvRow;
    private int targetCsvColumn;

    public GridCommunicationGUI(
            GridCellData sourceData,
            GridCellData targetData,
            WeatherMonitor weatherMonitor) {
        if (sourceData == null || targetData == null
                || targetData.getRow() != sourceData.getRow()
                || targetData.getColumn() != sourceData.getColumn() + 1
                || weatherMonitor == null) {
            throw new IllegalArgumentException(
                    "An adjacent eastward CSV cell pair and weather monitor are required.");
        }
        this.sourceData = sourceData;
        this.targetData = targetData;
        this.weatherMonitor = weatherMonitor;
        setLayout(new BorderLayout(14, 14));
        setBackground(new Color(244, 247, 244));
        setBorder(new EmptyBorder(20, 24, 20, 24));

        buildLayout();
        resetSimulation();
        observationTimer = new Timer(2000, event -> refreshObservation());
        refreshObservation();
    }

    private void buildLayout() {
        JPanel header = new JPanel(new BorderLayout(10, 6));
        header.setOpaque(false);
        JLabel title = new JLabel("Immediate Cell-to-Cell Fire Spread");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        JLabel subtitle = new JLabel(
                "Two adjacent 30 m cells read from the CSV — no full-grid display");
        subtitle.setForeground(new Color(80, 91, 82));
        JPanel titleStack = new JPanel(new GridLayout(2, 1, 0, 4));
        titleStack.setOpaque(false);
        titleStack.add(title);
        titleStack.add(subtitle);
        header.add(titleStack, BorderLayout.WEST);

        JPanel weather = new JPanel(new GridLayout(2, 1, 0, 4));
        weather.setOpaque(false);
        windLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        weather.add(windLabel);
        windDetailLabel.setForeground(new Color(78, 89, 80));
        weather.add(windDetailLabel);
        header.add(weather, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        pairPanel.setPreferredSize(new Dimension(850, 370));
        pairPanel.setBackground(new Color(230, 236, 226));
        pairPanel.setBorder(BorderFactory.createLineBorder(new Color(188, 199, 188)));
        add(pairPanel, BorderLayout.CENTER);

        JPanel details = new JPanel(new GridLayout(2, 2, 16, 8));
        details.setBackground(Color.WHITE);
        details.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(207, 215, 208)),
                new EmptyBorder(16, 18, 16, 18)));
        details.add(cellCard("FIRE SOURCE CELL", sourceDetailsLabel));
        details.add(cellCard("ONE IMMEDIATE NEIGHBOR", targetDetailsLabel));
        details.add(metricCard("Chance this neighbor catches fire in next 30 sec",
                probabilityLabel));
        details.add(metricCard("Wind-adjusted source-to-neighbor travel time",
                travelTimeLabel));

        JPanel lower = new JPanel(new BorderLayout(0, 10));
        lower.setOpaque(false);
        lower.add(details, BorderLayout.CENTER);
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        JButton igniteButton = new JButton("Start fire in source cell");
        igniteButton.addActionListener(event -> startFire());
        JButton advanceButton = new JButton("Advance 30 seconds");
        advanceButton.addActionListener(event -> advanceSimulation());
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(event -> resetSimulation());
        controls.add(igniteButton);
        controls.add(advanceButton);
        controls.add(resetButton);
        elapsedLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
        controls.add(elapsedLabel);
        lower.add(controls, BorderLayout.NORTH);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        statusLabel.setForeground(new Color(65, 78, 69));
        footer.add(statusLabel, BorderLayout.NORTH);
        JLabel caveat = new JLabel("<html>Wind comes from the latest fresh NWS station "
                + "observation, not from the CSV. The CSV supplies vegetation and fuel "
                + "codes only. Chances and travel times use illustrative fuel proxies, "
                + "not a calibrated or official fire forecast.</html>");
        caveat.setForeground(new Color(89, 97, 90));
        footer.add(caveat, BorderLayout.SOUTH);
        lower.add(footer, BorderLayout.SOUTH);
        add(lower, BorderLayout.SOUTH);

        sourceDetailsLabel.setText(cellDescription(sourceData));
        targetDetailsLabel.setText(cellDescription(targetData));
        sourceDetailsLabel.setVerticalAlignment(SwingConstants.TOP);
        targetDetailsLabel.setVerticalAlignment(SwingConstants.TOP);
    }

    private JPanel cellCard(String title, JLabel detail) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        card.add(heading, BorderLayout.NORTH);
        detail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        card.add(detail, BorderLayout.CENTER);
        return card;
    }

    private JPanel metricCard(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        card.add(heading, BorderLayout.NORTH);
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        value.setForeground(new Color(42, 105, 64));
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static String cellDescription(GridCellData data) {
        return "<html>CSV cell [" + data.getRow() + ", " + data.getColumn()
                + "]<br>Vegetation code: " + data.getVegetationCode()
                + "<br>Fuel model: " + data.getFuelModel() + " - "
                + FuelCover.descriptionForModel(data.getFuelModel()) + "</html>";
    }

    private void refreshObservation() {
        Optional<Wind> freshWind = weatherMonitor.getFreshWindAt(Instant.now());
        WeatherObservation observation = weatherMonitor.getLatest();
        if (freshWind.isPresent()) {
            observedWind = freshWind.get();
            simulation.setWind(observedWind);
            windLabel.setText(String.format(
                    java.util.Locale.ROOT,
                    "Observed wind: %.1f m/s toward %.0f degrees",
                    observedWind.getSpeedMetersPerSecond(),
                    observedWind.getDirectionDegrees()));
            String stationName = observation == null
                    ? "NWS station" : observation.getStationName();
            String stationId = observation == null ? null : observation.getStationId();
            String station = stationId == null || stationId.isEmpty()
                    ? stationName : stationName + " (" + stationId + ")";
            windDetailLabel.setText("From " + station + " | observed "
                    + OBSERVED_FORMAT.format(observation.getObservedAt()));
            statusLabel.setText("Using current observed wind for the cell-to-cell estimate.");
        } else {
            observedWind = null;
            windLabel.setText("Wind: no fresh NWS observation");
            String error = weatherMonitor.getLastError();
            windDetailLabel.setText(error == null
                    ? "Waiting for station wind speed and direction."
                    : error);
            statusLabel.setText("Chance and travel time are unavailable until fresh wind arrives.");
        }
        updateEstimates();
        pairPanel.repaint();
    }

    private void startFire() {
        if (simulation.getCell(SOURCE_ROW, SOURCE_COLUMN).getState()
                == FireState.NON_BURNABLE) {
            statusLabel.setText("The CSV source cell has no supported burnable fuel.");
            return;
        }
        try {
            simulation.igniteCell(SOURCE_ROW, SOURCE_COLUMN);
            statusLabel.setText("Source cell is burning; the arrow shows its one adjacent target.");
        } catch (IllegalStateException exception) {
            statusLabel.setText("Source cell cannot be ignited: " + exception.getMessage());
        }
        updateEstimates();
        pairPanel.repaint();
    }

    private void advanceSimulation() {
        if (simulation.getCell(SOURCE_ROW, SOURCE_COLUMN).getState() != FireState.BURNING
                && simulation.getCell(SOURCE_ROW, SOURCE_COLUMN).getState()
                        != FireState.BURNED) {
            statusLabel.setText("Start the source-cell fire before advancing time.");
            return;
        }
        simulation.advanceOneStep();
        elapsedLabel.setText("Elapsed: " + formatDuration(simulation.getTimeSeconds()));
        FireState targetState = simulation.getCell(TARGET_ROW, TARGET_COLUMN).getState();
        statusLabel.setText(targetState == FireState.BURNING
                ? "The immediate neighbor has ignited."
                : targetState == FireState.BURNED
                        ? "The immediate neighbor burned and is now burned out."
                        : "Advanced one 30-second step; the adjacent cell remains "
                                + "subject to the estimated spread chance.");
        updateEstimates();
        pairPanel.repaint();
    }

    private void resetSimulation() {
        int[][] vegetation = new int[3][3];
        int[][] fuel = new int[3][3];
        for (int row = 0; row < 3; row++) {
            java.util.Arrays.fill(vegetation[row], -1);
            java.util.Arrays.fill(fuel[row], 0);
        }
        vegetation[SOURCE_ROW][SOURCE_COLUMN] = sourceData.getVegetationCode();
        fuel[SOURCE_ROW][SOURCE_COLUMN] = sourceData.getFuelModel();
        vegetation[TARGET_ROW][TARGET_COLUMN] = targetData.getVegetationCode();
        fuel[TARGET_ROW][TARGET_COLUMN] = targetData.getFuelModel();
        simulation = new FireSimulation(
                vegetation, fuel, CELL_SIZE_METERS, TIME_STEP_SECONDS,
                observedWind == null ? Wind.calm() : observedWind, 20261026L);
        elapsedLabel.setText("Elapsed: 0 sec");
        statusLabel.setText(observedWind == null
                ? "Reset. Waiting for fresh NWS wind data."
                : "Reset. Start a fire to see this one immediate cell respond.");
        updateEstimates();
        pairPanel.repaint();
    }

    private void updateEstimates() {
        if (observedWind == null) {
            probabilityLabel.setText("--");
            travelTimeLabel.setText("--");
            return;
        }
        double probability = simulation.estimateIgnitionProbability(
                TARGET_ROW, TARGET_COLUMN, SOURCE_ROW, SOURCE_COLUMN,
                observedWind, TIME_STEP_SECONDS);
        double travelSeconds = simulation.estimateTravelTimeSeconds(
                TARGET_ROW, TARGET_COLUMN, SOURCE_ROW, SOURCE_COLUMN, observedWind);
        FireState targetState = simulation.getCell(TARGET_ROW, TARGET_COLUMN).getState();
        probabilityLabel.setText(targetState == FireState.NON_BURNABLE
                ? "Not burnable" : String.format("%.1f%% per 30 sec", probability * 100.0));
        travelTimeLabel.setText(targetState == FireState.NON_BURNABLE
                || !Double.isFinite(travelSeconds)
                        ? "--" : formatDuration(travelSeconds));
    }

    private static String formatDuration(double seconds) {
        if (!Double.isFinite(seconds)) {
            return "--";
        }
        if (seconds < 60.0) {
            return String.format("%.0f sec", seconds);
        }
        return String.format("%.1f min", seconds / 60.0);
    }

    public static void showSimulation(
            GridCellData sourceData,
            GridCellData targetData,
            WeatherMonitor weatherMonitor) {
        JFrame frame = new JFrame("Immediate Cell-to-Cell Fire Spread");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        GridCommunicationGUI panel =
                new GridCommunicationGUI(sourceData, targetData, weatherMonitor);
        frame.setContentPane(panel);
        frame.setMinimumSize(new Dimension(1000, 700));
        frame.setSize(1120, 790);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                panel.observationTimer.stop();
                weatherMonitor.close();
            }
        });
        panel.observationTimer.start();
        frame.setVisible(true);
    }

    private final class PairPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int centerY = getHeight() / 2;
            int cellWidth = Math.min(260, Math.max(150, getWidth() / 4));
            int cellHeight = Math.min(210, Math.max(140, getHeight() / 2));
            int sourceX = getWidth() / 2 - cellWidth - 65;
            int targetX = getWidth() / 2 + 65;
            int top = centerY - cellHeight / 2;
            FireCell source = simulation.getCell(SOURCE_ROW, SOURCE_COLUMN);
            FireCell target = simulation.getCell(TARGET_ROW, TARGET_COLUMN);

            drawCell(g, sourceX, top, cellWidth, cellHeight, source,
                    "SOURCE", sourceData);
            drawCell(g, targetX, top, cellWidth, cellHeight, target,
                    "IMMEDIATE NEIGHBOR", targetData);

            int arrowStart = sourceX + cellWidth + 14;
            int arrowEnd = targetX - 14;
            int arrowY = centerY;
            g.setStroke(new BasicStroke(4.0f));
            g.setColor(source.getState() == FireState.BURNING
                    ? new Color(205, 74, 31) : new Color(111, 126, 114));
            g.drawLine(arrowStart, arrowY, arrowEnd, arrowY);
            g.fillPolygon(
                    new int[] { arrowEnd, arrowEnd - 17, arrowEnd - 17 },
                    new int[] { arrowY, arrowY - 10, arrowY + 10 },
                    3);

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.setColor(new Color(48, 61, 51));
            String label = source.getState() == FireState.BURNING
                    ? "SPREAD" : "ONE-CELL CONNECTION";
            int labelWidth = g.getFontMetrics().stringWidth(label);
            g.drawString(label, (getWidth() - labelWidth) / 2, arrowY - 18);
            if (source.getState() == FireState.BURNING && observedWind != null) {
                String windArrow = String.format(
                        java.util.Locale.ROOT, "Wind toward %.0f°",
                        observedWind.getDirectionDegrees());
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                int windWidth = g.getFontMetrics().stringWidth(windArrow);
                g.drawString(windArrow, (getWidth() - windWidth) / 2, arrowY + 31);
            }
            g.dispose();
        }

        private void drawCell(
                Graphics2D graphics,
                int x,
                int y,
                int width,
                int height,
                FireCell cell,
                String heading,
                GridCellData data) {
            graphics.setColor(cellColor(cell));
            graphics.fillRoundRect(x, y, width, height, 18, 18);
            graphics.setColor(new Color(42, 60, 45));
            graphics.setStroke(new BasicStroke(2.0f));
            graphics.drawRoundRect(x, y, width, height, 18, 18);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            graphics.drawString(heading, x + 16, y + 28);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            graphics.drawString("CSV [" + data.getRow() + ", "
                    + data.getColumn() + "]", x + 16, y + 59);
            graphics.drawString("Vegetation " + data.getVegetationCode(), x + 16, y + 84);
            graphics.drawString("Fuel " + data.getFuelModel() + " - "
                    + FuelCover.descriptionForModel(data.getFuelModel()),
                    x + 16, y + 108);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            graphics.drawString(stateName(cell.getState()), x + 16, y + height - 24);
        }
    }

    private static Color cellColor(FireCell cell) {
        switch (cell.getState()) {
            case BURNING:
                return new Color(239, 111, 61);
            case BURNED:
                return new Color(99, 93, 84);
            case NON_BURNABLE:
                return new Color(186, 190, 187);
            default:
                return new Color(136, 187, 124);
        }
    }

    private static String stateName(FireState state) {
        switch (state) {
            case BURNING:
                return "BURNING";
            case BURNED:
                return "BURNED";
            case NON_BURNABLE:
                return "NON-BURNABLE";
            default:
                return "UNBURNED";
        }
    }
}
