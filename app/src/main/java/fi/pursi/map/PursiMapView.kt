package fi.pursi.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.graphics.PointF
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.core.ChartProvider
import fi.pursi.datasource.core.RadarProvider
import fi.pursi.ais.AisVessel
import fi.pursi.map.ais.AisVesselOverlay
import fi.pursi.map.overlays.BoatOverlay
import fi.pursi.map.overlays.ChartOverlay
import fi.pursi.map.overlays.MeasureOverlay
import fi.pursi.map.overlays.RadarOverlay
import fi.pursi.map.overlays.RouteOverlay
import fi.pursi.map.overlays.WeatherOverlay
import fi.pursi.map.overlays.WfsOverlay
import java.io.File
import okhttp3.OkHttpClient
import fi.pursi.ui.viewmodel.BoatIconSize
import fi.pursi.ui.viewmodel.FollowMode
import fi.pursi.ui.viewmodel.NavmarkSize
import fi.pursi.ui.viewmodel.OrientationMode
import fi.pursi.ui.viewmodel.SeamarkDetail
import fi.pursi.ui.viewmodel.SeamarkSource
import fi.pursi.weather.LightningStrike
import fi.pursi.weather.MarineWarning
import fi.pursi.data.model.WfsFeature
import fi.pursi.datasource.fi.TurvalaiteIconMapper
import fi.pursi.datasource.fi.VvSeamarkDeduplicator
import fi.pursi.water.WaterObservation
import fi.pursi.water.WaterObservationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.geojson.Polygon

private const val RADAR_OPACITY = 0.4f

