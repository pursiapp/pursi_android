package app.pursi.ais

import android.util.Log
import app.pursi.datasource.core.AisProvider
import app.pursi.datasource.core.SourceResolver
import app.pursi.location.LocationStateHolder
import app.pursi.location.SpeedCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AisRepository @Inject constructor(
    private val sourceResolver: SourceResolver,
    private val locationStateHolder: LocationStateHolder
) {
    companion object {
        private const val TAG = "AisRepository"
        private const val MIN_RADIUS_KM = 50
        private const val MAX_RADIUS_KM = 300
        private const val MOVE_THRESHOLD_KM = 25
        private const val DEFAULT_REFRESH_MS = 60_000L
        private const val DEBOUNCE_MS = 500L
        private const val MAX_AGE_MS = 30 * 60 * 1000L // 30 min
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoRefreshJob: Job? = null
    private var pendingRefresh: Job? = null

    private val _vessels = MutableStateFlow<List<AisVessel>>(emptyList())
    val vessels: StateFlow<List<AisVessel>> = _vessels.asStateFlow()

    private val _metadata = MutableStateFlow<Map<Int, VesselMetadata>>(emptyMap())
    val metadata: StateFlow<Map<Int, VesselMetadata>> = _metadata.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Camera/query center (updated on every camera move) */
    private var _queryLat = 60.0
    private var _queryLon = 23.0
    /** Query radius in km, computed from viewport */
    private var _queryRadiusKm = MIN_RADIUS_KM
    /** Last fetch center */
    private var _lastFetchLat = Double.NaN
    private var _lastFetchLon = Double.NaN
    private var _lastFetchRadius = MIN_RADIUS_KM

    private val _rateLimited = MutableStateFlow<String?>(null)
    val rateLimited: StateFlow<String?> = _rateLimited.asStateFlow()

    /** Only re-fetch when AIS is enabled */
    private var _enabled = false

    /** Cached AIS provider to avoid re-resolving on every fetch */
    private var activeProvider: AisProvider? = null

    /** Notify whether AIS toggle is on/off */
    fun setEnabled(on: Boolean) {
        _enabled = on
        if (!on) pendingRefresh?.cancel()
    }

    fun setQueryCenter(lat: Double, lon: Double) {
        _queryLat = lat
        _queryLon = lon
        activeProvider = sourceResolver.aisProviderFor(lat, lon)
        maybeRefresh()
    }

    fun setQueryBounds(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double) {
        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLng + maxLng) / 2.0
        _queryLat = centerLat
        _queryLon = centerLon
        val diagonalKm = SpeedCalculator.distanceBetween(minLat, minLng, maxLat, maxLng) / 1000.0
        _queryRadiusKm = (diagonalKm / 2.0).toInt().coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
        activeProvider = sourceResolver.aisProviderFor(centerLat, centerLon)
        maybeRefresh()
    }

    fun startAutoRefresh(intervalMs: Long = DEFAULT_REFRESH_MS) {
        stopAutoRefresh()
        autoRefreshJob = scope.launch {
            refresh()
            while (isActive) {
                delay(intervalMs)
                refresh()
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun destroy() {
        stopAutoRefresh()
        scope.cancel()
    }

    private fun maybeRefresh() {
        if (!_enabled) return
        if (_lastFetchLat.isNaN()) {
            pendingRefresh?.cancel()
            pendingRefresh = scope.launch {
                delay(DEBOUNCE_MS)
                refresh(force = true)
            }
            return
        }
        val dist = SpeedCalculator.distanceBetween(
            _lastFetchLat, _lastFetchLon, _queryLat, _queryLon
        )
        val radiusDelta = kotlin.math.abs(_lastFetchRadius - _queryRadiusKm)
        if (dist < MOVE_THRESHOLD_KM * 1000.0 && radiusDelta < 20) return

        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(DEBOUNCE_MS)
            refresh(force = true)
        }
    }

    fun triggerRefresh() {
        if (!_enabled) return
        pendingRefresh?.cancel()
        scope.launch { refresh(force = true) }
    }

    suspend fun refresh(force: Boolean = false) {
        if (!force && _isRefreshing.value) return
        if (force) _isRefreshing.value = false

        var lat = _queryLat
        var lon = _queryLon
        if (lat.isNaN()) {
            val location = withTimeoutOrNull(10_000L) {
                locationStateHolder.currentLocation.filterNotNull().firstOrNull()
            }
            if (location == null) { Log.w(TAG, "No location, skip"); return }
            lat = location.latitude; lon = location.longitude
            activeProvider = sourceResolver.aisProviderFor(lat, lon)
        }

        _isRefreshing.value = true
        try {
            val provider = activeProvider
                ?: sourceResolver.aisProviderFor(lat, lon)
            if (provider == null) {
                Log.d(TAG, "No AIS provider for location")
                _vessels.value = emptyList()
                _rateLimited.value = null
                return
            }
            activeProvider = provider

            Log.d(TAG, "Fetch AIS %.2f,%.2f r=%dkm via %s".format(lat, lon, _queryRadiusKm, provider.providerId))
            val vessels = provider.getVesselsNearby(lat, lon, _queryRadiusKm)
            _rateLimited.value = provider.rateLimited.value
            if (provider.rateLimited.value == null) {
                _lastFetchLat = lat; _lastFetchLon = lon; _lastFetchRadius = _queryRadiusKm
                Log.d(TAG, "Got ${vessels.size} vessels from ${provider.providerId}")
            }
            // Remove stale data (older than 30 min)
            val now = System.currentTimeMillis()
            val fresh = vessels.filter { now - it.timestampExternal < MAX_AGE_MS }
            val staleCount = vessels.size - fresh.size
            if (staleCount > 0) Log.d(TAG, "Filtered out $staleCount stale vessels")
            _vessels.value = fresh

            if (fresh.isNotEmpty() && _rateLimited.value == null) {
                prefetchMetadata(fresh)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Log.e(TAG, "AIS fetch failed", e) }
        finally { _isRefreshing.value = false }
    }

    private fun prefetchMetadata(vessels: List<AisVessel>) {
        val withoutName = vessels.filter { it.name == null }
        if (withoutName.isEmpty()) return
        Log.d(TAG, "Prefetching metadata for ${withoutName.size} vessels")
        scope.launch {
            withoutName.chunked(5).forEach { batch ->
                coroutineScope {
                    batch.map { v -> async { fetchMetadata(v.mmsi) } }.forEach { it.await() }
                }
                delay(100)
            }
        }
    }

    /** Fetch metadata for ONE vessel (on tap) */
    suspend fun fetchMetadata(mmsi: Int): VesselMetadata? {
        val existing = _metadata.value[mmsi]
        if (existing != null) return existing
        val provider = activeProvider
            ?: sourceResolver.aisProviderFor(_queryLat, _queryLon)
            ?: return null
        val meta = provider.fetchVesselMetadata(mmsi)
        if (meta != null) {
            _metadata.value = _metadata.value + (mmsi to meta)
            _vessels.value = _vessels.value.map { v ->
                if (v.mmsi == mmsi) v.copy(shipType = meta.shipType, name = meta.name, destination = meta.destination, draught = meta.draught)
                else v
            }
        }
        return meta
    }
}
