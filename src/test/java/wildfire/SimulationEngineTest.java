package wildfire;

public final class SimulationEngineTest {
    public static void main(String[] args) {
        testWaterCannotIgnite();
        testFuelIsConsumedAndDurationTracked();
        testNoFireMeansNoSpread();
        testProbabilityRange();
        testWindDirectionChangesInfluence();
        testMultipleBurningNeighborsIncreaseProbability();
        testFuelExhaustionEndsFireAndTracksDuration();
        testConfigurableRandomIgnitionCount();
        testNoReignitionAfterFuelExhausted();
        testResetRestoresGridAndSourceCount();
        System.out.println("All wildfire simulation tests passed.");
    }

    private static Grid smallGrid() { return new Grid(8, 8, 123L); }

    private static void testWaterCannotIgnite() {
        Cell water = new Cell(0, 0, "Water", 0);
        check(water.getState() == CellState.NON_BURNABLE, "zero fuel must be non-burnable");
        water.ignite(0, 5);
        check(water.getState() == CellState.NON_BURNABLE, "water must not ignite");
    }

    private static void testFuelIsConsumedAndDurationTracked() {
        Cell cell = new Cell(0, 0, "Grass", 4);
        cell.ignite(0, 5);
        cell.burnOneStep();
        check(cell.getRemainingFuel() < 4, "fuel should decrease while burning");
        check(cell.getBurningSteps() == 1, "burn duration should count burning steps");
    }

    private static void testNoFireMeansNoSpread() {
        Grid grid = smallGrid();
        FireSpreadEngine engine = new FireSpreadEngine(grid, Wind.calm(), 7);
        for (int r = 0; r < grid.getRows(); r++)
            for (int c = 0; c < grid.getCols(); c++)
                check(engine.calculateIgnitionProbability(r, c) == 0, "no source fire means no spread");
    }

    private static void testProbabilityRange() {
        Grid grid = smallGrid();
        FireSpreadEngine engine = new FireSpreadEngine(grid, new Wind(20, 90), 9);
        engine.ignite(3, 3);
        for (int r = 0; r < grid.getRows(); r++)
            for (int c = 0; c < grid.getCols(); c++) {
                double p = engine.calculateIgnitionProbability(r, c);
                check(p >= 0 && p <= 1, "probability must be within [0,1]");
            }
    }

    private static void testWindDirectionChangesInfluence() {
        Grid grid = smallGrid();
        FireSpreadEngine eastWind = new FireSpreadEngine(grid, new Wind(30, 90), 15);
        eastWind.ignite(3, 3);
        double eastTarget = eastWind.calculateIgnitionProbability(3, 4);
        double westTarget = eastWind.calculateIgnitionProbability(3, 2);
        check(eastTarget > westTarget, "wind toward east should favor eastward neighbor over westward neighbor");
    }

    private static void testConfigurableRandomIgnitionCount() {
        Grid grid = smallGrid();
        FireSpreadEngine engine = new FireSpreadEngine(grid, Wind.calm(), 22);
        engine.igniteRandom(5);
        check(engine.getInitialIgnitionCount() == 5, "requested initial source count should be tracked");
        check(grid.count(CellState.BURNING) == 5, "random ignition should place exactly requested count");
    }

    private static void testNoReignitionAfterFuelExhausted() {
        Cell cell = new Cell(0, 0, "Dry fuel", 0.5);
        cell.ignite(0, 5);
        cell.burnOneStep();
        check(cell.getState() == CellState.BURNED, "exhausted cell should be burned out");
        cell.ignite(2, 5);
        check(cell.getState() == CellState.BURNED, "burned-out cell must not reignite");
    }

    private static void testResetRestoresGridAndSourceCount() {
        Grid grid = smallGrid();
        FireSpreadEngine engine = new FireSpreadEngine(grid, Wind.calm(), 11);
        engine.ignite(2, 2);
        engine.advanceOneStep();
        engine.reset();
        check(engine.getStep() == 0, "reset should reset step count");
        check(engine.getInitialIgnitionCount() == 0, "reset should clear initial ignition count");
        check(grid.count(CellState.BURNING) == 0, "reset should clear active fires");
    }


    private static void testMultipleBurningNeighborsIncreaseProbability() {
        Grid oneSourceGrid = smallGrid();
        Grid twoSourceGrid = smallGrid();
        int targetRow = -1, targetCol = -1;
        int firstRow = -1, firstCol = -1, secondRow = -1, secondCol = -1;
        outer: for (int r = 1; r < 7; r++) {
            for (int c = 1; c < 7; c++) {
                if (oneSourceGrid.getCell(r, c).getRemainingFuel() <= 0) continue;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        int nr = r + dr, nc = c + dc;
                        if (oneSourceGrid.getCell(nr, nc).getRemainingFuel() <= 0) continue;
                        if (firstRow < 0) { firstRow = nr; firstCol = nc; }
                        else { targetRow = r; targetCol = c; secondRow = nr; secondCol = nc; break outer; }
                    }
                }
                firstRow = firstCol = -1;
            }
        }
        check(targetRow >= 0, "test grid should contain a burnable target with two burnable neighbors");
        FireSpreadEngine oneSource = new FireSpreadEngine(oneSourceGrid, Wind.calm(), 31);
        FireSpreadEngine twoSources = new FireSpreadEngine(twoSourceGrid, Wind.calm(), 31);
        oneSource.ignite(firstRow, firstCol);
        twoSources.ignite(firstRow, firstCol);
        twoSources.ignite(secondRow, secondCol);
        double pOne = oneSource.calculateIgnitionProbability(targetRow, targetCol);
        double pTwo = twoSources.calculateIgnitionProbability(targetRow, targetCol);
        check(pTwo > pOne, "multiple burning neighbors should increase aggregated ignition probability");
    }

    private static void testFuelExhaustionEndsFireAndTracksDuration() {
        Cell cell = new Cell(0, 0, "Dry leaves", 1.6);
        cell.ignite(0, 5);
        cell.burnOneStep();
        check(cell.getState() == CellState.BURNING, "cell should still burn while fuel remains");
        cell.burnOneStep();
        check(cell.getState() == CellState.BURNED, "fire should stop when fuel is exhausted");
        check(cell.getRemainingFuel() == 0, "remaining fuel should reach zero");
        check(cell.getBurningSteps() == 2, "duration should count both burning steps");
        check(cell.getIntensity() == 0, "burned-out cell should have zero intensity");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
