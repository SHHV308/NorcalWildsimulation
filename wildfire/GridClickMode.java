package wildfire;

/**
 * Decides what a click on a grid cell does during setup. Free of Swing so it can be tested.
 *
 * <p>Exactly one action is active at a time, so material painting and ignition toggling can
 * never both happen:
 * <ul>
 *   <li>{@link Action#PAINT_MATERIAL} - only while fuel assignment is Manual; changes the cell's
 *       vegetation/material and nothing else.</li>
 *   <li>{@link Action#TOGGLE_IGNITION} - only while initial fires are Manual; adds or removes an
 *       ignition point (cells without fuel are rejected by the controller).</li>
 *   <li>{@link Action#NONE} - neither manual mode is on; clicks only inspect the cell.</li>
 * </ul>
 * Turning a manual mode on switches the active action to that mode's action. Turning it off
 * switches to the other manual action if that one is available, otherwise to NONE.
 */
public final class GridClickMode {

    public enum Action {
        NONE("Inspect only"),
        PAINT_MATERIAL("Paint material"),
        TOGGLE_IGNITION("Toggle ignition point");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private boolean manualFuel;
    private boolean manualIgnition;
    private Action action = Action.NONE;

    /** Starts with Random fuel and Manual initial fires, so clicks toggle ignition points. */
    public GridClickMode() {
        setManualIgnition(true);
    }

    public boolean isManualFuel() {
        return manualFuel;
    }

    public boolean isManualIgnition() {
        return manualIgnition;
    }

    public Action getAction() {
        return action;
    }

    /** Fuel assignment switched to Manual (true) or Random (false). */
    public void setManualFuel(boolean manual) {
        manualFuel = manual;
        if (manual) {
            action = Action.PAINT_MATERIAL;
        } else if (action == Action.PAINT_MATERIAL) {
            action = manualIgnition ? Action.TOGGLE_IGNITION : Action.NONE;
        }
    }

    /** Initial fires switched to Manual click cells (true) or Random placement (false). */
    public void setManualIgnition(boolean manual) {
        manualIgnition = manual;
        if (manual) {
            action = Action.TOGGLE_IGNITION;
        } else if (action == Action.TOGGLE_IGNITION) {
            action = manualFuel ? Action.PAINT_MATERIAL : Action.NONE;
        }
    }

    /**
     * Explicitly chooses the grid action.
     *
     * @throws IllegalArgumentException if the action's manual mode is not selected
     */
    public void select(Action requested) {
        if (requested == Action.PAINT_MATERIAL && !manualFuel) {
            throw new IllegalArgumentException("Select Manual fuel assignment before painting material.");
        }
        if (requested == Action.TOGGLE_IGNITION && !manualIgnition) {
            throw new IllegalArgumentException("Select 'Manual (click cells)' before toggling ignition points.");
        }
        action = requested;
    }

    /**
     * Applies the active action to one clicked cell and returns a status message.
     *
     * @param material material to paint (used only by PAINT_MATERIAL)
     * @throws IllegalArgumentException if an ignition point is placed on a cell with no fuel
     * @throws IllegalStateException    if the simulation is not idle
     */
    public String apply(SimulationController controller, int row, int column, MaterialType material) {
        Cell cell = controller.getGrid().getCell(row, column);
        switch (action) {
            case PAINT_MATERIAL: {
                boolean wasIgnition = controller.getIgnitionCells().contains(cell);
                controller.paintCell(row, column, material);
                Cell painted = controller.getGrid().getCell(row, column);
                String dropped = wasIgnition && !controller.getIgnitionCells().contains(painted)
                        ? " Its ignition point was removed because it has no fuel." : "";
                return String.format("Painted %s as %s (fuel %d).%s", painted.getId(), material.getLabel(),
                        painted.getFuelValue(), dropped);
            }
            case TOGGLE_IGNITION:
                if (controller.getIgnitionCells().contains(cell)) {
                    controller.removeIgnition(row, column);
                    return "Removed ignition point " + cell.getId() + ".";
                }
                controller.addIgnition(row, column);
                return "Added ignition point " + cell.getId() + ".";
            default:
                return "Clicks only inspect cells. Choose Manual fuel (paint material) or "
                        + "Manual initial fires (toggle ignition points) to edit the grid.";
        }
    }
}
