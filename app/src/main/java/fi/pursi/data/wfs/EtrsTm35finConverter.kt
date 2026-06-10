package fi.pursi.data.wfs

object EtrsTm35finConverter {
    private const val A = 6378137.0
    private const val F = 1.0 / 298.257222101
    private const val E2 = 2.0 * F - F * F
    private const val K0 = 0.9996
    private const val CENTRAL_MERIDIAN = 27.0
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING = 0.0

    fun latLngToEtrsTm35fin(lat: Double, lng: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)
        val centralLngRad = Math.toRadians(CENTRAL_MERIDIAN)
        val dLng = lngRad - centralLngRad

        val n = F / (2.0 - F)

        val m = A * ((1.0 - n + 5.0 * (n * n - n * n * n) / 4.0 + 81.0 * (n * n * n * n - n * n * n * n * n) / 64.0) * latRad
                - (3.0 * n / 2.0 - 3.0 * n * n * n / 16.0) * Math.sin(2.0 * latRad)
                + (15.0 * n * n / 16.0 - 15.0 * n * n * n * n / 32.0) * Math.sin(4.0 * latRad)
                - 35.0 * n * n * n / 48.0 * Math.sin(6.0 * latRad)
                + 315.0 * n * n * n * n / 512.0 * Math.sin(8.0 * latRad))

        val sinLat = Math.sin(latRad)
        val cosLat = Math.cos(latRad)
        val t = sinLat / cosLat
        val eta2 = E2 * cosLat * cosLat / (1.0 - E2)

        val c1 = K0 * m
        val c2 = K0 * A * Math.pow(cosLat, 2.0) / 2.0
        val c3 = K0 * A * Math.pow(cosLat, 4.0) / 24.0 * (5.0 - t * t + 9.0 * eta2 + 4.0 * eta2 * eta2)
        val c4 = K0 * A * Math.pow(cosLat, 6.0) / 720.0 * (61.0 - 58.0 * t * t + t * t * t * t + 270.0 * eta2 - 330.0 * t * t * eta2)
        val c5 = K0 * A * Math.pow(cosLat, 2.0) / 6.0 * (1.0 - t * t + eta2)
        val c6 = K0 * A * Math.pow(cosLat, 4.0) / 120.0 * (5.0 - 18.0 * t * t + t * t * t * t + 14.0 * eta2 - 58.0 * t * t * eta2)
        val c7 = K0 * A * Math.pow(cosLat, 6.0) / 5040.0 * (61.0 - 479.0 * t * t + 179.0 * t * t * t * t - t * t * t * t * t * t)

        val easting = FALSE_EASTING + K0 * A * dLng * cosLat * (
            1.0 + dLng * dLng * cosLat * cosLat / 6.0 * (1.0 - t * t + eta2)
            + Math.pow(dLng, 4.0) * Math.pow(cosLat, 4.0) / 120.0 * (5.0 - 18.0 * t * t + t * t * t * t + 14.0 * eta2 - 58.0 * t * t * eta2)
        )

        val northing = FALSE_NORTHING + c1 + c2 * dLng * dLng + c3 * Math.pow(dLng, 4.0) + c4 * Math.pow(dLng, 6.0)

        return Pair(easting, northing)
    }

    fun bboxToEtrsTm35fin(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val sw = latLngToEtrsTm35fin(minLat, minLng)
        val ne = latLngToEtrsTm35fin(maxLat, maxLng)
        return Pair(
            Pair(minOf(sw.first, ne.first), minOf(sw.second, ne.second)),
            Pair(maxOf(sw.first, ne.first), maxOf(sw.second, ne.second))
        )
    }
}
