package app.pursi.ui.screens

import android.content.Context
import android.location.Location
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pursi.data.model.RouteWaypoint
import app.pursi.data.model.SavedRoute
import app.pursi.ui.components.*
import app.pursi.ui.components.RestoreStrip
import app.pursi.ui.components.SplitterHandle
import app.pursi.weather.WeatherUnitPrefs
import app.pursi.weather.currentForecastPoint
import app.pursi.data.model.TrackSummary
import app.pursi.location.BearingSmoother
import app.pursi.location.SpeedSmoother
import app.pursi.location.SpeedUnit
import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.Regions
import app.pursi.map.PursiMapView
import app.pursi.ui.viewmodel.FollowMode
import app.pursi.ui.viewmodel.MapPaneState
import app.pursi.ui.viewmodel.MapUiState
import app.pursi.ui.viewmodel.MapViewModel
import app.pursi.ui.viewmodel.PaneChartMode
import app.pursi.ui.viewmodel.PaneLayerState
import app.pursi.ui.viewmodel.SplitOrientation
import app.pursi.ui.viewmodel.SeamarkDetail
import app.pursi.ui.viewmodel.SeamarkSource
import app.pursi.DEFAULT_LAT
import app.pursi.DEFAULT_LON
import org.maplibre.android.maps.MapLibreMap
import app.pursi.ui.viewmodel.OrientationMode
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val PREFS_NAME = "pursi_map"
private const val KEY_CHART_OPACITY = "chart_opacity"

/**
 * OpenStreetMap / OpenSeaMap click fallback. Queries the rendered MapLibre layers
 * around the click point for a seamark feature that has no `_vv_id` (i.e. not
 * Väylävirasto data) and returns a [SeamarkDetail] built from its properties.
 *
 * Returns null when no OSM seamark is at the click point. Callers should always
 * first try [MapViewModel.findSeamarkAt] to prefer national/marine data; this
 * helper is the fallback for outside-Finland (or OSM-only) sea marks.
 */
private fun buildOsmSeamarkAt(map: MapLibreMap, clickLat: Double, clickLng: Double): SeamarkDetail? {
    fun humanReadableOsmSeamarkType(osmType: String): String = when (osmType) {
        "beacon_cardinal" -> "Viittamerkki"
        "beacon_isolated_danger" -> "Karioviitta"
        "beacon_lateral" -> "Lateraaliviitta"
        "beacon_safe_water" -> "Turvavesiviitta"
        "beacon_special_purpose" -> "Erikoisviitta"
        "buoy_cardinal" -> "Viittapoiju"
        "buoy_isolated_danger" -> "Kariopoiju"
        "buoy_lateral" -> "Lateraalipoiju"
        "buoy_safe_water" -> "Turvavesipoiju"
        "buoy_special_purpose" -> "Erikoispoiju"
        "buoy_installation" -> "Poiju (asennus)"
        "light_major" -> "Majakka"
        "light_minor" -> "Valo"
        "light_vessel" -> "Valolaiva"
        "daymark" -> "Päivämerkki"
        "fog_signal" -> "Sumumerkki"
        "wreck" -> "Hylky"
        "hulk" -> "Hylky (hylätty alus)"
        "pontoon" -> "Ponttoni"
        "mooring" -> "Kiinnityspoiju"
        "signal_station_warning" -> "Varoitusasema"
        else -> osmType.replace("_", " ")
    }
    val sp = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(clickLat, clickLng))
    val rect = android.graphics.RectF(sp.x - 20f, sp.y - 20f, sp.x + 20f, sp.y + 20f)
    val allHit = map.queryRenderedFeatures(rect)
    val osmHit = allHit.firstOrNull { feat ->
        feat.getNumberProperty("_vv_id") == null &&
            (feat.getStringProperty("seamark:type") != null ||
                feat.getStringProperty("seamark:name") != null)
    } ?: return null
    val osmType = osmHit.getStringProperty("seamark:type") ?: ""
    val name = osmHit.getStringProperty("seamark:name")
        ?: osmHit.getStringProperty("ref")
        ?: osmHit.getStringProperty("name")
        ?: ""
    val hasLight = osmHit.getStringProperty("seamark:light:character") != null ||
        osmHit.getStringProperty("seamark:light:colour") != null
    val lightParts = mutableListOf<String>()
    osmHit.getStringProperty("seamark:light:character")?.let { lightParts.add(it) }
    osmHit.getStringProperty("seamark:light:colour")?.let { lightParts.add(it) }
    osmHit.getStringProperty("seamark:light:period")?.let { lightParts.add("${it}s") }
    return SeamarkDetail(
        source = SeamarkSource.OSM,
        name = name,
        typeLabel = humanReadableOsmSeamarkType(osmType),
        hasLight = hasLight,
        lightCharacteristic = lightParts.joinToString(" ").takeIf { it.isNotBlank() },
        description = osmType.replace("_", " "),
        extraInfo = listOf("OpenSeaMap"),
        latitude = clickLat,
        longitude = clickLng
    )
}

private fun MapUiState.toPaneLayerState() = PaneLayerState(
    chartMode = PaneChartMode.Auto,
    showLightning = showLightning,
    showWarnings = showWarnings,
    showRadar = showRadar,
    radarTimeOffset = radarTimeOffset,
    radarOpacity = radarOpacity,
    showAis = showAis,
    showAlgae = showAlgae,
    showDepth = showDepth,
    showWindMeter = showWindMeter,
    showVvNavmarks = fiState?.showVvNavmarks ?: true,
    chartOpacity = chartOpacity,
    navmarkSize = navmarkSize,
    boatIconSize = boatIconSize,
    boatIconColor = boatIconColor,
)

