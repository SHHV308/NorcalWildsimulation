public final class FireGridMapper {

    private static final double METERS_PER_DEGREE = 111_320.0;

    private FireGridMapper() {
    }

    public static int[] toCell(
            GeoLocation center,
            ObservedFire fire,
            int rows,
            int columns,
            double cellSizeMeters) {
        if (center == null || fire == null || fire.getLatitude() == null
                || fire.getLongitude() == null || rows <= 0 || columns <= 0
                || !Double.isFinite(cellSizeMeters) || cellSizeMeters <= 0.0) {
            return null;
        }

        double latitude = fire.getLatitude();
        double longitude = fire.getLongitude();
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0
                || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            return null;
        }

        double northMeters = (latitude - center.getLatitude()) * METERS_PER_DEGREE;
        double eastMeters = (longitude - center.getLongitude()) * METERS_PER_DEGREE
                * Math.cos(Math.toRadians(center.getLatitude()));
        double halfHeight = rows * cellSizeMeters / 2.0;
        double halfWidth = columns * cellSizeMeters / 2.0;
        if (northMeters < -halfHeight || northMeters >= halfHeight
                || eastMeters < -halfWidth || eastMeters >= halfWidth) {
            return null;
        }

        int row = (int) Math.floor((halfHeight - northMeters) / cellSizeMeters);
        int column = (int) Math.floor((eastMeters + halfWidth) / cellSizeMeters);
        return new int[] { row, column };
    }
}
