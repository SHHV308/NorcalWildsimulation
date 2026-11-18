package wildfire;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import javax.swing.JPanel;

/**
 * Draws the grid and reports hover/click cell coordinates. Contains no simulation logic: every
 * number in the overlays comes from the controller (and so from {@link FireSpreadEngine}).
 *
 * <p>Communication overlays:
 * <ul>
 *   <li><b>Signals</b> - an arrow from every burning cell to every neighbour that can still ignite.
 *       Arrow width and opacity grow with the signal's contribution (its stand-alone chance of
 *       igniting that neighbour next step).</li>
 *   <li><b>Probabilities</b> - each cell that could ignite next step shows its combined P(ignite).</li>
 *   <li><b>Spread path</b> - a line from each burned or burning cell back to the neighbour that
 *       passed the fire on (strongest contributor when it ignited); start fires get a star.</li>
 *   <li><b>Focus</b> - the hovered/selected cell's own signals are labelled with exact
 *       contributions; all other arrows are faded.</li>
 * </ul>
 */
public class GridPanel extends JPanel {

    private static final Color SIGNAL = new Color(20, 80, 230);
    private static final Color SIGNAL_OUTLINE = new Color(255, 255, 255, 190);
    private static final Color PATH = new Color(40, 0, 70);
    private static final Color NEW_IGNITION = new Color(255, 255, 255);
    /** Below this cell size (px) only the hovered cell's signals are drawn, to keep big grids readable. */
    private static final int MIN_CELL_FOR_ALL_ARROWS = 14;

    private final SimulationController controller;
    private BiConsumer<Integer, Integer> onHover = (r, c) -> { };
    private BiConsumer<Integer, Integer> onClick = (r, c) -> { };
    private int cellSize = 12;
    private int selectedRow = -1;
    private int selectedColumn = -1;
    private int focusRow = -1;
    private int focusColumn = -1;

    private boolean showSignals = true;
    private boolean showProbabilities = true;
    private boolean showSpreadPath = false;