@Composable
fun PursiMapView(
    modifier: Modifier = Modifier,
    chartOpacity: Float = 1.0f,
    offlineMode: Boolean = false,
    tilesDirPath: String? = null,
    chartProviders: List<ChartProvider> = emptyList(),
    allChartProviders: List<ChartProvider> = chartProviders,
    location: LatLng? = null,
    bearingDeg: Float? = null,
    speedMps: Float = 0f,
    centerTrigger: Int = 0,
    zoomToBoatTrigger: Int = 0,
    zoomToBoatLevel: Float = 7f,
    courseLineMinutes: List<Int> = emptyList(),
    recordingTrail: List<LatLng> = emptyList(),
    routeWaypoints: List<LatLng> = emptyList(),
    savedRouteLines: List<List<LatLng>> = emptyList(),
    measureLinePoints: Pair<LatLng, LatLng>? = null,
    centerTarget: LatLng? = null,
    poiMarker: LatLng? = null,
    onClearPoiMarker: () -> Unit = {},
    initialCamLat: Double = Double.NaN,
    initialCamLon: Double = Double.NaN,
    initialCamZoom: Double = 7.0,
    onCameraMoved: (Double, Double, Double) -> Unit = { _, _, _ -> },
    onMapReady: (MapLibreMap) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    onVesselClick: (Int) -> Unit = {},
    onLongPress: (LatLng) -> Unit = {},
    onTwoFingerMeasure: ((LatLng, LatLng) -> Unit)? = null,
    onMeasureEnd: () -> Unit = {},
    onCameraIdle: (LatLng, LatLng) -> Unit = { _, _ -> },
    onUserPan: () -> Unit = {},
    onCameraBearingChanged: (Float) -> Unit = {},
    followMode: FollowMode = FollowMode.CENTERED,
    orientationMode: OrientationMode = OrientationMode.NORTH_UP,
    lookAheadFactor: Float = 0.25f,
    lookAheadSec: Int = 5,
    showLightning: Boolean = false,
    reloadTrigger: Int = 0,
    showWarnings: Boolean = false,
    showRadar: Boolean = false,
    radarTimeOffset: Int = 0,
    radarOpacity: Float = 0.7f,
    radarProvider: RadarProvider? = null,
    lightningStrikes: List<LightningStrike> = emptyList(),
    warnings: List<MarineWarning> = emptyList(),
    showAis: Boolean = false,
    vessels: List<AisVessel> = emptyList(),
    showAlgae: Boolean = false,
    waterObservations: List<WaterObservation> = emptyList(),
    seamarksDownloaded: Boolean = false,
    showDepth: Boolean = true,
    depthFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    isNightMode: Boolean = false,
    navmarkSize: NavmarkSize = NavmarkSize.MEDIUM,
    boatIconSize: BoatIconSize = BoatIconSize.MEDIUM,
    boatIconColor: String = "#1976D2",
    showVvNavmarks: Boolean = true,
    turvalaiteFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    turvalaitevikaFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    valosektoriFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    vesiliikennemerkkiFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    navlineFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    fairwayFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    vvUsingNetwork: Boolean = false,
    vvFetchCounter: Int = 0,
    onTurvalaiteClick: (Long) -> Unit = {},
    onOsmSeamarkClick: (SeamarkDetail) -> Unit = {},
    onAlgaeObservationClick: (Int) -> Unit = {},
    onRadarEffectiveDelay: (Int) -> Unit = {},
    showSectors: Boolean = true,
    viewportBounds: BoundingBox? = null
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            onStart()
            onResume()
        }
    }

    val currentMap = remember { mutableStateOf<MapLibreMap?>(null) }
    val currentStyle = remember { mutableStateOf<Style?>(null) }
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnTwoFingerMeasure by rememberUpdatedState(onTwoFingerMeasure)
    val currentOnMeasureEnd by rememberUpdatedState(onMeasureEnd)
    val currentOnCameraMoved by rememberUpdatedState(onCameraMoved)
    val currentOnUserPan by rememberUpdatedState(onUserPan)
    val currentOnCameraBearingChanged by rememberUpdatedState(onCameraBearingChanged)
    val currentOnRadarEffectiveDelay by rememberUpdatedState(onRadarEffectiveDelay)
    val currentOnClearPoiMarker by rememberUpdatedState(onClearPoiMarker)
    val currentOnVesselClick by rememberUpdatedState(onVesselClick)
    val currentOnTurvalaiteClick by rememberUpdatedState(onTurvalaiteClick)
    val currentOnOsmSeamarkClick by rememberUpdatedState(onOsmSeamarkClick)
    val currentOnAlgaeObservationClick by rememberUpdatedState(onAlgaeObservationClick)
    val currentRadarProvider by rememberUpdatedState(radarProvider)
    val obsRadarOpacity by rememberUpdatedState(radarOpacity)
    val obsLocation by rememberUpdatedState(location)
    val obsBearing by rememberUpdatedState(bearingDeg)
    val obsSpeed by rememberUpdatedState(speedMps)
    val obsLookAheadFactor by rememberUpdatedState(lookAheadFactor)
    val currentViewportBounds by rememberUpdatedState(viewportBounds)
    val obsBoatIconSize by rememberUpdatedState(boatIconSize)
    val obsBoatIconColor by rememberUpdatedState(boatIconColor)

    // Capture all registered providers at initial render for proper cleanup on subsequent renders
    val allRegisteredProviders = remember { allChartProviders.toList() }
    var mapReadyToken by remember { mutableStateOf(0) }

    var radarRefreshTick by remember { mutableStateOf(0) }
    var radarRetryTick by remember { mutableStateOf(0) }
    var radarEffectToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(mapReadyToken, showRadar) {
        if (!showRadar) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(300_000L)
            radarRefreshTick++
        }
    }

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())

        val longClickListener = MapLibreMap.OnMapLongClickListener { latlng ->
            currentOnLongPress(LatLng(latlng.latitude, latlng.longitude))
            true
        }

        val clickListener = MapLibreMap.OnMapClickListener { latlng ->
            currentOnMapClick(LatLng(latlng.latitude, latlng.longitude))
            true
        }

        val cameraMoveListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                currentOnUserPan()
                currentOnClearPoiMarker()
            }
        }

        val cameraIdleListener = object : MapLibreMap.OnCameraIdleListener {
            override fun onCameraIdle() {
                val map = currentMap.value ?: return
                val cam = map.cameraPosition
                val bounds = map.projection.visibleRegion.latLngBounds
                currentOnCameraIdle(
                    LatLng(bounds.latitudeSouth, bounds.longitudeWest),
                    LatLng(bounds.latitudeNorth, bounds.longitudeEast)
                )
                val target = cam.target
                val zoom = cam.zoom
                if (target != null && zoom != null) {
                    currentOnCameraMoved(target.latitude, target.longitude, zoom.toDouble())
                }
                currentOnCameraBearingChanged(cam.bearing.toFloat())
                // Force tile retry on camera idle — failed tiles are re-requested
                map.triggerRepaint()
            }
        }

        var vesselClickListener: MapLibreMap.OnMapClickListener? = null
        var turvalaiteClickListener: MapLibreMap.OnMapClickListener? = null
        var osmSeamarkClickListener: MapLibreMap.OnMapClickListener? = null
        var algaeObsClickListener: MapLibreMap.OnMapClickListener? = null

        mapView.getMapAsync { map ->
            configureMap(map, context, chartOpacity)
            currentMap.value = map

            // Two-finger distance measurement
            // Phase 1: 600ms hold → enters measurement mode
            // Phase 2: finger movement adjusts the line, zoom disabled via UiSettings
            var twoFingerX1 = 0f; var twoFingerY1 = 0f
            var twoFingerX2 = 0f; var twoFingerY2 = 0f
            var pendingTwoFinger: Runnable? = null
            var inMeasureMode = false

            mapView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (inMeasureMode) {
                            inMeasureMode = false
                            map.uiSettings.isZoomGesturesEnabled = true
                            map.uiSettings.isRotateGesturesEnabled = true
                            currentOnMeasureEnd()
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount >= 2) {
                            if (inMeasureMode) {
                                inMeasureMode = false
                                map.uiSettings.isZoomGesturesEnabled = true
                                map.uiSettings.isRotateGesturesEnabled = true
                                currentOnMeasureEnd()
                            }
                            pendingTwoFinger?.let { handler.removeCallbacks(it) }
                            twoFingerX1 = event.getX(0); twoFingerY1 = event.getY(0)
                            twoFingerX2 = event.getX(1); twoFingerY2 = event.getY(1)
                            val r = Runnable {
                                inMeasureMode = true
                                map.uiSettings.isZoomGesturesEnabled = false
                                map.uiSettings.isRotateGesturesEnabled = false
                                val p1 = map.projection.fromScreenLocation(PointF(twoFingerX1, twoFingerY1))
                                val p2 = map.projection.fromScreenLocation(PointF(twoFingerX2, twoFingerY2))
                                currentOnTwoFingerMeasure?.invoke(
                                    LatLng(p1.latitude, p1.longitude),
                                    LatLng(p2.latitude, p2.longitude)
                                )
                            }
                            pendingTwoFinger = r
                            handler.postDelayed(r, 600L)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (inMeasureMode) {
                            twoFingerX1 = event.getX(0); twoFingerY1 = event.getY(0)
                            twoFingerX2 = event.getX(1); twoFingerY2 = event.getY(1)
                            val p1 = map.projection.fromScreenLocation(PointF(twoFingerX1, twoFingerY1))
                            val p2 = map.projection.fromScreenLocation(PointF(twoFingerX2, twoFingerY2))
                            currentOnTwoFingerMeasure?.invoke(
                                LatLng(p1.latitude, p1.longitude),
                                LatLng(p2.latitude, p2.longitude)
                            )
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        if (inMeasureMode) {
                            inMeasureMode = false
                            map.uiSettings.isZoomGesturesEnabled = true
                            map.uiSettings.isRotateGesturesEnabled = true
                            currentOnMeasureEnd()
                        }
                        pendingTwoFinger?.let { handler.removeCallbacks(it) }
                        pendingTwoFinger = null
                    }
                    MotionEvent.ACTION_UP -> {
                        if (inMeasureMode) {
                            inMeasureMode = false
                            map.uiSettings.isZoomGesturesEnabled = true
                            map.uiSettings.isRotateGesturesEnabled = true
                            currentOnMeasureEnd()
                        }
                    }
                }
                false
            }

            map.addOnMapLongClickListener(longClickListener)
            vesselClickListener = MapLibreMap.OnMapClickListener { latlng ->
                val sp = map.projection.toScreenLocation(latlng)
                val vesselLayers = fi.pursi.map.ais.AisVesselOverlay.ALL_LAYERS
                val f = map.queryRenderedFeatures(sp, *vesselLayers.toTypedArray())
                if (f.isNotEmpty()) {
                    currentOnVesselClick(f.first().getNumberProperty("mmsi").toInt())
                    return@OnMapClickListener true
                }
                false
            }
            map.addOnMapClickListener(vesselClickListener)
            turvalaiteClickListener = MapLibreMap.OnMapClickListener { latlng ->
                val sp = map.projection.toScreenLocation(latlng)
                val rect = android.graphics.RectF(sp.x - 20f, sp.y - 20f, sp.x + 20f, sp.y + 20f)
                val turvalaiteLayers = arrayOf("layer-wfs-turvalaite")
                val f = map.queryRenderedFeatures(rect, *turvalaiteLayers)
                if (f.isNotEmpty()) {
                    val id = f.first().getNumberProperty("_vv_id")?.toLong() ?: 0L
                    if (id != 0L) {
                        currentOnTurvalaiteClick(id)
                        return@OnMapClickListener true
                    }
                }
                false
            }
            map.addOnMapClickListener(turvalaiteClickListener)
            osmSeamarkClickListener = MapLibreMap.OnMapClickListener { latlng ->
                val sp = map.projection.toScreenLocation(latlng)
                val rect = android.graphics.RectF(sp.x - 20f, sp.y - 20f, sp.x + 20f, sp.y + 20f)
                val allHit = map.queryRenderedFeatures(rect)
                val osmHit = allHit.firstOrNull { feat ->
                    feat.getNumberProperty("_vv_id") == null &&
                    (feat.getStringProperty("seamark:type") != null ||
                    feat.getStringProperty("seamark:name") != null)
                }
                if (osmHit != null) {
                    val osmType = osmHit.getStringProperty("seamark:type") ?: ""
                    val name = osmHit.getStringProperty("seamark:name")
                        ?: osmHit.getStringProperty("ref")
                        ?: osmHit.getStringProperty("name")
                        ?: ""
                    val hasLight = osmHit.getStringProperty("seamark:light:character") != null
                        || osmHit.getStringProperty("seamark:light:colour") != null
                    val lightParts = mutableListOf<String>()
                    osmHit.getStringProperty("seamark:light:character")?.let { lightParts.add(it) }
                    osmHit.getStringProperty("seamark:light:colour")?.let { lightParts.add(it) }
                    osmHit.getStringProperty("seamark:light:period")?.let { lightParts.add("${it}s") }
                    val detail = SeamarkDetail(
                        source = SeamarkSource.OSM,
                        name = name,
                        typeLabel = TurvalaiteIconMapper.humanReadableName(osmType),
                        hasLight = hasLight,
                        lightCharacteristic = lightParts.joinToString(" ").takeIf { it.isNotBlank() },
                        description = osmType.replace("_", " "),
                        extraInfo = listOf("OpenSeaMap"),
                        latitude = latlng.latitude,
                        longitude = latlng.longitude
                    )
                    currentOnOsmSeamarkClick(detail)
                    return@OnMapClickListener true
                }
                false
            }
            map.addOnMapClickListener(osmSeamarkClickListener)
            algaeObsClickListener = MapLibreMap.OnMapClickListener { latlng ->
                val sp = map.projection.toScreenLocation(latlng)
                val rect = android.graphics.RectF(sp.x - 24f, sp.y - 24f, sp.x + 24f, sp.y + 24f)
                val f = map.queryRenderedFeatures(rect, "water-obs-layer-a", "water-obs-layer-b")
                if (f.isNotEmpty()) {
                    val idx = f.first().getNumberProperty("obsIndex")?.toInt() ?: -1
                    if (idx >= 0) {
                        currentOnAlgaeObservationClick(idx)
                        return@OnMapClickListener true
                    }
                }
                false
            }
            map.addOnMapClickListener(algaeObsClickListener)
            map.addOnMapClickListener(clickListener)
            map.addOnCameraMoveStartedListener(cameraMoveListener)
            map.addOnCameraIdleListener(cameraIdleListener)
            onMapReady(map)

            // Restore saved camera position
            if (!initialCamLat.isNaN() && !initialCamLon.isNaN()) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(initialCamLat, initialCamLon))
                    .zoom(initialCamZoom)
                    .build()
            }
        }
        onDispose {
            handler.removeCallbacksAndMessages(null)
            mapView.setOnTouchListener(null)
            currentMap.value?.let { m ->
                m.removeOnMapLongClickListener(longClickListener)
                m.removeOnMapClickListener(clickListener)
                vesselClickListener?.let { m.removeOnMapClickListener(it) }
                turvalaiteClickListener?.let { m.removeOnMapClickListener(it) }
                osmSeamarkClickListener?.let { m.removeOnMapClickListener(it) }
                algaeObsClickListener?.let { m.removeOnMapClickListener(it) }
                m.removeOnCameraMoveStartedListener(cameraMoveListener)
                m.removeOnCameraIdleListener(cameraIdleListener)
            }
            currentMap.value = null
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    var tileServer by remember { mutableStateOf<SeamarkTileServer?>(null) }
    var tileServerProxy by remember { mutableStateOf<TraficomTileServer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            tileServer?.stopServer()
            tileServerProxy?.stopServer()
        }
    }

    // Track last loaded state to avoid duplicate style loads and server restarts
    var lastStyleUri by remember { mutableStateOf<String?>(null) }
    var lastReloadTrigger by remember { mutableStateOf(0) }
    var lastServerConfig by remember { mutableStateOf<Boolean?>(null) }
    var seamarkLayerIds by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(seamarksDownloaded, currentMap.value, reloadTrigger, isNightMode) {
        val map = currentMap.value ?: return@LaunchedEffect

        if (lastServerConfig == null || seamarksDownloaded != lastServerConfig) {
            lastServerConfig = seamarksDownloaded
            tileServer?.stopServer()
            val continentFiles = fi.pursi.map.PmtilesDownloader.CONTINENTS
                .map { java.io.File(context.filesDir, "seamarks_${it.id}.pmtiles") }
                .filter { it.exists() }
            val legacyFile = java.io.File(context.filesDir, "seamarks.pmtiles")
            val allLocalFiles = listOfNotNull(
                legacyFile.takeIf { it.exists() }
            ) + continentFiles
            if (allLocalFiles.isNotEmpty()) {
                val server = SeamarkTileServer(
                    pmtilesFiles = allLocalFiles,
                    port = 8080
                )
                if (withContext(Dispatchers.IO) { server.startServer() }) {
                    tileServer = server
                }
            } else {
                val server = SeamarkTileServer(
                    pmtilesUrls = listOf(fi.pursi.map.PmtilesDownloader.DEFAULT_SEAMARKS_URL),
                    client = okhttp3.OkHttpClient(),
                    port = 8080
                )
                if (withContext(Dispatchers.IO) { server.startServer() }) {
                    tileServer = server
                }
            }
        }

        val styleUri = if (isNightMode) "asset://pursi_style_fjord.json" else "asset://pursi_style_vector.json"
        if (styleUri != lastStyleUri || reloadTrigger != lastReloadTrigger) {
            lastStyleUri = styleUri
            lastReloadTrigger = reloadTrigger
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                try {
                    val dm = context.resources.displayMetrics.density
                    val sfname = if (dm >= 2.0f) "seamark_sprites@2x" else "seamark_sprites"
                    val jsobj = org.json.JSONObject(context.assets.open("$sfname.json").bufferedReader().readText())
                    val bmp = android.graphics.BitmapFactory.decodeStream(context.assets.open("$sfname.png"))
                    val names = jsobj.names()
                    for (i in 0 until names.length()) {
                        val n = names.getString(i)
                        val e = jsobj.getJSONObject(n)
                        val icon = android.graphics.Bitmap.createBitmap(bmp, e.getInt("x"), e.getInt("y"), e.getInt("width"), e.getInt("height"))
                        if (style.getImage(n) == null) style.addImage(n, icon)
                    }
                } catch (ex: Exception) {
                    android.util.Log.e("PursiMap", "Seamark sprite error: ${ex.message}")
                }
                try {
                    style.registerNavmarkIcons(context, isNightMode)
                    style.loadPoiIcons(context)
                } catch (ex: Exception) {
                    android.util.Log.e("PursiMap", "Navmark icon error: ${ex.message}")
                }
                try {
                    style.loadOfmSprite(context)
                    style.getLayer("DYNAMIC_icon_fixed_rotation")?.setProperties(
                        PropertyFactory.iconSize(navmarkSize.multiplier)
                    )
                    style.getLayer("DYNAMIC_icon_free_rotation")?.setProperties(
                        PropertyFactory.iconSize(navmarkSize.multiplier)
                    )
                } catch (ex: Exception) {
                    android.util.Log.e("SeamarkServer", "Sprite error: ${ex.message}")
                }
                currentStyle.value = style
                seamarkLayerIds = ChartOverlay.getSeamarkLayerIds(style)
                mapReadyToken++
            }
        }
    }

    // Periodic tile retry — every 5 seconds to retry failed tiles (both raster & vector)
    LaunchedEffect(currentMap.value) {
        val map = currentMap.value ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5_000L)
            map.triggerRepaint()
        }
    }

    // Update navmark icon size at runtime without reloading the style
    LaunchedEffect(currentMap.value, mapReadyToken, navmarkSize) {
        val map = currentMap.value ?: return@LaunchedEffect
        map.getStyle { style ->
            style.getLayer("DYNAMIC_icon_fixed_rotation")?.setProperties(
                PropertyFactory.iconSize(navmarkSize.multiplier)
            )
            style.getLayer("DYNAMIC_icon_free_rotation")?.setProperties(
                PropertyFactory.iconSize(navmarkSize.multiplier)
            )
        }
    }

    LaunchedEffect(mapReadyToken, chartOpacity, offlineMode, tilesDirPath, chartProviders) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect

        val useProxyServer = !offlineMode && chartProviders.any { it.needsTileServer }
        val localTileServerProxy = tileServerProxy
        if (useProxyServer && (localTileServerProxy == null || !localTileServerProxy.isRunning)) {
            val proxyProvider = chartProviders.first { it.needsTileServer }
            val fallbackLayers = proxyProvider.layers
                .sortedByDescending { it.minZoom }
                .map {
                    val threshold = if (it.minZoom <= 4f) 0 else 3000
                    FallbackTileLayer(it.tileUrl, it.minZoom, threshold)
                }
            val server = TraficomTileServer(fallbackLayers, cacheDir = context.cacheDir)
            if (server.startServer()) {
                tileServerProxy = server
            }
        }

        map.getStyle { style ->
            ChartOverlay.updateLayers(
                style, chartProviders, allRegisteredProviders,
                offlineMode, tilesDirPath, chartOpacity,
                tileServerProxy?.baseTileUrl
            )
        }
    }

    LaunchedEffect(mapReadyToken, isNightMode, chartOpacity) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style ->
            ChartOverlay.updateNightMode(style, allRegisteredProviders, isNightMode, chartOpacity)
        }
    }

    LaunchedEffect(mapReadyToken, obsBoatIconSize, obsBoatIconColor) {
        val style = currentStyle.value ?: return@LaunchedEffect
        BoatOverlay.setupLayers(style, obsBoatIconSize, obsBoatIconColor)
    }

    LaunchedEffect(mapReadyToken, location, bearingDeg, speedMps, courseLineMinutes) {
        val style = currentStyle.value ?: return@LaunchedEffect
        val p = location ?: return@LaunchedEffect
        BoatOverlay.updateBoatAndCourse(style, p.latitude, p.longitude, bearingDeg, speedMps, courseLineMinutes)
    }

    LaunchedEffect(mapReadyToken, centerTrigger) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        val pos = location ?: return@LaunchedEffect
        if (centerTrigger > 0) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(pos, map.cameraPosition.zoom.coerceAtLeast(10.0)),
                500
            )
        }
    }

    LaunchedEffect(mapReadyToken, zoomToBoatTrigger) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        val pos = location ?: return@LaunchedEffect
        if (zoomToBoatTrigger > 0) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(pos, zoomToBoatLevel.toDouble().coerceIn(4.0, 18.0)),
                150
            )
        }
    }

    // Center map on target (from search)
    LaunchedEffect(mapReadyToken, centerTarget) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        val target = centerTarget ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(target.latitude, target.longitude),
                map.cameraPosition.zoom.coerceAtLeast(12.0)
            ),
            800
        )
    }

    LaunchedEffect(mapReadyToken, poiMarker) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> MeasureOverlay.updatePoiMarker(style, poiMarker) }
    }

    // Camera tracking: follow mode + orientation mode
    LaunchedEffect(mapReadyToken, followMode, orientationMode) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        snapshotFlow { obsLocation to (obsBearing to obsSpeed) }.collect { (pos, pair) ->
            val (bearing, speed) = pair
            val offsetFactor = obsLookAheadFactor
            val p = pos ?: return@collect
            if (followMode == FollowMode.OFF) return@collect

            val target = when (followMode) {
                FollowMode.CENTERED -> {
                    if (offsetFactor > 0f) {
                        val boatScrn = map.projection.toScreenLocation(LatLng(p.latitude, p.longitude))
                        val mapH = mapView.height.toFloat()
                        if (mapH > 0f) {
                            boatScrn.offset(0f, -(mapH * offsetFactor))
                            val ahead = map.projection.fromScreenLocation(boatScrn)
                            LatLng(ahead.latitude, ahead.longitude)
                        } else p
                    } else p
                }
                FollowMode.OFF -> return@collect
            }

            val cameraBearing = when (orientationMode) {
                OrientationMode.NORTH_UP -> 0.0
                OrientationMode.COURSE_UP -> (bearing ?: 0f).toDouble()
            }

            val zoom = map.cameraPosition.zoom.coerceAtLeast(10.0)
            val cameraPos = CameraPosition.Builder()
                .target(target)
                .zoom(zoom)
                .bearing(cameraBearing)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPos),
                1000
            )
        }
    }

    LaunchedEffect(mapReadyToken, routeWaypoints) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> RouteOverlay.updatePlanning(style, routeWaypoints) }
    }

    LaunchedEffect(mapReadyToken, measureLinePoints) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> MeasureOverlay.updateMeasureLine(style, map, measureLinePoints) }
    }

    LaunchedEffect(mapReadyToken, recordingTrail) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> RouteOverlay.updateRecordingTrail(style, recordingTrail) }
    }

    LaunchedEffect(mapReadyToken, savedRouteLines) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> RouteOverlay.updateSavedRoutes(style, savedRouteLines) }
    }

    // ── AIS vessel overlay ──────────────────────────────────────────

    LaunchedEffect(mapReadyToken, showAis, vessels) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style ->
            if (showAis) {
                AisVesselOverlay.update(style, vessels)
            } else {
                AisVesselOverlay.remove(style)
            }
        }
    }

    LaunchedEffect(mapReadyToken, showLightning, lightningStrikes) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> WeatherOverlay.updateLightning(style, showLightning, lightningStrikes) }
    }

    LaunchedEffect(mapReadyToken, showWarnings, warnings) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> WeatherOverlay.updateWarnings(style, showWarnings, warnings) }
    }

    LaunchedEffect(mapReadyToken, showRadar, radarTimeOffset, obsRadarOpacity, radarRefreshTick, radarRetryTick, currentRadarProvider?.providerId ?: "") {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        val token = ++radarEffectToken

        if (!showRadar) {
            map.getStyle { style ->
                if (token == radarEffectToken) RadarOverlay.remove(style)
            }
            return@LaunchedEffect
        }

        val provider = currentRadarProvider
        if (provider == null) {
            return@LaunchedEffect
        }
        val result = provider.getRadarTileUrl(radarTimeOffset)

        if (result == null) {
            kotlinx.coroutines.delay(1000)
            radarRetryTick++
            return@LaunchedEffect
        }

        currentOnRadarEffectiveDelay(result.effectiveDelayMinutes)

        map.getStyle { style ->
            if (token != radarEffectToken) return@getStyle
            RadarOverlay.update(style, provider, result.url, obsRadarOpacity)
        }
    }

    LaunchedEffect(mapReadyToken, showAlgae, waterObservations) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> WfsOverlay.updateWaterObservations(style, showAlgae, waterObservations) }
    }

    LaunchedEffect(mapReadyToken, showAlgae) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        map.getStyle { style -> WeatherOverlay.updateAlgaeSatellite(style, showAlgae) }
    }

    var lastSeamarkHidden by remember { mutableStateOf(false) }
    LaunchedEffect(mapReadyToken, chartOpacity) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        val shouldHide = chartOpacity > 0.85f
        if (shouldHide == lastSeamarkHidden || seamarkLayerIds.isEmpty()) return@LaunchedEffect
        lastSeamarkHidden = shouldHide
        map.getStyle { style ->
            ChartOverlay.updateSeamarkVisibility(style, seamarkLayerIds, chartOpacity)
        }
    }

    var depthGen by remember { mutableStateOf(0) }

    LaunchedEffect(mapReadyToken, showDepth, depthFeatures) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        depthGen++
        val myGen = depthGen
        val prepared = withContext(Dispatchers.Default) {
            WfsOverlay.prepareDepth(showDepth, depthFeatures)
        }
        map.getStyle { style ->
            if (depthGen != myGen) return@getStyle
            WfsOverlay.applyDepth(style, prepared, isNightMode)
        }
    }

    var turvalaiteGen by remember { mutableStateOf(0) }

    LaunchedEffect(mapReadyToken, showVvNavmarks, turvalaiteFeatures, turvalaitevikaFeatures, vvFetchCounter, navmarkSize) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        if (!showVvNavmarks) {
            map.getStyle { style ->
                WfsOverlay.applyTurvalaite(style, null, false, navmarkSize.multiplier, isNightMode)
            }
            return@LaunchedEffect
        }
        turvalaiteGen++
        val myGen = turvalaiteGen
        val prepared = withContext(Dispatchers.Default) {
            WfsOverlay.prepareTurvalaite(showVvNavmarks, turvalaiteFeatures, turvalaitevikaFeatures)
        }
        val viewport = currentViewportBounds
        val hasFeaturesInView = prepared != null && WfsOverlay.hasTurvalaiteInView(prepared, viewport)
        map.getStyle { style ->
            if (turvalaiteGen != myGen) return@getStyle
            WfsOverlay.applyTurvalaite(style, prepared, hasFeaturesInView, navmarkSize.multiplier, isNightMode)
        }
    }

    var vvOverlayGen by remember { mutableStateOf(0) }

    LaunchedEffect(mapReadyToken, showVvNavmarks, showSectors, navlineFeatures, fairwayFeatures, valosektoriFeatures, vesiliikennemerkkiFeatures, vvFetchCounter) {
        val map = currentMap.value as? MapLibreMap ?: return@LaunchedEffect
        if (!showVvNavmarks) {
            map.getStyle { style ->
                WfsOverlay.updateVvFeatures(style, showVvNavmarks, showSectors, navlineFeatures, fairwayFeatures, valosektoriFeatures, vesiliikennemerkkiFeatures, isNightMode)
            }
            return@LaunchedEffect
        }
        vvOverlayGen++
        val myGen = vvOverlayGen
        map.getStyle { style ->
            if (vvOverlayGen != myGen) return@getStyle
            WfsOverlay.updateVvFeatures(style, showVvNavmarks, showSectors, navlineFeatures, fairwayFeatures, valosektoriFeatures, vesiliikennemerkkiFeatures, isNightMode)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { }
    )
}



