package wildfire;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public final class SimulationGUI extends JFrame {
    private final Grid grid;
    private final FireSpreadEngine engine;
    private final SimulationStatistics stats;
    private final GridView gridView;
    private final JLabel status = new JLabel("Select a burnable cell to place an ignition.");
    private final JLabel metrics = new JLabel();
    private final JLabel selected = new JLabel("Cell details: none");
    private final JTextArea scenarioLog = new JTextArea(7, 22);
    private final JSpinner ignitionCount = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
    private final Timer timer;
    private int selectedRow = -1, selectedCol = -1;
    private boolean running;
    private int savedScenarioCount;

    public SimulationGUI() {
        super("Wildfire Grid Simulation");
        grid = new Grid(10, 10, 20261113L);
        engine = new FireSpreadEngine(grid, Wind.calm(), 42L);
        stats = new SimulationStatistics(grid);
        gridView = new GridView();
        timer = new Timer(180, e -> tick());
        build();
        refresh();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 760));
        setLocationRelativeTo(null);
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(244, 246, 243));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("WILDFIRE GRID SIMULATION");
        title.setFont(new Font("SansSerif", Font.BOLD, 23));
        title.setForeground(new Color(35, 56, 45));
        JLabel subtitle = new JLabel("Discrete-cell model  |  Fuel, neighboring fire influence and wind");
        subtitle.setForeground(new Color(90, 105, 96));
        JPanel heading = new JPanel(new GridLayout(2, 1));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(subtitle);
        header.add(heading, BorderLayout.WEST);
        root.add(header, BorderLayout.NORTH);

        gridView.setPreferredSize(new Dimension(620, 620));
        root.add(gridView, BorderLayout.CENTER);
        JPanel controlsPanel = buildControls();
        JScrollPane controlsScroll = new JScrollPane(controlsPanel);
        controlsScroll.setBorder(null);
        controlsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlsScroll.setPreferredSize(new Dimension(310, 620));
        root.add(controlsScroll, BorderLayout.EAST);
        setContentPane(root);
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(290, 620));
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 224, 218)),
                new EmptyBorder(14, 14, 14, 14)));

        JLabel controls = new JLabel("SIMULATION CONTROLS");
        controls.setFont(new Font("SansSerif", Font.BOLD, 15));
        panel.add(controls);
        panel.add(gap(14));

        JButton start = button("Start simulation");
        start.addActionListener(e -> { running = true; timer.start(); status.setText("Simulation running."); });
        JButton pause = button("Pause");
        pause.addActionListener(e -> { running = false; timer.stop(); status.setText("Simulation paused."); });
        JButton step = button("Advance one step");
        step.addActionListener(e -> { timer.stop(); running = false; tick(); });
        JButton reset = button("Reset grid");
        reset.addActionListener(e -> { timer.stop(); running = false; engine.reset(); selectedRow = selectedCol = -1; status.setText("Grid reset."); refresh(); });
        panel.add(new JLabel("Number of random initial fires"));
        ignitionCount.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.add(ignitionCount);
        JButton randomIgnition = button("Place random fires");
        randomIgnition.addActionListener(e -> {
            try {
                int count = (Integer) ignitionCount.getValue();
                engine.igniteRandom(count);
                status.setText(count + " random initial ignition point(s) placed.");
                refresh();
            } catch (IllegalStateException | IllegalArgumentException ex) {
                status.setText(ex.getMessage());
            }
        });
        JButton saveScenario = button("Save scenario result");
        saveScenario.addActionListener(e -> saveScenarioResult());
        panel.add(start); panel.add(gap(7)); panel.add(pause); panel.add(gap(7));
        panel.add(step); panel.add(gap(7)); panel.add(reset); panel.add(gap(7));
        panel.add(randomIgnition); panel.add(gap(7)); panel.add(saveScenario); panel.add(gap(18));

        JLabel windLabel = new JLabel("WIND");
        windLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        panel.add(windLabel);
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        JComboBox<String> direction = new JComboBox<>(directions);
        JSlider speed = new JSlider(0, 40, 0);
        speed.setMajorTickSpacing(10); speed.setPaintTicks(true); speed.setPaintLabels(true);
        JButton applyWind = button("Apply wind");
        applyWind.addActionListener(e -> {
            double degrees = direction.getSelectedIndex() * 45.0;
            engine.setWind(new Wind(speed.getValue(), degrees));
            status.setText("Wind set to " + directions[direction.getSelectedIndex()] + ", " + speed.getValue() + " units.");
        });
        panel.add(new JLabel("Direction (where wind blows toward)"));
        panel.add(direction);
        panel.add(new JLabel("Wind strength"));
        panel.add(speed);
        panel.add(applyWind);
        panel.add(gap(18));

        JLabel statsHeading = new JLabel("LIVE STATISTICS");
        statsHeading.setFont(new Font("SansSerif", Font.BOLD, 14));
        panel.add(statsHeading);
        metrics.setFont(new Font("Monospaced", Font.PLAIN, 13));
        panel.add(metrics);
        panel.add(gap(14));
        selected.setFont(new Font("SansSerif", Font.PLAIN, 12));
        selected.setBorder(new EmptyBorder(8, 0, 8, 0));
        panel.add(selected);
        panel.add(gap(8));
        JLabel compareHeading = new JLabel("SCENARIO COMPARISON LOG");
        compareHeading.setFont(new Font("SansSerif", Font.BOLD, 13));
        panel.add(compareHeading);
        scenarioLog.setEditable(false);
        scenarioLog.setLineWrap(true);
        scenarioLog.setWrapStyleWord(true);
        scenarioLog.setFont(new Font("Monospaced", Font.PLAIN, 10));
        scenarioLog.setText("Save each run, reset, then change one setting to compare outcomes.\n");
        JScrollPane scenarioScroll = new JScrollPane(scenarioLog);
        scenarioScroll.setPreferredSize(new Dimension(255, 115));
        scenarioScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 115));
        panel.add(scenarioScroll);
        panel.add(gap(8));
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setForeground(new Color(60, 88, 68));
        panel.add(status);
        panel.add(javax.swing.Box.createVerticalGlue());
        JLabel note = new JLabel("<html><body width='245'>Educational simulation only. It is not a real-world wildfire forecast.</body></html>");
        note.setForeground(new Color(130, 80, 55));
        panel.add(note);
        return panel;
    }

    private JButton button(String text) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return b;
    }

    private java.awt.Component gap(int height) {
        return javax.swing.Box.createVerticalStrut(height);
    }

    private void tick() {
        if (stats.getBurning() == 0) {
            timer.stop(); running = false; status.setText("No active fires. Simulation complete."); return;
        }
        engine.advanceOneStep();
        refresh();
        if (stats.getBurning() == 0) {
            timer.stop(); running = false; status.setText("No active fires. Simulation complete.");
        }
    }

    private void refresh() {
        metrics.setText("<html>Step: " + engine.getStep()
                + "<br>Burning: " + stats.getBurning()
                + "<br>Burned: " + stats.getBurned()
                + "<br>Unburned: " + stats.getUnburned()
                + "<br>Non-burnable: " + stats.getNonBurnable()
                + String.format("<br>Affected: %.1f%%", stats.getAffectedPercent())
                + "<br>Longest burn: " + stats.getLongestBurnDuration() + " steps"
                + String.format("<br>Average burn: %.1f steps", stats.getAverageBurnDuration())
                + "</html>");
        gridView.repaint();
        if (selectedRow >= 0) updateSelected();
    }

    private void saveScenarioResult() {
        Wind wind = engine.getWind();
        String direction = String.format("%.0f°", wind.getDirectionDegrees());
        String result = String.format("Run %d | starts=%d | wind=%s @ %.1f | steps=%d | burned=%d | burning=%d | affected=%.1f%% | longest burn=%d steps",
                ++savedScenarioCount, engine.getInitialIgnitionCount(), direction,
                wind.getSpeed(), engine.getStep(), stats.getBurned(), stats.getBurning(),
                stats.getAffectedPercent(), stats.getLongestBurnDuration());
        String existing = scenarioLog.getText();
        if (existing.startsWith("Save a completed")) scenarioLog.setText("");
        scenarioLog.append(result + "\n");
        status.setText("Scenario result saved. Reset and run another scenario to compare.");
    }

    private void updateSelected() {
        Cell c = grid.getCell(selectedRow, selectedCol);
        selected.setText(String.format("<html>Selected cell [%d, %d]<br>Material: %s<br>State: %s<br>Fuel: %.1f remaining / %.1f initial<br>Fire intensity: %.1f / 10<br>Burn duration: %d steps<br>Ignition step: %s<br>Next-step ignition probability: %.1f%%</html>",
                selectedRow, selectedCol, c.getMaterial(), c.getState(),
                c.getRemainingFuel(), c.getInitialFuel(), c.getIntensity(),
                c.getBurningSteps(), c.getIgnitionStep() < 0 ? "not ignited" : Integer.toString(c.getIgnitionStep()),
                c.getIgnitionProbability() * 100));
    }

    private final class GridView extends JPanel {
        GridView() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(185, 197, 185)));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int cellW = Math.max(1, getWidth() / grid.getCols());
                    int cellH = Math.max(1, getHeight() / grid.getRows());
                    int col = e.getX() / cellW, row = e.getY() / cellH;
                    if (row < 0 || row >= grid.getRows() || col < 0 || col >= grid.getCols()) return;
                    selectedRow = row; selectedCol = col;
                    Cell cell = grid.getCell(row, col);
                    if (SwingUtilities.isLeftMouseButton(e) && cell.getState() == CellState.UNBURNED) {
                        engine.ignite(row, col);
                        status.setText("Ignition placed at [" + row + ", " + col + "].");
                    }
                    updateSelected(); repaint(); refresh();
                }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int cw = getWidth() / grid.getCols(), ch = getHeight() / grid.getRows();
            for (int r = 0; r < grid.getRows(); r++) {
                for (int c = 0; c < grid.getCols(); c++) {
                    Cell cell = grid.getCell(r, c);
                    g.setColor(colorFor(cell));
                    int x = c * cw, y = r * ch;
                    g.fillRect(x, y, cw, ch);
                    g.setColor(new Color(255, 255, 255, 55));
                    g.drawRect(x, y, cw, ch);
                    if (r == selectedRow && c == selectedCol) {
                        g.setColor(Color.BLACK); g.drawRect(x + 1, y + 1, cw - 3, ch - 3);
                    }
                }
            }
            g.dispose();
        }

        private Color colorFor(Cell cell) {
            switch (cell.getState()) {
                case NON_BURNABLE: return new Color(190, 198, 199);
                case BURNING: return new Color(235, 75, 35);
                case BURNED: return new Color(75, 61, 56);
                default:
                    if (cell.getRemainingFuel() >= 7) return new Color(48, 116, 73);
                    if (cell.getRemainingFuel() >= 4) return new Color(113, 157, 78);
                    return new Color(190, 178, 105);
            }
        }
    }
}