    public GridPanel(SimulationController controller) {
        this.controller = controller;
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int[] rc = locate(e);
                if (rc != null) {
                    setFocus(rc[0], rc[1]);
                    onHover.accept(rc[0], rc[1]);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setFocus(selectedRow, selectedColumn);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int[] rc = locate(e);
                if (rc != null) {
                    selectedRow = rc[0];
                    selectedColumn = rc[1];
                    setFocus(rc[0], rc[1]);
                    onClick.accept(rc[0], rc[1]);
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int[] rc = locate(e);
                if (rc != null) {
                    setFocus(rc[0], rc[1]);
                    onHover.accept(rc[0], rc[1]);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        refreshSize();
    }

    public void setOnHover(BiConsumer<Integer, Integer> handler) {
        this.onHover = handler;
    }

    public void setOnClick(BiConsumer<Integer, Integer> handler) {
        this.onClick = handler;
    }

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedColumn() {
        return selectedColumn;
    }

    public void setShowSignals(boolean show) {
        showSignals = show;
        repaint();
    }

    public void setShowProbabilities(boolean show) {
        showProbabilities = show;
        repaint();
    }

    public void setShowSpreadPath(boolean show) {
        showSpreadPath = show;
        repaint();
    }

    private void setFocus(int row, int column) {
        if (row != focusRow || column != focusColumn) {
            focusRow = row;
            focusColumn = column;
            repaint();
        }
    }

    /** Recomputes the cell size after the grid dimensions changed. */
    public void refreshSize() {
        Grid grid = controller.getGrid();
        // fit an n x m grid into roughly 680 x 560 px; non-square grids use the room on the longer side
        cellSize = Math.max(4, Math.min(56, Math.min(680 / grid.getColumns(), 560 / grid.getRows())));
        if (selectedRow >= grid.getRows() || selectedColumn >= grid.getColumns()) {
            selectedRow = -1;
            selectedColumn = -1;
        }
        focusRow = selectedRow;
        focusColumn = selectedColumn;
        setPreferredSize(new Dimension(grid.getColumns() * cellSize, grid.getRows() * cellSize));
        revalidate();
        repaint();
    }

    private int[] locate(MouseEvent e) {
        Grid grid = controller.getGrid();
        int c = e.getX() / cellSize;
        int r = e.getY() / cellSize;
        return grid.inBounds(r, c) ? new int[] {r, c} : null;
    }

    // ----- painting -----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Grid grid = controller.getGrid();
        boolean idle = controller.getStatus() == SimulationController.Status.IDLE;

        paintCells(g2, grid);
        paintNewIgnitionRings(g2, grid);
        if (showSpreadPath) {
            paintSpreadPath(g2, grid);
        }
        if (showSignals) {
            paintSignals(g2, grid);
        }
        if (showProbabilities) {
            paintProbabilities(g2, grid);
        }
        if (idle) {
            paintIgnitionPoints(g2);
        }
        paintSelection(g2, grid);
    }

    private void paintCells(Graphics2D g2, Grid grid) {
        for (int r = 0; r < grid.getRows(); r++) {
            for (int c = 0; c < grid.getColumns(); c++) {
                Cell cell = grid.getCell(r, c);
                g2.setColor(colorFor(cell));
                g2.fillRect(c * cellSize, r * cellSize, cellSize, cellSize);
                if (cellSize >= 8) {
                    g2.setColor(new Color(0, 0, 0, 40));
                    g2.drawRect(c * cellSize, r * cellSize, cellSize, cellSize);
                }
                if (cell.isBurning() && cellSize >= 40) {
                    // remaining-fuel bar along the bottom edge of a burning cell
                    double fraction = cell.getRemainingFuel() / Math.max(1, cell.getFuelValue());
                    int x = c * cellSize + 3;
                    int y = (r + 1) * cellSize - 6;
                    int w = cellSize - 6;
                    g2.setColor(new Color(0, 0, 0, 120));
                    g2.fillRect(x, y, w, 3);
                    g2.setColor(new Color(255, 255, 255, 220));
                    g2.fillRect(x, y, (int) Math.round(w * fraction), 3);
                }
            }
        }
    }

    /** White ring on cells that caught fire in the most recent step. */
    private void paintNewIgnitionRings(Graphics2D g2, Grid grid) {
        StepTrace trace = controller.getLastTrace();
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(cellSize >= 20 ? 2f : 1f));
        g2.setColor(NEW_IGNITION);
        for (StepTrace.Evaluation e : trace.getEvaluations()) {
            if (e.isIgnited()) {
                Cell cell = e.getTarget();
                int inset = Math.max(2, cellSize / 8);
                g2.drawOval(cell.getColumn() * cellSize + inset, cell.getRow() * cellSize + inset,
                        cellSize - 2 * inset, cellSize - 2 * inset);
            }
        }
        g2.setStroke(old);
    }

    /** Who-ignited-whom tree: arrow from the passing neighbour to each ignited cell. */
    private void paintSpreadPath(Graphics2D g2, Grid grid) {
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(Math.max(1.5f, cellSize / 22f)));
        for (Cell cell : grid.allCells()) {
            if (cell.getIgnitionTime() < 0) {
                continue;
            }
            Cell source = cell.getIgnitedBy();
            if (source == null) {
                drawStar(g2, cell);
            } else {
                g2.setColor(PATH);
                drawArrow(g2, source, cell, Math.max(1.5f, cellSize / 22f), PATH, null);
            }
        }
        g2.setStroke(old);
    }

    /** Live signals: what each burning cell sends to each ignitable neighbour at the next step. */
    private void paintSignals(Graphics2D g2, Grid grid) {
        Cell focus = grid.inBounds(focusRow, focusColumn) ? grid.getCell(focusRow, focusColumn) : null;
        NeighborManager neighbors = controller.getNeighborManager();
        java.util.List<IgnitionBreakdown.Signal> focused = new java.util.ArrayList<>();

        for (Cell source : grid.allCells()) {
            if (!source.isBurning()) {
                continue;
            }
            for (Cell target : neighbors.neighborsOf(source)) {
                if (!target.canIgnite()) {
                    continue;
                }
                boolean isFocused = focus != null && (focus == source || focus == target);
                if (!isFocused && cellSize < MIN_CELL_FOR_ALL_ARROWS) {
                    continue; // tiny cells on a large n x m grid: only the hovered cell's arrows
                }
                IgnitionBreakdown.Signal signal = controller.signalBetween(source, target);
                if (isFocused) {
                    focused.add(signal);
                    continue; // drawn on top afterwards
                }
                int alpha = focus == null ? (int) (120 + 135 * signal.getContribution()) : 60;
                drawSignal(g2, signal, alpha, false);
            }
        }
        for (IgnitionBreakdown.Signal signal : focused) {
            drawSignal(g2, signal, 255, true);
        }
    }