private fun configureMap(map: MapLibreMap, context: Context, initialChartOpacity: Float = 1.0f) {
    map.setMinZoomPreference(4.0)
    map.setMaxZoomPreference(18.0)

    map.uiSettings.isAttributionEnabled = true
    map.uiSettings.isLogoEnabled = false
    map.uiSettings.isRotateGesturesEnabled = true
    map.uiSettings.isTiltGesturesEnabled = true
    map.uiSettings.isCompassEnabled = false

    val cameraPosition = CameraPosition.Builder()
        .target(LatLng(60.0, 23.0))
        .zoom(7.0)
        .build()
    map.cameraPosition = cameraPosition
}

private fun Style.registerNavmarkIcons(context: android.content.Context, isNightMode: Boolean = false) {
    val dir = if (isNightMode) "navmarks-night" else "navmarks"
    val names = listOf(
        "bc_north", "bc_east", "bc_south", "bc_west",
        "bc_bcn_north", "bc_bcn_east", "bc_bcn_south", "bc_bcn_west",
        "bl_red", "bl_green", "bl_bcn_red", "bl_bcn_green",
        "bsw", "bsw_bcn", "bid", "bid_bcn", "bsp", "bsp_bcn",
        "bi", "daymark", "fog",
        "lmaj_red", "lmaj_green", "lmaj_yellow", "lmaj_white",
        "lmin_red", "lmin_green", "lmin_yellow", "lmin_white",
        "lflt_red", "lflt_green", "lflt_yellow", "lflt_white",
        "lves", "moor_buoy",
        "plat", "top_cone_point_up", "virt",
        "buoy", "beacon", "marker",
        "josm_tower_generic", "josm_buoyant_generic",
        "josm_spar_generic", "josm_pillar_generic",
        "josm_spherical_generic", "josm_can_generic",
        "josm_conical_generic", "josm_stake_generic",
        "josm_cairn_generic", "josm_super-buoy_generic",
        "josm_Q126_generic_crossing", "josm_Q126_BNIWR_no_anchoring",
        "josm_Q126_generic_no_berthing", "josm_Q126_CEVNI_no_convoy_overtaking",
        "josm_Q126_CEVNI_no_convoy_passing", "josm_Q126_generic_speed_limit",
        "josm_Q126_generic_no_waterskiing", "josm_Q126_generic_no_sailboards",
        "josm_Q126_CEVNI_no_entry", "josm_Q126_CEVNI_no_waterbikes",
        "josm_Q126_CEVNI_stop", "josm_Q126_generic_make_radio_contact",
        "josm_Q126_BNIWR_limited_headroom", "josm_Q126_generic_limited_depth",
        "josm_Q126_CEVNI_radio_information", "josm_Q126_CEVNI_berthing_permitted",
        "josm_Q126_CEVNI_overhead_cable", "josm_Q126_generic_telephone",
        "josm_Q126_generic_ferry_independent", "josm_Q126_generic_drinking_water",
        "josm_Q126_CEVNI_prohibition_ends", "josm_Q126_generic_alignment",
    )
    var loaded = 0
    for (name in names) {
        try {
            val bmp = android.graphics.BitmapFactory.decodeStream(
                context.assets.open("$dir/$name.png")
            )
            if (bmp != null && getImage(name) == null) {
                addImage(name, bmp)
                loaded++
            }
        } catch (_: Exception) {}
    }
    android.util.Log.d("SeamarkServer", "Loaded $loaded navmark icons from $dir")
}

