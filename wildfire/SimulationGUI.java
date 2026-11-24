package wildfire;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Swing front end. Holds no simulation rules; it forwards user actions to the controller. */
public class SimulationGUI extends JFrame {

    private static final int MAX_GUI_DIMENSION = Grid.MAX_DIMENSION;

    private final SimulationController controller;
    private final GridPanel gridPanel;
    private final Timer timer;

    private final JSpinner rowsSpinner = new JSpinner(new SpinnerNumberModel(10, Grid.MIN_DIMENSION, MAX_GUI_DIMENSION, 1));
    private final JSpinner columnsSpinner = new JSpinner(new SpinnerNumberModel(10, Grid.MIN_DIMENSION, MAX_GUI_DIMENSION, 1));
    private final JRadioButton randomFuelMode = new JRadioButton("Random", true);
    private final JRadioButton manualFuelMode = new JRadioButton("Manual");
    private final JComboBox<MaterialType> materialCombo = new JComboBox<>(MaterialType.values());
    private final JButton rerollFuelButton = new JButton("Re-roll random fuel");
    /** Single source of truth for what a grid click does (paint XOR toggle ignition XOR inspect). */
    private final GridClickMode clickMode = new GridClickMode();
    private final JRadioButton paintMaterialAction = new JRadioButton("Paint material (click a cell)");
    private final JRadioButton toggleIgnitionAction = new JRadioButton("Toggle ignition point (click a cell)");
    /** Groups the two grid actions so only one can ever be selected. */
    private final ButtonGroup gridActionGroup = new ButtonGroup();
    private final JPanel manualFuelPanel = new JPanel();
    private final JRadioButton randomIgnitionMode = new JRadioButton("Random placement");
    private final JRadioButton manualIgnitionMode = new JRadioButton("Manual (click cells)", true);
    private final JSpinner fireCountSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JButton placeFiresButton = new JButton("Place random fires");
    private final JButton clearFiresButton = new JButton("Clear fires");
    private final JLabel fireCountLabel = new JLabel();
    private final JSlider windDirectionSlider = new JSlider(0, 359, 90);
    /** Wind speed in miles per hour, 0-80 mph (covers Beaufort force 0 to 12). Default 15 mph = Force 4. */
    private final JSlider windSpeedSlider = new JSlider(0, 80, 15);
    private final JLabel windLabel = new JLabel();
    private final JLabel beaufortLabel = new JLabel();
    private final JTextArea timingReport = new JTextArea(8, 60);
    /** Delay between automatic steps, in ms: 0.1 s (fast) to 5 s (very slow); default 1.5 s. */
    private final JSlider speedSlider = new JSlider(100, 5000, 1500);
    private final JLabel speedLabel = new JLabel();

    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resumeButton = new JButton("Resume");
    private final JButton stepButton = new JButton("Step");
    private final JButton stopButton = new JButton("Stop");
    private final JButton resetButton = new JButton("Reset");
    private final JButton generateButton = new JButton("New grid");

