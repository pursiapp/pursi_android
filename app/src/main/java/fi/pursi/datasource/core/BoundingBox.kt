package fi.pursi.datasource.core

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLng..maxLng

    companion object {
        val WORLD = BoundingBox(-90.0, 90.0, -180.0, 180.0)
    }
}