@Composable
fun MapScreen(
    mapViewModel: MapViewModel,
    courseLinesEnabled: Boolean = true,
    bottomInsetPx: androidx.compose.ui.unit.Dp = 0.dp,
    offlineMode: Boolean = false,
    tilesDirPath: String? = null,
    centerTarget: LatLng? = null,
    poiMarker: LatLng? = null,
    onClearPoiMarker: () -> Unit = {},
    onSearchPoi: () -> Unit = {},
    initialCamLat: Double = Double.NaN,
    initialCamLon: Double = Double.NaN,
    initialCamZoom: Double = 7.0,
    onCameraMoved: (Double, Double, Double) -> Unit = { _, _, _ -> },
    viewingRouteId: String? = null,
    onClearViewingRoute: () -> Unit = {},
    viewingTrackId: String? = null,
    onClearViewingTrack: () -> Unit = {},
    showDebug: Boolean = false,
    isNightMode: Boolean = false,
    onNavigateToWarnings: () -> Unit = {},
    windUnit: WeatherUnitPrefs.WindUnit = WeatherUnitPrefs.WindUnit.MS,
    tempUnit: WeatherUnitPrefs.TempUnit = WeatherUnitPrefs.TempUnit.CELSIUS,
    pressureUnit: WeatherUnitPrefs.PressureUnit = WeatherUnitPrefs.PressureUnit.HPA,
    windMeterSize: WeatherUnitPrefs.WindMeterSize = WeatherUnitPrefs.WindMeterSize.AUTO,
    snackbarHostState: SnackbarHostState? = null,
    downloadManager: app.pursi.map.DownloadManager? = null,
    pmtilesDownloader: app.pursi.map.PmtilesDownloader? = null,
    vvDataDownloader: app.pursi.map.VvDataDownloader? = null,
    tileSources: List<app.pursi.map.TileSource>? = null,
    onChooseCustom: (() -> Unit)? = null
) {
    val location by mapViewModel.currentLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRecording by mapViewModel.trackRecorder.isRecording.collectAsStateWithLifecycle()
    val recordedPoints by mapViewModel.trackRecorder.recordedPoints.collectAsStateWithLifecycle()
    val warnings by mapViewModel.warnings.collectAsStateWithLifecycle()
    val lightningStrikes by mapViewModel.lightning.collectAsStateWithLifecycle()
    val lightningMode by mapViewModel.lightningMode.collectAsStateWithLifecycle()
    val vessels by mapViewModel.vessels.collectAsStateWithLifecycle()
    val aisMetadata by mapViewModel.aisMetadata.collectAsStateWithLifecycle()
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val forecast by mapViewModel.forecast.collectAsStateWithLifecycle()
    val radarProvider by mapViewModel.currentRadarProvider.collectAsStateWithLifecycle()
    val nearestWind = currentForecastPoint(forecast)
    val windSpeedMs = nearestWind?.windSpeedMs
    val windDirectionDeg = nearestWind?.windDirectionDeg
    val temperatureC = nearestWind?.temperatureC
    val pressureHPa = nearestWind?.pressureHPa

    val lastKnownBearing by mapViewModel.lastKnownBearing.collectAsStateWithLifecycle()
    var currentZoom by remember { mutableStateOf(initialCamZoom) }

    var localPoiMarker by remember { mutableStateOf<LatLng?>(null) }
    var localCenterTarget by remember { mutableStateOf<LatLng?>(null) }
    val effectivePoiMarker = poiMarker ?: localPoiMarker
    val effectiveCenterTarget = centerTarget ?: localCenterTarget
    LaunchedEffect(centerTarget) {
        localPoiMarker = centerTarget
    }
    val searchTarget by mapViewModel.searchTarget.collectAsState()
    LaunchedEffect(searchTarget) {
        val target = searchTarget ?: return@LaunchedEffect
        localPoiMarker = target
        localCenterTarget = target
        mapViewModel.clearSearchTarget()
    }

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var showOnboarding by remember {
        mutableStateOf(!prefs.getBoolean("onboarding_shown", false))
    }
    val scope = rememberCoroutineScope()

    val currentTrackId by mapViewModel.trackRecorder.currentTrackId.collectAsState()

    var trailCoordinates by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    LaunchedEffect(isRecording, currentTrackId) {
        val trackId = currentTrackId ?: return@LaunchedEffect
        if (isRecording) {
            mapViewModel.trackRecorder.getTrackPointsFlow(trackId).collect { points ->
                trailCoordinates = points.map { LatLng(it.latitude, it.longitude) }
            }
        }
    }

    var recordingStartTime by rememberSaveable { mutableStateOf(0L) }
    var recordingElapsed by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            if (recordingStartTime == 0L) recordingStartTime = System.currentTimeMillis()
            while (true) {
                recordingElapsed = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                kotlinx.coroutines.delay(1000L)
            }
        } else {
            recordingElapsed = 0
            recordingStartTime = 0L
        }
    }

    val recordingDistanceNm = remember(trailCoordinates) {
        if (trailCoordinates.size < 2) 0.0
        else {
            var d = 0.0
            for (i in 1 until trailCoordinates.size) {
                d += app.pursi.location.SpeedCalculator.distanceNm(
                    trailCoordinates[i - 1].latitude, trailCoordinates[i - 1].longitude,
                    trailCoordinates[i].latitude, trailCoordinates[i].longitude
                )
            }
            d
        }
    }

    val speedSmoother = remember { SpeedSmoother() }
    val rawSpeed = location?.speed ?: 0f
    val smoothedSpeed = remember(rawSpeed) { speedSmoother.update(rawSpeed) }

    val lookAheadTarget = if (smoothedSpeed > 0.5f) 0.25f else 0f
    val lookAheadFactor by animateFloatAsState(
        targetValue = lookAheadTarget,
        animationSpec = tween(durationMillis = 2000),
        label = "lookAhead"
    )

    val bearingSmoother = remember { BearingSmoother() }
    val rawBearing = location?.bearing ?: 0f
    val smoothedBearing = if (location != null) {
        bearingSmoother.update(rawBearing, rawSpeed)
    } else {
        rawBearing
    }



    var speedUnit by rememberSaveable { mutableStateOf(WeatherUnitPrefs.speedUnit(prefs)) }
    var chartOpacity by rememberSaveable {
        mutableStateOf(prefs.getFloat(KEY_CHART_OPACITY, 0.2f))
    }
    var showLayersPanel by rememberSaveable { mutableStateOf(false) }
    var showRadarSlider by rememberSaveable { mutableStateOf(false) }
    var centerTrigger by rememberSaveable { mutableStateOf(0) }
    var zoomToBoatTrigger by remember { mutableStateOf(0) }
    var zoomToBoatLevel by remember { mutableStateOf(initialCamZoom.toFloat()) }

    // Per-pane local state for split-screen second pane
    var pane2CenterTrigger by remember { mutableStateOf(0) }
    var pane2ZoomToBoatTrigger by remember { mutableStateOf(0) }
    var pane2ZoomToBoatLevel by remember { mutableStateOf(initialCamZoom.toFloat()) }

    // Per-pane layer state (independently toggleable in split mode)
    var paneALayerState by remember { mutableStateOf(uiState.toPaneLayerState()) }
    var paneBLayerState by remember { mutableStateOf(uiState.toPaneLayerState()) }
    // Which pane's layers panel is open: null = closed, true = pane A, false = pane B
    var layersTargetPane by remember { mutableStateOf<Boolean?>(null) }

    var mapBearing by remember { mutableStateOf(0f) }
    var pane2Bearing by remember { mutableStateOf(0f) }
    var viewportBounds by remember { mutableStateOf<BoundingBox?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    // Route planning state (persisted across rotation via saveable)
    var routeWaypoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var showRouteSaveDialog by rememberSaveable { mutableStateOf(false) }
    var routeName by rememberSaveable { mutableStateOf("") }
    var selectedVesselMmsi by remember { mutableStateOf<Int?>(null) }

    var showAlgaeReport by remember { mutableStateOf(false) }
    var algaeReportLocation by remember { mutableStateOf<LatLng?>(null) }

    var measureMode by rememberSaveable { mutableStateOf(false) }
    var measurePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var twoFingerMeasure by remember { mutableStateOf<Float?>(null) }
    var measureLinePoints by remember { mutableStateOf<Pair<LatLng, LatLng>?>(null) }
    var measureActive by remember { mutableStateOf(false) }
    var showCoords by rememberSaveable { mutableStateOf(0) }

    var mockLocationPending by remember { mutableStateOf(false) }
    val isMockLocation by mapViewModel.isMockLocation.collectAsStateWithLifecycle()


    var orientationLabel by remember { mutableStateOf<String?>(null) }
    val orientationLabelText = when (uiState.orientationMode) {
        OrientationMode.NORTH_UP -> stringResource(app.pursi.R.string.nav_north_up)
        OrientationMode.COURSE_UP -> stringResource(app.pursi.R.string.nav_course_up)
    }
    LaunchedEffect(uiState.orientationMode) {
        orientationLabel = orientationLabelText
        kotlinx.coroutines.delay(2000)
        if (orientationLabel != null) orientationLabel = null
    }



    val allBoats by mapViewModel.boatDao.getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val defaultBoat = allBoats.firstOrNull { it.isDefault }

    var viewingRouteWaypoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var viewingRouteName by remember { mutableStateOf("") }

    LaunchedEffect(viewingRouteId) {
        val routeId = viewingRouteId
        if (routeId == null) {
            viewingRouteWaypoints = emptyList()
            viewingRouteName = ""
        } else {
            val route = mapViewModel.savedRouteDao.getById(routeId)
            if (route != null) {
                viewingRouteName = route.name
            }
            val wps = mapViewModel.savedRouteDao.getWaypointsSync(routeId)
            viewingRouteWaypoints = wps.map { LatLng(it.latitude, it.longitude) }
        }
    }

    var viewingTrackCoordinates by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var viewingTrackName by remember { mutableStateOf("") }

    LaunchedEffect(viewingTrackId) {
        val trackId = viewingTrackId
        if (trackId == null) {
            viewingTrackCoordinates = emptyList()
            viewingTrackName = ""
        } else {
            val summary = mapViewModel.trackSummaryDao.getById(trackId)
            if (summary != null) {
                viewingTrackName = summary.name
            }
            val pts = mapViewModel.trackDao.getTrackPointsSync(trackId)
            viewingTrackCoordinates = pts.map { LatLng(it.latitude, it.longitude) }
        }
    }

    val fallbackLocation = remember {
        android.location.Location("").apply {
            latitude = DEFAULT_LAT
            longitude = DEFAULT_LON
        }
    }
    val effectiveLocation = location ?: fallbackLocation

    val loc = location
    val relevantWarnings = if (loc != null) {
        warnings.filter { it.polygonCoords.isEmpty() || app.pursi.weather.pointInPolygon(loc.latitude, loc.longitude, it.polygonCoords) }
    } else {
        warnings.filter { it.polygonCoords.isEmpty() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val mapLocation = LatLng(effectiveLocation.latitude, effectiveLocation.longitude)
        val useSplit = uiState.splitMode

        // Auto-swap split orientation on device rotation
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        LaunchedEffect(isLandscape, useSplit) {
            if (useSplit) {
                val desired = if (isLandscape) SplitOrientation.Vertical else SplitOrientation.Horizontal
                if (uiState.splitOrientation != desired) {
                    mapViewModel.setSplitOrientation(desired)
                }
            }
        }

        val mapPaneParams: @Composable (MapPaneState, (Float) -> Unit) -> Unit = { paneState, camBearingCallback ->
            PursiMapView(
                modifier = Modifier.fillMaxSize(),
                paneState = paneState,
                chartOpacity = paneState.paneLayerState.chartOpacity,
                offlineMode = offlineMode,
                tilesDirPath = tilesDirPath,
                chartProviders = uiState.chartProviders,
                location = mapLocation,
                bearingDeg = smoothedBearing,
                speedMps = smoothedSpeed,
                courseLineMinutes = if (courseLinesEnabled) {
                    val kn = smoothedSpeed / 0.514f
                    when {
                        kn < 3f -> listOf(10, 20)
                        kn < 10f -> listOf(5, 15)
                        kn < 20f -> listOf(2, 10)
                        else -> listOf(1, 5)
                    }
                } else emptyList(),
                recordingTrail = when {
                    isRecording -> trailCoordinates
                    viewingTrackId != null -> viewingTrackCoordinates
                    else -> emptyList()
                },
                routeWaypoints = if (routeWaypoints.isNotEmpty()) routeWaypoints else viewingRouteWaypoints,
                savedRouteLines = if (viewingRouteWaypoints.isNotEmpty()) listOf(viewingRouteWaypoints) else emptyList(),
                measureLinePoints = measureLinePoints,
                centerTarget = effectiveCenterTarget,
                poiMarker = effectivePoiMarker,
                onClearPoiMarker = {
                    localPoiMarker = null
                    onClearPoiMarker()
                },
                onCameraMoved = { lat, lon, zoom ->
                    currentZoom = zoom
                    onCameraMoved(lat, lon, zoom)
                },
                onCameraIdle = { sw, ne ->
                    mapViewModel.setCameraTarget(
                        (sw.latitude + ne.latitude) / 2.0,
                        (sw.longitude + ne.longitude) / 2.0
                    )
                    mapViewModel.setCameraBounds(
                        sw.latitude, sw.longitude, ne.latitude, ne.longitude, currentZoom
                    )
                    viewportBounds = BoundingBox(sw.latitude, ne.latitude, sw.longitude, ne.longitude)
                },
                onUserPan = { mapViewModel.setFollowMode(FollowMode.OFF) },
                onCameraBearingChanged = camBearingCallback,
                lastKnownBearing = lastKnownBearing,
                lookAheadFactor = lookAheadFactor,
                lookAheadSec = uiState.lookAheadSec,
                showLightning = paneState.paneLayerState.showLightning,
                showWarnings = paneState.paneLayerState.showWarnings,
                showRadar = paneState.paneLayerState.showRadar,
                radarTimeOffset = paneState.paneLayerState.radarTimeOffset,
                radarOpacity = paneState.paneLayerState.radarOpacity,
                radarProvider = radarProvider,
                onRadarEffectiveDelay = { mapViewModel.setRadarEffectiveDelay(it) },
                lightningStrikes = lightningStrikes,
                warnings = warnings,
                showAis = paneState.paneLayerState.showAis,
                vessels = vessels,
                seamarksDownloaded = uiState.seamarksDownloaded,
                showAlgae = paneState.paneLayerState.showAlgae,
                waterObservations = uiState.waterObservations,
                showDepth = paneState.paneLayerState.showDepth,
                depthFeatures = uiState.depthFeatures,
                emodnetDepthSamples = uiState.emodnetDepthSamples,
                navmarkSize = paneState.paneLayerState.navmarkSize,
                boatIconSize = paneState.paneLayerState.boatIconSize,
                boatIconColor = paneState.paneLayerState.boatIconColor,
                isNightMode = isNightMode,
                showVvNavmarks = paneState.paneLayerState.showVvNavmarks,
                turvalaiteFeatures = uiState.fiState?.turvalaiteFeatures ?: emptyMap(),
                turvalaitevikaFeatures = uiState.fiState?.turvalaitevikaFeatures ?: emptyMap(),
                valosektoriFeatures = uiState.fiState?.valosektoriFeatures ?: emptyMap(),
                vesiliikennemerkkiFeatures = uiState.fiState?.vesiliikennemerkkiFeatures ?: emptyMap(),
                navlineFeatures = uiState.fiState?.navlineFeatures ?: emptyMap(),
                fairwayFeatures = uiState.fiState?.fairwayFeatures ?: emptyMap(),
                vvUsingNetwork = uiState.fiState?.vvUsingNetwork ?: false,
                vvFetchCounter = uiState.fiState?.vvFetchCounter ?: 0,
                showSectors = mapViewModel.isSectorVisible(isNightMode),
                viewportBounds = viewportBounds,
                onSeamarkClick = { lat, lng ->
                    var detail = mapViewModel.findSeamarkAt(lat, lng)
                    if (detail == null) {
                        val map = mapRef
                        val osmDetail = map?.let { buildOsmSeamarkAt(it, lat, lng) }
                        if (osmDetail != null) {
                            val vvOverride = mapViewModel.findSeamarkAt(osmDetail.latitude, osmDetail.longitude)
                            detail = vvOverride ?: osmDetail
                        }
                    }
                    if (detail != null) {
                        mapViewModel.selectSeamark(detail)
                    } else {
                        if (mockLocationPending) {
                            mapViewModel.setMockLocation(lat, lng)
                            mockLocationPending = false
                        } else {
                            val ll = LatLng(lat, lng)
                            if (measureMode) {
                                measurePoints = if (measurePoints.size >= 2) listOf(ll) else measurePoints + ll
                            }
                            if (selectedVesselMmsi != null) selectedVesselMmsi = null
                            mapViewModel.clearSeamark()
                        }
                    }
                },
                onAlgaeObservationClick = { idx ->
                    val clicked = uiState.waterObservations.getOrNull(idx)
                    if (clicked != null) {
                        val key = "%.5f_%.5f".format(clicked.latitude, clicked.longitude)
                        val same = uiState.waterObservations.filter {
                            "%.5f_%.5f".format(it.latitude, it.longitude) == key
                        }
                        mapViewModel.selectAlgaeObservations(same)
                    }
                },
                onMapReady = { map -> mapRef = map },
                onMapClick = { },
                onVesselClick = { mmsi ->
                    selectedVesselMmsi = if (selectedVesselMmsi == mmsi) null else mmsi
                    mapViewModel.fetchVesselMetadata(mmsi)
                },
                onLongPress = { latlng ->
                    routeWaypoints = routeWaypoints + latlng
                },
                onTwoFingerMeasure = { p1, p2 ->
                    val d = app.pursi.location.SpeedCalculator.distanceBetween(
                        p1.latitude, p1.longitude, p2.latitude, p2.longitude
                    )
                    twoFingerMeasure = d.toFloat()
                    measureLinePoints = Pair(p1, p2)
                    measureActive = true
                },
                onMeasureEnd = {
                    measureActive = false
                }
            )
        }

        if (!useSplit) {
            mapPaneParams(
                MapPaneState(
                    centerTrigger = centerTrigger,
                    zoomToBoatTrigger = zoomToBoatTrigger,
                    zoomToBoatLevel = zoomToBoatLevel,
                    followMode = uiState.followMode,
                    orientationMode = uiState.orientationMode,
                    initialCamLat = initialCamLat,
                    initialCamLon = initialCamLon,
                    initialCamZoom = initialCamZoom,
                    paneLayerState = uiState.toPaneLayerState(),
                )
            ) { mapBearing = it }
        } else {
            val orientation = uiState.splitOrientation
            val fraction = uiState.splitFraction
            val animFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 200),
                label = "splitAnim"
            )

            val isCollapsed = fraction == 0f || fraction == 1f
            val paneACollapsed = fraction == 0f

            val onSwapPanes = {
                val tmpCt = centerTrigger; centerTrigger = pane2CenterTrigger; pane2CenterTrigger = tmpCt
                val tmpZt = zoomToBoatTrigger; zoomToBoatTrigger = pane2ZoomToBoatTrigger; pane2ZoomToBoatTrigger = tmpZt
                val tmpZl = zoomToBoatLevel; zoomToBoatLevel = pane2ZoomToBoatLevel; pane2ZoomToBoatLevel = tmpZl
            }

            if (isCollapsed) {
                Box(Modifier.fillMaxSize()) {
                    if (paneACollapsed) {
                        Box(Modifier.fillMaxSize()) {
                            mapPaneParams(
                                MapPaneState(
                                    centerTrigger = pane2CenterTrigger,
                                    zoomToBoatTrigger = pane2ZoomToBoatTrigger,
                                    zoomToBoatLevel = pane2ZoomToBoatLevel,
                                    followMode = uiState.followMode,
                                    orientationMode = uiState.orientationMode,
                                    initialCamLat = initialCamLat,
                                    initialCamLon = initialCamLon,
                                    initialCamZoom = initialCamZoom,
                                    paneLayerState = paneBLayerState,
                                )
                            ) { pane2Bearing = it }
                            PaneControls(
                                paneBearing = pane2Bearing,
                                onZoomIn = {
                                    pane2ZoomToBoatLevel = (pane2ZoomToBoatLevel + 0.5f).coerceAtMost(18f)
                                    pane2ZoomToBoatTrigger++
                                },
                                onZoomOut = {
                                    pane2ZoomToBoatLevel = (pane2ZoomToBoatLevel - 0.5f).coerceAtLeast(4f)
                                    pane2ZoomToBoatTrigger++
                                },
                                onCenterLocation = {
                                    mapViewModel.centerOnLocation()
                                    pane2CenterTrigger++
                                },
                                onCompassClick = { mapViewModel.cycleOrientationMode() },
                                onLayersToggle = { layersTargetPane = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            mapPaneParams(
                                MapPaneState(
                                    centerTrigger = centerTrigger,
                                    zoomToBoatTrigger = zoomToBoatTrigger,
                                    zoomToBoatLevel = zoomToBoatLevel,
                                    followMode = uiState.followMode,
                                    orientationMode = uiState.orientationMode,
                                    initialCamLat = initialCamLat,
                                    initialCamLon = initialCamLon,
                                    initialCamZoom = initialCamZoom,
                                    paneLayerState = paneALayerState,
                                )
                            ) { mapBearing = it }
                            PaneControls(
                                paneBearing = mapBearing,
                                onZoomIn = {
                                    zoomToBoatLevel = (currentZoom.toFloat() + 0.5f).coerceAtMost(18f)
                                    zoomToBoatTrigger++
                                },
                                onZoomOut = {
                                    zoomToBoatLevel = (currentZoom.toFloat() - 0.5f).coerceAtLeast(4f)
                                    zoomToBoatTrigger++
                                },
                                onCenterLocation = {
                                    mapViewModel.centerOnLocation()
                                    centerTrigger++
                                },
                                onCompassClick = { mapViewModel.cycleOrientationMode() },
                                onLayersToggle = { layersTargetPane = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    RestoreStrip(
                        orientation = orientation,
                        paneACollapsed = paneACollapsed,
                        onRestore = { mapViewModel.setSplitFraction(0.5f) },
                        modifier = Modifier.align(
                            when {
                                orientation == SplitOrientation.Vertical && paneACollapsed -> Alignment.CenterStart
                                orientation == SplitOrientation.Vertical -> Alignment.CenterEnd
                                paneACollapsed -> Alignment.TopCenter
                                else -> Alignment.BottomCenter
                            }
                        )
                    )
                }
            } else if (orientation == SplitOrientation.Vertical) {
                var rowWidthPx by remember { mutableStateOf(0f) }
                Row(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { rowWidthPx = it.width.toFloat() }
                ) {
                    Box(Modifier.weight(animFraction.coerceAtLeast(0.001f)).fillMaxHeight()) {
                        Box(Modifier.fillMaxSize()) {
                            mapPaneParams(
                                MapPaneState(
                                    centerTrigger = centerTrigger,
                                    zoomToBoatTrigger = zoomToBoatTrigger,
                                    zoomToBoatLevel = zoomToBoatLevel,
                                    followMode = uiState.followMode,
                                    orientationMode = uiState.orientationMode,
                                    initialCamLat = initialCamLat,
                                    initialCamLon = initialCamLon,
                                    initialCamZoom = initialCamZoom,
                                    paneLayerState = paneALayerState,
                                )
                            ) { mapBearing = it }
                            PaneControls(
                                paneBearing = mapBearing,
                                onZoomIn = {
                                    zoomToBoatLevel = (currentZoom.toFloat() + 0.5f).coerceAtMost(18f)
                                    zoomToBoatTrigger++
                                },
                                onZoomOut = {
                                    zoomToBoatLevel = (currentZoom.toFloat() - 0.5f).coerceAtLeast(4f)
                                    zoomToBoatTrigger++
                                },
                                onCenterLocation = {
                                    mapViewModel.centerOnLocation()
                                    centerTrigger++
                                },
                                onCompassClick = { mapViewModel.cycleOrientationMode() },
                                onLayersToggle = { layersTargetPane = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    SplitterHandle(
                        orientation = orientation,
                        splitFraction = fraction,
                        parentSizePx = rowWidthPx,
                        onFractionChange = { mapViewModel.setSplitFraction(it) },
                        onFractionCommit = { mapViewModel.setSplitFraction(it) },
                        onSwapPanes = onSwapPanes
                    )
                    Box(Modifier.weight((1f - animFraction).coerceAtLeast(0.001f)).fillMaxHeight()) {
                        Box(Modifier.fillMaxSize()) {
                            mapPaneParams(
                                MapPaneState(
                                    centerTrigger = pane2CenterTrigger,
                                    zoomToBoatTrigger = pane2ZoomToBoatTrigger,
                                    zoomToBoatLevel = pane2ZoomToBoatLevel,
                                    followMode = uiState.followMode,
                                    orientationMode = uiState.orientationMode,
                                    initialCamLat = initialCamLat,
                                    initialCamLon = initialCamLon,
                                    initialCamZoom = initialCamZoom,
                                    paneLayerState = paneBLayerState,
                                )
                            ) { pane2Bearing = it }
                            PaneControls(
                                paneBearing = pane2Bearing,
                                onZoomIn = {
                                    pane2ZoomToBoatLevel = (pane2ZoomToBoatLevel + 0.5f).coerceAtMost(18f)
                                    pane2ZoomToBoatTrigger++
                                },
                                onZoomOut = {
                                    pane2ZoomToBoatLevel = (pane2ZoomToBoatLevel - 0.5f).coerceAtLeast(4f)
                                    pane2ZoomToBoatTrigger++
                                },
                                onCenterLocation = {
                                    mapViewModel.centerOnLocation()
                                    pane2CenterTrigger++
                                },
                                onCompassClick = { mapViewModel.cycleOrientationMode() },
                                onLayersToggle = { layersTargetPane = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                var colHeightPx by remember { mutableStateOf(0f) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { colHeightPx = it.height.toFloat() }
                ) {
                    Box(Modifier.weight(animFraction.coerceAtLeast(0.001f)).fillMaxWidth()) {
                        Box(Modifier.fillMaxSize()) {
                            mapPaneParams(
                                MapPaneState(
                                    centerTrigger = centerTrigger,
                                    zoomToBoatTrigger = zoomToBoatTrigger,
                                    zoomToBoatLevel = zoomToBoatLevel,
                                    followMode = uiState.followMode,
                                    orientationMode = uiState.orientationMode,
                                    initialCamLat = initialCamLat,
                                    initialCamLon = initialCamLon,
                                    initialCamZoom = initialCamZoom,
                                    paneLayerState = paneALayerState,
                                )
                            ) { mapBearing = it }
                            PaneControls(
                                paneBearing = mapBearing,
                                onZoomIn = {
                                    zoomToBoatLevel = (currentZoom.toFloat() + 0.5f).coerceAtMost(18f)
                                    zoomToBoatTrigger++
                                },
                                onZoomOut = {
                                    zoomToBoatLevel = (currentZoom.toFloat() - 0.5f).coerceAtLeast(4f)
                                    zoomToBoatTrigger++
                                },
                                onCenterLocation = {
                                    mapViewModel.centerOnLocation()
                                    centerTrigger++
                                },
                                onCompassClick = { mapViewModel.cycleOrientationMode() },
                                onLayersToggle = { layersTargetPane = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    SplitterHandle(
                        orientation = orientation,
                        splitFraction = fraction,
                        parentSizePx = colHeightPx,
                        onFractionChange = { mapViewModel.setSplitFraction(it) },
                        onFractionCommit = { mapViewModel.setSplitFraction(it) },
                        onSwapPanes = onSwapPanes
                    )
                    Box(Modifier.weight((1f - animFraction).coerceAtLeast(0.001f)).fillMaxWidth()) {
                        Box(Modifier.fillMaxSize()) {
                            mapPaneParams(
                                MapPaneState(
                                    centerTrigger = pane2CenterTrigger,
                                    zoomToBoatTrigger = pane2ZoomToBoatTrigger,
                                    zoomToBoatLevel = pane2ZoomToBoatLevel,
                                    followMode = uiState.followMode,
                                    orientationMode = uiState.orientationMode,
                                    initialCamLat = initialCamLat,
                                    initialCamLon = initialCamLon,
                                    initialCamZoom = initialCamZoom,
                                    paneLayerState = paneBLayerState,
                                )
                            ) { pane2Bearing = it }
                            PaneControls(
                                paneBearing = pane2Bearing,
                                onZoomIn = {
                                    pane2ZoomToBoatLevel = (pane2ZoomToBoatLevel + 0.5f).coerceAtMost(18f)
                                    pane2ZoomToBoatTrigger++
                                },
                                onZoomOut = {
                                    pane2ZoomToBoatLevel = (pane2ZoomToBoatLevel - 0.5f).coerceAtLeast(4f)
                                    pane2ZoomToBoatTrigger++
                                },
                                onCenterLocation = {
                                    mapViewModel.centerOnLocation()
                                    pane2CenterTrigger++
                                },
                                onCompassClick = { mapViewModel.cycleOrientationMode() },
                                onLayersToggle = { layersTargetPane = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        TopBarOverlay(
            smoothedSpeed = smoothedSpeed,
            speedUnit = speedUnit,
            visibleWarnings = relevantWarnings,
            lightningMode = lightningMode,
            recordingData = RecordingData(isRecording, recordingDistanceNm, recordingElapsed),
            mapBearing = mapBearing,
            orientationLabel = orientationLabel,
            onSpeedUnitClick = {
                speedUnit = when (speedUnit) {
                    SpeedUnit.KNOTS -> SpeedUnit.KMH
                    SpeedUnit.KMH -> SpeedUnit.MPH
                    SpeedUnit.MPH -> SpeedUnit.KNOTS
                }
                WeatherUnitPrefs.setSpeedUnit(prefs, speedUnit)
            },
            onCompassClick = { mapViewModel.cycleOrientationMode() },
            onWarningClick = {
                mapViewModel.requestWeatherTab(2)
                onNavigateToWarnings()
            },
            showCompass = !uiState.splitMode
        )

        MapControls(
            currentZoom = currentZoom,
            splitMode = uiState.splitMode,
            recordingData = RecordingData(isRecording, recordingDistanceNm, recordingElapsed),
            showLayersPanel = showLayersPanel,
            showRadarSlider = showRadarSlider,
            followMode = uiState.followMode,
            windData = WindData(windSpeedMs, windDirectionDeg, temperatureC, pressureHPa),
            mapBearing = mapBearing,
            windUnit = windUnit,
            tempUnit = tempUnit,
            pressureUnit = pressureUnit,
            windMeterSize = windMeterSize,
            showWindMeter = uiState.showWindMeter,
            showRadar = uiState.showRadar,
            onZoomIn = {
                zoomToBoatLevel = (currentZoom.toFloat() + 0.5f).coerceAtMost(18f)
                zoomToBoatTrigger++
            },
            onZoomOut = {
                zoomToBoatLevel = (currentZoom.toFloat() - 0.5f).coerceAtLeast(4f)
                zoomToBoatTrigger++
            },
            onRecordToggle = {
                if (isRecording) {
                    mapViewModel.trackRecorder.stopRecording()
                    currentTrackId?.let { id ->
                        scope.launch {
                            try {
                                val pts = mapViewModel.trackRecorder.recordedPoints.value
                                val stats = mapViewModel.trackRecorder.computeTrackStats()
                                mapViewModel.trackSummaryDao.finalize(id, System.currentTimeMillis(), pts, stats.distanceNm, stats.maxSpeedKn)
                            } catch (_: Exception) {
                                scope.showUserError(context, snackbarHostState, app.pursi.R.string.stop_recording_failed)
                            }
                        }
                    }
                } else {
                    val id = mapViewModel.trackRecorder.startRecording()
                    scope.launch {
                        try {
                            mapViewModel.trackSummaryDao.insert(TrackSummary(
                                id = id,
                                name = SimpleDateFormat("d.M.yyyy HH:mm", Locale.getDefault()).format(Date()),
                                startTime = System.currentTimeMillis(),
                                isRecording = true
                            ))
                        } catch (_: Exception) {
                            scope.showUserError(context, snackbarHostState, app.pursi.R.string.start_recording_failed)
                        }
                    }
                }
            },
            onLayersToggle = { showLayersPanel = !showLayersPanel },
            onCenterLocation = { mapViewModel.centerOnLocation(); centerTrigger++; showCoords++ },
            onRadarSliderToggle = {
                if (showRadarSlider) {
                    mapViewModel.setRadarTimeOffset(0)
                }
                showRadarSlider = !showRadarSlider
            },
            onSplitToggle = { mapViewModel.toggleSplitMode() }
        )

        if (showLayersPanel || layersTargetPane != null) {
            val targetIsPaneA = layersTargetPane == true
            val targetIsPaneB = layersTargetPane == false
            val targetState = when {
                targetIsPaneA -> paneALayerState
                targetIsPaneB -> paneBLayerState
                else -> uiState.toPaneLayerState()
            }
            val onDismiss = {
                showLayersPanel = false
                layersTargetPane = null
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            LayersPanel(
                chartOpacity = targetState.chartOpacity,
                showRadar = targetState.showRadar,
                showAis = targetState.showAis,
                vesselCount = vessels.size,
                showDepth = targetState.showDepth,
                showWindMeter = targetState.showWindMeter,
                showAlgae = targetState.showAlgae,
                onChartOpacityChange = { newOpacity ->
                    when {
                        targetIsPaneA -> paneALayerState = targetState.copy(chartOpacity = newOpacity)
                        targetIsPaneB -> paneBLayerState = targetState.copy(chartOpacity = newOpacity)
                        else -> chartOpacity = newOpacity
                    }
                },
                onChartOpacityFinished = {
                    val final = when {
                        targetIsPaneA -> paneALayerState.chartOpacity
                        targetIsPaneB -> paneBLayerState.chartOpacity
                        else -> chartOpacity
                    }
                    prefs.edit().putFloat(KEY_CHART_OPACITY, final).apply()
                    mapViewModel.setChartOpacity(final)
                },
                onToggleRadar = {
                    when {
                        targetIsPaneA -> paneALayerState = targetState.copy(showRadar = !targetState.showRadar)
                        targetIsPaneB -> paneBLayerState = targetState.copy(showRadar = !targetState.showRadar)
                        else -> mapViewModel.toggleRainAndLightning()
                    }
                },
                onToggleAis = {
                    when {
                        targetIsPaneA -> paneALayerState = targetState.copy(showAis = !targetState.showAis)
                        targetIsPaneB -> paneBLayerState = targetState.copy(showAis = !targetState.showAis)
                        else -> mapViewModel.toggleShowAis()
                    }
                },
                onToggleDepth = {
                    when {
                        targetIsPaneA -> paneALayerState = targetState.copy(showDepth = !targetState.showDepth)
                        targetIsPaneB -> paneBLayerState = targetState.copy(showDepth = !targetState.showDepth)
                        else -> mapViewModel.toggleShowDepth()
                    }
                },
                onToggleWindMeter = {
                    when {
                        targetIsPaneA -> paneALayerState = targetState.copy(showWindMeter = !targetState.showWindMeter)
                        targetIsPaneB -> paneBLayerState = targetState.copy(showWindMeter = !targetState.showWindMeter)
                        else -> mapViewModel.toggleShowWindMeter()
                    }
                },
                onToggleAlgae = {
                    when {
                        targetIsPaneA -> paneALayerState = targetState.copy(showAlgae = !targetState.showAlgae)
                        targetIsPaneB -> paneBLayerState = targetState.copy(showAlgae = !targetState.showAlgae)
                        else -> mapViewModel.toggleShowAlgae()
                    }
                },
                bottomInsetPx = bottomInsetPx,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (routeWaypoints.isNotEmpty() || viewingRouteWaypoints.isNotEmpty() || viewingTrackId != null) {
            val activeWps = when {
                routeWaypoints.isNotEmpty() -> routeWaypoints
                viewingRouteWaypoints.isNotEmpty() -> viewingRouteWaypoints
                else -> viewingTrackCoordinates
            }
            val cardLabel = when {
                routeWaypoints.isNotEmpty() -> null
                viewingRouteWaypoints.isNotEmpty() -> "${stringResource(app.pursi.R.string.track_viewing)}: $viewingRouteName"
                viewingTrackId != null -> "${stringResource(app.pursi.R.string.track_viewing)}: $viewingTrackName"
                else -> null
            }
            val lastWp = activeWps.lastOrNull()
            val showReportButton = routeWaypoints.isNotEmpty() &&
                lastWp != null &&
                Regions.FINLAND.contains(lastWp.latitude, lastWp.longitude)
            RouteActionCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInsetPx + 80.dp),
                waypoints = activeWps,
                defaultBoat = defaultBoat,
                isPlanningMode = routeWaypoints.isNotEmpty(),
                label = cardLabel,
                showReportButton = showReportButton,
                onReportObservation = {
                    algaeReportLocation = lastWp
                    showAlgaeReport = true
                },
                onUndo = { routeWaypoints = routeWaypoints.toMutableList().also { it.removeAt(it.lastIndex) } },
                onClear = { routeWaypoints = emptyList() },
                onSave = {
                    routeName = "Route ${SimpleDateFormat("d.M.yyyy", Locale.getDefault()).format(Date())}"
                    showRouteSaveDialog = true
                },
                onClose = {
                    when {
                        viewingRouteWaypoints.isNotEmpty() -> {
                            viewingRouteWaypoints = emptyList()
                            onClearViewingRoute()
                        }
                        viewingTrackId != null -> {
                            viewingTrackCoordinates = emptyList()
                            onClearViewingTrack()
                        }
                    }
                }
            )
        }

        if (measurePoints.size >= 2 || twoFingerMeasure != null) {
            MeasureDisplay(
                measurePoints = measurePoints,
                twoFingerMeasure = twoFingerMeasure,
                measureActive = measureActive,
                onTwoFingerMeasureDismiss = {
                    twoFingerMeasure = null
                    measureLinePoints = null
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        RouteSaveDialog(
            showDialog = showRouteSaveDialog,
            routeName = routeName,
            onRouteNameChange = { routeName = it },
            onSave = {
                scope.launch {
                    try {
                        val routeId = UUID.randomUUID().toString()
                        mapViewModel.savedRouteDao.insert(SavedRoute(
                            id = routeId, name = routeName, waypointCount = routeWaypoints.size,
                            totalDistanceNm = {
                                var d = 0.0
                                for (i in 0 until routeWaypoints.size - 1)
                                    d += app.pursi.location.SpeedCalculator.distanceNm(
                                        routeWaypoints[i].latitude, routeWaypoints[i].longitude,
                                        routeWaypoints[i + 1].latitude, routeWaypoints[i + 1].longitude
                                    )
                                d
                            }(),
                            boatId = defaultBoat?.id
                        ))
                        routeWaypoints.forEachIndexed { i, p ->
                            mapViewModel.savedRouteDao.insertWaypoint(RouteWaypoint(
                                routeId = routeId, name = "WP ${i + 1}",
                                latitude = p.latitude, longitude = p.longitude, order = i
                            ))
                        }
                        routeWaypoints = emptyList()
                    } catch (_: Exception) {
                        scope.showUserError(context, snackbarHostState, app.pursi.R.string.save_route_failed)
                    }
                }
                showRouteSaveDialog = false
            },
            onDismiss = { showRouteSaveDialog = false }
        )

        VesselInfoPopup(
            selectedVesselMmsi = selectedVesselMmsi,
            vessels = vessels,
            aisMetadata = aisMetadata,
            onClose = { selectedVesselMmsi = null },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // ── Seamark detail popup ──
        val selectedSeamark = uiState.selectedSeamark
        if (selectedSeamark != null) {
            SeamarkInfoCard(
                data = selectedSeamark,
                onClose = { mapViewModel.clearSeamark() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
            )
        }

        // ── Water observation detail popup ──
        val selectedAlgaeObs = uiState.selectedAlgaeObservations
        if (selectedAlgaeObs.isNotEmpty()) {
            WaterObservationCard(
                observations = selectedAlgaeObs,
                onClose = { mapViewModel.clearAlgaeObservation() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
            )
        }

        // ── Cyanobacteria report dialog ──
        if (showAlgaeReport) {
            algaeReportLocation?.let { loc ->
                CyanobacteriaReportDialog(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    onDismiss = {
                        showAlgaeReport = false
                        algaeReportLocation = null
                    }
                )
            }
        }

        CoordinateDisplay(
            showCoords = showCoords,
            location = effectiveLocation,
            onDismiss = { showCoords = 0 },
            bottomInsetPx = bottomInsetPx,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        DebugOverlay(
            showDebug = showDebug,
            currentZoom = currentZoom,
            followMode = uiState.followMode,
            orientationMode = uiState.orientationMode,
            showDepth = uiState.showDepth,
            vvStatus = VvStatus(
                downloaded = uiState.fiState?.vvDataDownloaded ?: false,
                usingNetwork = uiState.fiState?.vvUsingNetwork ?: false,
                turvalaiteCount = uiState.fiState?.turvalaiteFeatures?.values?.sumOf { it.size } ?: 0,
                valosektoriCount = uiState.fiState?.valosektoriFeatures?.values?.sumOf { it.size } ?: 0,
                navlineCount = uiState.fiState?.navlineFeatures?.values?.sumOf { it.size } ?: 0
            ),
            isMockLocation = isMockLocation,
            mockLocationPending = mockLocationPending,
            showRadar = uiState.showRadar,
            radarProviderId = radarProvider?.providerId ?: "null",
            radarTimeOffset = uiState.radarTimeOffset,
            showRadarSlider = showRadarSlider,
            onMockGpsToggle = {
                if (isMockLocation) {
                    mapViewModel.centerOnLocation()
                } else {
                    mockLocationPending = true
                }
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (showRadarSlider) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showRadarSlider = false
                        mapViewModel.setRadarTimeOffset(0)
                    }
            )
            RadarTimeSlider(
                showRadar = uiState.showRadar,
                showSlider = true,
                radarTimeOffset = uiState.radarTimeOffset,
                effectiveDelay = uiState.radarEffectiveDelay,
                onRadarTimeOffsetChange = { mapViewModel.setRadarTimeOffset(it) },
                bottomInsetPx = bottomInsetPx,
                maxHistoryMinutes = radarProvider?.maxHistoryMinutes ?: 60,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showOnboarding) {
            val dismissOnboarding = {
                showOnboarding = false
                prefs.edit().putBoolean("onboarding_shown", true).apply()
            }
            OnboardingOverlay(
                onComplete = dismissOnboarding,
                modifier = Modifier.fillMaxSize(),
                downloadManager = downloadManager,
                pmtilesDownloader = pmtilesDownloader,
                vvDataDownloader = vvDataDownloader,
                currentLatLng = location?.let { LatLng(it.latitude, it.longitude) },
                onChooseCustom = {
                    dismissOnboarding()
                    onChooseCustom?.invoke()
                },
                tileSources = tileSources
            )
        }

        val downloadProgress by downloadManager?.progress?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(emptyMap()) }
        val runningJob = downloadProgress.values.firstOrNull { it.status == "RUNNING" || it.status == "PENDING" }
        if (!showOnboarding && runningJob != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp)
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
