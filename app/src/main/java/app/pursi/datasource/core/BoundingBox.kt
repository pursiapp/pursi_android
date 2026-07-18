package app.pursi.datasource.core

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLng..maxLng

    fun intersects(other: BoundingBox): Boolean =
        minLat <= other.maxLat && maxLat >= other.minLat &&
        minLng <= other.maxLng && maxLng >= other.minLng

    fun expanded(marginFactor: Double): BoundingBox {
        val latSpan = maxLat - minLat
        val lngSpan = maxLng - minLng
        val dLat = latSpan * marginFactor
        val dLng = lngSpan * marginFactor
        return copy(
            minLat = minLat - dLat,
            maxLat = maxLat + dLat,
            minLng = minLng - dLng,
            maxLng = maxLng + dLng
        )
    }

    companion object {
        val WORLD = BoundingBox(-90.0, 90.0, -180.0, 180.0)
    }
}
