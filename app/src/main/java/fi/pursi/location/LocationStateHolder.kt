package fi.pursi.location

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
        val filtered = positionSmoother.filter(location)
        if (filtered != null) {
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
    }

    fun forceUpdateLocation() {
        val raw = lastRawLocation ?: return
        positionSmoother.reset()
        val accepted = positionSmoother.filter(raw) ?: return
        lastRealLocation = accepted
        _currentLocation.value = accepted
        startInterpolation()
    }

    fun clear() {
        stopInterpolation()
        positionSmoother.reset()
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
                if (!real.hasSpeed() || real.speed < MIN_SPEED_MPS) continue
                val distanceM = real.speed * elapsed

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
        private const val MAX_DRIFT_M = 500.0
        private const val MIN_SPEED_MPS = 0.2f
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
