public final class GridCellData {

    private final int row;
    private final int column;
    private final int vegetationCode;
    private final int fuelModel;

    public GridCellData(int row, int column, int vegetationCode, int fuelModel) {
        if (row < 0 || column < 0) {
            throw new IllegalArgumentException("CSV cell coordinates must be non-negative.");
        }
        this.row = row;
        this.column = column;
        this.vegetationCode = vegetationCode;
        this.fuelModel = fuelModel;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public int getVegetationCode() {
        return vegetationCode;
    }

    public int getFuelModel() {
        return fuelModel;
    }
}
