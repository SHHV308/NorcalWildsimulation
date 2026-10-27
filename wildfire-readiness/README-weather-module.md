## Weather and Real-Fire Data Module

This module gives the simulation current outside information: the latest wind and temperature near the selected location, and the fires that agencies and satellites report right now. Reported fires are shown separately from simulated fires and never change simulated cells or the simulation clock.

### Files

| Area | Files in `SRC/` |
|---|---|
| Weather | `WeatherApiClient`, `WeatherMonitor`, `WeatherObservation`, `Wind` |
| Reported fires | `FireDataApiClient`, `FireObservationMonitor`, `ObservedFire`, `ObservationFeed`, `FireGridMapper` |
| Shared helper | `JsonParser` |
| Tests in `TEST/` | `ExternalDataIntegrationTest`, `StationFallbackTest`, `RecentOnlyTest` |

### How it works

**Weather.** For a selected latitude and longitude, `WeatherApiClient` asks the National Weather Service (`api.weather.gov`) for the nearby observation stations and reads each station's latest observation. It tries up to five stations until one has a fresh wind speed and direction (calm air also counts). Wind speed is converted from km/h to m/s. The service reports the direction the wind comes FROM, so `WeatherObservation.toWindToward()` adds 180 degrees to give the direction fire is pushed. `WeatherMonitor` refreshes every 10 minutes and treats a reading older than 90 minutes as stale.

**Reported fires.** `FireDataApiClient` reads CAL FIRE incident reports and NASA FIRMS (VIIRS) satellite detections for northern California. FIRMS detections are limited to the last 24 hours. `FireObservationMonitor` refreshes every 15 minutes and marks a feed stale after 30 minutes. If a source fails, the last good data is kept until it becomes stale, and the problem is reported.

### What other modules use

| Need | Call |
|---|---|
| Latest observation | `WeatherMonitor.getLatest()` |
| Fresh wind for the simulation | `WeatherMonitor.getFreshWindAt(Instant.now())` returns `Optional<Wind>` |
| Change the location | `WeatherMonitor.setLocation(latitude, longitude)` |
| Reported fires | `FireObservationMonitor.getCalFireFeed()` and `getFirmsFeed()` return an `ObservationFeed` |
| Place a reported fire on the grid | `FireGridMapper.toCell(center, fire, rows, columns, cellSizeMeters)` |

### Setup

NASA FIRMS needs a free MAP_KEY from https://firms.modaps.eosdis.nasa.gov/api/map_key/. Set it as an environment variable before running. Do not put the key in code or commit it.

```bash
export NASA_FIRMS_MAP_KEY=your_key_here
```

Without the key, CAL FIRE and weather still work and the FIRMS feed shows as not configured. To use a different CAL FIRE layer, set `CAL_FIRE_LAYER_URL`.

### Run the tests

From the `wildfire-readiness` folder:

```bash
javac -d out SRC/*.java TEST/*.java
java -cp out ExternalDataIntegrationTest          # offline, uses a local fake server
java -cp out ExternalDataIntegrationTest --live   # calls the real services
java -cp out StationFallbackTest                  # five station cases
java -cp out RecentOnlyTest                       # 24-hour FIRMS filter
```

The offline test checks parsing, stale data, failures, and that reported fires do not modify simulated cell state or advance simulation time.

### Known limits

- One weather point stands for the whole map.
- The CAL FIRE data comes from a public map layer with no official documentation. Its records are mostly small recent incidents, and the source can change.
- FIRMS detections are satellite heat signals, not confirmed wildfires.
- Wind speed units were checked against the service's `unitCode` (km/h).
