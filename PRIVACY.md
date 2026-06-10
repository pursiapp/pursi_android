# Privacy Policy — Pursi

**Last updated:** June 2026

Pursi is a free, open-source marine navigation app for recreational boating in Finnish waters, with global OpenSeaMap seamark overlays. This policy explains what data the app collects and how it is used.

## Data Collected

### Location Data
Pursi requests precise GPS location to:
- Show your boat's position on the nautical chart
- Display speed, heading, and course lines
- Fetch local weather data (FMI), wave observations (FMI), water level data (FMI), and AIS vessel traffic (Digitraffic)
- Record tracks and routes
- Power the anchor alarm and man-overboard features

Location data is processed **on-device** and sent to third-party APIs solely for fetching relevant nearby data (weather, warnings, depth, seamarks). It is **not stored on any server** by Pursi.

During route recording, GPS coordinates are saved locally in the device's Room database and are never transmitted automatically. GPX export is initiated manually by the user.

### Device Information
Pursi uses a self-hosted Umami analytics instance to collect anonymous usage statistics, including:
- App version and Android version
- Device model and screen resolution
- Language setting
- Session duration and feature usage (which tabs are opened)

Umami is privacy-focused analytics software. No personal data (IP addresses, unique identifiers, cookies) is collected or stored. You can disable analytics at any time in Settings. When disabled, no data is sent.

### Crash Reports
When the app crashes, a stack trace and app version information are sent to the same Umami analytics instance for debugging purposes. No personal data is included. Crash reporting can be disabled together with analytics in Settings.

## Third-Party Services

Pursi communicates with the following services to function:

| Service | Data sent | Purpose | Privacy policy |
|---------|-----------|---------|----------------|
| **FMI (Finnish Meteorological Institute)** | GPS coordinates | Weather, warnings, wave buoy, and water level data | https://en.ilmatieteenlaitos.fi/privacy |
| **Traficom WMTS** | Tile coordinates (not personal) | Nautical charts | https://www.traficom.fi/en/privacy |
| **Digitraffic / Fintraffic** | GPS coordinates | AIS vessel traffic | https://www.digitraffic.fi/en/privacy/ |
| **OpenStreetMap / Nominatim** | Search query | Geocoding (search for locations) | https://wiki.osmfoundation.org/wiki/Privacy_Policy |
| **Overpass API** | Bounding box | OpenStreetMap seamark data | https://wiki.osmfoundation.org/wiki/Privacy_Policy |
| **Met Norway / SMHI** | GPS coordinates | Weather data | https://www.met.no/en/privacypolicy |
| **Järviwiki** | None (WebView) | Cyanobacteria observation report | https://www.jarviwiki.fi |

## Data Storage

- **Route recordings, waypoints, boat profiles, saved routes** — stored locally on device in an encrypted Room database
- **Offline map tiles** — stored locally as cached image files
- **Settings and preferences** — stored in SharedPreferences on device
- All local data is deleted when the app is uninstalled

## Third-Party Analytics

Pursi uses a **self-hosted** Umami instance. Umami does not use cookies, does not track users across sites, and does not collect personally identifiable information. The server is located in the EU.

## Your Rights (GDPR)

Since Pursi does not collect or store personal data on any server, there is no personal data for us to delete or export. All your data is on your device and can be removed by:
- Clearing app data (Android Settings → Apps → Pursi → Storage → Clear data)
- Uninstalling the app

If you have questions about this policy, please open an issue at:
https://github.com/pursiapp/pursi-android/issues

## Changes to This Policy

Policy updates will be posted here and reflected in the app version. Continued use after changes constitutes acceptance of the updated policy.

---

**Suomeksi:** Tietosuojaseloste löytyy osoitteesta https://github.com/pursiapp/pursi-android/blob/main/PRIVACY.md
