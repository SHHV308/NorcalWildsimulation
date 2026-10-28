import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * Animated fire spread demo on the Northern California 100 x 100 grid.
 *
 * Uses FireSimulation as-is: land cover comes from the CSV fuel models,
 * and wind direction and speed come from the sliders.
 *
 * Run from the wildfire-readiness folder:
 *   javac -d out SRC/*.java
 *   java -cp out SpreadDemo                 (interactive window)
 *   java -cp out SpreadDemo --export demo [minutes] [windSpeed]
 *        (saves no-wind vs wind comparison PNGs, no window; defaults 45 min, 8 m/s)
 */
public class SpreadDemo {

    static final String CSV_FILE = "Data/northern_california_100x100_java_grid.csv";
    static final int ROWS = 100;
    static final int COLS = 100;
    static final double CELL_SIZE_METERS = 30.0;
    static final double TIME_STEP_SECONDS = 30.0;
    static final long SEED = 20261016L;

    static final Color GRASS = new Color(192, 221, 151);
    static final Color SHRUB = new Color(99, 153, 34);
    static final Color TIMBER = new Color(39, 80, 10);
    static final Color NON_BURNABLE = new Color(180, 178, 169);
    static final Color BURNING = new Color(226, 75, 74);
    static final Color BURNED = new Color(68, 68, 65);
    static final Color IGNITION = new Color(255, 214, 0);

    public static void main(String[] args) throws IOException {
        int[][][] grids = loadGrids(CSV_FILE);
        if (args.length >= 2 && "--export".equals(args[0])) {
            double minutes = args.length >= 3 ? Double.parseDouble(args[2]) : 45;
            double windSpeed = args.length >= 4 ? Double.parseDouble(args[3]) : 8;
            exportComparison(grids, new File(args[1]), minutes, windSpeed);
            return;
        }
        SwingUtilities.invokeLater(() -> new SpreadDemo(grids).show());
    }

    // ---------- Data ----------

