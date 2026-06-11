# Pursi — Marine Navigation for Android

![License](https://img.shields.io/badge/license-GPL%20v3-blue)
![Platform](https://img.shields.io/badge/platform-Android%2024%2B-brightgreen)
![Kotlin](https://img.shields.io/badge/language-Kotlin%20%2B%20Compose-purple)
![Version](https://img.shields.io/badge/version-0.5.0-blue)

Pursi is a free, open-source Android marine navigation app for recreational
boaters. It uses official Finnish nautical charts (Traficom), FMI weather
data, and global OpenSeaMap seamark overlays. The app is designed with
multi-source extensibility — support for other countries' data can be
added as new chart/weather providers.

**License**: GPL v3-or-later  
**Platform**: Android 8+ (API 24+) — phones and tablets  
**Tech**: Kotlin + Jetpack Compose, MapLibre Native 13.2.0, Room DB, Retrofit

---

## Features

- **Nautical Charts** — Traficom (Finland WMTS boating & harbour charts)
- **Global Seamark Overlay** — OpenSeaMap buoys, lights, marks worldwide
- **GPS Navigation** — position, speed (kn/km/h/mph), heading, course lines
- **Offline Charts** — cache tile regions for areas without connectivity
- **Weather** — FMI (Finland) — conditions, forecasts, warnings
- **Radar** — FMI and RainViewer global precipitation overlay
- **AIS Vessel Tracking** — real-time traffic via Digitraffic (Finland)
- **Route Recording** — GPX track export via FileProvider
- **Route Planning** — long-press waypoints, dashed line, distance, ETA
- **Saved Routes** — green dashed lines and dots on the map
- **Boat Profiles** — add/edit/delete with speed and fuel consumption
- **Navigation Aids** — Väylävirasto fairways, lights, marks, depth data
- **Distance Measurement** — two-finger long-press (600ms)
- **Coordinates Display** — crosshair press, 5-second display
- **Astronomy** — sunrise/sunset, moonrise/moonset, moon phase, UV index
- **Search** — Nominatim geocoding with tap-to-center
- **Cyanobacteria Reports** — view and submit algae observations (Järviwiki)
- **i18n** — Suomi, Svenska, English

---

## Regional Coverage

| Region | Charts | Weather | AIS | Status |
|--------|--------|---------|-----|--------|
| Finland | Traficom WMTS | FMI warnings, forecast, radar | Digitraffic | **Active** |
| Sweden | — | — | — | Planned |
| Norway | — | — | — | Planned |
| Denmark | — | — | — | Planned |
| Global | OpenSeaMap seamarks, OpenFreeMap basemap, RainViewer radar | — | — | Active |

---

## Building

### Requirements
- Android Studio or command-line build tools
- JDK 17+
- Android SDK API 35

### Debug APK
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release AAB
```bash
./gradlew bundleRelease
```
AAB: `app/build/outputs/bundle/release/app-release.aab`

Release signing is configured via `local.properties` or environment variables:
- `signing.keystore.path` / `SIGNING_KEYSTORE_PATH`
- `signing.keystore.password` / `SIGNING_KEYSTORE_PASSWORD`
- `signing.key.alias` / `SIGNING_KEY_ALIAS`
- `signing.key.password` / `SIGNING_KEY_PASSWORD`

Keystore location (local dev): `.keystore/release.keystore` (gitignored)

---

## Project Structure

```
pursi/
├── app/src/main/java/app/pursi/
│   ├── MainActivity.kt
│   ├── PursiApplication.kt
│   ├── map/          # MapLibre views, chart layers, tile cache, geocoding
│   ├── location/     # GPS service, track recorder, speed calculator
│   ├── navigation/   # waypoints, routes, boat profiles, safety monitor
│   ├── weather/      # FMI client, weather data, HUD, warnings
│   ├── ais/          # Vessel tracking & display
│   ├── data/         # Room DB, DAOs, models
│   ├── datasource/   # Chart, weather, warning providers (FI, SE, NO, DK, global)
│   └── ui/           # Compose screens & components
├── app/src/main/res/
│   ├── values/       # English strings
│   ├── values-fi/    # Finnish strings
│   └── values-sv/    # Swedish strings
└── scripts/          # Tile generation & conversion utilities
```

---

## Data Sources

| Agency | Region | Data | License |
|--------|--------|------|---------|
| Traficom | Finland | Nautical charts (WMTS), depth, seamarks | CC BY 4.0 |
| Väylävirasto | Finland | Fairways, signs, lights | CC BY 4.0 |
| FMI | Finland | Weather, warnings, radar, waves | CC BY 4.0 |
| Digitraffic | Finland | AIS vessel traffic | CC BY 4.0 |
| OpenSeaMap | Global | Seamarks | ODbL |
| OpenFreeMap | Global | Vector basemap | ODbL |
| RainViewer | Global | Radar overlay | © RainViewer |
| Nominatim | Global | Geocoding | ODbL |

Support for additional countries (Sweden, Norway, Denmark, EMODnet) is
planned in the provider architecture — contributions welcome.

---

## Contributing

Contributions, bug reports, and feature requests are welcome. The project is
under active development and benefits from community input.

See the [issue tracker](https://github.com/pursiapp/pursi_android/issues) for known
issues and planned features.

---

## License

Copyright 2026 The Pursi Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