private fun Style.loadOfmSprite(context: android.content.Context) {
    try {
        val dm = context.resources.displayMetrics.density
        val sfname = if (dm >= 2.0f) "ofm@2x" else "ofm"
        val jsobj = org.json.JSONObject(context.assets.open("ofm_sprite/$sfname.json").bufferedReader().readText())
        val bmp = android.graphics.BitmapFactory.decodeStream(context.assets.open("ofm_sprite/$sfname.png"))
        val names = jsobj.names()
        var count = 0
        for (i in 0 until names.length()) {
            val n = names.getString(i)
            if (getImage(n) == null) {
                val e = jsobj.getJSONObject(n)
                val icon = android.graphics.Bitmap.createBitmap(bmp, e.getInt("x"), e.getInt("y"), e.getInt("width"), e.getInt("height"))
                addImage(n, icon)
                count++
            }
        }
        android.util.Log.d("PursiMap", "Loaded $count ofm sprite icons")
    } catch (ex: Exception) {
        android.util.Log.e("PursiMap", "OFM sprite error: ${ex.message}")
    }
}

private fun Style.loadPoiIcons(context: android.content.Context) {
    val names = listOf(
        "sauna", "bbq", "bench", "shower", "firepit", "campfire",
        "viewpoint", "information"
    )
    var loaded = 0
    for (name in names) {
        try {
            val bmp = android.graphics.BitmapFactory.decodeStream(
                context.assets.open("poi_icons/$name.png")
            )
            if (bmp != null && getImage(name) == null) {
                addImage(name, bmp)
                loaded++
            }
        } catch (_: Exception) {}
    }
    android.util.Log.d("PursiMap", "Loaded $loaded POI icons")
}
