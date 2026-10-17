public final class FireCell {

    private final int row;
    private final int col;
    private final int vegetationCode;
    private final int fuelModel;
    private final boolean burnable;

    private FireState state;
    private double ignitionProbability;
    private double firelineIntensityKwPerMeter;
    private double spreadRateMetersPerSecond;
    private double ignitionTimeSeconds = Double.NaN;
    private double predictedArrivalTimeSeconds = Double.POSITIVE_INFINITY;

    FireCell(int row, int col, int vegetationCode, int fuelModel, boolean burnable) {
        this.row = row;
        this.col = col;
        this.vegetationCode = vegetationCode;
        this.fuelModel = fuelModel;
        this.burnable = burnable;
        reset();
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public int getVegetationCode() {
        return vegetationCode;
    }

    public int getFuelModel() {
        return fuelModel;
    }

    public FireState getState() {
        return state;
    }

    public double getIgnitionProbability() {
        return ignitionProbability;
    }

    public double getFirelineIntensityKwPerMeter() {
        return firelineIntensityKwPerMeter;
    }

    public double getSpreadRateMetersPerSecond() {
        return spreadRateMetersPerSecond;
    }

    public double getIgnitionTimeSeconds() {
        return ignitionTimeSeconds;
    }

    public double getPredictedArrivalTimeSeconds() {
        return predictedArrivalTimeSeconds;
    }

    void setIgnitionProbability(double ignitionProbability) {
        this.ignitionProbability = ignitionProbability;
    }

    void setPredictedArrivalTimeSeconds(double predictedArrivalTimeSeconds) {
        this.predictedArrivalTimeSeconds = Math.min(
                this.predictedArrivalTimeSeconds,
                predictedArrivalTimeSeconds);
    }

    void ignite(double timeSeconds, double spreadRate, double intensity) {
        state = FireState.BURNING;
        ignitionTimeSeconds = timeSeconds;
        predictedArrivalTimeSeconds = timeSeconds;
        spreadRateMetersPerSecond = spreadRate;
        firelineIntensityKwPerMeter = intensity;
    }

    void markBurned() {
        state = FireState.BURNED;
    }

    void reset() {
        state = burnable ? FireState.UNBURNED : FireState.NON_BURNABLE;
        ignitionProbability = 0.0;
        firelineIntensityKwPerMeter = 0.0;
        spreadRateMetersPerSecond = 0.0;
        ignitionTimeSeconds = Double.NaN;
        predictedArrivalTimeSeconds = Double.POSITIVE_INFINITY;
    }
}
