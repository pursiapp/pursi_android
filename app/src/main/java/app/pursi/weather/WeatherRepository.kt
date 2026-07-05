package app.pursi.weather

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import app.pursi.datasource.core.SourceResolver
import app.pursi.datasource.core.WeatherProvider
import app.pursi.location.LocationStateHolder
import app.pursi.map.NetworkMonitor
import app.pursi.datasource.core.RadarProvider
import app.pursi.location.SpeedCalculator
import app.pursi.weather.WaterLevelStation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.pursi.datasource.core.CompositeWeatherProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Central weather repository that manages fetching, caching, and periodic refresh
 * of all weather data (stations, waves, forecast, warnings, lightning).
 *
 * Architecture:
 * - Single source of truth for weather data across the app
 * - In-memory StateFlows for immediate UI consumption
 * - Persistent SharedPreferences cache for warnings (critical for safety)
 * - Location-aware: only refreshes if moved >10 km or cache expired
 * - Provider-agnostic: uses SourceResolver to pick correct WeatherProvider/WarningProvider
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceResolver: SourceResolver,
    private val locationStateHolder: LocationStateHolder,
    private val networkMonitor: NetworkMonitor
) {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_WARNINGS = "cached_warnings"
        private const val KEY_WARNING_TIME = "warning_fetch_time"
        private const val KEY_CORE_TIME = "core_fetch_time"
        private const val KEY_FORECAST_TIME = "forecast_fetch_time"
        private const val KEY_LIGHTNING_TIME = "lightning_fetch_time"
        private const val KEY_FORECAST_CACHE = "cached_forecast"
        private const val KEY_STATIONS_CACHE = "cached_stations"
        private const val KEY_WAVES_CACHE = "cached_waves"
        private const val KEY_WATER_CACHE = "cached_water"
        private const val CORE_REFRESH_MS = 600_000L // 10 min
        private const val FORECAST_REFRESH_MS = 3_600_000L // 60 min
        private const val LIGHTNING_SLOW_MS = 600_000L // 10 min
        private const val LIGHTNING_FAST_MS = 60_000L // 1 min
        private const val LIGHTNING_EMPTY_THRESHOLD = 30 // 30 empty checks before slow mode
        private const val MIN_MOVE_M = 10_000.0 // 10 km
    }

    enum class LightningMode { SLOW, FAST }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var coreRefreshJob: Job? = null
    private var forecastRefreshJob: Job? = null
    private var lightningRefreshJob: Job? = null

    // ── Public StateFlows ────────────────────────────────────────────────

    private val _stations = MutableStateFlow<List<StationWeatherData>>(emptyList())
    val stations: StateFlow<List<StationWeatherData>> = _stations.asStateFlow()

    private val _waveStations = MutableStateFlow<List<WaveStation>>(emptyList())
    val waveStations: StateFlow<List<WaveStation>> = _waveStations.asStateFlow()

    private val _forecast = MutableStateFlow<List<ForecastPoint>>(emptyList())
    val forecast: StateFlow<List<ForecastPoint>> = _forecast.asStateFlow()

    private val _warnings = MutableStateFlow<List<MarineWarning>>(emptyList())
    val warnings: StateFlow<List<MarineWarning>> = _warnings.asStateFlow()

    private val _lightning = MutableStateFlow<List<LightningStrike>>(emptyList())
    val lightning: StateFlow<List<LightningStrike>> = _lightning.asStateFlow()

    private val _waterLevel = MutableStateFlow<List<WaterLevelStation>>(emptyList())
    val waterLevel: StateFlow<List<WaterLevelStation>> = _waterLevel.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Atomic refresh guard (S4) — a force refresh can wait for a running core
    // refresh, instead of being silently dropped by the check-then-set race.
    private val refreshMutex = Mutex()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lightningMode = MutableStateFlow(LightningMode.SLOW)
    val lightningMode: StateFlow<LightningMode> = _lightningMode.asStateFlow()

    private val _activeProvider = MutableStateFlow<WeatherProvider?>(null)
    val activeProvider: StateFlow<WeatherProvider?> = _activeProvider.asStateFlow()

    // Cache coordinates
    @Volatile private var lastFetchLat = Double.NaN
    @Volatile private var lastFetchLon = Double.NaN

    // Suspend-on-background gate (S3.1). When `paused`, loops idle at 1s cadence
    // instead of running while the app is backgrounded — a brief background doesn't
    // cancel/restart them.
    @Volatile private var paused: Boolean = false

    // Network-restore collector (S3.2) — refreshed only on state changes.
    private var networkRestoreJob: Job? = null

    init {
        loadCachedWarnings()
        loadCachedForecast()
        loadCachedStations()
        loadCachedWaves()
        loadCachedWaterLevel()
    }

    // ── Auto Refresh (three separate loops) ──────────────────────────────

    fun startAutoRefresh() {
        stopAutoRefresh()

        coreRefreshJob = scope.launch {
            refresh()
            while (isActive) {
                pauseGate()
                delay(CORE_REFRESH_MS)
                refresh()
            }
        }

        forecastRefreshJob = scope.launch {
            // Initial fetch — wait briefly for location, then retry on 30s cadence if absent
            // (S3.3: don't blind-spot for 60 min on a slow GPS lock)
            while (isActive) {
                val loc = withTimeoutOrNull(10_000L) {
                    locationStateHolder.currentLocation.filterNotNull().firstOrNull()
                }
                if (loc != null) {
                    refreshForecastOnly(loc.latitude, loc.longitude)
                    break
                }
                pauseGate()
                delay(30_000L)
            }
            while (isActive) {
                pauseGate()
                delay(FORECAST_REFRESH_MS)
                val fLoc = withTimeoutOrNull(10_000L) {
                    locationStateHolder.currentLocation.filterNotNull().firstOrNull()
                } ?: continue
                refreshForecastOnly(fLoc.latitude, fLoc.longitude)
            }
        }

        startLightningSlowLoop()

        // Network-restore hook: when connectivity returns, refresh weather and clear
        // any cached radar state for the current region (S3.2).
        networkRestoreJob?.cancel()
        networkRestoreJob = scope.launch {
            networkMonitor.isOnline
                .filter { it }
                .collect {
                    refresh(force = true)
                    refreshRegionalRadarCaches()
                }
        }
    }

    fun stopAutoRefresh() {
        coreRefreshJob?.cancel(); coreRefreshJob = null
        forecastRefreshJob?.cancel(); forecastRefreshJob = null
        lightningRefreshJob?.cancel(); lightningRefreshJob = null
        networkRestoreJob?.cancel(); networkRestoreJob = null
    }

    /** Suspend-on-background gate; called from loops to idle while paused. */
    private suspend fun pauseGate() {
        while (paused && currentCoroutineContext()[Job]?.isActive != false) {
            delay(1_000L)
        }
    }

    /** Called by MainActivity.onStop — loops idle but stay running. */
    fun pause() { paused = true }

    /** Called by MainActivity.onStart — loops resume. */
    fun resume() { paused = false }

    private fun refreshRegionalRadarCaches() {
        if (lastFetchLat.isNaN() || lastFetchLon.isNaN()) return
        sourceResolver.radarProviders
            .filter { it.coverage.contains(lastFetchLat, lastFetchLon) }
            .forEach { (it as RadarProvider).refreshCache() }
    }

    fun destroy() {
        stopAutoRefresh()
        scope.cancel()
    }

    // ── Lightning adaptive refresh ───────────────────────────────────────

    private fun startLightningSlowLoop() {
        lightningRefreshJob?.cancel()
        _lightningMode.value = LightningMode.SLOW
        lightningRefreshJob = scope.launch {
            while (isActive) {
                pauseGate()
                val loc = withTimeoutOrNull(10_000L) {
                    locationStateHolder.currentLocation.filterNotNull().firstOrNull()
                }
                if (loc == null) { delay(LIGHTNING_SLOW_MS); continue }
                val strikes = fetchLightning(loc.latitude, loc.longitude)
                if (strikes.isNotEmpty()) {
                    _lightning.value = strikes
                    startLightningFastLoop()
                    return@launch
                }
                delay(LIGHTNING_SLOW_MS)
            }
        }
    }

    private fun startLightningFastLoop() {
        lightningRefreshJob?.cancel()
        _lightningMode.value = LightningMode.FAST
        lightningRefreshJob = scope.launch {
            var emptyCount = 0
            while (isActive) {
                pauseGate()
                delay(LIGHTNING_FAST_MS)
                val loc = withTimeoutOrNull(10_000L) {
                    locationStateHolder.currentLocation.filterNotNull().firstOrNull()
                } ?: continue
                val strikes = fetchLightning(loc.latitude, loc.longitude)
                if (strikes.isNotEmpty()) {
                    _lightning.value = strikes
                    emptyCount = 0
                } else {
                    emptyCount++
                    if (emptyCount >= LIGHTNING_EMPTY_THRESHOLD) {
                        _lightning.value = emptyList()
                        startLightningSlowLoop()
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun fetchLightning(lat: Double, lon: Double): List<LightningStrike> {
        val allProviders = sourceResolver.allWeatherProvidersFor(lat, lon)
        val primary = allProviders.firstOrNull() ?: return emptyList()
        // Use composite so a failing primary (e.g. FMI down) falls back to any
        // secondary that supports lightning (S4). Composite falls back on
        // WeatherProviderException (R2) AND on empty.
        val provider: WeatherProvider = if (allProviders.size > 1) {
            CompositeWeatherProvider(primary, allProviders.drop(1))
        } else {
            primary
        }
        return try {
            val strikes = provider.getLightningData(
                lat - 1.0, lon - 1.0, lat + 1.0, lon + 1.0
            )
            prefs.edit().putLong(KEY_LIGHTNING_TIME, System.currentTimeMillis()).apply()
            strikes
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: app.pursi.datasource.core.WeatherProviderException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Manual Refresh (core data only) ──────────────────────────────────

    suspend fun refresh(force: Boolean = false) {
        if (force) {
            // Force refresh blocks until it can run.
            refreshMutex.withLock { doRefresh(force = true) }
        } else {
            // Non-force returns immediately if another refresh is in progress.
            if (!refreshMutex.tryLock()) return
            try { doRefresh(force = false) } finally { refreshMutex.unlock() }
        }
    }

    private suspend fun doRefresh(force: Boolean) {
        _isRefreshing.value = true
        _error.value = null
        try {
            val location = withTimeoutOrNull(20_000L) {
                locationStateHolder.currentLocation.filterNotNull().firstOrNull()
            }
            if (location == null) return
            val lat = location.latitude; val lon = location.longitude; val now = System.currentTimeMillis()
            if (!force && !shouldRefreshCore(lat, lon, now)) return
            refreshCoreData(lat, lon, now)
            if (force || shouldRefreshForecast(now)) refreshForecastOnly(lat, lon)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            _error.value = e.message ?: "Weather data refresh failed"
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun refreshCoreData(lat: Double, lon: Double, now: Long) {
        val allProviders = sourceResolver.allWeatherProvidersFor(lat, lon)
        val primaryProvider = allProviders.firstOrNull()
        _activeProvider.value = primaryProvider
        val weatherProvider = if (primaryProvider != null && allProviders.size > 1) {
            CompositeWeatherProvider(primaryProvider, allProviders.drop(1))
        } else {
            primaryProvider
        }
        val warningProvider = sourceResolver.warningProviderFor(lat, lon)

        val locales = context.resources.configuration.locales
        val lang = if (locales.isEmpty()) "fi" else locales[0].language

        if (weatherProvider != null) {
            coroutineScope {
                val stationsDeferred = async { 
                    withTimeoutOrNull(15_000L) { weatherProvider.getNearestWeatherStations(lat, lon) }
                }
                val wavesDeferred = async { 
                    withTimeoutOrNull(15_000L) { weatherProvider.getWaveObservations(lat, lon) }
                }
                val waterDeferred = async {
                    withTimeoutOrNull(15_000L) { weatherProvider.getWaterLevelData(lat, lon) }
                }
                _stations.value = stationsDeferred.await()?.filter { it.station.hasAnyData } ?: emptyList()
                _waveStations.value = wavesDeferred.await() ?: emptyList()
                _waterLevel.value = waterDeferred.await() ?: emptyList()
            }
            persistList(KEY_STATIONS_CACHE, _stations.value)
            persistList(KEY_WAVES_CACHE, _waveStations.value)
            persistList(KEY_WATER_CACHE, _waterLevel.value)
            prefs.edit().putLong(KEY_CORE_TIME, now).apply()
        }

        if (warningProvider != null) {
            val fetchedWarnings = withTimeoutOrNull(15_000L) {
                warningProvider.getMarineWarnings(lang, lat, lon)
            } ?: emptyList()
            _warnings.value = fetchedWarnings
            // Always persist (including []) so loadCachedWarnings() on cold start
            // can't restore expired warnings when FMI has cleared them.
            persistWarnings(fetchedWarnings)
            prefs.edit().putLong(KEY_WARNING_TIME, now).apply()
        }

        lastFetchLat = lat
        lastFetchLon = lon
    }

    private suspend fun refreshForecastOnly(lat: Double, lon: Double) {
        val allProviders = sourceResolver.allWeatherProvidersFor(lat, lon)
        val primary = allProviders.firstOrNull() ?: return
        // Use composite so a failing primary falls back to a working secondary
        // (e.g. FMI down → MET Norway). Single-provider case is a no-op composite.
        val provider: WeatherProvider = if (allProviders.size > 1) {
            CompositeWeatherProvider(primary, allProviders.drop(1))
        } else {
            primary
        }
        try {
            _forecast.value = provider.getForecast(lat, lon)
            persistList(KEY_FORECAST_CACHE, _forecast.value)
            prefs.edit().putLong(KEY_FORECAST_TIME, System.currentTimeMillis()).apply()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: app.pursi.datasource.core.WeatherProviderException) {
            // Surface to UI; do NOT clear _forecast — keep last good list (S2 persistence
            // will repopulate it on cold start).
            _error.value = e.message ?: "Sääennuste ei juuri nyt saatavilla"
        } catch (_: Exception) { }
    }

    // ── Cache Logic ──────────────────────────────────────────────────────

    private fun shouldRefreshCore(lat: Double, lon: Double, now: Long): Boolean {
        if (lastFetchLat.isNaN() || lastFetchLat.isInfinite()) return true
        val dist = SpeedCalculator.distanceBetween(lastFetchLat, lastFetchLon, lat, lon)
        val timeSince = now - prefs.getLong(KEY_CORE_TIME, 0L)
        return dist > MIN_MOVE_M || timeSince > CORE_REFRESH_MS
    }

    private fun shouldRefreshForecast(now: Long): Boolean {
        val timeSince = now - prefs.getLong(KEY_FORECAST_TIME, 0L)
        return timeSince > FORECAST_REFRESH_MS
    }

    // ── Persistent Warning Cache ─────────────────────────────────────────

    private fun persistWarnings(warnings: List<MarineWarning>) {
        try {
            val serialized = json.encodeToString(warnings)
            prefs.edit().putString(KEY_WARNINGS, serialized).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Warning persistence failed", e)
        }
    }

    private fun loadCachedWarnings() {
        try {
            val serialized = prefs.getString(KEY_WARNINGS, null) ?: return
            val cached: List<MarineWarning> = json.decodeFromString(serialized)
            _warnings.value = cached
        } catch (_: Exception) { }
    }

    private inline fun <reified T> loadCachedList(key: String, assign: (List<T>) -> Unit) {
        try {
            val serialized = prefs.getString(key, null) ?: return
            val cached: List<T> = json.decodeFromString(serialized)
            assign(cached)
        } catch (_: Exception) { }
    }

    private inline fun <reified T> persistList(key: String, value: List<T>) {
        try {
            val serialized = json.encodeToString(value)
            prefs.edit().putString(key, serialized).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Cache write failed for $key", e)
        }
    }

    private fun loadCachedForecast() =
        loadCachedList<ForecastPoint>(KEY_FORECAST_CACHE) { _forecast.value = it }

    private fun loadCachedStations() =
        loadCachedList<StationWeatherData>(KEY_STATIONS_CACHE) { _stations.value = it }

    private fun loadCachedWaves() =
        loadCachedList<WaveStation>(KEY_WAVES_CACHE) { _waveStations.value = it }

    private fun loadCachedWaterLevel() =
        loadCachedList<WaterLevelStation>(KEY_WATER_CACHE) { _waterLevel.value = it }

    // ── Warning Helpers for Map & Safety ─────────────────────────────────

    /** Any active wind warning (seaWind) */
    fun hasWindWarning(): Boolean = _warnings.value.any { it.eventCode == "seaWind" }

    /** Severe wind / storm warning: orange or red seaWind */
    fun hasStormWarning(): Boolean = _warnings.value.any {
        it.eventCode == "seaWind" && (it.color == "orange" || it.color == "red")
    }

    /** Thunderstorm: either lightning detected or explicit thunderstorm warning */
    fun hasThunderstormWarning(): Boolean {
        val hasLightning = _lightning.value.isNotEmpty()
        val hasWarning = _warnings.value.any {
            it.eventCode.contains("thunder", ignoreCase = true) ||
            it.eventCode.contains("lightning", ignoreCase = true)
        }
        return hasLightning || hasWarning
    }

    /** Highest severity level among current warnings (for map badge color) */
    fun highestWarningSeverity(): WarningSeverity {
        val colors = _warnings.value.map { it.color }
        return when {
            colors.contains("red") -> WarningSeverity.VERY_SEVERE
            colors.contains("orange") -> WarningSeverity.SEVERE
            colors.contains("yellow") -> WarningSeverity.MODERATE
            else -> WarningSeverity.MINOR
        }
    }

    /** Returns a short summary string for the map indicator tooltip */
    fun activeWarningSummary(): String = buildString {
        val w = _warnings.value
        if (w.isEmpty()) return@buildString
        val byType = w.groupBy { it.eventCode }
        byType.entries.forEach { (code, list) ->
            if (isNotEmpty()) append(", ")
            append("${list.size} $code")
        }
    }
}
