package wildfire;

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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** Swing front end. Holds no simulation rules; it forwards user actions to the controller. */
public class SimulationGUI extends JFrame {

    private static final int MAX_GUI_DIMENSION = Grid.MAX_DIMENSION;
    private static final int MAX_LOGGED_TESTS_PER_STEP = 40;

    private final SimulationController controller;
    private final GridPanel gridPanel;
    private final Timer timer;

    private final JSpinner rowsSpinner = new JSpinner(new SpinnerNumberModel(10, Grid.MIN_DIMENSION, MAX_GUI_DIMENSION, 1));
    private final JSpinner columnsSpinner = new JSpinner(new SpinnerNumberModel(10, Grid.MIN_DIMENSION, MAX_GUI_DIMENSION, 1));
    private final JRadioButton randomFuelMode = new JRadioButton("Random", true);
    private final JRadioButton manualFuelMode = new JRadioButton("Manual");
    private final JComboBox<MaterialType> materialCombo = new JComboBox<>(MaterialType.values());
    private final JButton rerollFuelButton = new JButton("Re-roll random fuel");
    private final JRadioButton clickIgnition = new JRadioButton("Click toggles ignition point", true);
    private final JRadioButton clickPaint = new JRadioButton("Click paints material");
    private final JRadioButton randomIgnitionMode = new JRadioButton("Random placement");
    private final JRadioButton manualIgnitionMode = new JRadioButton("Manual (click cells)", true);
    private final JSpinner fireCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JButton placeFiresButton = new JButton("Place random fires");
    private final JButton clearFiresButton = new JButton("Clear fires");
    private final JLabel fireCountLabel = new JLabel();
    private final JSlider windDirectionSlider = new JSlider(0, 359, 90);
    private final JSlider windStrengthSlider = new JSlider(0, 100, 50);
    private final JLabel windLabel = new JLabel();
    private final WindIndicator windIndicator = new WindIndicator();
    /** Delay between automatic steps, in ms: 0.1 s (fast) to 5 s (very slow); default 1.5 s. */
    private final JSlider speedSlider = new JSlider(100, 5000, 1500);
    private final JLabel speedLabel = new JLabel();

    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resumeButton = new JButton("Resume");
    private final JButton stepButton = new JButton("Step");
    private final JButton resetButton = new JButton("Reset");
    private final JButton generateButton = new JButton("New grid");
    private final JButton experimentButton = new JButton("Run experiment...");

    private final JLabel[] statLabels = new JLabel[8];
    private final JTextArea cellInfo = new JTextArea(22, 50);
    private final JTextArea stepLog = new JTextArea(8, 60);
    private final java.util.Deque<String> stepLogEntries = new java.util.ArrayDeque<>();
    private int lastLoggedStep = -1;
    private final JCheckBox showSignalsBox = new JCheckBox("Signal arrows (burning -> neighbour)", true);
    private final JCheckBox showProbabilityBox = new JCheckBox("P(ignite) on each cell", true);
    private final JCheckBox showSpreadPathBox = new JCheckBox("Spread path (who ignited whom)", false);
    private final JLabel gridSizeLabel = new JLabel();
    private final JLabel statusBar = new JLabel(" ");
    private int inspectedRow = -1;
    private int inspectedColumn = -1;

