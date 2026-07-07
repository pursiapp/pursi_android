package app.pursi.ui.viewmodel

import android.content.Context
import android.location.Location
import org.maplibre.android.geometry.LatLng
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import app.pursi.data.dao.BoatDao
import app.pursi.data.dao.EmodnetDepthSampleDao
import app.pursi.data.dao.SavedRouteDao
import app.pursi.data.dao.TrackDao
import app.pursi.data.dao.TrackSummaryDao
import app.pursi.data.dao.WfsFeatureDao
import app.pursi.data.model.EmodnetDepthSample
import app.pursi.data.model.WfsFeature
import app.pursi.datasource.core.ChartProvider
import app.pursi.datasource.core.FeatureRendererRegistry
import app.pursi.datasource.core.SourceResolver
import app.pursi.datasource.global.EmodnetDepthClient
import app.pursi.ais.AisRepository
import app.pursi.ais.AisVessel
import app.pursi.ais.VesselMetadata
import app.pursi.location.LocationStateHolder
import app.pursi.location.TrackRecorder
import app.pursi.weather.ForecastPoint
import app.pursi.weather.MarineWarning
import app.pursi.weather.WeatherRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.delay
import app.pursi.weather.sunriseSunset
import javax.inject.Inject

import app.pursi.water.WaterObservation
import app.pursi.water.WaterObservationRepository

private const val MIN_ZOOM_FOR_AREA = 11.0
private const val MIN_ZOOM_FOR_VV = 10.0
private const val BBOX_EXPAND_FACTOR = 1.0
private const val TURVALAITE_BBOX_EXPAND = 2.5
private const val MAX_WFS_REQUESTS = 4
private const val RATE_WINDOW_MS = 15_000L

enum class FollowMode { OFF, CENTERED }
enum class OrientationMode { NORTH_UP, COURSE_UP }
enum class NightMode { DAY, NIGHT, AUTO }
enum class SectorMode { OFF, NIGHT, ALWAYS }

