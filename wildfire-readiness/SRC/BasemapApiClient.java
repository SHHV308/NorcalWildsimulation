import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class BasemapApiClient {

    private static final String EXPORT_URL =
            "https://services.arcgisonline.com/ArcGIS/rest/services/"
                    + "World_Imagery/MapServer/export";
    private final HttpClient httpClient;

    public BasemapApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    BasemapApiClient(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HTTP client is required.");
        }
        this.httpClient = httpClient;
    }

    public BufferedImage fetch(
            GeoLocation center,
            int rows,
            int columns,
            double cellSizeMeters,
            int imageWidth,
            int imageHeight) throws IOException, InterruptedException {
        if (center == null || rows <= 0 || columns <= 0
                || !Double.isFinite(cellSizeMeters) || cellSizeMeters <= 0.0
                || imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Basemap extent and image size must be valid.");
        }
        return fetchForExtent(
                center,
                columns * cellSizeMeters,
                rows * cellSizeMeters,
                imageWidth,
                imageHeight);
    }

    public BufferedImage fetchForExtent(
            GeoLocation center,
            double extentWidthMeters,
            double extentHeightMeters,
            int imageWidth,
            int imageHeight) throws IOException, InterruptedException {
        if (center == null || !Double.isFinite(extentWidthMeters) || extentWidthMeters <= 0.0
                || !Double.isFinite(extentHeightMeters) || extentHeightMeters <= 0.0
                || imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Basemap extent and image size must be valid.");
        }
        double halfHeight = extentHeightMeters / 2.0;
        double halfWidth = extentWidthMeters / 2.0;
        double latitudeSpan = halfHeight / 111_320.0;
        double longitudeScale = 111_320.0 * Math.cos(Math.toRadians(center.getLatitude()));
        if (longitudeScale <= 0.0) {
            throw new IllegalArgumentException("Basemap longitude scale is invalid.");
        }
        double longitudeSpan = halfWidth / longitudeScale;
        String bbox = String.format(
                Locale.ROOT,
                "%.6f,%.6f,%.6f,%.6f",
                center.getLongitude() - longitudeSpan,
                center.getLatitude() - latitudeSpan,
                center.getLongitude() + longitudeSpan,
                center.getLatitude() + latitudeSpan);
        String query = "?bbox=" + bbox
                + "&bboxSR=4326&imageSR=4326&size=" + imageWidth + "," + imageHeight
                + "&format=jpg&f=image";
        HttpRequest request = HttpRequest.newBuilder(URI.create(EXPORT_URL + query))
                .timeout(java.time.Duration.ofSeconds(20))
                .header("User-Agent", "CALSAFE-Wildfire-Readiness/1.0")
                .header("Accept", "image/jpeg,image/png")
                .GET()
                .build();
        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Satellite basemap returned HTTP "
                    + response.statusCode() + ".");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null) {
            throw new IOException("Satellite basemap returned an unreadable image.");
        }
        return image;
    }
}