    public SimulationGUI(SimulationController controller) {
        super("California Wildfire Simulation");
        this.controller = controller;
        this.gridPanel = new GridPanel(controller);
        this.timer = new Timer(speedSlider.getValue(), e -> onTick());

        rowsSpinner.setValue(controller.getGrid().getRows());
        columnsSpinner.setValue(controller.getGrid().getColumns());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(buildConfigPanel(), BorderLayout.WEST);
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        center.add(gridPanel);
        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.add(new JScrollPane(center), BorderLayout.CENTER);
        JPanel logBox = new JPanel(new BorderLayout());
        logBox.setBorder(BorderFactory.createTitledBorder(
                "Step log - every ignition test: P vs random roll, and which neighbours sent signals"));
        stepLog.setEditable(false);
        stepLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logBox.add(new JScrollPane(stepLog), BorderLayout.CENTER);
        middle.add(logBox, BorderLayout.SOUTH);
        add(middle, BorderLayout.CENTER);
        add(buildInfoPanel(), BorderLayout.EAST);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        wireEvents();
        controller.addListener(this::refreshAll);
        applyWindFromSliders();
        refreshAll();
        pack();
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);
    }

    // ----- layout -----

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));

        JPanel gridBox = titled("Grid");
        gridBox.setLayout(new GridLayout(0, 2, 4, 4));
        gridBox.add(new JLabel("Rows (n)"));
        gridBox.add(rowsSpinner);
        gridBox.add(new JLabel("Columns (m)"));
        gridBox.add(columnsSpinner);
        gridBox.add(generateButton);
        gridBox.add(gridSizeLabel);
        generateButton.setToolTipText("Discard the current layout and fire points and build a fresh n x m grid");
        commitWhileTyping(rowsSpinner);
        commitWhileTyping(columnsSpinner);
        rowsSpinner.addChangeListener(e -> updateGridSizeLabel());
        columnsSpinner.addChangeListener(e -> updateGridSizeLabel());
        updateGridSizeLabel();
        panel.add(gridBox);

        JPanel fuelBox = titled("Fuel assignment");
        fuelBox.setLayout(new BoxLayout(fuelBox, BoxLayout.Y_AXIS));
        ButtonGroup fuelGroup = new ButtonGroup();
        fuelGroup.add(randomFuelMode);
        fuelGroup.add(manualFuelMode);
        fuelBox.add(randomFuelMode);
        fuelBox.add(rerollFuelButton);
        fuelBox.add(manualFuelMode);
        fuelBox.add(materialCombo);
        ButtonGroup clickGroup = new ButtonGroup();
        clickGroup.add(clickIgnition);
        clickGroup.add(clickPaint);
        fuelBox.add(clickIgnition);
        fuelBox.add(clickPaint);
        panel.add(fuelBox);

        JPanel fireBox = titled("Initial fires");
        fireBox.setLayout(new BoxLayout(fireBox, BoxLayout.Y_AXIS));
        ButtonGroup ignitionGroup = new ButtonGroup();
        ignitionGroup.add(manualIgnitionMode);
        ignitionGroup.add(randomIgnitionMode);
        fireBox.add(manualIgnitionMode);
        fireBox.add(randomIgnitionMode);
        JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        countRow.add(new JLabel("Number of fires"));
        countRow.add(fireCountSpinner);
        fireBox.add(countRow);
        fireBox.add(placeFiresButton);
        fireBox.add(clearFiresButton);
        fireBox.add(fireCountLabel);
        panel.add(fireBox);

        JPanel windBox = titled("Wind (blows toward)");
        windBox.setLayout(new BoxLayout(windBox, BoxLayout.Y_AXIS));
        windBox.add(windLabel);
        windBox.add(windIndicator);
        windBox.add(new JLabel("Direction (0=N, 90=E, 180=S, 270=W)"));
        windBox.add(windDirectionSlider);
        windBox.add(new JLabel("Strength"));
        windBox.add(windStrengthSlider);
        panel.add(windBox);

        JPanel speedBox = titled("Simulation speed (time per step)");
        speedBox.setLayout(new BorderLayout());
        java.util.Hashtable<Integer, JLabel> ticks = new java.util.Hashtable<>();
        ticks.put(100, new JLabel("fast"));
        ticks.put(2500, new JLabel("2.5 s"));
        ticks.put(5000, new JLabel("slow"));
        speedSlider.setLabelTable(ticks);
        speedSlider.setPaintLabels(true);
        speedSlider.setMajorTickSpacing(500);
        speedSlider.setPaintTicks(true);
        speedBox.add(speedLabel, BorderLayout.NORTH);
        speedBox.add(speedSlider, BorderLayout.CENTER);
        updateSpeedLabel();
        panel.add(speedBox);

        JPanel viewBox = titled("Cell communication view");
        viewBox.setLayout(new BoxLayout(viewBox, BoxLayout.Y_AXIS));
        viewBox.add(showSignalsBox);
        viewBox.add(showProbabilityBox);
        viewBox.add(showSpreadPathBox);
        viewBox.add(new JLabel("<html><small>Arrow width = signal strength.<br>"
                + "Hover a cell to label its own signals.<br>White ring = ignited last step.</small></html>"));
        panel.add(viewBox);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    /** Spinner value updates on every valid keystroke, not only on Enter, so the grid follows the typing. */
    private static void commitWhileTyping(JSpinner spinner) {
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor
                && editor.getTextField().getFormatter() instanceof javax.swing.text.DefaultFormatter formatter) {
            formatter.setCommitsOnValidEdit(true);
        }
    }

    /** Live resize: rows/columns changes reshape the grid at once, keeping the overlapping layout. */
    private void resizeFromSpinners() {
        if (controller.getStatus() != SimulationController.Status.IDLE) {
            return; // spinners are disabled while running; guard against programmatic changes
        }
        int n = (Integer) rowsSpinner.getValue();
        int m = (Integer) columnsSpinner.getValue();
        guarded(() -> {
            if (controller.resizeGrid(n, m, randomFuelMode.isSelected())) {
                gridPanel.refreshSize();
                statusBar.setText(String.format("Grid resized to %d x %d (%,d cells). Existing cells and fire points kept.",
                        n, m, n * m));
            }
        });
    }

    private void updateSpeedLabel() {
        speedLabel.setText(String.format("%.1f s per step  (adjustable while running)",
                speedSlider.getValue() / 1000.0));
    }

    private void updateGridSizeLabel() {
        int n = (Integer) rowsSpinner.getValue();
        int m = (Integer) columnsSpinner.getValue();
        gridSizeLabel.setText(String.format("%d x %d = %,d cells", n, m, n * m));
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));

        JPanel stats = titled("Statistics");
        stats.setLayout(new GridLayout(0, 2, 4, 2));
        String[] names = {"Total cells", "Unburned", "Burning", "Burned out",
            "Total ignitions", "Simulation step", "% affected", "Status"};
        for (int i = 0; i < names.length; i++) {
            stats.add(new JLabel(names[i]));
            statLabels[i] = new JLabel("-");
            stats.add(statLabels[i]);
        }
        panel.add(stats);

        JPanel info = titled("Cell communication (hover / click)");
        info.setLayout(new BorderLayout());
        cellInfo.setEditable(false);
        cellInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane infoScroll = new JScrollPane(cellInfo);
        infoScroll.setPreferredSize(new Dimension(390, 330));
        info.add(infoScroll, BorderLayout.CENTER);
        panel.add(info);

        JPanel legend = titled("Legend");
        legend.setLayout(new GridLayout(0, 1, 0, 2));
        for (MaterialType type : MaterialType.values()) {
            legend.add(swatch(GridPanel.materialColor(type), type.getLabel()
                    + " (fuel " + type.getMinFuel()
                    + (type.getMaxFuel() != type.getMinFuel() ? "-" + type.getMaxFuel() : "") + ")"));
        }
        legend.add(swatch(new Color(255, 220, 0), "Burning, low intensity"));
        legend.add(swatch(new Color(190, 0, 0), "Burning, high intensity"));
        legend.add(swatch(new Color(55, 55, 55), "Burned out"));
        legend.add(swatch(Color.MAGENTA, "Chosen ignition point (outline)"));
        legend.add(swatch(new Color(20, 80, 230), "Signal: burning cell -> neighbour"));
        legend.add(swatch(new Color(40, 0, 70), "Spread path / start fire (star)"));
        panel.add(legend);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        buttons.add(startButton);
        buttons.add(pauseButton);
        buttons.add(resumeButton);
        buttons.add(stepButton);
        buttons.add(resetButton);
        buttons.add(experimentButton);
        panel.add(buttons, BorderLayout.CENTER);
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        panel.add(statusBar, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel titled(String title) {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel swatch(Color color, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel box = new JLabel("   ");
        box.setOpaque(true);
        box.setBackground(color);
        box.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        row.add(box);
        row.add(new JLabel(text));
        return row;
    }

    // ----- events -----

    private void wireEvents() {
        experimentButton.addActionListener(e -> runExperiment());
        rowsSpinner.addChangeListener(e -> resizeFromSpinners());
        columnsSpinner.addChangeListener(e -> resizeFromSpinners());
        generateButton.addActionListener(e -> guarded(() -> {
            controller.generateGrid((Integer) rowsSpinner.getValue(),
                    (Integer) columnsSpinner.getValue(), randomFuelMode.isSelected());
            gridPanel.refreshSize();
            pack();
        }));
        rerollFuelButton.addActionListener(e -> guarded(controller::randomizeFuel));
        randomFuelMode.addActionListener(e -> {
            clickIgnition.setSelected(true);
            guarded(controller::randomizeFuel);
        });
        manualFuelMode.addActionListener(e -> refreshAll());
        clickIgnition.addActionListener(e -> refreshAll());
        clickPaint.addActionListener(e -> refreshAll());
        manualIgnitionMode.addActionListener(e -> refreshAll());
        randomIgnitionMode.addActionListener(e -> refreshAll());
        placeFiresButton.addActionListener(e -> guarded(
                () -> controller.randomizeIgnitions((Integer) fireCountSpinner.getValue())));
        clearFiresButton.addActionListener(e -> guarded(controller::clearIgnitions));

        windDirectionSlider.addChangeListener(e -> applyWindFromSliders());
        windStrengthSlider.addChangeListener(e -> applyWindFromSliders());
        speedSlider.addChangeListener(e -> {
            timer.setDelay(speedSlider.getValue());
            timer.setInitialDelay(speedSlider.getValue());
            updateSpeedLabel();
        });
        showSignalsBox.addActionListener(e -> gridPanel.setShowSignals(showSignalsBox.isSelected()));
        showProbabilityBox.addActionListener(e -> gridPanel.setShowProbabilities(showProbabilityBox.isSelected()));
        showSpreadPathBox.addActionListener(e -> gridPanel.setShowSpreadPath(showSpreadPathBox.isSelected()));

        startButton.addActionListener(e -> guarded(() -> {
            if (randomIgnitionMode.isSelected()
                    && controller.getIgnitionCells().size() != (Integer) fireCountSpinner.getValue()) {
                controller.randomizeIgnitions((Integer) fireCountSpinner.getValue());
            }
            controller.start();
            timer.start();
        }));
        pauseButton.addActionListener(e -> {
            timer.stop();
            controller.pause();
        });
        resumeButton.addActionListener(e -> {
            controller.resume();
            timer.start();
        });
        stepButton.addActionListener(e -> guarded(() -> {
            if (randomIgnitionMode.isSelected() && controller.getStatus() == SimulationController.Status.IDLE
                    && controller.getIgnitionCells().size() != (Integer) fireCountSpinner.getValue()) {
                controller.randomizeIgnitions((Integer) fireCountSpinner.getValue());
            }
            timer.stop();
            controller.step();
        }));
        resetButton.addActionListener(e -> {
            timer.stop();
            controller.reset();
        });

        gridPanel.setOnHover((r, c) -> inspect(r, c));
        gridPanel.setOnClick(this::handleClick);
    }

    private void handleClick(int row, int column) {
        inspect(row, column);
        if (controller.getStatus() != SimulationController.Status.IDLE) {
            return;
        }
        guarded(() -> {
            if (clickPaint.isSelected() && manualFuelMode.isSelected()) {
                controller.paintCell(row, column, (MaterialType) materialCombo.getSelectedItem());
            } else if (manualIgnitionMode.isSelected()) {
                Cell cell = controller.getGrid().getCell(row, column);
                if (controller.getIgnitionCells().contains(cell)) {
                    controller.removeIgnition(row, column);
                } else {
                    controller.addIgnition(row, column);
                }
            } else {
                statusBar.setText("Switch to 'Manual (click cells)' to choose ignition points by hand.");
            }
        });
    }

    /** Called by the Swing timer on the EDT: one short step per tick, never a long loop. */
    private void onTick() {
        if (!controller.step() || controller.isFinished()) {
            timer.stop();
        }
    }

    private void applyWindFromSliders() {
        controller.getWind().setDirectionDegrees(windDirectionSlider.getValue());
        controller.getWind().setStrength(windStrengthSlider.getValue() / 100.0);
        windLabel.setText(String.format("%s (%d deg), strength %.2f",
                controller.getWind().compassName(), windDirectionSlider.getValue(),
                controller.getWind().getStrength()));
        windIndicator.repaint();
    }

    private void guarded(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            timer.stop();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot do that", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ----- display refresh -----

    private void inspect(int row, int column) {
        inspectedRow = row;
        inspectedColumn = column;
        updateCellInfo();
    }

    private void updateCellInfo() {
        Grid grid = controller.getGrid();
        if (!grid.inBounds(inspectedRow, inspectedColumn)) {
            cellInfo.setText("Move over or click a cell.");
            return;
        }
        Cell cell = grid.getCell(inspectedRow, inspectedColumn);
        SimulationConfig cfg = controller.getConfig();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s  (row %d, col %d)  %s%n", cell.getId(), cell.getRow(), cell.getColumn(),
                cell.getMaterialType().getLabel()));
        sb.append(String.format("State %-10s fuel %d, remaining %.2f%n", cell.getState(),
                cell.getFuelValue(), cell.getRemainingFuel()));
        if (cell.getState() != FireState.UNBURNED) {
            sb.append(String.format("Intensity %.3f   burning for %d step(s)%n",
                    cell.getFireIntensity(), cell.getFireDuration()));
        }
        if (cell.getIgnitionTime() >= 0) {
            Cell by = cell.getIgnitedBy();
            sb.append(String.format("Ignited at step %d %s%n", cell.getIgnitionTime(),
                    by == null ? "(start fire)" : "- fire passed on by " + by.getId() + " (strongest signal)"));
        }

        if (cell.isBurning()) {
            appendOutgoing(sb, cell, cfg);
        } else if (cell.canIgnite()) {
            appendIncoming(sb, controller.explainIgnition(cell), cfg);
        } else if (cell.getFuelValue() == 0) {
            sb.append(String.format("%nNo fuel: this cell ignores every signal (P = 0).%n"));
        } else {
            sb.append(String.format("%nBurned out: no fuel left, never re-ignites (P = 0).%n"));
        }

        StepTrace trace = controller.getLastTrace();
        StepTrace.Evaluation test = trace.evaluationFor(cell);
        if (test != null) {
            sb.append(String.format("%nTEST IN STEP %d (from start-of-step state)%n", trace.getStep()));
            sb.append(String.format("  roll %.4f %s P %.4f -> %s%n", test.getRoll(),
                    test.isIgnited() ? "<" : ">=", test.getProbability(),
                    test.isIgnited() ? "IGNITED" : "stayed unburned"));
        }
        cellInfo.setText(sb.toString());
        cellInfo.setCaretPosition(0);
    }

    /** What every burning neighbour is currently telling this unburned cell. */
    private void appendIncoming(StringBuilder sb, IgnitionBreakdown b, SimulationConfig cfg) {
        sb.append(String.format("%nINCOMING SIGNALS (evaluated at next step)%n"));
        if (b.getSignals().isEmpty()) {
            sb.append(String.format("  none - no burning neighbour, so P = 0 whatever the wind.%n"));
            return;
        }
        sb.append(String.format("  contrib = %.2f x intensity x dist x wind%n", cfg.getSpreadCoefficient()));
        sb.append(String.format("  from  dir inten dist  wind(cos)    contrib%n"));
        StringBuilder product = new StringBuilder();
        for (IgnitionBreakdown.Signal s : b.getSignals()) {
            sb.append(String.format("  %-5s %-3s %.2f  %.2f  %.2f(%+.2f)  %.4f%s%n",
                    s.getSource().getId(), s.getDirectionName(), s.getSourceIntensity(),
                    s.getDistanceWeight(), s.getWindMultiplier(), s.getWindAlignment(),
                    s.getContribution(), s.getRawContribution() > 1.0 ? " (capped)" : ""));
            product.append(String.format("(1-%.3f)", s.getContribution()));
        }
        sb.append(String.format("  influence = 1 - %s%n", product));
        sb.append(String.format("            = %.4f%n", b.getFireInfluence()));
        sb.append(String.format("  fuel factor = %.2f + %.2f x %.2f/%d = %.4f%n", cfg.getMinFuelFactor(),
                1.0 - cfg.getMinFuelFactor(), b.getTarget().getRemainingFuel(), cfg.getMaxFuel(), b.getFuelFactor()));
        sb.append(String.format("  P(ignite) = %.4f x %.4f = %.4f%n", b.getFireInfluence(), b.getFuelFactor(),
                b.getProbability()));
    }

    /** What this burning cell is currently telling each neighbour. */
    private void appendOutgoing(StringBuilder sb, Cell source, SimulationConfig cfg) {
        sb.append(String.format("%nOUTGOING SIGNALS (evaluated at next step)%n"));
        sb.append(String.format("  to    dir dist  wind(cos)    contrib  its P%n"));
        int sent = 0;
        for (Cell target : controller.getNeighborManager().neighborsOf(source)) {
            String id = String.format("  %-5s ", target.getId());
            if (!target.canIgnite()) {
                String why = target.isBurning() ? "already burning"
                        : target.getState() == FireState.BURNED_OUT ? "burned out" : "no fuel";
                sb.append(id).append(String.format("%-3s -- ignored: %s%n",
                        IgnitionBreakdown.directionName(target.getRow() - source.getRow(),
                                target.getColumn() - source.getColumn()), why));
                continue;
            }
            IgnitionBreakdown.Signal s = controller.signalBetween(source, target);
            double p = controller.explainIgnition(target).getProbability();
            sb.append(id).append(String.format("%-3s %.2f  %.2f(%+.2f)  %.4f   %.4f%n",
                    s.getDirectionName(), s.getDistanceWeight(), s.getWindMultiplier(),
                    s.getWindAlignment(), s.getContribution(), p));
            sent++;
        }
        sb.append(String.format("  %d live signal(s). Next step fuel %.2f -> %.2f%n", sent,
                source.getRemainingFuel(), Math.max(0.0, source.getRemainingFuel() - cfg.getFuelConsumptionPerStep())));
    }

    /** Appends the most recent step's tests to the step log (newest step on top). */
    private void updateStepLog() {
        StepTrace trace = controller.getLastTrace();
        if (trace.getStep() == 0) {
            if (lastLoggedStep != 0) {
                stepLogEntries.clear();
                lastLoggedStep = 0;
                stepLog.setText("Press Step to see how each cell's neighbours decide whether it ignites.");
            }
            return;
        }
        if (trace.getStep() == lastLoggedStep) {
            return;
        }
        lastLoggedStep = trace.getStep();
        java.util.List<StepTrace.Evaluation> tests = new java.util.ArrayList<>(trace.getEvaluations());
        tests.sort((a, b) -> a.isIgnited() != b.isIgnited() ? (a.isIgnited() ? -1 : 1)
                : Double.compare(b.getProbability(), a.getProbability()));
        long ignited = tests.stream().filter(StepTrace.Evaluation::isIgnited).count();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("STEP %d - phase 1: %d cell(s) tested, all from the start-of-step state; %d ignited%n",
                trace.getStep(), tests.size(), ignited));
        // on big n x m grids a step can test hundreds of cells; list ignitions first, then the likeliest
        int misses = 0;
        for (StepTrace.Evaluation e : tests) {
            if (!e.isIgnited() && misses++ >= MAX_LOGGED_TESTS_PER_STEP) {
                sb.append(String.format("  ... %d more test(s) with lower P, all 'no' (hover a cell for its details)%n",
                        tests.size() - ignited - MAX_LOGGED_TESTS_PER_STEP));
                break;
            }
            StringBuilder senders = new StringBuilder();
            for (IgnitionBreakdown.Signal s : e.getBreakdown().getSignals()) {
                if (senders.length() > 0) {
                    senders.append(", ");
                }
                senders.append(String.format("%s->%s %.3f", s.getSource().getId(), s.getDirectionName(),
                        s.getContribution()));
            }
            sb.append(String.format("  %-5s P=%.4f roll=%.4f %-8s signals: %s%n", e.getTarget().getId(),
                    e.getProbability(), e.getRoll(), e.isIgnited() ? "IGNITED" : "no", senders));
        }
        StringBuilder out = new StringBuilder();
        for (Cell c : trace.getBurnedOut()) {
            out.append(out.length() > 0 ? ", " : "").append(c.getId());
        }
        sb.append(String.format("  phase 2: burning cells consumed %.2f fuel each; burned out: %s%n",
                controller.getConfig().getFuelConsumptionPerStep(), out.length() == 0 ? "none" : out));
        sb.append(String.format("  phase 3: %d new fire(s) lit together (they start sending signals next step)%n", ignited));
        stepLogEntries.addFirst(sb.toString());
        while (stepLogEntries.size() > 60) {
            stepLogEntries.removeLast();
        }
        stepLog.setText(String.join("\n", stepLogEntries));
        stepLog.setCaretPosition(0);
    }

    private void refreshAll() {
        SimulationStatistics s = controller.getStatistics();
        SimulationController.Status status = controller.getStatus();
        statLabels[0].setText(String.valueOf(s.getTotalCells()));
        statLabels[1].setText(String.valueOf(s.getUnburned()));
        statLabels[2].setText(String.valueOf(s.getBurning()));
        statLabels[3].setText(String.valueOf(s.getBurnedOut()));
        statLabels[4].setText(String.valueOf(s.getTotalIgnitions()));
        statLabels[5].setText(String.valueOf(s.getStep()));
        statLabels[6].setText(String.format("%.1f%%", s.getPercentAffected()));
        statLabels[7].setText(status.name());

        boolean idle = status == SimulationController.Status.IDLE;
        boolean hasFires = !controller.getIgnitionCells().isEmpty();
        generateButton.setEnabled(idle);
        rowsSpinner.setEnabled(idle);
        columnsSpinner.setEnabled(idle);
        randomFuelMode.setEnabled(idle);
        manualFuelMode.setEnabled(idle);
        rerollFuelButton.setEnabled(idle && randomFuelMode.isSelected());
        materialCombo.setEnabled(idle && manualFuelMode.isSelected());
        clickPaint.setEnabled(idle && manualFuelMode.isSelected());
        clickIgnition.setEnabled(idle);
        manualIgnitionMode.setEnabled(idle);
        randomIgnitionMode.setEnabled(idle);
        fireCountSpinner.setEnabled(idle && randomIgnitionMode.isSelected());
        placeFiresButton.setEnabled(idle && randomIgnitionMode.isSelected());
        clearFiresButton.setEnabled(idle && hasFires);
        fireCountLabel.setText("Selected ignition points: " + controller.getIgnitionCells().size());

        startButton.setEnabled(idle && (hasFires || randomIgnitionMode.isSelected()));
        pauseButton.setEnabled(status == SimulationController.Status.RUNNING);
        resumeButton.setEnabled(status == SimulationController.Status.PAUSED);
        stepButton.setEnabled(status == SimulationController.Status.PAUSED
                || (idle && (hasFires || randomIgnitionMode.isSelected())));
        resetButton.setEnabled(status != SimulationController.Status.IDLE);

        switch (status) {
            case IDLE -> statusBar.setText(hasFires
                    ? "Ready. Press Start or Step."
                    : "Choose ignition points (click cells, or use random placement), then press Start.");
            case RUNNING -> statusBar.setText("Running...");
            case PAUSED -> statusBar.setText("Paused.");
            case FINISHED -> statusBar.setText(String.format(
                    "Finished at step %d: no cells burning. %.1f%% of the grid was affected. %s",
                    s.getStep(), s.getPercentAffected(), outcomeDescription(s)));
        }
        gridPanel.repaint();
        updateCellInfo();
        updateStepLog();
    }

    /** Answers the professor's question: did the fire spread widely or die down? */
    private String outcomeDescription(SimulationStatistics s) {
        long burnable = controller.getGrid().allCells().stream()
                .filter(cell -> cell.getFuelValue() > 0).count();
        if (burnable == 0) {
            return "";
        }
        double percentOfBurnable = 100.0 * s.getBurnedOut() / burnable;
        if (percentOfBurnable >= 99.9) {
            return "Outcome: the fire burned all the fuel in the area.";
        }
        return percentOfBurnable > ExperimentRunner.MAJOR_FIRE_FRACTION_PERCENT
                ? String.format("Outcome: major fire (%.0f%% of burnable cells).", percentOfBurnable)
                : String.format("Outcome: the fire died down (%.0f%% of burnable cells).", percentOfBurnable);
    }

    /** Runs the batch experiments off the EDT and shows the tables in a dialog. */
    private void runExperiment() {
        JTextArea output = new JTextArea(22, 70);
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        final int rows = controller.getGrid().getRows();
        final int columns = controller.getGrid().getColumns();
        // 100 runs per setting on small grids, fewer on large n x m grids so it finishes in seconds
        final int runs = Math.max(10, Math.min(100, 10_000 / (rows * columns)));
        output.setText("Running " + runs + " simulations per setting on a " + rows + " x " + columns + " grid...");
        JDialog dialog = new JDialog(this, "Experiment: when does the fire spread?", false);
        dialog.add(new JScrollPane(output));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        final double direction = controller.getWind().getDirectionDegrees();
        final double strength = controller.getWind().getStrength();
        final int fires = (Integer) fireCountSpinner.getValue();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                ExperimentRunner runner = new ExperimentRunner(
                        rows, columns, controller.getConfig(), System.nanoTime());
                String first = ExperimentRunner.format(
                        String.format("A) Number of initial fires (wind %s, strength %.2f), %dx%d grid, random fuel each run",
                                controller.getWind().compassName(), strength, rows, columns),
                        runner.sweepInitialFires(8, runs, new Wind(direction, strength)));
                String second = ExperimentRunner.format(
                        String.format("B) Wind strength (blowing toward %.0f deg), %d initial fire(s)", direction, fires),
                        runner.sweepWindStrength(fires, runs, direction));
                return first + "\n" + second + "\n'Burned' = % of cells that had fuel. Major fire = more than "
                        + ExperimentRunner.MAJOR_FIRE_FRACTION_PERCENT + "% burned.";
            }

            @Override
            protected void done() {
                try {
                    output.setText(get());
                } catch (Exception ex) {
                    output.setText("Experiment failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** Small compass-style arrow showing where the wind blows toward. */
    private final class WindIndicator extends JPanel {
        WindIndicator() {
            setPreferredSize(new Dimension(70, 70));
            setMaximumSize(new Dimension(70, 70));
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawOval(cx - 30, cy - 30, 60, 60);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString("N", cx - 4, cy - 32 + 10);
            double rad = Math.toRadians(controller.getWind().getDirectionDegrees());
            double length = 8 + 22 * controller.getWind().getStrength();
            int ex = cx + (int) Math.round(Math.sin(rad) * length);
            int ey = cy - (int) Math.round(Math.cos(rad) * length);
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(30, 90, 200));
            g2.drawLine(cx, cy, ex, ey);
            g2.fillOval(ex - 4, ey - 4, 8, 8);
        }
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            SimulationController controller = new SimulationController(
                    10, 10, new SimulationConfig(), System.nanoTime());
            new SimulationGUI(controller).setVisible(true);
        });
    }
}