@HiltViewModel
class MapViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    val trackRecorder: TrackRecorder,
    trackSummaryDao: TrackSummaryDao,
    savedRouteDao: SavedRouteDao,
    boatDao: BoatDao,
    trackDao: TrackDao,
    private val sourceResolver: SourceResolver,
    private val weatherRepository: WeatherRepository,
    private val locationStateHolder: LocationStateHolder,
    private val aisRepository: AisRepository,
    private val waterObservationRepository: WaterObservationRepository,
    private val featureRendererRegistry: FeatureRendererRegistry,
    private val emodnetDepthClient: EmodnetDepthClient,
    private val emodnetDepthSampleDao: EmodnetDepthSampleDao,
    private val wfsFeatureDao: WfsFeatureDao
) : ViewModel() {
    private val prefs = context.getSharedPreferences("pursi_map", Context.MODE_PRIVATE)

    private var lastBoundsMinLat = 0.0
    private var lastBoundsMinLng = 0.0
    private var lastBoundsMaxLat = 0.0
    private var lastBoundsMaxLng = 0.0
    private var lastBoundsZoom = 0.0
    private var hasBounds = false
    private var lastCameraLat = Double.NaN
    private var lastCameraLon = Double.NaN
    private var depthFetchJob: kotlinx.coroutines.Job? = null
    private var turvalaiteFetchJob: kotlinx.coroutines.Job? = null
    private var turvalaitevikaFetchJob: kotlinx.coroutines.Job? = null
    private val wfsRequestTimestamps = ConcurrentLinkedQueue<Long>()

    val savedRouteDao = savedRouteDao
    val trackSummaryDao = trackSummaryDao
    val boatDao = boatDao
    val trackDao = trackDao

    val currentLocation = locationStateHolder.currentLocation
    val isMockLocation = locationStateHolder.isMocking
    val lastKnownBearing: StateFlow<Float?> = locationStateHolder.lastKnownBearing

    private val _currentRadarProvider = MutableStateFlow<app.pursi.datasource.core.RadarProvider?>(null)
    val currentRadarProvider: StateFlow<app.pursi.datasource.core.RadarProvider?> = _currentRadarProvider.asStateFlow()

    private val _nightMode = MutableStateFlow(
        try { NightMode.valueOf(savedStateHandle.get<String>("nightMode")
            ?: prefs.getString("night_mode", NightMode.DAY.name)
            ?: NightMode.DAY.name) }
        catch (_: Exception) { NightMode.DAY }
    )
    val nightMode: StateFlow<NightMode> = _nightMode.asStateFlow()

    private val _effectiveIsNightMode = MutableStateFlow(false)
    val effectiveIsNightMode: StateFlow<Boolean> = _effectiveIsNightMode.asStateFlow()

    init {
        viewModelScope.launch {
            val active = trackSummaryDao.hasActiveRecording()
            if (active > 0) {
                val orphaned = trackSummaryDao.getActiveRecordings()
                for (t in orphaned) {
                    val pts = trackDao.getPointCount(t.id)
                    val points = trackDao.getTrackPointsSync(t.id)
                    var distanceNm = 0.0
                    var maxSpeedKn: Float? = null
                    for (i in 0 until points.size - 1) {
                        distanceNm += app.pursi.location.SpeedCalculator.distanceNm(
                            points[i].latitude, points[i].longitude,
                            points[i + 1].latitude, points[i + 1].longitude
                        )
                    }
                    for (p in points) {
                        p.speedOverGround?.let { speed ->
                            val kn = speed / 0.514f
                            if (maxSpeedKn == null || kn > maxSpeedKn) maxSpeedKn = kn
                        }
                    }
                    trackSummaryDao.finalize(t.id, System.currentTimeMillis(), pts, distanceNm, maxSpeedKn)
                }
            }
        }

        viewModelScope.launch {
            locationStateHolder.currentLocation.collect { loc ->
                if (loc != null) {
                    trackRecorder.onLocationUpdate(Location("pursi").apply {
                        latitude = loc.latitude
                        longitude = loc.longitude
                        speed = loc.speed
                        bearing = loc.bearing
                        time = System.currentTimeMillis()
                    })
                }
            }
        }

        viewModelScope.launch {
            _nightMode.collectLatest { mode ->
                if (mode != NightMode.AUTO) {
                    _effectiveIsNightMode.value = mode == NightMode.NIGHT
                } else {
                    while (true) {
                        val loc = currentLocation.value
                        if (loc != null) {
                            val (sunrise, sunset) = sunriseSunset(
                                loc.latitude, loc.longitude, System.currentTimeMillis() / 1000L
                            )
                            val now = System.currentTimeMillis() / 1000L
                            _effectiveIsNightMode.value = now < sunrise || now > sunset
                            delay(300_000L)
                        } else {
                            delay(30_000L)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            val yearMs = 365L * 24 * 3600 * 1000
            val cutoff = System.currentTimeMillis() - yearMs
            emodnetDepthSampleDao.clearOlderThan(cutoff)
        }
    }

    val warnings: StateFlow<List<MarineWarning>> = weatherRepository.warnings

    private val _requestedWeatherTab = MutableStateFlow(-1)
    val requestedWeatherTab: StateFlow<Int> = _requestedWeatherTab.asStateFlow()

    fun requestWeatherTab(tab: Int) { _requestedWeatherTab.value = tab }

    fun consumeRequestedWeatherTab(): Int {
        val tab = _requestedWeatherTab.value
        if (tab >= 0) _requestedWeatherTab.value = -1
        return tab
    }

    val lightning: StateFlow<List<app.pursi.weather.LightningStrike>> = weatherRepository.lightning

    val lightningMode: StateFlow<app.pursi.weather.WeatherRepository.LightningMode> = weatherRepository.lightningMode

    val forecast: StateFlow<List<ForecastPoint>> = weatherRepository.forecast

    private var windMeterRefreshJob: kotlinx.coroutines.Job? = null

    val vessels: StateFlow<List<AisVessel>> = aisRepository.vessels
    val aisMetadata: StateFlow<Map<Int, VesselMetadata>> = aisRepository.metadata
    val aisRateLimited: StateFlow<String?> = aisRepository.rateLimited

    val allChartProviders: List<ChartProvider> = sourceResolver.chartProviders.toList()

    private fun boolPref(key: String, default: Boolean): Boolean =
        savedStateHandle.get<Boolean>(key) ?: prefs.getBoolean(key, default)

    private fun persistBoth(key: String, value: Boolean) {
        savedStateHandle[key] = value
        prefs.edit().putBoolean(key, value).apply()
    }

    private inline fun toggleUiFlag(
        key: String,
        crossinline get: (MapUiState) -> Boolean,
        crossinline set: (MapUiState, Boolean) -> MapUiState,
        noinline after: ((Boolean) -> Unit)? = null,
    ) {
        val v = !get(_uiState.value)
        _uiState.update { set(it, v) }
        persistBoth(key, v)
        after?.invoke(v)
    }

    private fun defaultNavmarkSize(): NavmarkSize {
        val swDp = context.resources.configuration.smallestScreenWidthDp
        return if (swDp < 500) NavmarkSize.LARGE else NavmarkSize.MEDIUM
    }

    private fun readNavmarkSize(): NavmarkSize {
        val stored = savedStateHandle.get<String>("navmarkSize")
            ?: prefs.getString("navmark_size", null)
        if (stored != null) {
            return try { NavmarkSize.valueOf(stored) } catch (_: Exception) { defaultNavmarkSize() }
        }
        val def = defaultNavmarkSize()
        savedStateHandle["navmarkSize"] = def.name
        prefs.edit().putString("navmark_size", def.name).apply()
        return def
    }

    private fun readBoatIconSize(): BoatIconSize {
        val stored = savedStateHandle.get<String>("boatIconSize")
            ?: prefs.getString("boat_icon_size", null)
        if (stored != null) {
            return try { BoatIconSize.valueOf(stored) } catch (_: Exception) { BoatIconSize.MEDIUM }
        }
        return BoatIconSize.MEDIUM
    }

    private fun readBoatIconColor(): String {
        return savedStateHandle.get<String>("boatIconColor")
            ?: prefs.getString("boat_icon_color", null)
            ?: "#F57C00"
    }

    private val _uiState = MutableStateFlow(
        MapUiState(
            showLightning = boolPref("showLightning", false),
            showWarnings = boolPref("showWarnings", false),
            showRadar = boolPref("showRadar", false),
            showAis = boolPref("showAis", false),
            radarOpacity = 0.4f,
            chartOpacity = savedStateHandle.get<Float>("chartOpacity") ?: 1.0f,
            chartProviders = allChartProviders,
            lookAheadSec = savedStateHandle.get<Int>("lookAheadSec") ?: 5,
            followMode = savedStateHandle.get<FollowMode>("followMode") ?: FollowMode.CENTERED,
            orientationMode = savedStateHandle.get<OrientationMode>("orientationMode") ?: OrientationMode.COURSE_UP,
            seamarksDownloaded = java.io.File(context.filesDir, "seamarks.pmtiles").exists(),
            downloadedSeamarkContinents = app.pursi.map.PmtilesDownloader.CONTINENTS
                .map { it.id }
                .filter { java.io.File(context.filesDir, "seamarks_$it.pmtiles").exists() }
                .toSet(),
            showAlgae = boolPref("showAlgae", false),
            showDepth = boolPref("showDepth", true),
            showWindMeter = boolPref("showWindMeter", false),
            navmarkSize = readNavmarkSize(),
            boatIconSize = readBoatIconSize(),
            boatIconColor = readBoatIconColor(),
            fiState = FinnishMapState(
                vvDataDownloaded = java.io.File(context.filesDir, "vv_data.meta").exists(),
                showVvNavmarks = boolPref("showVvNavmarks", true),
                showTurvalaiteviat = boolPref("showTurvalaiteviat", true)
            ),
            sectorMode = try { SectorMode.valueOf(
                savedStateHandle.get<String>("sectorMode") ?: prefs.getString("sector_mode", SectorMode.NIGHT.name) ?: SectorMode.NIGHT.name
            ) } catch (_: Exception) { SectorMode.NIGHT }
        )
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _searchTarget = MutableStateFlow<LatLng?>(null)
    val searchTarget: StateFlow<LatLng?> = _searchTarget.asStateFlow()

    private val _viewingRouteId = MutableStateFlow<String?>(null)
    val viewingRouteId: StateFlow<String?> = _viewingRouteId.asStateFlow()

    private val _viewingTrackId = MutableStateFlow<String?>(null)
    val viewingTrackId: StateFlow<String?> = _viewingTrackId.asStateFlow()

    init {
        aisRepository.setEnabled(_uiState.value.showAis)
    }

    private fun updateFiState(transform: FinnishMapState.() -> FinnishMapState) {
        _uiState.update { it.copy(fiState = (it.fiState ?: FinnishMapState()).transform()) }
    }

    private val fiState: FinnishMapState get() = _uiState.value.fiState ?: FinnishMapState()

    fun setSearchTarget(latitude: Double, longitude: Double) {
        _searchTarget.value = LatLng(latitude, longitude)
    }

    fun clearSearchTarget() {
        _searchTarget.value = null
    }

    fun setViewingRouteId(id: String?) {
        _viewingRouteId.value = id
    }

    fun setViewingTrackId(id: String?) {
        _viewingTrackId.value = id
    }

    fun setCameraTarget(latitude: Double, longitude: Double) {
        lastCameraLat = latitude
        lastCameraLon = longitude
        val radarP = sourceResolver.radarProviderFor(latitude, longitude)
        _currentRadarProvider.value = radarP
        android.util.Log.d("PursiMap", "setCameraTarget($latitude, $longitude): radarProvider=${radarP?.providerId ?: "null"}, zoom=$lastBoundsZoom")
        val providers = sourceResolver.chartProvidersFor(latitude, longitude)
        val newIds = providers.map { it.providerId }.toSet()
        val currentIds = _uiState.value.chartProviders.map { it.providerId }.toSet()
        if (newIds != currentIds) {
            _uiState.update { it.copy(chartProviders = providers) }
        }
        aisRepository.setQueryCenter(latitude, longitude)
    }

    fun setCameraBounds(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double, zoom: Double) {
        lastBoundsMinLat = minLat
        lastBoundsMinLng = minLng
        lastBoundsMaxLat = maxLat
        lastBoundsMaxLng = maxLng
        lastBoundsZoom = zoom
        hasBounds = true
        aisRepository.setQueryBounds(minLat, minLng, maxLat, maxLng)
        if (_uiState.value.showDepth) {
            fetchDepthFeatures(minLat, minLng, maxLat, maxLng, zoom)
            fetchEmodnetDepthFeatures(minLat, minLng, maxLat, maxLng, zoom)
        }
        if (fiState.showVvNavmarks) {
            fetchTurvalaitteet(minLat, minLng, maxLat, maxLng, zoom)
        }
        if (fiState.showTurvalaiteviat) {
            fetchTurvalaiteviat(minLat, minLng, maxLat, maxLng, zoom)
        }
    }

    fun setChartOpacity(opacity: Float) {
        _uiState.update { it.copy(chartOpacity = opacity) }
        savedStateHandle["chartOpacity"] = opacity
    }

    fun toggleShowLightning() = toggleUiFlag("showLightning", { it.showLightning }, { s, v -> s.copy(showLightning = v) })

    fun toggleShowWarnings() = toggleUiFlag("showWarnings", { it.showWarnings }, { s, v -> s.copy(showWarnings = v) })

    fun toggleShowAlgae() = toggleUiFlag("showAlgae", { it.showAlgae }, { s, v -> s.copy(showAlgae = v) }) { v ->
        if (v) _uiState.update { it.copy(waterObservations = waterObservationRepository.observations.value) }
        else _uiState.update { it.copy(waterObservations = emptyList()) }
    }

    init {
        viewModelScope.launch {
            waterObservationRepository.observations.collect { obs ->
                if (_uiState.value.showAlgae) {
                    _uiState.update { it.copy(waterObservations = obs) }
                }
            }
        }
        if (_uiState.value.showAlgae) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(waterObservations = waterObservationRepository.observations.value)
                }
            }
        }
    }

    fun selectAlgaeObservations(obs: List<WaterObservation>) {
        _uiState.update { it.copy(
            selectedAlgaeObservations = obs,
            selectedSeamark = null
        ) }
    }

    fun toggleShowRadar() = toggleUiFlag("showRadar", { it.showRadar }, { s, v -> s.copy(showRadar = v) }) { v ->
        if (v && !lastCameraLat.isNaN() && !lastCameraLon.isNaN()) {
            _currentRadarProvider.value = sourceResolver.radarProviderFor(lastCameraLat, lastCameraLon)
        }
    }

    fun toggleShowAis() = toggleUiFlag("showAis", { it.showAis }, { s, v -> s.copy(showAis = v) }) { v ->
        aisRepository.setEnabled(v)
        if (v) aisRepository.triggerRefresh()
    }

    fun toggleShowDepth() = toggleUiFlag("showDepth", { it.showDepth }, { s, v -> s.copy(showDepth = v) }) { v ->
        if (v && hasBounds) fetchDepthFeatures(lastBoundsMinLat, lastBoundsMinLng, lastBoundsMaxLat, lastBoundsMaxLng, lastBoundsZoom)
    }

    fun toggleShowWindMeter() {
        val v = !_uiState.value.showWindMeter
        _uiState.update { it.copy(showWindMeter = v) }
        persistBoth("showWindMeter", v)
        if (v) {
            windMeterRefreshJob?.cancel()
            windMeterRefreshJob = viewModelScope.launch {
                while (true) {
                    delay(300_000L)
                    weatherRepository.refresh(force = true)
                }
            }
        } else {
            windMeterRefreshJob?.cancel()
            windMeterRefreshJob = null
        }
    }

    private fun fetchDepthFeatures(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double, zoom: Double) {
        if (zoom < MIN_ZOOM_FOR_AREA) return

        // Expand bbox so panning doesn't immediately trigger a re-fetch
        val latSpan = maxLat - minLat
        val lngSpan = maxLng - minLng
        val qMinLat = (minLat - latSpan * BBOX_EXPAND_FACTOR).coerceAtLeast(-90.0)
        val qMaxLat = (maxLat + latSpan * BBOX_EXPAND_FACTOR).coerceAtMost(90.0)
        val qMinLng = (minLng - lngSpan * BBOX_EXPAND_FACTOR).coerceAtLeast(-180.0)
        val qMaxLng = (maxLng + lngSpan * BBOX_EXPAND_FACTOR).coerceAtMost(180.0)

        // Cancel in-flight fetch, start a new one with debounce
        depthFetchJob?.cancel()
        depthFetchJob = viewModelScope.launch {
            // Small debounce: if user zooms/pans again during this delay, coroutine is
            // cancelled and no WFS request is made
            delay(300L)

            // Rate limit: max MAX_WFS_REQUESTS per RATE_WINDOW_MS sliding window
            val now = System.currentTimeMillis()
            val cutoff = now - RATE_WINDOW_MS
            while (true) {
                val oldest = wfsRequestTimestamps.peek() ?: break
                if (oldest < cutoff) wfsRequestTimestamps.poll() else break
            }
            if (wfsRequestTimestamps.size >= MAX_WFS_REQUESTS) return@launch
            wfsRequestTimestamps.add(now)

            val lat = (qMinLat + qMaxLat) / 2.0
            val lng = (qMinLng + qMaxLng) / 2.0
            val provider = sourceResolver.marineFeatureProviderFor(lat, lng)
            if (provider == null) {
                _uiState.update { it.copy(depthFeatures = emptyMap()) }
                return@launch
            }
            val allDepthSources = provider.sources.filter { it.featureType.startsWith("depth_") }
            if (allDepthSources.isEmpty()) return@launch
            val result = mutableMapOf<String, List<WfsFeature>>()
            for (source in allDepthSources) {
                val queryResult = provider.getFeatures(source, qMinLat, qMinLng, qMaxLat, qMaxLng)
                if (queryResult.features.isNotEmpty()) result[source.featureType] = queryResult.features
            }
            _uiState.update { it.copy(depthFeatures = result) }
        }
    }

    private fun fetchEmodnetDepthFeatures(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double, zoom: Double) {
        if (zoom < MIN_ZOOM_FOR_AREA) return

        viewModelScope.launch {
            delay(600L)

            val allKeys = emodnetDepthClient.gridKeysForBbox(minLat, minLng, maxLat, maxLng)
            if (allKeys.isEmpty()) return@launch

            val cached = emodnetDepthSampleDao.getByGridKeys(allKeys)
            val cachedKeySet = cached.map { it.gridKey }.toSet()
            val missing = allKeys - cachedKeySet

            if (cached.isNotEmpty()) {
                _uiState.update { it.copy(emodnetDepthSamples = cached) }
            }

            if (missing.isEmpty()) return@launch

            val hasTraficomData = _uiState.value.depthFeatures.values.flatten().isNotEmpty()
            if (hasTraficomData) {
                _uiState.update { it.copy(emodnetDepthSamples = emptyList()) }
                return@launch
            }

            val newSamples = emodnetDepthClient.fetchMissingGridKeys(missing.toList())
            if (newSamples.isNotEmpty()) {
                emodnetDepthSampleDao.insertAll(newSamples)
            }
            _uiState.update { it.copy(emodnetDepthSamples = cached + newSamples) }
        }
    }

    private fun fetchTurvalaitteet(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double, zoom: Double) {
        if (zoom < MIN_ZOOM_FOR_VV) return
        val latSpan = maxLat - minLat
        val lngSpan = maxLng - minLng
        // Use a larger bbox expansion at high zoom to keep features in the
        // cache longer and reduce re-fetch frequency (camera moves fast at
        // high zoom; a small expansion means every tiny pan requires a new
        // Room query even though the data is already cached).
        val expand = if (zoom > 14) 6.0 else TURVALAITE_BBOX_EXPAND
        val qMinLat = (minLat - latSpan * expand).coerceAtLeast(58.0)
        val qMaxLat = (maxLat + latSpan * expand).coerceAtMost(71.0)
        val qMinLng = (minLng - lngSpan * expand).coerceAtLeast(19.0)
        val qMaxLng = (maxLng + lngSpan * expand).coerceAtMost(33.0)

        turvalaiteFetchJob?.cancel()
        turvalaiteFetchJob = viewModelScope.launch {
            delay(100L)
            val now = System.currentTimeMillis()
            val cutoff = now - RATE_WINDOW_MS
            while (true) {
                val oldest = wfsRequestTimestamps.peek() ?: break
                if (oldest < cutoff) wfsRequestTimestamps.poll() else break
            }
            if (wfsRequestTimestamps.size >= MAX_WFS_REQUESTS) return@launch
            wfsRequestTimestamps.add(now)

            val lat = (qMinLat + qMaxLat) / 2.0
            val lng = (qMinLng + qMaxLng) / 2.0
            val provider = sourceResolver.marineFeatureProviderFor(lat, lng)
            if (provider == null) {
                updateFiState {
                    copy(
                        turvalaiteFeatures = emptyMap(),
                        valosektoriFeatures = emptyMap(),
                        vesiliikennemerkkiFeatures = emptyMap(),
                        navlineFeatures = emptyMap(),
                        fairwayFeatures = emptyMap()
                    )
                }
                return@launch
            }

            suspend fun fetchGroup(type: String): Pair<Map<String, List<WfsFeature>>, Boolean> {
                val sources = provider.sources.filter { it.featureType == type }
                var usedNet = false
                val result = mutableMapOf<String, List<WfsFeature>>()
                for (source in sources) {
                    val qr = provider.getFeatures(source, qMinLat, qMinLng, qMaxLat, qMaxLng)
                    if (!qr.fromCache) usedNet = true
                    if (qr.features.isNotEmpty()) result[source.name] = qr.features
                }
                return result to usedNet
            }

            val (turvalaiteResult, net1) = fetchGroup("navigation_aid")
            val (sektoriResult, net2) = fetchGroup("light_sector")
            val (vesiMerkkiResult, net3) = fetchGroup("notice")
            val (navlineResult, net4) = fetchGroup("navigation_line")
            val (fairwayResult, net5) = fetchGroup("fairway")

            updateFiState {
                copy(
                    turvalaiteFeatures = turvalaiteResult,
                    valosektoriFeatures = sektoriResult,
                    vesiliikennemerkkiFeatures = vesiMerkkiResult,
                    navlineFeatures = navlineResult,
                    fairwayFeatures = fairwayResult,
                    vvUsingNetwork = net1 || net2 || net3 || net4 || net5,
                    vvFetchCounter = vvFetchCounter + 1
                )
            }
        }
    }

    private fun fetchTurvalaiteviat(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double, zoom: Double) {
        if (zoom < MIN_ZOOM_FOR_AREA) return
        val latSpan = maxLat - minLat
        val lngSpan = maxLng - minLng
        val qMinLat = (minLat - latSpan * BBOX_EXPAND_FACTOR).coerceAtLeast(-90.0)
        val qMaxLat = (maxLat + latSpan * BBOX_EXPAND_FACTOR).coerceAtMost(90.0)
        val qMinLng = (minLng - lngSpan * BBOX_EXPAND_FACTOR).coerceAtLeast(-180.0)
        val qMaxLng = (maxLng + lngSpan * BBOX_EXPAND_FACTOR).coerceAtMost(180.0)

        turvalaitevikaFetchJob?.cancel()
        turvalaitevikaFetchJob = viewModelScope.launch {
            delay(300L)
            val lat = (qMinLat + qMaxLat) / 2.0
            val lng = (qMinLng + qMaxLng) / 2.0
            val provider = sourceResolver.marineFeatureProviderFor(lat, lng)
            if (provider == null) {
                updateFiState { copy(turvalaitevikaFeatures = emptyMap()) }
                return@launch
            }
            val vikaSources = provider.sources.filter { it.featureType == "turvalaitevika" }
            val result = mutableMapOf<String, List<WfsFeature>>()
            for (source in vikaSources) {
                val qr = provider.getFeatures(source, qMinLat, qMinLng, qMaxLat, qMaxLng)
                if (qr.features.isNotEmpty()) result[source.name] = qr.features
            }
            updateFiState { copy(turvalaitevikaFeatures = result) }
        }
    }

    /**
     * Geographic lookup for a marine (Väylävirasto / national provider) feature at the given
     * coordinate. Returns the closest cached WfsFeature within [radiusMeters], then dispatches
     * to the appropriate FeatureRenderer to build a SeamarkDetail.
     *
     * This is render-independent: it does not query MapLibre rendered features, so it works
     * at any zoom, before fetches complete, and even when a layer is missing. Used by the
     * click path to prioritize national/marine data over OpenStreetMap sea marks.
     */
    fun findSeamarkAt(lat: Double, lng: Double, radiusMeters: Double = 30.0): SeamarkDetail? {
        val wfs = findWfsFeatureAt(lat, lng, radiusMeters) ?: return null
        val renderer = featureRendererRegistry.getRenderer(wfs.featureType, wfs.source) ?: return null
        return renderer.handleClick(wfs)
    }

    /**
     * Find the closest cached marine WfsFeature within [radiusMeters] of (lat, lng).
     * Searches across all marine feature buckets in fiState. Returns null if the cache
     * has no features in range. Fault reports (turvalaitevika) are excluded because
     * they have no click popup.
     */
    private fun findWfsFeatureAt(lat: Double, lng: Double, radiusMeters: Double): WfsFeature? {
        val fi = fiState
        val all: List<WfsFeature> = buildList {
            for ((_, features) in fi.turvalaiteFeatures) addAll(features)
            for ((_, features) in fi.valosektoriFeatures) addAll(features)
            for ((_, features) in fi.vesiliikennemerkkiFeatures) addAll(features)
            for ((_, features) in fi.navlineFeatures) addAll(features)
            for ((_, features) in fi.fairwayFeatures) addAll(features)
        }
        if (all.isEmpty()) return null
        val best = all.minBy {
            val (dLng, dLat) = approxMetersOffset(it.latitude, it.longitude, lat, lng)
            dLat * dLat + dLng * dLng
        }
        val (dLng, dLat) = approxMetersOffset(best.latitude, best.longitude, lat, lng)
        val dist = kotlin.math.sqrt(dLat * dLat + dLng * dLng)
        return if (dist <= radiusMeters) best else null
    }

    private fun approxMetersOffset(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Pair<Double, Double> {
        val meanLatRad = Math.toRadians((lat1 + lat2) / 2.0)
        val dLatMeters = (lat2 - lat1) * 111_320.0
        val dLngMeters = (lng2 - lng1) * 111_320.0 * kotlin.math.cos(meanLatRad)
        return dLngMeters to dLatMeters
    }

    fun setRadarTimeOffset(minutesAgo: Int) {
        val maxHistory = _currentRadarProvider.value?.maxHistoryMinutes ?: 60
        _uiState.update { it.copy(radarTimeOffset = minutesAgo.coerceIn(0, maxHistory)) }
    }

    fun setRadarEffectiveDelay(delayMinutes: Int) {
        _uiState.update { it.copy(radarEffectiveDelay = delayMinutes) }
    }

    fun fetchVesselMetadata(mmsi: Int) {
        viewModelScope.launch {
            aisRepository.fetchMetadata(mmsi)
        }
    }

    fun toggleRainAndLightning() {
        val current = _uiState.value.showRadar && _uiState.value.showLightning
        val target = !current
        _uiState.update {
            it.copy(
                showRadar = target,
                showLightning = target,
                radarTimeOffset = if (target) 0 else it.radarTimeOffset
            )
        }
        persistBoth("showRadar", target)
        persistBoth("showLightning", target)
        if (target && !lastCameraLat.isNaN() && !lastCameraLon.isNaN()) {
            _currentRadarProvider.value = sourceResolver.radarProviderFor(lastCameraLat, lastCameraLon)
        }
    }

    fun setMockLocation(lat: Double, lon: Double) {
        locationStateHolder.setMockLocation(lat, lon)
        _uiState.update { it.copy(followMode = FollowMode.OFF) }
    }

    fun centerOnLocation() {
        locationStateHolder.clearMockLocation()
        locationStateHolder.forceUpdateLocation()
        _uiState.update { it.copy(followMode = FollowMode.CENTERED) }
        savedStateHandle["followMode"] = FollowMode.CENTERED
    }

    fun setFollowMode(mode: FollowMode) {
        _uiState.update { it.copy(followMode = mode) }
        savedStateHandle["followMode"] = mode
    }

    fun cycleOrientationMode() {
        _uiState.update {
            it.copy(orientationMode = when (it.orientationMode) {
                OrientationMode.NORTH_UP -> OrientationMode.COURSE_UP
                OrientationMode.COURSE_UP -> OrientationMode.NORTH_UP
            })
        }
        savedStateHandle["orientationMode"] = _uiState.value.orientationMode
    }

    fun setNightMode(mode: NightMode) {
        android.util.Log.d("PursiMap", "setNightMode: $mode")
        _nightMode.value = mode
        savedStateHandle["nightMode"] = mode.name
        prefs.edit().putString("night_mode", mode.name).apply()
    }

    fun setSectorMode(mode: SectorMode) {
        android.util.Log.d("PursiMap", "setSectorMode: $mode")
        val wasOff = _uiState.value.sectorMode == SectorMode.OFF
        _uiState.update { it.copy(sectorMode = mode) }
        savedStateHandle["sectorMode"] = mode.name
        prefs.edit().putString("sector_mode", mode.name).apply()
        if (wasOff && mode != SectorMode.OFF && hasBounds) {
            fetchTurvalaitteet(lastBoundsMinLat, lastBoundsMinLng, lastBoundsMaxLat, lastBoundsMaxLng, lastBoundsZoom)
        }
    }

    fun isSectorVisible(isNightMode: Boolean): Boolean = when (_uiState.value.sectorMode) {
        SectorMode.OFF -> false
        SectorMode.NIGHT -> isNightMode
        SectorMode.ALWAYS -> true
    }

    fun setOrientationMode(mode: OrientationMode) {
        _uiState.update { it.copy(orientationMode = mode) }
        savedStateHandle["orientationMode"] = mode
    }

    fun setLookAheadSec(sec: Int) {
        _uiState.update { it.copy(lookAheadSec = sec.coerceIn(3, 15)) }
        savedStateHandle["lookAheadSec"] = _uiState.value.lookAheadSec
    }

    fun toggleShowVvNavmarks() {
        val v = !fiState.showVvNavmarks
        updateFiState { copy(showVvNavmarks = v) }
        persistBoth("showVvNavmarks", v)
        if (v && hasBounds) {
            fetchTurvalaitteet(lastBoundsMinLat, lastBoundsMinLng, lastBoundsMaxLat, lastBoundsMaxLng, lastBoundsZoom)
        } else if (!v) {
            updateFiState { copy(
                turvalaiteFeatures = emptyMap(),
                valosektoriFeatures = emptyMap(),
                vesiliikennemerkkiFeatures = emptyMap(),
                navlineFeatures = emptyMap(),
                fairwayFeatures = emptyMap()
            ) }
        }
    }

    fun toggleShowTurvalaiteviat() {
        val v = !fiState.showTurvalaiteviat
        updateFiState { copy(showTurvalaiteviat = v) }
        persistBoth("showTurvalaiteviat", v)
        if (v && hasBounds) {
            fetchTurvalaiteviat(lastBoundsMinLat, lastBoundsMinLng, lastBoundsMaxLat, lastBoundsMaxLng, lastBoundsZoom)
        } else if (!v) {
                updateFiState { copy(turvalaitevikaFeatures = emptyMap()) }
        }
    }

    fun selectSeamark(detail: SeamarkDetail?) {
        _uiState.update { it.copy(selectedSeamark = detail) }
    }

    fun clearSeamark() {
        _uiState.update { it.copy(selectedSeamark = null) }
    }

    fun clearAlgaeObservation() {
        _uiState.update { it.copy(selectedAlgaeObservations = emptyList()) }
    }

    fun refreshSeamarksStatus() {
        _uiState.update { it.copy(
            seamarksDownloaded = java.io.File(context.filesDir, "seamarks.pmtiles").exists(),
            downloadedSeamarkContinents = app.pursi.map.PmtilesDownloader.CONTINENTS
                .map { c -> c.id }
                .filter { java.io.File(context.filesDir, "seamarks_$it.pmtiles").exists() }
                .toSet()
        ) }
    }

    fun refreshVvDataStatus() {
        updateFiState { copy(
            vvDataDownloaded = java.io.File(context.filesDir, "vv_data.meta").exists(),
            turvalaiteFeatures = emptyMap(),
            valosektoriFeatures = emptyMap(),
            vesiliikennemerkkiFeatures = emptyMap(),
            navlineFeatures = emptyMap(),
            fairwayFeatures = emptyMap()
        ) }
    }

    fun setNavmarkSize(size: NavmarkSize) {
        _uiState.update { it.copy(navmarkSize = size) }
        savedStateHandle["navmarkSize"] = size.name
        prefs.edit().putString("navmark_size", size.name).apply()
    }

    fun setBoatIconSize(size: BoatIconSize) {
        _uiState.update { it.copy(boatIconSize = size) }
        savedStateHandle["boatIconSize"] = size.name
        prefs.edit().putString("boat_icon_size", size.name).apply()
    }

    fun setBoatIconColor(color: String) {
        _uiState.update { it.copy(boatIconColor = color) }
        savedStateHandle["boatIconColor"] = color
        prefs.edit().putString("boat_icon_color", color).apply()
    }

    fun setCurrentTrackId(id: String?) {
        _uiState.update { it.copy(currentTrackId = id) }
    }

    fun startRecording(intervalMs: Long = 2000L): String {
        return trackRecorder.startRecording(intervalMs = intervalMs)
    }

    fun stopRecording() {
        trackRecorder.stopRecording()
    }

    override fun onCleared() {
        super.onCleared()
        trackRecorder.stopRecording()
    }
}
