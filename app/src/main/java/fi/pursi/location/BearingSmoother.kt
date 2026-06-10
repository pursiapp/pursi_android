package fi.pursi.location

class BearingSmoother(
    private val alpha: Float = 0.20f,
    private val minSpeedMps: Float = 0.3f
) {
    private var previousBearingDeg: Float? = null

    fun update(rawBearingDeg: Float, speedMps: Float): Float {
        if (speedMps < minSpeedMps) {
            return previousBearingDeg ?: rawBearingDeg
        }

        val prev = previousBearingDeg
        if (prev == null) {
            previousBearingDeg = rawBearingDeg
            return rawBearingDeg
        }

        var diff = rawBearingDeg - prev
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f

        val smoothed = prev + alpha * diff
        val result = ((smoothed % 360f) + 360f) % 360f

        previousBearingDeg = result
        return result
    }

    fun reset() {
        previousBearingDeg = null
    }
}
