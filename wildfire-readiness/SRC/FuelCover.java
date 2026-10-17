public final class FuelCover {

    private FuelCover() {
    }

    public static String descriptionForModel(int fuelModel) {
        if (fuelModel >= 101 && fuelModel <= 109) {
            return "Grass / fine fuels";
        }
        if ((fuelModel >= 121 && fuelModel <= 124)
                || (fuelModel >= 141 && fuelModel <= 149)) {
            return "Shrub / brush";
        }
        if ((fuelModel >= 161 && fuelModel <= 165)
                || (fuelModel >= 181 && fuelModel <= 189)) {
            return "Timber / woody fuels";
        }
        return "No burnable cover classified";
    }

    public static String moistureDescription() {
        return "Not provided in sample grid";
    }
}