    private final JLabel[] statLabels = new JLabel[10];
    private final JTextArea cellInfo = new JTextArea(22, 50);
    private final JCheckBox showSignalsBox = new JCheckBox("Signal arrows (burning -> neighbour)", true);
    private final JCheckBox showProbabilityBox = new JCheckBox("P(ignite) on each cell", true);
    private final JCheckBox showSpreadPathBox = new JCheckBox("Spread path + travel time (steps)", false);
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
        JPanel configPanel = buildConfigPanel();
        JScrollPane configScroll = new JScrollPane(configPanel,
                // ALWAYS: the scrollbar's width is then part of the preferred width, so it never covers controls
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        configScroll.setBorder(null);
        configScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(configScroll, BorderLayout.WEST);
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        center.add(gridPanel);
        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.add(new JScrollPane(center), BorderLayout.CENTER);
        timingReport.setEditable(false);
        timingReport.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        javax.swing.JTabbedPane bottomTabs = new javax.swing.JTabbedPane();
        bottomTabs.addTab("Spread timing (vegetation & wind)", new JScrollPane(timingReport));
        bottomTabs.setPreferredSize(new Dimension(700, 200));
        middle.add(bottomTabs, BorderLayout.SOUTH);
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
        // shown only in Manual fuel mode: which material to paint, and the explicit paint action
        manualFuelPanel.setLayout(new BoxLayout(manualFuelPanel, BoxLayout.Y_AXIS));
        manualFuelPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        manualFuelPanel.setAlignmentX(LEFT_ALIGNMENT);
        materialCombo.setAlignmentX(LEFT_ALIGNMENT);
        paintMaterialAction.setAlignmentX(LEFT_ALIGNMENT);
        manualFuelPanel.add(materialCombo);
        manualFuelPanel.add(paintMaterialAction);
        fuelBox.add(manualFuelPanel);
        randomFuelMode.setToolTipText("Every cell gets a weighted random material and fuel value");
        manualFuelMode.setToolTipText("Choose a material and paint cells with it");
        materialCombo.setToolTipText("Material that a click paints onto a cell");
        paintMaterialAction.setToolTipText("Clicking a cell changes ONLY its vegetation/material; "
                + "ignition points are not touched (a cell painted water or bare land loses its ignition point)");
        panel.add(fuelBox);

        JPanel fireBox = titled("Initial fires");
        fireBox.setLayout(new BoxLayout(fireBox, BoxLayout.Y_AXIS));
        ButtonGroup ignitionGroup = new ButtonGroup();
        ignitionGroup.add(manualIgnitionMode);
        ignitionGroup.add(randomIgnitionMode);
        fireBox.add(manualIgnitionMode);
        JPanel toggleRow = new JPanel();
        toggleRow.setLayout(new BoxLayout(toggleRow, BoxLayout.Y_AXIS));
        toggleRow.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        toggleRow.setAlignmentX(LEFT_ALIGNMENT);
        toggleRow.add(toggleIgnitionAction);
        fireBox.add(toggleRow);
        fireBox.add(randomIgnitionMode);
        manualIgnitionMode.setToolTipText("Choose start fires yourself by clicking cells");
        randomIgnitionMode.setToolTipText("Place the configured number of fires on random cells that have fuel");
        toggleIgnitionAction.setToolTipText("Clicking a cell adds or removes an ignition point; material is not changed. "
                + "Water and other fuel-free cells cannot be ignition points");
        gridActionGroup.add(paintMaterialAction);
        gridActionGroup.add(toggleIgnitionAction);
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
        windBox.add(new JLabel("Direction (0=N, 90=E, 180=S, 270=W)"));
        windBox.add(windDirectionSlider);
        windBox.add(new JLabel("Speed (mph, Beaufort scale)"));
        java.util.Hashtable<Integer, JLabel> mphTicks = new java.util.Hashtable<>();
        for (int mph : new int[] {0, 13, 25, 39, 55, 73}) {
            mphTicks.put(mph, new JLabel(String.valueOf(mph)));
        }
        windSpeedSlider.setLabelTable(mphTicks);
        windSpeedSlider.setPaintLabels(true);
        windSpeedSlider.setMinorTickSpacing(1);
        windSpeedSlider.setSnapToTicks(true);
        windBox.add(windSpeedSlider);
        windBox.add(beaufortLabel);
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
            "Total ignitions", "Simulation step", "% affected", "Status",
            "Simulated time", "Avg cell-to-cell"};
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
        legend.add(swatch(new Color(40, 0, 70), "Spread path, label = travel steps; star = start fire"));
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
        buttons.add(stopButton);
        buttons.add(resetButton);
        stopButton.setToolTipText("End the run now and keep the grid as it is (Reset to start again)");
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
            clickMode.setManualFuel(false);
            guarded(controller::randomizeFuel);
            refreshAll();
        });
        manualFuelMode.addActionListener(e -> {
            clickMode.setManualFuel(true); // explicitly switches the grid action to painting
            refreshAll();
        });
        manualIgnitionMode.addActionListener(e -> {
            clickMode.setManualIgnition(true); // explicitly switches the grid action to ignition toggling
            refreshAll();
        });
        randomIgnitionMode.addActionListener(e -> {
            clickMode.setManualIgnition(false);
            refreshAll();
        });
        // one listener each: select the action first, then redraw (Swing fires listeners in reverse order)
        paintMaterialAction.addActionListener(e -> {
            guarded(() -> clickMode.select(GridClickMode.Action.PAINT_MATERIAL));
            refreshAll();
        });
        toggleIgnitionAction.addActionListener(e -> {
            guarded(() -> clickMode.select(GridClickMode.Action.TOGGLE_IGNITION));
            refreshAll();
        });
        placeFiresButton.addActionListener(e -> guarded(
                () -> controller.randomizeIgnitions((Integer) fireCountSpinner.getValue())));
        clearFiresButton.addActionListener(e -> guarded(controller::clearIgnitions));

        windDirectionSlider.addChangeListener(e -> applyWindFromSliders());
        windSpeedSlider.addChangeListener(e -> applyWindFromSliders());
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
        stopButton.addActionListener(e -> {
            timer.stop();
            controller.stop();
        });
        resetButton.addActionListener(e -> {
            timer.stop();
            controller.reset();
        });

        gridPanel.setOnHover((r, c) -> inspect(r, c));
        gridPanel.setOnClick(this::handleClick);
    }

    /** Shows the active grid action from {@link GridClickMode}; the two action radios can never both be on. */
    private void syncGridActionControls(boolean idle) {
        manualFuelPanel.setVisible(clickMode.isManualFuel());
        materialCombo.setEnabled(idle && clickMode.isManualFuel());
        paintMaterialAction.setEnabled(idle && clickMode.isManualFuel());
        toggleIgnitionAction.setEnabled(idle && clickMode.isManualIgnition());
        switch (clickMode.getAction()) {
            case PAINT_MATERIAL -> paintMaterialAction.setSelected(true);
            case TOGGLE_IGNITION -> toggleIgnitionAction.setSelected(true);
            default -> gridActionGroup.clearSelection();
        }
    }

    private void handleClick(int row, int column) {
        inspect(row, column);
        if (controller.getStatus() != SimulationController.Status.IDLE) {
            return;
        }
        guarded(() -> statusBar.setText(clickMode.apply(controller, row, column,
                (MaterialType) materialCombo.getSelectedItem())));
    }

    /** Called by the Swing timer on the EDT: one short step per tick, never a long loop. */
    private void onTick() {
        if (!controller.step() || controller.isFinished()) {
            timer.stop();
        }
    }

    private void applyWindFromSliders() {
        controller.getWind().setDirectionDegrees(windDirectionSlider.getValue());
        Wind wind = controller.getWind();
        wind.setSpeedMph(windSpeedSlider.getValue());
        BeaufortScale b = wind.getBeaufort();
        windLabel.setText(String.format("%s (%d deg) at %d mph", wind.compassName(),
                windDirectionSlider.getValue(), windSpeedSlider.getValue()));
        beaufortLabel.setText(String.format("<html><b>Force %d - %s</b> (%s, %s)<br><small>%s<br>"
                + "Model strength %.2f = min(1, %d mph / %.0f mph)</small></html>",
                b.getForce(), b.getLabel(), b.getMphRange(), b.getKnotRange(), b.getDescription(),
                wind.getStrength(), windSpeedSlider.getValue(), Wind.FULL_EFFECT_MPH));
        if (gridPanel != null) {
            gridPanel.repaint(); // signal arrows and P% depend on the wind
            updateCellInfo();
        }
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
            sb.append(String.format("Ignited at step %d (t = %s) %s%n", cell.getIgnitionTime(),
                    cfg.formatSteps(cell.getIgnitionTime()),
                    by == null ? "- start fire" : "- passed on by " + by.getId()));
            if (by != null) {
                int travel = cell.getTravelSteps();
                double align = cell.getIgnitionWindAlignment();
                sb.append(String.format("%nFIRE TRAVEL %s -> %s (%s)%n", by.getId(), cell.getId(),
                        IgnitionBreakdown.directionName(cell.getRow() - by.getRow(), cell.getColumn() - by.getColumn())));
                sb.append(String.format("  %s lit at step %d, %s lit at step %d%n", by.getId(), by.getIgnitionTime(),
                        cell.getId(), cell.getIgnitionTime()));
                sb.append(String.format("  travel time = %d step(s) = %s%n", travel, cfg.formatSteps(travel)));
                sb.append(String.format("  into %s (fuel %d), from %s (fuel %d)%n", cell.getMaterialType().getLabel(),
                        cell.getFuelValue(), by.getMaterialType().getLabel(), by.getFuelValue()));
                sb.append(String.format("  wind: %s, cos %+.2f%n",
                        SpreadTimingReport.WindRelation.of(align).getLabel(), align));
            }
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
        sb.append(String.format("%nEXPECTED TIME FOR FIRE TO REACH THIS CELL%n"));
        sb.append(String.format("  (if neighbours kept burning as now: about 1 / P steps)%n"));
        if (b.getProbability() > 0) {
            double wait = 1.0 / b.getProbability();
            sb.append(String.format("  all signals together: 1 / %.4f = %.1f steps (%s)%n",
                    b.getProbability(), wait, cfg.formatSteps(wait)));
        }
        for (IgnitionBreakdown.Signal s : b.getSignals()) {
            double alone = s.getContribution() * b.getFuelFactor();
            sb.append(alone > 0
                    ? String.format("  from %-5s alone: %.1f steps (%s)%n", s.getSource().getId(), 1.0 / alone,
                            cfg.formatSteps(1.0 / alone))
                    : String.format("  from %-5s alone: never (signal 0)%n", s.getSource().getId()));
        }
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
        SimulationConfig cfg = controller.getConfig();
        statLabels[8].setText(cfg.formatSteps(s.getStep()));
        SpreadTimingReport timing = SpreadTimingReport.of(controller.getGrid(),
                controller.getWind().getSpeedMph() < 1.0);
        statLabels[9].setText(timing.getOverall().getCount() == 0 ? "-"
                : String.format("%.2f steps (%s)", timing.getOverall().getAverage(),
                        cfg.formatSteps(timing.getOverall().getAverage())));
        timingReport.setText(timing.format(cfg, controller.getWind()));
        timingReport.setCaretPosition(0);

        boolean idle = status == SimulationController.Status.IDLE;
        boolean hasFires = !controller.getIgnitionCells().isEmpty();
        generateButton.setEnabled(idle);
        rowsSpinner.setEnabled(idle);
        columnsSpinner.setEnabled(idle);
        randomFuelMode.setEnabled(idle);
        manualFuelMode.setEnabled(idle);
        rerollFuelButton.setEnabled(idle && randomFuelMode.isSelected());
        syncGridActionControls(idle);
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
        stopButton.setEnabled(status == SimulationController.Status.RUNNING
                || status == SimulationController.Status.PAUSED);
        resetButton.setEnabled(status != SimulationController.Status.IDLE);

        switch (status) {
            case IDLE -> statusBar.setText(hasFires
                    ? "Ready. Press Start or Step."
                    : "Choose ignition points (click cells, or use random placement), then press Start.");
            case RUNNING -> statusBar.setText("Running...");
            case PAUSED -> statusBar.setText("Paused.");
            case FINISHED -> statusBar.setText(controller.isStoppedByUser()
                    ? String.format("Stopped by user at step %d (t = %s): %d cell(s) still burning, %.1f%% of the grid "
                            + "affected. Press Reset to start again.", s.getStep(),
                            controller.getConfig().formatSteps(s.getStep()), s.getBurning(), s.getPercentAffected())
                    : String.format("Finished at step %d: no cells burning. %.1f%% of the grid was affected. %s",
                            s.getStep(), s.getPercentAffected(), outcomeDescription(s)));
        }
        gridPanel.repaint();
        updateCellInfo();
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


    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            SimulationController controller = new SimulationController(
                    10, 10, new SimulationConfig(), System.nanoTime());
            new SimulationGUI(controller).setVisible(true);
        });
    }
}
