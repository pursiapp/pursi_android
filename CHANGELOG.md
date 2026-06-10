# Changelog

## [0.5.0] — 2026-06-08

Initial pre-release version.

### Features
- Traficom WMTS nautical charts (veneilymaps, harbour maps) with adjustable opacity
- Night mode filter for charts
- GPS location, speed (kn/km/h/mph), heading, adaptive course lines
- FMI weather: Conditions tab (nearest stations, sparklines), Forecast tab (HARMONIE 5-day), Warnings tab (CAP feed)
- Wave buoy observations (height, direction, period, water temperature)
- Astronomy: sunrise/sunset, moonrise/moonset, moon phase, UV index
- Water level from nearest station
- AIS vessel tracking via Digitraffic
- Route recording with pulsing indicator, GPX export via FileProvider
- Route planning: long-press waypoints, dashed line with distance and ETA, undo
- Saved routes: green dashed lines and dots on map
- Boat profiles: add, edit, delete with default boat selection
- OpenStreetMap-based seamarks
- Nominatim geocoding (search)
- Distance measurement (two-finger long-press)
- Crosshair coordinate display
- Camera position saved between sessions
- Offline tile caching with region picker
- Auto offline mode when no network
- Station favourites (star toggle, pinned to top)
- Chart layer overlays (OpenSeaMap, AIS, wind, depth, radar, algae)
- Languages: suomi, svenska, English

### Technical
- Kotlin + Jetpack Compose
- MapLibre Native 13.2.0
- Room database for local persistence
- Dagger Hilt for dependency injection
- Umami self-hosted analytics (opt-in)
