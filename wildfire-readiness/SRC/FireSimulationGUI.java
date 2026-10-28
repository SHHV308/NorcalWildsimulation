import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingWorker;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

public class FireSimulationGUI extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int DISPLAY_GRID_ROWS = 6;
    private static final int DISPLAY_GRID_COLUMNS = 6;
    private static final Color PANEL = new Color(247, 249, 251);
    private static final Color TEXT = new Color(40, 51, 62);
    private static final Color FOREST = new Color(25, 65, 55);
    private static final Color GREEN = new Color(42, 112, 83);
    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final FireSimulation simulation;
    private final FireObservationMonitor fireObservationMonitor;
    private WeatherMonitor weatherMonitor;
    private Wind forecastWind;
    private final JLabel weatherSummary = new JLabel("Waiting for station observation...");
    private final JLabel weatherTimestamp = new JLabel("NWS observation timestamp: pending");
    private final JLabel cellStateLabel = new JLabel("Cell state: --");
    private final JLabel observationSummary = new JLabel("Loading public incident feeds...");
    private final JLabel weatherSpeedValue = metricValue("--", "mph");
    private final JLabel weatherDirectionValue = metricValue("--", "\u00b0");
    private final JLabel weatherTemperatureValue = metricValue("--", "\u00b0F");
    private final JLabel weatherHumidityValue = metricValue("--", "%");
    private final JLabel weatherUpdatedValue = new JLabel("Last updated: --");
    private final JLabel fireStateValue = metricValue("Waiting for reports", "");
    private final JLabel reportedFireValue = metricValue("--", "");
    private final JLabel selectedEtaValue = metricValue("--", "");
    private final CompassPanel compassPanel = new CompassPanel();
    private final JLabel selectedCoverValue = new JLabel("--");
    private final JLabel selectedMoistureValue =
            new JLabel(FuelCover.moistureDescription());
    private final JLabel selectedCoordinateValue = new JLabel("--, --");
    private final JLabel selectedLocationValue = new JLabel("--, --");
    private final JLabel selectedFireValue = new JLabel("No reported fire in this area");
    private final JLabel selectedFireSourceValue = new JLabel("--");
    private final JLabel selectedFireDetailsValue = new JLabel("--");
    private final JLabel selectedFireTimeValue = new JLabel("--");
    private final JButton hypotheticalFireButton =
            new JButton("Model fire starting at searched location");
    private final JComboBox<GeoLocation> locationChoice = new JComboBox<>(new GeoLocation[] {
            new GeoLocation("Butte County grid center (near Chico)", 39.6999, -121.9674),
            new GeoLocation("Redding, CA (96001)", 40.5862, -122.3914),
            new GeoLocation("Sacramento, CA (95814)", 38.5816, -121.4944),
            new GeoLocation("Chico, CA (95926)", 39.7285, -121.8375),
            new GeoLocation("Santa Rosa, CA (95401)", 38.4405, -122.7144)
    });
    private final JTextField locationSearchField = new JTextField(13);
    private final JButton locationSearchButton = new JButton("Search");
    private final JLabel locationSearchStatus = new JLabel("Map centered on Butte County grid center (near Chico)");
    private final GeocodingApiClient geocodingApiClient = new GeocodingApiClient();
    private GeoLocation activeLocation;
    private boolean syncingLocationChoice;
    private final SimulationGrid gridPanel;
    private final Timer displayTimer;
    private int selectedRow;
    private int selectedCol;

    public FireSimulationGUI(FireSimulation simulation) {
        this(simulation, null, null, simulation.getWind());
    }

    public FireSimulationGUI(
            FireSimulation simulation,
            WeatherMonitor weatherMonitor,
            FireObservationMonitor fireObservationMonitor,
            Wind fallbackWind) {
        this.simulation = simulation;
        this.weatherMonitor = weatherMonitor;
        this.fireObservationMonitor = fireObservationMonitor;
        this.activeLocation = locationChoice.getItemAt(0);
        this.selectedRow = simulation.getRows() / 2;
        this.selectedCol = simulation.getColumns() / 2;
        setLayout(new BorderLayout(0, 0));
        setBackground(PANEL);

        gridPanel = new SimulationGrid();
        displayTimer = new Timer(5000, event -> updateDisplay());
        buildLayout();
        bindControls();
        updateDisplay();
    }

    private void buildLayout() {
        JPanel top = new JPanel(new BorderLayout());
        top.add(new HeaderPanel(), BorderLayout.NORTH);
        top.add(buildSearchBar(), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel dashboard = new JPanel(new GridBagLayout());
        dashboard.setBackground(new Color(247, 246, 241));
        dashboard.setBorder(new EmptyBorder(10, 14, 8, 14));
        GridBagConstraints column = new GridBagConstraints();
        column.gridy = 0;
        column.fill = GridBagConstraints.BOTH;
        column.weighty = 1.0;
        column.insets = new java.awt.Insets(0, 0, 0, 10);

        column.gridx = 0;
        column.weightx = 0.28;
        dashboard.add(buildLeftPanel(), column);
        column.gridx = 1;
        column.weightx = 0.52;
        column.insets = new java.awt.Insets(0, 0, 0, 10);
        dashboard.add(buildMapPanel(), column);
        column.gridx = 2;
        column.weightx = 0.28;
        column.insets = new java.awt.Insets(0, 0, 0, 0);
        dashboard.add(buildRightPanel(), column);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.add(dashboard, BorderLayout.CENTER);
        content.add(buildLegend(), BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);
    }

    private JPanel buildSearchBar() {
        JPanel search = new JPanel(new GridBagLayout());
        search.setBackground(new Color(250, 249, 245));
        search.setBorder(new EmptyBorder(9, 22, 9, 22));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new java.awt.Insets(0, 0, 0, 14);

        JLabel heading = label("Search Location", true);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        constraints.gridx = 0;
        constraints.weightx = 0;
        search.add(heading, constraints);

        locationSearchField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        locationSearchField.setToolTipText("Enter a Northern California location or ZIP code");
        locationSearchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(198, 205, 205)),
                new EmptyBorder(8, 12, 8, 12)));
        locationSearchField.addActionListener(event -> searchLocation());
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        search.add(locationSearchField, constraints);

        locationSearchButton.setText("\u2315  Search");
        styleButton(locationSearchButton, GREEN);
        locationSearchButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        locationSearchButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GREEN),
                new EmptyBorder(9, 18, 9, 18)));
        locationSearchButton.addActionListener(event -> searchLocation());
        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.insets = new java.awt.Insets(0, 0, 0, 14);
        search.add(locationSearchButton, constraints);

        locationChoice.setToolTipText("Choose a preset location");
        locationChoice.setPreferredSize(new Dimension(170, 38));
        locationChoice.setMaximumSize(new Dimension(200, 38));
        constraints.gridx = 3;
        constraints.insets = new java.awt.Insets(0, 0, 0, 0);
        search.add(locationChoice, constraints);
        locationSearchStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        locationSearchStatus.setForeground(new Color(82, 92, 86));
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.gridwidth = 3;
        constraints.insets = new java.awt.Insets(4, 0, 0, 0);
        search.add(locationSearchStatus, constraints);
        return search;
    }

    private JPanel buildLeftPanel() {
        JPanel content = panelCard();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(cardHeading("\u2600  Weather Conditions"));
        content.add(metricRow("\u224b", "Wind Speed", weatherSpeedValue));
        content.add(metricRow("\u27a4", "Wind Direction", weatherDirectionValue));
        content.add(metricRow("\u21c5", "Temperature", weatherTemperatureValue));
        content.add(metricRow("\u25cf", "Humidity", weatherHumidityValue));
        content.add(Box.createVerticalStrut(7));
        weatherUpdatedValue.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        weatherUpdatedValue.setForeground(new Color(92, 99, 102));
        content.add(weatherUpdatedValue);
        weatherSummary.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        weatherSummary.setForeground(new Color(115, 120, 119));
        content.add(Box.createVerticalStrut(4));
        content.add(weatherSummary);
        content.add(Box.createVerticalStrut(3));
        content.add(weatherTimestamp);
        content.add(new javax.swing.JSeparator());
        content.add(infoText("Spread estimates use the latest observed NWS station wind. "
                + "No moving simulation is run."));
        content.add(Box.createVerticalGlue());
        return content;
    }

    private JPanel buildMapPanel() {
        JPanel map = new JPanel(new BorderLayout(0, 0));
        map.setBackground(new Color(103, 127, 88));
        map.setBorder(BorderFactory.createLineBorder(new Color(212, 214, 205)));
        JPanel caption = new JPanel(new BorderLayout());
        caption.setBackground(new Color(248, 248, 243));
        caption.setBorder(new EmptyBorder(9, 12, 9, 12));
        caption.add(label("NORTHERN CALIFORNIA FIRE MAP", true), BorderLayout.WEST);
        caption.add(label(DISPLAY_GRID_ROWS + " x " + DISPLAY_GRID_COLUMNS
                + " forecast tiles  |  " + simulation.getRows() + " x "
                + simulation.getColumns() + " fuel cells", false),
                BorderLayout.EAST);
        map.add(caption, BorderLayout.NORTH);
        gridPanel.setPreferredSize(new Dimension(600, 390));
        map.add(gridPanel, BorderLayout.CENTER);
        return map;
    }

    private JPanel buildRightPanel() {
        JPanel content = panelCard();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(cardHeading("\uD83D\uDD25  Fire Spread ETA"));
        styleButton(hypotheticalFireButton, GREEN);
        hypotheticalFireButton.setText("Model fire at searched location");
        hypotheticalFireButton.addActionListener(event -> {
            if (gridPanel.hasHypotheticalSource()) {
                gridPanel.setHypotheticalSource(false);
                hypotheticalFireButton.setText("Model fire at searched location");
            } else {
                gridPanel.setHypotheticalSource(true);
                hypotheticalFireButton.setText("Clear hypothetical fire");
            }
            updateObservations();
            updateSelectedCell();
        });
        content.add(hypotheticalFireButton);
        content.add(metricRow("", "Reported fires in map", reportedFireValue));
        content.add(metricRow("", "Selected area's arrival", selectedEtaValue));
        content.add(new javax.swing.JSeparator());
        JLabel windTitle = label("Wind Direction (arrow points toward)", true);
        windTitle.setBorder(new EmptyBorder(5, 0, 0, 0));
        content.add(windTitle);
        content.add(compassPanel);
        content.add(new javax.swing.JSeparator());

        fireStateValue.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        fireStateValue.setForeground(FOREST);
        fireStateValue.setAlignmentX(LEFT_ALIGNMENT);
        content.add(fireStateValue);
        observationSummary.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        observationSummary.setForeground(new Color(82, 91, 86));
        observationSummary.setPreferredSize(new Dimension(230, 32));
        observationSummary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        content.add(observationSummary);
        content.add(infoText("Map tiles show the earliest estimated arrival within each "
                + "roughly 500 m area. Estimates use the nearest NWS station wind and "
                + "generalized fuel rates; they are not official forecasts. Cover groups "
                + "are inferred from fuel models because the raster-code legend is not included. "
                + "The grid does not contain fuel dryness or moisture."));
        return content;
    }

    private JPanel buildLegend() {
        JPanel footer = new JPanel(new BorderLayout(14, 0));
        footer.setBackground(new Color(250, 249, 245));
        footer.setBorder(new EmptyBorder(10, 18, 12, 18));
        JPanel legend = panelCard();
        legend.setLayout(new BorderLayout(10, 5));
        legend.add(cardHeading("\u25a7  Cell Status Legend"), BorderLayout.NORTH);
        JPanel states = new JPanel(new GridLayout(1, 7, 8, 0));
        states.setOpaque(false);
        states.add(legendItem(new Color(133, 194, 103), "No forecast path"));
        states.add(legendItem(new Color(255, 194, 64), "ETA 30+ min"));
        states.add(legendItem(new Color(255, 131, 38), "ETA 10-30 min"));
        states.add(legendItem(new Color(232, 54, 49), "ETA under 10 min"));
        states.add(legendItem(new Color(84, 91, 95), "Non-burnable"));
        states.add(legendItem(new Color(205, 52, 40), "Reported fire"));
        states.add(legendItem(new Color(255, 156, 44), "Hypothetical start"));
        legend.add(states, BorderLayout.CENTER);
        footer.add(legend, BorderLayout.CENTER);
        footer.add(buildSelectedCellPanel(), BorderLayout.EAST);
        return footer;
    }

    private JPanel buildSelectedCellPanel() {
        JPanel details = panelCard();
        details.setPreferredSize(new Dimension(430, 145));
        details.setLayout(new BorderLayout(8, 0));
        JLabel title = cardHeading("\u25c6  Selected Cell Details");
        details.add(title, BorderLayout.NORTH);
        JPanel values = new JPanel(new GridLayout(5, 2, 14, 4));
        values.setOpaque(false);
        values.add(detailItem("Arrival Estimate", cellStateLabel));
        values.add(detailItem("Vegetation / Fuel Cover", selectedCoverValue));
        values.add(detailItem("Dryness / Moisture", selectedMoistureValue));
        values.add(detailItem("Row, Column", selectedCoordinateValue));
        values.add(detailItem("Latitude, Longitude", selectedLocationValue));
        values.add(detailItem("Current Fire(s) Here", selectedFireValue));
        values.add(detailItem("Selected Cell ETA", selectedEtaValue));
        values.add(detailItem("Fire Source(s)", selectedFireSourceValue));
        values.add(detailItem("Fire Details", selectedFireDetailsValue));
        values.add(detailItem("Reported At (UTC)", selectedFireTimeValue));
        details.add(values, BorderLayout.CENTER);
        return details;
    }

    private JPanel detailItem(String title, JLabel value) {
        JPanel item = new JPanel(new BorderLayout(5, 0));
        item.setOpaque(false);
        JLabel heading = label(title, false);
        heading.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        value.setForeground(new Color(35, 56, 48));
        value.setHorizontalAlignment(JLabel.RIGHT);
        item.add(heading, BorderLayout.WEST);
        item.add(value, BorderLayout.CENTER);
        return item;
    }

    private static JPanel panelCard() {
        JPanel card = new JPanel();
        card.setBackground(new Color(255, 255, 252));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 220, 210)),
                new EmptyBorder(11, 12, 10, 12)));
        return card;
    }

    private static JLabel cardHeading(String text) {
        JLabel heading = new JLabel(text);
        heading.setFont(new Font(Font.SERIF, Font.BOLD, 18));
        heading.setForeground(new Color(25, 53, 48));
        heading.setBorder(new EmptyBorder(0, 0, 8, 0));
        return heading;
    }

    private static JPanel metricRow(String icon, String name, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(7, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(5, 0, 5, 0));
        if (icon != null && !icon.isEmpty()) {
            JLabel symbol = label(icon, true);
            symbol.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            symbol.setForeground(FOREST);
            symbol.setPreferredSize(new Dimension(27, 30));
            row.add(symbol, BorderLayout.WEST);
        }
        JLabel caption = label(name, false);
        caption.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        row.add(caption, BorderLayout.CENTER);
        value.setOpaque(true);
        value.setBackground(new Color(246, 245, 240));
        value.setHorizontalAlignment(JLabel.CENTER);
        value.setBorder(new EmptyBorder(6, 7, 6, 7));
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void bindControls() {
        locationChoice.addActionListener(event -> {
            GeoLocation selected = (GeoLocation) locationChoice.getSelectedItem();
            if (selected != null && !syncingLocationChoice) {
                applyLocation(selected);
            }
        });
    }

    private void searchLocation() {
        String query = locationSearchField.getText();
        if (query == null || query.trim().isEmpty()) {
            locationSearchStatus.setText("Enter a city, address, or ZIP code.");
            return;
        }

        locationSearchButton.setEnabled(false);
        locationSearchField.setEnabled(false);
        locationChoice.setEnabled(false);
        locationSearchStatus.setText("Searching for " + query.trim() + "...");
        new SwingWorker<GeoLocation, Void>() {
            @Override
            protected GeoLocation doInBackground() throws Exception {
                return geocodingApiClient.search(query);
            }

            @Override
            protected void done() {
                locationSearchButton.setEnabled(true);
                locationSearchField.setEnabled(true);
                locationChoice.setEnabled(true);
                try {
                    applyLocation(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    locationSearchStatus.setText("Location search was interrupted.");
                } catch (java.util.concurrent.ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String message = cause == null ? exception.getMessage() : cause.getMessage();
                    locationSearchStatus.setText("Search failed: " + message);
                }
            }
        }.execute();
    }

    private void applyLocation(GeoLocation location) {
        activeLocation = location;
        syncingLocationChoice = true;
        try {
            locationChoice.setSelectedItem(location);
        } finally {
            syncingLocationChoice = false;
        }
        gridPanel.setHypotheticalSource(false);
        hypotheticalFireButton.setText("Model fire at searched location");
        gridPanel.setMapCenter(location);
        locationSearchStatus.setText("<html>Map centered on "
                + escape(location.getDisplayName()) + "</html>");
        if (weatherMonitor != null) {
            weatherMonitor.setLocation(location.getLatitude(), location.getLongitude());
            weatherSummary.setText("Requesting NWS observation near "
                    + escape(location.getDisplayName()));
            weatherTimestamp.setText("Waiting for a fresh observation...");
        }
        updateDisplay();
        gridPanel.repaint();
    }

    private void updateDisplay() {
        updateWeatherDisplay();
        updateObservations();
        updateSelectedCell();
        gridPanel.repaint();
    }

    private void updateWeatherDisplay() {
        forecastWind = null;
        WeatherObservation observation = weatherMonitor == null
                ? null : weatherMonitor.getLatest();
        if (observation == null
                || !observation.isFreshAt(Instant.now(), WeatherMonitor.MAX_OBSERVATION_AGE)) {
            weatherSpeedValue.setText("-- mph");
            weatherDirectionValue.setText("--");
            weatherTemperatureValue.setText("-- \u00b0F");
            weatherHumidityValue.setText("-- %");
            weatherUpdatedValue.setText("Last updated: --");
            weatherSummary.setText(weatherMonitor == null
                    ? "Weather service unavailable."
                    : "Waiting for current NWS station observation...");
            String error = weatherMonitor == null ? null : weatherMonitor.getLastError();
            weatherTimestamp.setText(error == null ? "Fresh observed wind required for ETA."
                    : "NWS error: " + error);
            compassPanel.setDirection(0.0);
            return;
        }
        forecastWind = observation.toWindToward().orElse(null);
        weatherSpeedValue.setText(observation.getWindSpeedMetersPerSecond() == null
                ? "-- mph"
                : String.format(Locale.ROOT, "%.1f mph",
                        observation.getWindSpeedMetersPerSecond() / 0.44704));
        Double windFrom = observation.getWindDirectionFromDegrees();
        weatherDirectionValue.setText(windFrom == null
                ? "--" : formatWindFrom(windFrom));
        compassPanel.setDirection(windFrom == null ? 0.0 : windFrom + 180.0);
        Double temperatureF = observation.getTemperatureCelsius() == null
                ? null : observation.getTemperatureCelsius() * 9.0 / 5.0 + 32.0;
        weatherTemperatureValue.setText(temperatureF == null ? "-- \u00b0F"
                : String.format(Locale.ROOT, "%.1f \u00b0F", temperatureF));
        weatherHumidityValue.setText(observation.getRelativeHumidityPercent() == null
                ? "-- %" : String.format(Locale.ROOT, "%.0f %%",
                        observation.getRelativeHumidityPercent()));
        weatherUpdatedValue.setText("Last updated: " + UTC_FORMAT.format(observation.getObservedAt()));
        String wind = observation.toWindToward()
                .map(FireSimulationGUI::formatWindToward)
                .orElse("wind direction unavailable");
        weatherSummary.setText("<html><b>" + escape(observation.getStationName())
                + "</b><br>" + escape(wind)
                + "</html>");
        weatherTimestamp.setText("Observed: " + UTC_FORMAT.format(observation.getObservedAt()));
    }

    private void updateSelectedCell() {
        FireCell cell = simulation.getCell(selectedRow, selectedCol);
        double cellEta = gridPanel.getSelectedArrivalTime(selectedRow, selectedCol);
        double areaEta = gridPanel.getDisplayArrivalTime(selectedRow, selectedCol);
        cellStateLabel.setText(formatEtaOrNone(areaEta));
        cellStateLabel.setOpaque(true);
        cellStateLabel.setBackground(Double.isFinite(areaEta)
                ? new Color(255, 229, 190) : new Color(226, 241, 216));
        selectedEtaValue.setText(formatEtaOrNone(cellEta));
        String coverDescription = FuelCover.descriptionForModel(cell.getFuelModel());
        selectedCoverValue.setText(coverDescription);
        selectedCoverValue.setToolTipText("<html>Approximate cover class from fuel model "
                + cell.getFuelModel() + ". Raw raster vegetation code "
                + cell.getVegetationCode()
                + " has no bundled name legend. This class is not a plant-species "
                + "identification.</html>");
        selectedMoistureValue.setText(FuelCover.moistureDescription());
        selectedMoistureValue.setToolTipText(
                "The sample data contains no live/dead fuel moisture or dryness measurement.");
        selectedCoordinateValue.setText(selectedRow + ", " + selectedCol);
        double latitude = activeLocation.getLatitude()
                + ((simulation.getRows() / 2.0 - selectedRow - 0.5)
                        * simulation.getCellSizeMeters())
                        / 111_320.0;
        double longitude = activeLocation.getLongitude()
                + ((selectedCol - simulation.getColumns() / 2.0 + 0.5)
                        * simulation.getCellSizeMeters())
                        / (111_320.0 * Math.cos(Math.toRadians(activeLocation.getLatitude())));
        selectedLocationValue.setText(String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude));
        List<ObservedFire> firesHere = gridPanel.getFiresAtCell(selectedRow, selectedCol);
        if (firesHere.isEmpty()) {
            if (gridPanel.isHypotheticalCell(selectedRow, selectedCol)) {
                selectedFireValue.setText("Hypothetical ignition");
                selectedFireSourceValue.setText("User scenario");
                selectedFireDetailsValue.setText("Not an active fire");
                selectedFireTimeValue.setText("Not reported");
            } else {
                selectedFireValue.setText("None reported");
                selectedFireSourceValue.setText("--");
                selectedFireDetailsValue.setText("--");
                selectedFireTimeValue.setText("--");
            }
            selectedFireValue.setToolTipText(null);
            selectedFireSourceValue.setToolTipText(null);
            selectedFireDetailsValue.setToolTipText(null);
            selectedFireTimeValue.setToolTipText(null);
        } else {
            List<String> fireNames = new ArrayList<>();
            List<String> fireSources = new ArrayList<>();
            for (ObservedFire fire : firesHere) {
                fireNames.add(fire.getName());
                String source = fire.getSource() == ObservedFire.Source.CAL_FIRE_INCIDENT
                        ? "CAL FIRE" : "NASA FIRMS";
                if (!fireSources.contains(source)) {
                    fireSources.add(source);
                }
            }
            String names = String.join(", ", fireNames);
            String sources = String.join(", ", fireSources);
            selectedFireValue.setText(ellipsize(names, 28));
            selectedFireSourceValue.setText(sources);
            ObservedFire latestFire = firesHere.get(0);
            for (ObservedFire fire : firesHere) {
                if (fire.getObservedAt() != null
                        && (latestFire.getObservedAt() == null
                                || fire.getObservedAt().isAfter(latestFire.getObservedAt()))) {
                    latestFire = fire;
                }
            }
            String fireDetails = latestFire.getDetails() == null
                    || latestFire.getDetails().isEmpty() ? "No extra details"
                            : latestFire.getDetails();
            selectedFireDetailsValue.setText(ellipsize(fireDetails, 30));
            selectedFireTimeValue.setText(latestFire.getObservedAt() == null
                    ? "Time unavailable" : UTC_FORMAT.format(latestFire.getObservedAt()));
            selectedFireValue.setToolTipText(names);
            selectedFireSourceValue.setToolTipText(sources);
            selectedFireDetailsValue.setToolTipText(fireDetails);
            selectedFireTimeValue.setToolTipText(latestFire.getObservedAt() == null
                    ? "Observation time unavailable"
                    : UTC_FORMAT.format(latestFire.getObservedAt()));
        }
    }

    private void updateObservations() {
        if (fireObservationMonitor == null) {
            observationSummary.setText("Fire observation feeds are not connected.");
            gridPanel.setReportedFires(java.util.Collections.emptyList(), forecastWind);
            fireStateValue.setText(gridPanel.hasHypotheticalSource()
                    ? "Hypothetical fire location selected"
                    : "No fire feed connected");
            return;
        }
        ObservationFeed calFire = fireObservationMonitor.getCalFireFeed();
        ObservationFeed firms = fireObservationMonitor.getFirmsFeed();
        List<ObservedFire> currentObservations = new ArrayList<>();
        addFreshObservations(calFire, currentObservations);
        addFreshObservations(firms, currentObservations);
        int mappedFireCount = gridPanel.setReportedFires(currentObservations, forecastWind);
        reportedFireValue.setText(Integer.toString(mappedFireCount));
        if (forecastWind == null) {
            fireStateValue.setText("Waiting for fresh NWS wind");
            selectedEtaValue.setText("--");
        } else if (mappedFireCount == 0 && !gridPanel.hasHypotheticalSource()) {
            fireStateValue.setText("No reported fire in map window");
            selectedEtaValue.setText("--");
        } else if (gridPanel.hasHypotheticalSource()) {
            fireStateValue.setText("Hypothetical origin at searched location");
        } else {
            fireStateValue.setText("ETA from reported fire(s) using current wind");
        }
        observationSummary.setText("<html><b>CAL FIRE:</b> " + compactFeedSummary(calFire)
                + " &nbsp; <b>NASA FIRMS:</b> " + compactFeedSummary(firms)
                + "<br>Observed fires shown on map: " + mappedFireCount + "</html>");
        observationSummary.setToolTipText("<html><b>CAL FIRE:</b> "
                + feedSummary(calFire) + "<br><b>NASA FIRMS:</b> "
                + feedSummary(firms) + "</html>");
    }

    private String compactFeedSummary(ObservationFeed feed) {
        if (feed.getLastError() != null) {
            return "Error";
        }
        if (!feed.isConfigured()) {
            return "Not configured";
        }
        if (feed.getFetchedAt() == null) {
            return "Loading";
        }
        return (feed.isStaleAt(Instant.now(), FireObservationMonitor.MAX_FEED_AGE)
                ? "Stale" : "Current") + " (" + feed.getObservations().size() + ")";
    }

    private void addFreshObservations(ObservationFeed feed, List<ObservedFire> destination) {
        if (feed.getFetchedAt() != null
                && !feed.isStaleAt(Instant.now(), FireObservationMonitor.MAX_FEED_AGE)) {
            destination.addAll(feed.getObservations());
        }
    }

    private String feedSummary(ObservationFeed feed) {
        if (!feed.isConfigured()) {
            return "Not configured (set NASA_FIRMS_MAP_KEY).";
        }
        if (feed.getFetchedAt() == null) {
            return escape(feed.getLastError() == null
                    ? "Waiting for initial response." : feed.getLastError());
        }
        String status = feed.isStaleAt(Instant.now(), FireObservationMonitor.MAX_FEED_AGE)
                ? "STALE" : "Current";
        String summary = status + " — " + feed.getObservations().size() + " records; fetched "
                + UTC_FORMAT.format(feed.getFetchedAt());
        if (feed.getLastError() != null) {
            summary += "; " + feed.getLastError();
        }
        if (!feed.getObservations().isEmpty()) {
            ObservedFire latest = feed.getObservations().get(0);
            summary += "<br>Latest: " + escape(latest.getName())
                    + (latest.getObservedAt() == null
                            ? "" : " (" + UTC_FORMAT.format(latest.getObservedAt()) + ")");
        }
        return summary;
    }

    private void selectCell(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        updateSelectedCell();
        selectedEtaValue.setText(formatEtaOrNone(
                gridPanel.getSelectedArrivalTime(row, col)));
        gridPanel.repaint();
    }

    private static String formatEtaOrNone(double seconds) {
        return Double.isFinite(seconds) ? formatEta(seconds) : "No ETA";
    }

    private static String formatEta(double seconds) {
        long totalMinutes = Math.max(0L, (long) Math.ceil(seconds / 60.0));
        if (totalMinutes < 60) {
            return totalMinutes + " min";
        }
        return String.format(Locale.ROOT, "%d hr %02d min",
                totalMinutes / 60, totalMinutes % 60);
    }

    private static String directionName(double degrees) {
        String[] directions = {
                "North", "Northeast", "East", "Southeast",
                "South", "Southwest", "West", "Northwest"
        };
        int index = (int) Math.round((((degrees % 360.0) + 360.0) % 360.0) / 45.0) % 8;
        return directions[index];
    }

    static String formatWindFrom(double degrees) {
        return directionName(degrees) + " (from)";
    }

    static String formatWindToward(Wind wind) {
        if (wind == null) {
            return "wind direction unavailable";
        }
        return String.format(Locale.ROOT, "%.1f mph toward %s",
                wind.getSpeedMetersPerSecond() / 0.44704,
                directionName(wind.getDirectionDegrees()));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String ellipsize(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maximumLength - 3)) + "...";
    }

    private static JLabel metricValue(String value, String suffix) {
        JLabel label = new JLabel(value + suffix);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        label.setForeground(new Color(42, 50, 47));
        return label;
    }

    private static JLabel label(String text, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 11));
        label.setForeground(TEXT);
        return label;
    }

    private static JLabel infoText(String text) {
        JLabel label = new JLabel("<html><body style='width:205px'>" + text + "</body></html>");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        label.setForeground(new Color(85, 96, 107));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static void styleButton(JButton button, Color background) {
        button.setBackground(background);
        button.setForeground(background.getRed() < 100 ? Color.WHITE : TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(202, 210, 217)),
                new EmptyBorder(7, 4, 7, 4)));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
    }

    private static JLabel legendItem(Color color, String text) {
        JLabel label = new JLabel("\u25A0  " + text);
        label.setForeground(TEXT);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        label.setIconTextGap(3);
        label.setToolTipText("Grid color for " + text.toLowerCase(Locale.ROOT) + " cells");
        label.setForeground(color.darker());
        return label;
    }

    public static void showSimulation(FireSimulation simulation) {
        showSimulation(simulation, null, null, simulation.getWind());
    }

    public static void showSimulation(
            FireSimulation simulation,
            WeatherMonitor weatherMonitor,
            FireObservationMonitor fireObservationMonitor,
            Wind fallbackWind) {
        JFrame frame = new JFrame("Northern California Wildfire Simulator");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        FireSimulationGUI panel = new FireSimulationGUI(
                simulation, weatherMonitor, fireObservationMonitor, fallbackWind);
        frame.add(panel);
        frame.setMinimumSize(new Dimension(1120, 720));
        frame.setSize(1420, 900);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                panel.displayTimer.stop();
                if (panel.gridPanel.basemapWorker != null) {
                    panel.gridPanel.basemapWorker.cancel(true);
                    panel.gridPanel.basemapRequest++;
                }
                if (weatherMonitor != null) {
                    weatherMonitor.close();
                }
                if (fireObservationMonitor != null) {
                    fireObservationMonitor.close();
                }
            }
        });
        frame.setVisible(true);
        panel.gridPanel.loadBasemap();
        panel.displayTimer.start();
    }

    private static final class HeaderPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private HeaderPanel() {
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(9, 26, 9, 20));
            JLabel title = new JLabel("  CalFire Simulator");
            title.setForeground(new Color(255, 239, 194));
            title.setFont(new Font(Font.SERIF, Font.BOLD, 32));
            JLabel subtitle = new JLabel("  Wildfire Spread Simulation for Northern California");
            subtitle.setForeground(new Color(243, 244, 224));
            subtitle.setFont(new Font(Font.SERIF, Font.BOLD, 14));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(title);
            text.add(subtitle);
            add(text, BorderLayout.CENTER);
            setPreferredSize(new Dimension(900, 92));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            int width = getWidth();
            int height = getHeight();
            g.setPaint(new java.awt.GradientPaint(
                    0, 0, new Color(18, 47, 43),
                    width, height, new Color(92, 104, 72)));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(221, 171, 98, 155));
            g.fill(new Ellipse2D.Double(width * 0.86, 8, 42, 42));
            Path2D mountains = new Path2D.Double();
            mountains.moveTo(0, height * 0.78);
            mountains.lineTo(width * 0.12, height * 0.31);
            mountains.lineTo(width * 0.19, height * 0.74);
            mountains.lineTo(width * 0.32, height * 0.23);
            mountains.lineTo(width * 0.47, height * 0.77);
            mountains.lineTo(width * 0.61, height * 0.35);
            mountains.lineTo(width * 0.77, height * 0.79);
            mountains.lineTo(width * 0.9, height * 0.38);
            mountains.lineTo(width, height * 0.73);
            mountains.lineTo(width, height);
            mountains.lineTo(0, height);
            mountains.closePath();
            g.setColor(new Color(13, 42, 36, 185));
            g.fill(mountains);
            Random random = new Random(3214L);
            for (int x = -4; x < width + 12; x += 13) {
                int treeHeight = 12 + random.nextInt(24);
                int base = height - random.nextInt(9);
                Path2D tree = new Path2D.Double();
                tree.moveTo(x, base);
                tree.lineTo(x + 6, base - treeHeight);
                tree.lineTo(x + 12, base);
                tree.closePath();
                g.setColor(new Color(9, 37 + random.nextInt(18), 33));
                g.fill(tree);
            }
            g.dispose();
        }
    }

    private final class CompassPanel extends JPanel {

        private static final long serialVersionUID = 1L;
        private double directionDegrees;

        private CompassPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(170, 112));
        }

        private void setDirection(double degrees) {
            directionDegrees = Double.isFinite(degrees) ? degrees : 0.0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2 + 1;
            int radius = Math.min(37, Math.min(getWidth() / 2 - 18, getHeight() / 2 - 10));
            g.setColor(new Color(116, 162, 143));
            g.setStroke(new BasicStroke(1.3f));
            g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.setColor(TEXT);
            g.drawString("N", centerX - 4, centerY - radius - 6);
            g.drawString("S", centerX - 4, centerY + radius + 15);
            g.drawString("W", centerX - radius - 15, centerY + 4);
            g.drawString("E", centerX + radius + 7, centerY + 4);

            Graphics2D arrow = (Graphics2D) g.create();
            arrow.rotate(Math.toRadians(directionDegrees), centerX, centerY);
            Path2D pointer = new Path2D.Double();
            pointer.moveTo(centerX, centerY - radius * 0.72);
            pointer.lineTo(centerX + 8, centerY + radius * 0.45);
            pointer.lineTo(centerX, centerY + radius * 0.19);
            pointer.lineTo(centerX - 8, centerY + radius * 0.45);
            pointer.closePath();
            arrow.setColor(GREEN);
            arrow.fill(pointer);
            arrow.dispose();
            g.dispose();
        }
    }

    private final class SimulationGrid extends JPanel {

        private static final long serialVersionUID = 1L;
        private int cellSize;
        private int gridX;
        private int gridY;
        private GeoLocation mapCenter = activeLocation;
        private final BasemapApiClient basemapApiClient = new BasemapApiClient();
        private java.awt.image.BufferedImage basemapImage;
        private String basemapError;
        private SwingWorker<java.awt.image.BufferedImage, Void> basemapWorker;
        private int basemapRequest;
        private Map<Integer, List<ObservedFire>> observedFireCells = new HashMap<>();
        private final Set<Integer> hypotheticalSourceTiles = new HashSet<>();
        private List<ObservedFire> observedFires = java.util.Collections.emptyList();
        private boolean hypotheticalSource;
        private double[][] arrivalTimes = new double[0][0];
        private double[][] displayArrivalTimes =
                new double[DISPLAY_GRID_ROWS][DISPLAY_GRID_COLUMNS];

        private void setMapCenter(GeoLocation center) {
            mapCenter = center;
            setReportedFires(observedFires, forecastWind);
            loadBasemap();
        }

        private void loadBasemap() {
            if (!isShowing()) {
                return;
            }
            if (basemapWorker != null) {
                basemapWorker.cancel(true);
            }
            int requestNumber = ++basemapRequest;
            int gridSize = Math.max(1, Math.min(
                    Math.min(getWidth() * 68 / 100, getHeight() * 88 / 100), 520));
            int mapGridCell = Math.max(2, gridSize
                    / Math.max(DISPLAY_GRID_ROWS, DISPLAY_GRID_COLUMNS));
            double extentWidth = simulation.getColumns() * simulation.getCellSizeMeters()
                    * getWidth() / (double) (mapGridCell * DISPLAY_GRID_COLUMNS);
            double extentHeight = simulation.getRows() * simulation.getCellSizeMeters()
                    * getHeight() / (double) (mapGridCell * DISPLAY_GRID_ROWS);
            basemapError = null;
            basemapWorker = new SwingWorker<java.awt.image.BufferedImage, Void>() {
                @Override
                protected java.awt.image.BufferedImage doInBackground() throws Exception {
                    return basemapApiClient.fetchForExtent(
                            mapCenter, extentWidth, extentHeight, 1000, 700);
                }

                @Override
                protected void done() {
                    if (requestNumber != basemapRequest || isCancelled()) {
                        return;
                    }
                    try {
                        basemapImage = get();
                        basemapError = null;
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        basemapError = "Map imagery request interrupted";
                    } catch (java.util.concurrent.ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        basemapError = cause == null
                                ? "Map imagery unavailable" : cause.getMessage();
                    }
                    repaint();
                }
            };
            basemapWorker.execute();
        }

        private int setReportedFires(List<ObservedFire> observations, Wind wind) {
            observedFires = new ArrayList<>(observations);
            observedFireCells.clear();
            hypotheticalSourceTiles.clear();
            List<int[]> sourceCells = new ArrayList<>();
            int mappedCount = 0;
            for (ObservedFire fire : observations) {
                int[] cell = FireGridMapper.toCell(
                        mapCenter,
                        fire,
                        simulation.getRows(),
                        simulation.getColumns(),
                        simulation.getCellSizeMeters());
                if (cell != null) {
                    sourceCells.add(cell);
                    mappedCount++;
                    int displayRow = Math.min(DISPLAY_GRID_ROWS - 1,
                            cell[0] * DISPLAY_GRID_ROWS / simulation.getRows());
                    int displayColumn = Math.min(DISPLAY_GRID_COLUMNS - 1,
                            cell[1] * DISPLAY_GRID_COLUMNS / simulation.getColumns());
                    int index = displayRow * DISPLAY_GRID_COLUMNS + displayColumn;
                    observedFireCells.computeIfAbsent(index, key -> new ArrayList<>()).add(fire);
                }
            }
            if (hypotheticalSource) {
                int row = simulation.getRows() / 2;
                int column = simulation.getColumns() / 2;
                sourceCells.add(new int[] { row, column });
                int displayRow = Math.min(DISPLAY_GRID_ROWS - 1,
                        row * DISPLAY_GRID_ROWS / simulation.getRows());
                int displayColumn = Math.min(DISPLAY_GRID_COLUMNS - 1,
                        column * DISPLAY_GRID_COLUMNS / simulation.getColumns());
                hypotheticalSourceTiles.add(
                        displayRow * DISPLAY_GRID_COLUMNS + displayColumn);
            }
            if (wind == null || sourceCells.isEmpty()) {
                arrivalTimes = new double[simulation.getRows()][simulation.getColumns()];
                for (double[] row : arrivalTimes) {
                    java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
                }
                displayArrivalTimes = new double[DISPLAY_GRID_ROWS][DISPLAY_GRID_COLUMNS];
                for (double[] row : displayArrivalTimes) {
                    java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
                }
            } else {
                arrivalTimes = FireSpreadForecaster.estimateArrivalTimes(
                        simulation, wind, sourceCells);
                displayArrivalTimes = FireSpreadForecaster.summarizeDisplayCells(
                        arrivalTimes, DISPLAY_GRID_ROWS, DISPLAY_GRID_COLUMNS);
            }
            repaint();
            return mappedCount;
        }

        private void setHypotheticalSource(boolean enabled) {
            hypotheticalSource = enabled;
            setReportedFires(observedFires, forecastWind);
        }

        private boolean hasHypotheticalSource() {
            return hypotheticalSource;
        }

        private boolean isHypotheticalCell(int row, int column) {
            int displayRow = Math.min(DISPLAY_GRID_ROWS - 1,
                    row * DISPLAY_GRID_ROWS / simulation.getRows());
            int displayColumn = Math.min(DISPLAY_GRID_COLUMNS - 1,
                    column * DISPLAY_GRID_COLUMNS / simulation.getColumns());
            return hypotheticalSourceTiles.contains(
                    displayRow * DISPLAY_GRID_COLUMNS + displayColumn);
        }

        private double getSelectedArrivalTime(int row, int column) {
            if (row < 0 || row >= simulation.getRows()
                    || column < 0 || column >= simulation.getColumns()
                    || arrivalTimes.length == 0) {
                return Double.POSITIVE_INFINITY;
            }
            return arrivalTimes[row][column];
        }

        private double getDisplayArrivalTime(int row, int column) {
            if (row < 0 || row >= simulation.getRows()
                    || column < 0 || column >= simulation.getColumns()
                    || displayArrivalTimes.length == 0) {
                return Double.POSITIVE_INFINITY;
            }
            int displayRow = Math.min(DISPLAY_GRID_ROWS - 1,
                    row * DISPLAY_GRID_ROWS / simulation.getRows());
            int displayColumn = Math.min(DISPLAY_GRID_COLUMNS - 1,
                    column * DISPLAY_GRID_COLUMNS / simulation.getColumns());
            return displayArrivalTimes[displayRow][displayColumn];
        }

        private List<ObservedFire> getFiresAtCell(int row, int column) {
            int displayRow = Math.min(DISPLAY_GRID_ROWS - 1,
                    row * DISPLAY_GRID_ROWS / simulation.getRows());
            int displayColumn = Math.min(DISPLAY_GRID_COLUMNS - 1,
                    column * DISPLAY_GRID_COLUMNS / simulation.getColumns());
            List<ObservedFire> fires =
                    observedFireCells.get(displayRow * DISPLAY_GRID_COLUMNS + displayColumn);
            return fires == null ? java.util.Collections.emptyList() : new ArrayList<>(fires);
        }

        private SimulationGrid() {
            setBackground(new Color(57, 73, 68));
            setPreferredSize(new Dimension(680, 620));
            for (double[] row : displayArrivalTimes) {
                java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
            }
            setToolTipText("Click a grid cell to inspect estimated arrival time.");
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    if (cellSize <= 0 || event.getX() < gridX || event.getY() < gridY) {
                        return;
                    }
                    int col = (event.getX() - gridX) / cellSize;
                    int row = (event.getY() - gridY) / cellSize;
                    if (row >= 0 && row < DISPLAY_GRID_ROWS
                            && col >= 0 && col < DISPLAY_GRID_COLUMNS) {
                        selectCell(simulationRow(row), simulationColumn(col));
                    }
                }
            });
            setToolTipText("");
        }

        @Override
        public String getToolTipText(java.awt.event.MouseEvent event) {
            if (cellSize <= 0 || event.getX() < gridX || event.getY() < gridY) {
                return null;
            }
            int col = (event.getX() - gridX) / cellSize;
            int row = (event.getY() - gridY) / cellSize;
            if (row < 0 || row >= DISPLAY_GRID_ROWS
                    || col < 0 || col >= DISPLAY_GRID_COLUMNS) {
                return null;
            }
            List<ObservedFire> fires =
                    observedFireCells.get(row * DISPLAY_GRID_COLUMNS + col);
            int tileIndex = row * DISPLAY_GRID_COLUMNS + col;
            double eta = displayArrivalTimes[row][col];
            if (hypotheticalSourceTiles.contains(tileIndex)) {
                return "Hypothetical ignition at searched location; "
                        + (Double.isFinite(eta) ? formatEta(eta) : "waiting for observed wind");
            }
            if (fires == null || fires.isEmpty()) {
                return Double.isFinite(eta) ? "Estimated arrival: " + formatEta(eta)
                        : "No reported fire path to this cell.";
            }
            List<String> names = new ArrayList<>();
            for (ObservedFire fire : fires) {
                names.add(fire.getName());
            }
            return "Reported fire: " + String.join(", ", names)
                    + "; forecast starts here (0 min).";
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintTerrain(g, getWidth(), getHeight());
            int gridSize = Math.max(1, Math.min(
                    Math.min((getWidth() * 68 / 100), (getHeight() * 88 / 100)), 520));
            cellSize = Math.max(2, gridSize
                    / Math.max(DISPLAY_GRID_ROWS, DISPLAY_GRID_COLUMNS));
            int gridWidth = cellSize * DISPLAY_GRID_COLUMNS;
            int gridHeight = cellSize * DISPLAY_GRID_ROWS;
            gridX = (getWidth() - gridWidth) / 2;
            gridY = (getHeight() - gridHeight) / 2;

            g.setColor(new Color(32, 47, 39, 190));
            g.fillRoundRect(gridX - 9, gridY - 9, gridWidth + 18, gridHeight + 18, 9, 9);
            for (int row = 0; row < DISPLAY_GRID_ROWS; row++) {
                for (int col = 0; col < DISPLAY_GRID_COLUMNS; col++) {
                    FireCell cell = displayCell(row, col);
                    int x = gridX + col * cellSize;
                    int y = gridY + row * cellSize;
                    double eta = displayArrivalTimes[row][col];
                    Color cellColor = forecastColor(eta, cell);
                    g.setColor(new Color(cellColor.getRed(), cellColor.getGreen(),
                            cellColor.getBlue(), 205));
                    g.fillRect(x, y, cellSize, cellSize);
                    g.setColor(new Color(12, 24, 20, 170));
                    g.drawRect(x, y, cellSize, cellSize);
                    List<ObservedFire> fires = observedFireCells.get(
                            row * DISPLAY_GRID_COLUMNS + col);
                    int tileIndex = row * DISPLAY_GRID_COLUMNS + col;
                    if (fires != null) {
                        drawObservedFireMarker(g, x, y, cellSize);
                        if (hypotheticalSourceTiles.contains(tileIndex)) {
                            drawHypotheticalFireMarker(g, x, y, cellSize);
                        }
                    } else if (hypotheticalSourceTiles.contains(tileIndex)) {
                        drawHypotheticalFireMarker(g, x, y, cellSize);
                    } else if (Double.isFinite(eta)) {
                        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                        g.setColor(new Color(30, 32, 29));
                        String etaText = shortEta(eta);
                        java.awt.FontMetrics metrics = g.getFontMetrics();
                        g.drawString(etaText,
                                x + (cellSize - metrics.stringWidth(etaText)) / 2,
                                y + (cellSize + metrics.getAscent()) / 2 - 2);
                    }
                }
            }

            int selectedDisplayCol = Math.min(DISPLAY_GRID_COLUMNS - 1,
                    selectedCol * DISPLAY_GRID_COLUMNS / simulation.getColumns());
            int selectedDisplayRow = Math.min(DISPLAY_GRID_ROWS - 1,
                    selectedRow * DISPLAY_GRID_ROWS / simulation.getRows());
            int selectedX = gridX + selectedDisplayCol * cellSize;
            int selectedY = gridY + selectedDisplayRow * cellSize;
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.0f));
            g.drawRect(selectedX, selectedY, cellSize, cellSize);

            g.setColor(new Color(255, 255, 255, 225));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            g.drawString("\u2191 N", gridX + gridWidth - 35, Math.max(20, gridY - 17));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.setColor(Color.WHITE);
            g.drawString(activeLocation.getDisplayName()
                            + "  |  " + (int) simulation.getCellSizeMeters() + " m cells",
                    12, getHeight() - 12);
            String mapCredit = basemapImage != null
                    ? "Imagery: Esri, Maxar, Earthstar Geographics"
                    : basemapError == null ? "Terrain preview" : "Basemap unavailable: "
                            + basemapError;
            g.drawString(mapCredit, Math.max(12, getWidth() - 290), getHeight() - 12);
            g.dispose();
        }

        private int simulationRow(int displayRow) {
            return Math.min(simulation.getRows() - 1,
                    displayRow * simulation.getRows() / DISPLAY_GRID_ROWS
                            + simulation.getRows() / (2 * DISPLAY_GRID_ROWS));
        }

        private int simulationColumn(int displayColumn) {
            return Math.min(simulation.getColumns() - 1,
                    displayColumn * simulation.getColumns() / DISPLAY_GRID_COLUMNS
                            + simulation.getColumns() / (2 * DISPLAY_GRID_COLUMNS));
        }

        private FireCell displayCell(int displayRow, int displayColumn) {
            int firstRow = displayRow * simulation.getRows() / DISPLAY_GRID_ROWS;
            int lastRow = Math.max(firstRow + 1,
                    (displayRow + 1) * simulation.getRows() / DISPLAY_GRID_ROWS);
            int firstColumn = displayColumn * simulation.getColumns() / DISPLAY_GRID_COLUMNS;
            int lastColumn = Math.max(firstColumn + 1,
                    (displayColumn + 1) * simulation.getColumns() / DISPLAY_GRID_COLUMNS);
            FireCell representative =
                    simulation.getCell(simulationRow(displayRow), simulationColumn(displayColumn));
            FireCell burning = null;
            FireCell highestRisk = null;
            double maximumRisk = 0.0;
            int burnedCount = 0;
            int nonBurnableCount = 0;
            int cellCount = 0;
            FireCell burnedCell = null;
            FireCell nonBurnableCell = null;
            for (int row = firstRow; row < lastRow; row++) {
                for (int column = firstColumn; column < lastColumn; column++) {
                    FireCell cell = simulation.getCell(row, column);
                    cellCount++;
                    if (cell.getState() == FireState.BURNING
                            && (burning == null || cell.getFirelineIntensityKwPerMeter()
                                    > burning.getFirelineIntensityKwPerMeter())) {
                        burning = cell;
                    } else if (cell.getState() == FireState.BURNED) {
                        burnedCount++;
                        burnedCell = cell;
                    } else if (cell.getState() == FireState.NON_BURNABLE) {
                        nonBurnableCount++;
                        nonBurnableCell = cell;
                    } else if (cell.getIgnitionProbability() > maximumRisk) {
                        maximumRisk = cell.getIgnitionProbability();
                        highestRisk = cell;
                    }
                }
            }
            if (burning != null) {
                return burning;
            }
            if (highestRisk != null && maximumRisk > 0.0) {
                return highestRisk;
            }
            if (burnedCount > cellCount / 2) {
                return burnedCell;
            }
            if (nonBurnableCount > cellCount / 2) {
                return nonBurnableCell;
            }
            return representative;
        }

        private void paintTerrain(Graphics2D graphics, int width, int height) {
            if (basemapImage != null) {
                graphics.drawImage(basemapImage, 0, 0, width, height, null);
                return;
            }
            graphics.setPaint(new java.awt.GradientPaint(
                    0, 0, new Color(112, 143, 96),
                    width, height, new Color(65, 99, 68)));
            graphics.fillRect(0, 0, width, height);
            Random terrain = new Random(413L);
            for (int index = 0; index < 90; index++) {
                int x = terrain.nextInt(Math.max(1, width));
                int y = terrain.nextInt(Math.max(1, height));
                int size = 18 + terrain.nextInt(75);
                int red = 66 + terrain.nextInt(50);
                int green = 82 + terrain.nextInt(65);
                int blue = 47 + terrain.nextInt(35);
                graphics.setColor(new Color(red, green, blue, 75));
                graphics.fill(new Ellipse2D.Double(x, y, size, size * 0.55));
            }
            Path2D river = new Path2D.Double();
            river.moveTo(width * 0.03, height * 0.48);
            river.curveTo(width * 0.2, height * 0.23, width * 0.28, height * 0.86,
                    width * 0.47, height * 0.52);
            river.curveTo(width * 0.63, height * 0.23, width * 0.72, height * 0.75,
                    width * 0.98, height * 0.37);
            graphics.setColor(new Color(51, 114, 139, 175));
            graphics.setStroke(new BasicStroke(12.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(river);
            graphics.setColor(new Color(212, 200, 160, 105));
            graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(river);
        }

        private void drawObservedFireMarker(Graphics2D graphics, int cellX, int cellY, int size) {
            int markerSize = Math.max(7, Math.min(11, size + 2));
            int x = cellX + (size - markerSize) / 2;
            int y = cellY + (size - markerSize) / 2;
            Path2D flame = new Path2D.Double();
            flame.moveTo(x + markerSize * 0.50, y + markerSize);
            flame.curveTo(x - markerSize * 0.05, y + markerSize * 0.78,
                    x + markerSize * 0.26, y + markerSize * 0.35,
                    x + markerSize * 0.50, y);
            flame.curveTo(x + markerSize * 0.74, y + markerSize * 0.35,
                    x + markerSize * 1.05, y + markerSize * 0.78,
                    x + markerSize * 0.50, y + markerSize);
            flame.closePath();
            graphics.setColor(new Color(45, 34, 29));
            graphics.fill(flame);
            graphics.setColor(new Color(205, 52, 40));
            graphics.fill(new java.awt.geom.Ellipse2D.Double(
                    x + markerSize * 0.16, y + markerSize * 0.12,
                    markerSize * 0.68, markerSize * 0.78));
            graphics.setColor(new Color(255, 207, 54));
            graphics.fill(new java.awt.geom.Ellipse2D.Double(
                    x + markerSize * 0.34, y + markerSize * 0.51,
                    markerSize * 0.32, markerSize * 0.36));
        }

        private void drawHypotheticalFireMarker(Graphics2D graphics, int cellX, int cellY, int size) {
            int markerSize = Math.max(12, Math.min(18, size / 2));
            int x = cellX + size - markerSize - 2;
            int y = cellY + 2;
            graphics.setColor(new Color(255, 250, 231));
            graphics.fill(new Ellipse2D.Double(x - 1, y - 1, markerSize + 2, markerSize + 2));
            graphics.setColor(new Color(230, 117, 26));
            graphics.fill(new Ellipse2D.Double(x, y, markerSize, markerSize));
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            graphics.drawString("H", x + markerSize / 2 - 3, y + markerSize / 2 + 4);
        }

        private Color forecastColor(double eta, FireCell cell) {
            if (Double.isFinite(eta)) {
                if (eta < 10.0 * 60.0) {
                    return new Color(232, 72, 56);
                }
                if (eta < 30.0 * 60.0) {
                    return new Color(255, 151, 46);
                }
                return new Color(255, 203, 70);
            }
            if (cell.getState() == FireState.NON_BURNABLE) {
                return new Color(84, 91, 95);
            }
            return new Color(133, 194, 103);
        }

        private String shortEta(double seconds) {
            long minutes = Math.max(1L, (long) Math.ceil(seconds / 60.0));
            return minutes < 60 ? minutes + "m"
                    : (minutes / 60) + "h" + String.format(Locale.ROOT, "%02d", minutes % 60);
        }

        private Color fuelColor(int fuel) {
            if (fuel >= 101 && fuel <= 109) {
                return new Color(129, 180, 91);
            }
            if ((fuel >= 121 && fuel <= 124) || (fuel >= 141 && fuel <= 149)) {
                return new Color(166, 177, 71);
            }
            if ((fuel >= 161 && fuel <= 165) || (fuel >= 181 && fuel <= 189)) {
                return new Color(77, 137, 75);
            }
            return new Color(148, 153, 153);
        }
    }

}