    private void drawSignal(Graphics2D g2, IgnitionBreakdown.Signal signal, int alpha, boolean label) {
        float width = (float) (1.6 + 7.0 * signal.getContribution()) * Math.max(0.35f, cellSize / 56f);
        Color color = new Color(SIGNAL.getRed(), SIGNAL.getGreen(), SIGNAL.getBlue(), Math.min(255, alpha));
        String text = label && cellSize >= 28 ? String.format("%.2f", signal.getContribution()) : null;
        drawArrow(g2, signal.getSource(), signal.getTarget(), width, color, text);
    }

    /** Combined P(ignite) at the next step for each cell that has at least one burning neighbour. */
    private void paintProbabilities(Graphics2D g2, Grid grid) {
        if (cellSize < 24) {
            return;
        }
        Font font = getFont().deriveFont(Font.BOLD, Math.max(9f, cellSize / 4.6f));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        for (Cell cell : grid.allCells()) {
            if (!cell.canIgnite()) {
                continue;
            }
            double p = controller.explainIgnition(cell).getProbability();
            if (p <= 0.0) {
                continue;
            }
            String text = String.format("%.0f%%", p * 100);
            int tw = fm.stringWidth(text);
            int cx = cell.getColumn() * cellSize + cellSize / 2;
            int cy = cell.getRow() * cellSize + cellSize / 2;
            g2.setColor(new Color(255, 255, 255, 215));
            g2.fillRoundRect(cx - tw / 2 - 3, cy - fm.getAscent() / 2 - 2, tw + 6, fm.getAscent() + 4, 6, 6);
            g2.setColor(new Color(15, 15, 15));
            g2.drawString(text, cx - tw / 2, cy + fm.getAscent() / 2 - 1);
        }
    }

    private void paintIgnitionPoints(Graphics2D g2) {
        g2.setColor(Color.MAGENTA);
        for (Cell cell : controller.getIgnitionCells()) {
            g2.drawRect(cell.getColumn() * cellSize + 1, cell.getRow() * cellSize + 1,
                    cellSize - 3, cellSize - 3);
            g2.drawLine(cell.getColumn() * cellSize + 2, cell.getRow() * cellSize + 2,
                    (cell.getColumn() + 1) * cellSize - 3, (cell.getRow() + 1) * cellSize - 3);
        }
    }

