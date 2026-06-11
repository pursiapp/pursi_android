package app.pursi.location

import android.location.Location

class PositionSmoother(
    private val minAccuracyM: Float = 50f,
    private val alpha: Float = 0.25f,
    private val jumpDistanceM: Double = 200.0,
    private val minSpeedForJumpCheckMps: Float = 10f
) {
    private var previousLat: Double? = null
    private var previousLon: Double? = null
    private var smoothedLat: Double? = null
    private var smoothedLon: Double? = null
    private var previousTimeMs: Long = 0L
    private var acceptedCount: Int = 0

    fun filter(location: Location): Location? {
        if (!location.hasAccuracy() || location.accuracy > minAccuracyM) {
            return null
        }

        val prevLat = smoothedLat ?: previousLat
        val prevLon = smoothedLon ?: previousLon

        if (prevLat == null || prevLon == null) {
            previousLat = location.latitude
            previousLon = location.longitude
            previousTimeMs = location.time
            smoothedLat = location.latitude
            smoothedLon = location.longitude
            acceptedCount = 1
            return location
        }

        val distanceM = SpeedCalculator.distanceBetween(
            prevLat, prevLon,
            location.latitude, location.longitude
        )

        val timeDeltaS = ((location.time - previousTimeMs) / 1000.0).coerceAtLeast(0.5)

        if (acceptedCount >= 3 && distanceM > jumpDistanceM) {
            val impliedSpeedMps = distanceM / timeDeltaS
            if (impliedSpeedMps > minSpeedForJumpCheckMps) {
                return null
            }
        }

        previousLat = location.latitude
        previousLon = location.longitude
        previousTimeMs = location.time

        val effectiveAlpha = (1.0 - Math.exp(-timeDeltaS / TAU)).toFloat().coerceIn(0.1f, 1.0f)
        val newSmoothedLat = effectiveAlpha * location.latitude + (1f - effectiveAlpha) * prevLat
        val newSmoothedLon = effectiveAlpha * location.longitude + (1f - effectiveAlpha) * prevLon

        smoothedLat = newSmoothedLat
        smoothedLon = newSmoothedLon
        acceptedCount++

        return Location(location).apply {
            latitude = newSmoothedLat
            longitude = newSmoothedLon
        }
    }

    companion object {
        private const val TAU = 3.0 // seconds — EMA time constant
    }

    fun reset() {
        previousLat = null
        previousLon = null
        smoothedLat = null
        smoothedLon = null
        previousTimeMs = 0L
        acceptedCount = 0
    }
}
