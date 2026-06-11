package app.pursi.location

enum class SpeedUnit(val label: String, val shortLabel: String) {
    KNOTS("knots", "kn"),
    KMH("km/h", "km/h"),
    MPH("mph", "mph")
}

object SpeedCalculator {

    private const val KNOTS_FACTOR = 1.94384
    private const val KMH_FACTOR = 3.6
    private const val MPH_FACTOR = 2.23694

    private val earthRadius = 6371000.0

    fun metersPerSecondToKnots(mps: Float): Float = mps * KNOTS_FACTOR.toFloat()

    fun metersPerSecondToKmh(mps: Float): Float = mps * KMH_FACTOR.toFloat()

    fun metersPerSecondToMph(mps: Float): Float = mps * MPH_FACTOR.toFloat()

    fun convert(speedMps: Float, unit: SpeedUnit): Float {
        return when (unit) {
            SpeedUnit.KNOTS -> metersPerSecondToKnots(speedMps)
            SpeedUnit.KMH -> metersPerSecondToKmh(speedMps)
            SpeedUnit.MPH -> metersPerSecondToMph(speedMps)
        }
    }

    fun formatSpeed(speedMps: Float, unit: SpeedUnit, decimals: Int = 1): String {
        val converted = convert(speedMps, unit)
        return "%.${decimals}f %s".format(converted, unit.shortLabel)
    }

    fun distanceNm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
        distanceBetween(lat1, lng1, lat2, lng2) / 1852.0

    fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).pow(2) +
                Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2).pow(2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    fun bearingBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val dLng = Math.toRadians(lng2 - lng1)
        val y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2))
        val x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng)
        val bearing = Math.toDegrees(Math.atan2(y, x))
        return ((bearing + 360.0) % 360.0).toFloat()
    }

    private fun Double.pow(exp: Int): Double {
        var result = 1.0
        repeat(exp) { result *= this }
        return result
    }
}
