package app.pursi.location

import android.location.Location
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationStateHolder @Inject constructor() {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val positionSmoother = PositionSmoother()
    private var lastRawLocation: Location? = null
    private var lastRealLocation: Location? = null
    private var prevRealLocation: Location? = null
    private var interpolationBearing: Double = 0.0
    private var interpolationJob: Job? = null

    // Kerros 4: peräkkäiset paikallaanolofixit
    private var consecutiveStoppedCount = 0

    // Kerros 2: raa'at positiot nopeusvalidointiin
    private var prevRawLat: Double? = null
    private var prevRawLon: Double? = null
    private var prevRawTimeMs: Long = 0L
    private var positionSpeedMps: Double = 0.0

    private var mockLocation: Location? = null
    private val _isMocking = MutableStateFlow(false)
    val isMocking: StateFlow<Boolean> = _isMocking.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun setMockLocation(lat: Double, lon: Double) {
        if (mockLocation != null) return
        stopInterpolation()
        val loc = Location("mock").apply {
            latitude = lat
            longitude = lon
            time = System.currentTimeMillis()
            accuracy = 0f
        }
        mockLocation = loc
        lastRealLocation = loc
        _isMocking.value = true
        _currentLocation.value = loc
    }

    fun clearMockLocation() {
        if (mockLocation == null) return
        mockLocation = null
        _isMocking.value = false
        stopInterpolation()
        forceUpdateLocation()
    }

    fun updateLocation(location: Location) {
        if (mockLocation != null) return
        lastRawLocation = location
        val filtered = positionSmoother.filter(location) ?: return

        // Kerros 2: laske raaka positiojohdannainen nopeus
        if (prevRawLat != null && prevRawLon != null) {
            val dt = ((location.time - prevRawTimeMs) / 1000.0).coerceAtLeast(0.5)
            val dist = SpeedCalculator.distanceBetween(
                prevRawLat!!, prevRawLon!!,
                location.latitude, location.longitude
            )
            positionSpeedMps = dist / dt
        }
        prevRawLat = location.latitude
        prevRawLon = location.longitude
        prevRawTimeMs = location.time

        // Kerros 4: tunnista pitkittynyt paikallaanolo
        val speed = if (filtered.hasSpeed()) filtered.speed else 0f
        if (speed < STATIONARY_SPEED_MPS) {
            consecutiveStoppedCount++
            if (consecutiveStoppedCount >= STATIONARY_FIXES_REQUIRED) {
                positionSmoother.reset()
                consecutiveStoppedCount = 0
            }
        } else {
            consecutiveStoppedCount = 0
        }

        if (lastRealLocation != null) {
            prevRealLocation = lastRealLocation
            interpolationBearing = SpeedCalculator.bearingBetween(
                prevRealLocation!!.latitude, prevRealLocation!!.longitude,
                filtered.latitude, filtered.longitude
            ).toDouble()
        }
        lastRealLocation = filtered
        _currentLocation.value = filtered
        startInterpolation()
    }

    fun forceUpdateLocation() {
        val raw = lastRawLocation ?: return
        positionSmoother.reset()
        consecutiveStoppedCount = 0
        positionSpeedMps = 0.0
        val accepted = positionSmoother.filter(raw) ?: return
        lastRealLocation = accepted
        _currentLocation.value = accepted
        startInterpolation()
    }

    fun clear() {
        stopInterpolation()
        positionSmoother.reset()
        consecutiveStoppedCount = 0
        prevRawLat = null
        prevRawLon = null
        prevRawTimeMs = 0L
        positionSpeedMps = 0.0
        mockLocation = null
        _isMocking.value = false
        _currentLocation.value = null
        lastRawLocation = null
        lastRealLocation = null
        prevRealLocation = null
        interpolationBearing = 0.0
    }

    private fun startInterpolation() {
        if (interpolationJob?.isActive == true) return
        interpolationJob = scope.launch {
            while (isActive) {
                delay(100L)
                val real = lastRealLocation ?: continue
                val elapsed = (System.currentTimeMillis() - real.time) / 1000.0
                if (elapsed <= 0.0) continue

                // Kerros 1: aikaraja — 4 s ilman uutta fixiä = pysäytä
                if (elapsed > MAX_DRIFT_TIME_S) {
                    stopInterpolation()
                    return@launch
                }

                if (!real.hasSpeed() || real.speed < MIN_SPEED_MPS) continue

                // Kerros 2: sido nopeus raakaan positiodeltaan
                val gpsSpeed = real.speed.toDouble()
                val maxSpeed = positionSpeedMps * POSITION_SPEED_FACTOR + POSITION_NOISE_FLOOR_MPS
                val effectiveSpeed = minOf(gpsSpeed, maxSpeed)
                if (effectiveSpeed < MIN_SPEED_MPS) continue

                val distanceM = effectiveSpeed * elapsed

                // Kerros 1: matkaraja (tiukennettu)
                if (distanceM > MAX_DRIFT_M) {
                    stopInterpolation()
                    return@launch
                }

                val result = deadReckon(
                    real.latitude, real.longitude, distanceM, interpolationBearing
                )
                _currentLocation.value = Location(real).apply {
                    latitude = result.first
                    longitude = result.second
                    time = System.currentTimeMillis()
                }
            }
        }
    }

    private fun stopInterpolation() {
        interpolationJob?.cancel()
        interpolationJob = null
    }

    companion object {
        private const val MAX_DRIFT_M = 200.0              // Kerros 1: matkaraja (oli 500)
        private const val MAX_DRIFT_TIME_S = 4.0            // Kerros 1: aikaraja
        private const val MIN_SPEED_MPS = 0.2f
        private const val STATIONARY_SPEED_MPS = 0.1f       // Kerros 4
        private const val STATIONARY_FIXES_REQUIRED = 3     // Kerros 4
        private const val POSITION_SPEED_FACTOR = 2.0       // Kerros 2: marginaali kohinalle
        private const val POSITION_NOISE_FLOOR_MPS = 1.5    // Kerros 2: lattiataso
        private const val EARTH_RADIUS_M = 6371000.0

        private fun deadReckon(
            lat: Double, lon: Double,
            distanceM: Double, bearingDeg: Double
        ): Pair<Double, Double> {
            val bearingRad = Math.toRadians(bearingDeg)
            val angDist = distanceM / EARTH_RADIUS_M
            val lat1 = Math.toRadians(lat)
            val lon1 = Math.toRadians(lon)

            val lat2 = Math.asin(
                Math.sin(lat1) * Math.cos(angDist) +
                Math.cos(lat1) * Math.sin(angDist) * Math.cos(bearingRad)
            )
            val lon2 = lon1 + Math.atan2(
                Math.sin(bearingRad) * Math.sin(angDist) * Math.cos(lat1),
                Math.cos(angDist) - Math.sin(lat1) * Math.sin(lat2)
            )
            return Pair(Math.toDegrees(lat2), Math.toDegrees(lon2))
        }
    }
}
