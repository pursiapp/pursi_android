package app.pursi.datasource.core

sealed class Region {
    data class Rectangular(val bbox: BoundingBox) : Region()
    data class Polygonal(val vertices: List<Pair<Double, Double>>) : Region()

    fun contains(lat: Double, lon: Double): Boolean = when (this) {
        is Rectangular -> bbox.contains(lat, lon)
        is Polygonal -> pointInPolygon(lat, lon, vertices)
    }

    companion object {
        fun pointInPolygon(
            lat: Double,
            lon: Double,
            polygon: List<Pair<Double, Double>>
        ): Boolean {
            var inside = false
            var j = polygon.size - 1
            for (i in polygon.indices) {
                val (latI, lonI) = polygon[i]
                val (latJ, lonJ) = polygon[j]
                if ((latI > lat) != (latJ > lat) &&
                    lon < lonI + (lat - latI) * (lonJ - lonI) / (latJ - latI)
                ) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }
}