    private void paintSelection(Graphics2D g2, Grid grid) {
        Stroke old = g2.getStroke();
        if (grid.inBounds(focusRow, focusColumn) && (focusRow != selectedRow || focusColumn != selectedColumn)) {
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] {4f, 3f}, 0f));
            g2.setColor(Color.BLACK);
            g2.drawRect(focusColumn * cellSize + 1, focusRow * cellSize + 1, cellSize - 3, cellSize - 3);
        }
        g2.setStroke(old);
        if (selectedRow >= 0) {
            g2.setColor(Color.WHITE);
            g2.drawRect(selectedColumn * cellSize, selectedRow * cellSize, cellSize - 1, cellSize - 1);
            g2.setColor(Color.BLACK);
            g2.drawRect(selectedColumn * cellSize + 1, selectedRow * cellSize + 1,
                    cellSize - 3, cellSize - 3);
        }
    }

    // ----- drawing helpers -----

    /** Arrow between two adjacent cell centres, shortened so it does not cover the centres. */
    private void drawArrow(Graphics2D g2, Cell from, Cell to, float width, Color color, String label) {
        double x1 = from.getColumn() * cellSize + cellSize / 2.0;
        double y1 = from.getRow() * cellSize + cellSize / 2.0;
        double x2 = to.getColumn() * cellSize + cellSize / 2.0;
        double y2 = to.getRow() * cellSize + cellSize / 2.0;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        double ux = dx / length;
        double uy = dy / length;
        double startGap = cellSize * 0.16;
        double endGap = cellSize * 0.27;
        double sx = x1 + ux * startGap;
        double sy = y1 + uy * startGap;
        double ex = x2 - ux * endGap;
        double ey = y2 - uy * endGap;
        double head = Math.max(3.0, Math.min(cellSize * 0.35, width * 2.4 + 2.0));
        double baseX = ex - ux * head;
        double baseY = ey - uy * head;

        Stroke old = g2.getStroke();
        if (color.getAlpha() > 150) {
            g2.setStroke(new BasicStroke(width + 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // light halo so arrows stay readable over red/dark cells
            g2.setColor(withAlpha(SIGNAL_OUTLINE, color.getAlpha() * 3 / 4));
            g2.drawLine((int) sx, (int) sy, (int) baseX, (int) baseY);
        }
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        g2.drawLine((int) Math.round(sx), (int) Math.round(sy), (int) Math.round(baseX), (int) Math.round(baseY));
        Polygon arrowHead = new Polygon();
        arrowHead.addPoint((int) Math.round(ex), (int) Math.round(ey));
        arrowHead.addPoint((int) Math.round(baseX - uy * head * 0.6), (int) Math.round(baseY + ux * head * 0.6));
        arrowHead.addPoint((int) Math.round(baseX + uy * head * 0.6), (int) Math.round(baseY - ux * head * 0.6));
        g2.fillPolygon(arrowHead);
        g2.setStroke(old);

        if (label != null) {
            Font font = getFont().deriveFont(Font.BOLD, Math.max(9f, cellSize / 5.5f));
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(label);
            // label sits 38% of the way from the source, so labels of converging arrows don't collide
            int mx = (int) Math.round(x1 + dx * 0.38);
            int my = (int) Math.round(y1 + dy * 0.38);
            g2.setColor(new Color(20, 80, 230));
            g2.fillRoundRect(mx - tw / 2 - 3, my - fm.getAscent() / 2 - 2, tw + 6, fm.getAscent() + 4, 6, 6);
            g2.setColor(Color.WHITE);
            g2.drawString(label, mx - tw / 2, my + fm.getAscent() / 2 - 1);
        }
    }

    private void drawStar(Graphics2D g2, Cell cell) {
        double cx = cell.getColumn() * cellSize + cellSize / 2.0;
        double cy = cell.getRow() * cellSize + cellSize / 2.0;
        double outer = cellSize * 0.22;
        double inner = outer * 0.45;
        Polygon star = new Polygon();
        for (int i = 0; i < 10; i++) {
            double radius = i % 2 == 0 ? outer : inner;
            double angle = Math.PI / 5 * i - Math.PI / 2;
            star.addPoint((int) Math.round(cx + Math.cos(angle) * radius),
                    (int) Math.round(cy + Math.sin(angle) * radius));
        }
        g2.setColor(Color.WHITE);
        g2.fillPolygon(star);
        g2.setColor(PATH);
        g2.drawPolygon(star);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /** Fire-state colour: green shades by material (unburned), yellow-to-red by intensity, grey ash. */
    static Color colorFor(Cell cell) {
        switch (cell.getState()) {
            case BURNING: {
                double t = Math.max(0.0, Math.min(1.0, cell.getFireIntensity()));
                // low intensity = yellow (255,220,0); high = deep red (190,0,0)
                int red = (int) (255 - 65 * t);
                int green = (int) (220 * (1.0 - t));
                return new Color(red, green, 0);
            }
            case BURNED_OUT:
                return new Color(55, 55, 55);
            default:
                return materialColor(cell.getMaterialType());
        }
    }

    static Color materialColor(MaterialType type) {
        switch (type) {
            case WATER:
                return new Color(64, 140, 220);
            case BARE_LAND:
                return new Color(196, 170, 125);
            case SPARSE_GRASS:
                return new Color(190, 220, 130);
            case DENSE_GRASS:
                return new Color(120, 190, 90);
            case SHRUBS:
                return new Color(80, 150, 80);
            default:
                return new Color(30, 100, 50);
        }
    }
}
