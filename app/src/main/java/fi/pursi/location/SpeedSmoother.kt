package fi.pursi.location

class SpeedSmoother(
    private val alphaAccel: Float = 0.30f,
    private val alphaDecel: Float = 0.20f,
    private val zeroClampMps: Float = 0.07f,
    private val rawZeroThresholdMps: Float = 0.1f
) {
    private var previousMps = 0.0f
    private var initialized = false

    fun update(rawMps: Float): Float {
        if (!initialized || previousMps == 0.0f) {
            previousMps = rawMps
            initialized = true
            return rawMps
        }
        val alpha = if (rawMps > previousMps) alphaAccel else alphaDecel
        var smoothed = alpha * rawMps + (1f - alpha) * previousMps
        if (rawMps < rawZeroThresholdMps && smoothed < zeroClampMps) {
            smoothed = 0.0f
        }
        previousMps = smoothed
        return smoothed
    }

    fun reset() {
        initialized = false
        previousMps = 0.0f
    }
}