    /** Returns {vegetationGrid, fuelGrid} read from the project CSV. */
    static int[][][] loadGrids(String path) throws IOException {
        int[][] vegetation = new int[ROWS][COLS];
        int[][] fuel = new int[ROWS][COLS];
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] v = line.split(",", -1);
                int row = Integer.parseInt(v[0].trim());
                int col = Integer.parseInt(v[1].trim());
                vegetation[row][col] = Integer.parseInt(v[4].trim());
                fuel[row][col] = Integer.parseInt(v[5].trim());
            }
        }
        return new int[][][] {vegetation, fuel};
    }

    static FireSimulation newSimulation(int[][][] grids, Wind wind) {
        return new FireSimulation(grids[0], grids[1], CELL_SIZE_METERS, TIME_STEP_SECONDS, wind, SEED);
    }

    static boolean isBurnable(FireCell cell) {
        return cell.getState() == FireState.UNBURNED
                && !"No burnable cover classified".equals(FuelCover.descriptionForModel(cell.getFuelModel()));
    }

    static Color landColor(int fuelModel) {
        String cover = FuelCover.descriptionForModel(fuelModel);
        if (cover.startsWith("Grass")) {
            return GRASS;
        }
        if (cover.startsWith("Shrub")) {
            return SHRUB;
        }
        if (cover.startsWith("Timber")) {
            return TIMBER;
        }
        return NON_BURNABLE;
    }

    static Color cellColor(FireCell cell) {
        switch (cell.getState()) {
            case BURNING:
                return BURNING;
            case BURNED:
                return BURNED;
            default:
                return landColor(cell.getFuelModel());
        }
    }

    // ---------- Drawing (shared by window and PNG export) ----------

    static void drawGrid(Graphics2D g, FireSimulation sim, int cellPx, int[] ignition) {
        for (int r = 0; r < sim.getRows(); r++) {
            for (int c = 0; c < sim.getColumns(); c++) {
                g.setColor(cellColor(sim.getCell(r, c)));
                g.fillRect(c * cellPx, r * cellPx, cellPx, cellPx);
            }
        }
        if (ignition != null) {
            g.setColor(IGNITION);
            g.setStroke(new BasicStroke(2f));
            g.drawOval(ignition[1] * cellPx - 4, ignition[0] * cellPx - 4, cellPx + 8, cellPx + 8);
        }
        drawWindArrow(g, sim.getWind(), sim.getColumns() * cellPx - 50, 50);
    }

    /** Arrow points the way the wind pushes fire (degrees clockwise from north). */
    static void drawWindArrow(Graphics2D g, Wind wind, int cx, int cy) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 255, 255, 220));
        g.fillOval(cx - 38, cy - 38, 76, 76);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.drawString("N", cx - 4, cy - 26);
        double speed = wind.getSpeedMetersPerSecond();
        if (speed < 0.1) {
            g.drawString("calm", cx - 13, cy + 4);
            return;
        }
        double rad = Math.toRadians(wind.getDirectionDegrees());
        int len = 14 + (int) Math.min(12, speed);
        int dx = (int) Math.round(Math.sin(rad) * len);
        int dy = (int) Math.round(-Math.cos(rad) * len);
        g.setColor(new Color(216, 90, 48));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx - dx, cy - dy, cx + dx, cy + dy);
        double back = Math.atan2(dy, dx) + Math.PI;
        for (double side : new double[] {-0.5, 0.5}) {
            g.drawLine(cx + dx, cy + dy,
                    cx + dx + (int) Math.round(Math.cos(back + side) * 9),
                    cy + dy + (int) Math.round(Math.sin(back + side) * 9));
        }
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.drawString(String.format("%.0f m/s", speed), cx - 14, cy + 32);
    }

    static int[] countFire(FireSimulation sim) {
        int burning = 0;
        int burned = 0;
        for (int r = 0; r < sim.getRows(); r++) {
            for (int c = 0; c < sim.getColumns(); c++) {
                FireState s = sim.getCell(r, c).getState();
                if (s == FireState.BURNING) {
                    burning++;
                } else if (s == FireState.BURNED) {
                    burned++;
                }
            }
        }
        return new int[] {burning, burned};
    }

    /** Finds the burnable cell closest to the grid center. */
    static int[] centerIgnition(FireSimulation sim) {
        int best = Integer.MAX_VALUE;
        int[] pick = null;
        for (int r = 0; r < sim.getRows(); r++) {
            for (int c = 0; c < sim.getColumns(); c++) {
                int d = (r - ROWS / 2) * (r - ROWS / 2) + (c - COLS / 2) * (c - COLS / 2);
                if (d < best && isBurnable(sim.getCell(r, c))) {
                    best = d;
                    pick = new int[] {r, c};
                }
            }
        }
        return pick;
    }

    // ---------- PNG export for slides ----------

    static void exportComparison(int[][][] grids, File dir, double minutes, double windSpeed)
            throws IOException {
        dir.mkdirs();
        int steps = (int) Math.round(minutes * 60 / TIME_STEP_SECONDS);
        Wind[] winds = {Wind.calm(), new Wind(windSpeed, 45), new Wind(windSpeed, 225)};
        String w = String.format("%.0fms", windSpeed);
        String[] names = {"1_no_wind", "2_wind_" + w + "_toward_NE", "3_wind_" + w + "_toward_SW"};
        int cellPx = 6;
        for (int i = 0; i < winds.length; i++) {
            FireSimulation sim = newSimulation(grids, winds[i]);
            int[] start = centerIgnition(sim);
            sim.igniteCell(start[0], start[1]);
            for (int s = 0; s < steps && !sim.isComplete(); s++) {
                sim.advanceOneStep();
            }
            BufferedImage img = new BufferedImage(COLS * cellPx, ROWS * cellPx + 28, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            drawGrid(g, sim, cellPx, start);
            int[] n = countFire(sim);
            g.setColor(Color.BLACK);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString(String.format("%s  |  %.0f min  |  burning %d, burned %d cells (%.1f ha)",
                    names[i].substring(2).replace('_', ' '), minutes, n[0], n[1],
                    (n[0] + n[1]) * CELL_SIZE_METERS * CELL_SIZE_METERS / 10_000.0),
                    8, ROWS * cellPx + 19);
            g.dispose();
            File out = new File(dir, names[i] + ".png");
            ImageIO.write(img, "png", out);
            System.out.printf("Saved %s  (burning %d, burned %d)%n", out.getPath(), n[0], n[1]);
        }
    }

    // ---------- Interactive window ----------

    private final int[][][] grids;
    private FireSimulation sim;
    private int[] ignition;
    private final MapPanel map = new MapPanel();
    private final JLabel status = new JLabel();
    private final JSlider direction = new JSlider(0, 359, 45);
    private final JSlider speed = new JSlider(0, 20, 8);
    private final JSlider stepsPerFrame = new JSlider(1, 20, 6);
    private final JButton play = new JButton("Play");
    private final Timer timer;

    SpreadDemo(int[][][] grids) {
        this.grids = grids;
        this.sim = newSimulation(grids, currentWind());
        this.timer = new Timer(80, e -> tick());
    }

    private Wind currentWind() {
        return speed.getValue() == 0 ? Wind.calm() : new Wind(speed.getValue(), direction.getValue());
    }

    private void tick() {
        for (int i = 0; i < stepsPerFrame.getValue() && !sim.isComplete(); i++) {
            sim.advanceOneStep();
        }
        if (sim.isComplete()) {
            stop();
        }
        refresh();
    }

    private void start() {
        if (ignition == null) {
            ignition = centerIgnition(sim);
            sim.igniteCell(ignition[0], ignition[1]);
        }
        timer.start();
        play.setText("Pause");
    }

    private void stop() {
        timer.stop();
        play.setText("Play");
    }

    private void reset() {
        stop();
        sim = newSimulation(grids, currentWind());
        ignition = null;
        refresh();
    }

    private void refresh() {
        int[] n = countFire(sim);
        double hectares = (n[0] + n[1]) * CELL_SIZE_METERS * CELL_SIZE_METERS / 10_000.0;
        status.setText(String.format(
                "  Time %.0f min   |   Burning %d   Burned %d   |   Area %.1f ha   |   Wind %s",
                sim.getTimeSeconds() / 60.0, n[0], n[1], hectares, windText()));
        map.repaint();
    }

    private String windText() {
        if (speed.getValue() == 0) {
            return "calm";
        }
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return speed.getValue() + " m/s toward "
                + names[(int) Math.round(direction.getValue() / 45.0) % 8]
                + " (" + direction.getValue() + "°)";
    }

    void show() {
        direction.setMajorTickSpacing(90);
        direction.setPaintTicks(true);
        speed.setMajorTickSpacing(5);
        speed.setPaintTicks(true);
        speed.setPaintLabels(true);

        Runnable windChanged = () -> {
            sim.setWind(currentWind());
            refresh();
        };
        direction.addChangeListener(e -> windChanged.run());
        speed.addChangeListener(e -> windChanged.run());

        play.addActionListener(e -> {
            if (timer.isRunning()) {
                stop();
            } else {
                start();
            }
        });
        JButton step = new JButton("Step");
        step.addActionListener(e -> {
            if (ignition == null) {
                ignition = centerIgnition(sim);
                sim.igniteCell(ignition[0], ignition[1]);
            }
            sim.advanceOneStep();
            refresh();
        });
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> reset());

        JPanel controls = new JPanel(new GridLayout(0, 1, 0, 6));
        controls.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        controls.add(new JLabel("Wind direction (toward, degrees)"));
        controls.add(direction);
        controls.add(new JLabel("Wind speed (m/s)"));
        controls.add(speed);
        controls.add(new JLabel("Steps per frame (1 step = 30 s)"));
        controls.add(stepsPerFrame);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(play);
        buttons.add(step);
        buttons.add(resetButton);
        controls.add(buttons);
        controls.add(new JLabel("Click a cell on the map to start a fire."));
        controls.add(legend());

        JPanel side = new JPanel(new BorderLayout());
        side.add(controls, BorderLayout.NORTH);

        JFrame frame = new JFrame("Wildfire Spread Demo - Northern California 100 x 100 grid");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(map, BorderLayout.CENTER);
        frame.add(side, BorderLayout.EAST);
        frame.add(status, BorderLayout.SOUTH);
        refresh();
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel legend() {
        JPanel p = new JPanel(new GridLayout(0, 2, 4, 2));
        Object[][] items = {
            {GRASS, "Grass"}, {SHRUB, "Shrub"}, {TIMBER, "Timber"}, {NON_BURNABLE, "Non-burnable"},
            {BURNING, "Burning"}, {BURNED, "Burned"}
        };
        for (Object[] item : items) {
            JLabel l = new JLabel("■ " + item[1]);
            l.setForeground((Color) item[0]);
            p.add(l);
        }
        return p;
    }

    private final class MapPanel extends JPanel {
        private static final int CELL_PX = 7;

        MapPanel() {
            setPreferredSize(new Dimension(COLS * CELL_PX, ROWS * CELL_PX));
            setToolTipText("");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int r = e.getY() / CELL_PX;
                    int c = e.getX() / CELL_PX;
                    if (r < 0 || r >= ROWS || c < 0 || c >= COLS) {
                        return;
                    }
                    FireCell cell = sim.getCell(r, c);
                    if (!isBurnable(cell)) {
                        status.setText("  That cell can't burn (" + FuelCover.descriptionForModel(cell.getFuelModel())
                                + ", or already burning). Pick a green cell.");
                        return;
                    }
                    sim.igniteCell(r, c);
                    if (ignition == null) {
                        ignition = new int[] {r, c};
                    }
                    refresh();
                }
            });
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int r = e.getY() / CELL_PX;
            int c = e.getX() / CELL_PX;
            if (r < 0 || r >= ROWS || c < 0 || c >= COLS) {
                return null;
            }
            FireCell cell = sim.getCell(r, c);
            return String.format("[%d, %d] %s, fuel model %d, %s", r, c,
                    FuelCover.descriptionForModel(cell.getFuelModel()), cell.getFuelModel(), cell.getState());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            drawGrid((Graphics2D) g, sim, CELL_PX, ignition);
        }
    }
}
