package fi.pursi.map

import kotlin.math.floor

class RectangleTileCalculator {

    data class Estimate(val tileCount: Int, val sizeBytes: Long)

    fun calculateTiles(
        rectangles: List<LatLngRect>,
        minZoom: Int,
        maxZoom: Int
    ): Set<TileCoordinate> {
        val tiles = mutableSetOf<TileCoordinate>()
        for (zoom in minZoom..maxZoom) {
            for (rect in rectangles) {
                val x1 = lngToTileX(rect.minLng, zoom)
                val x2 = lngToTileX(rect.maxLng, zoom)
                val y1 = latToTileY(rect.maxLat, zoom)
                val y2 = latToTileY(rect.minLat, zoom)
                for (x in x1..x2) {
                    for (y in y1..y2) {
                        tiles.add(TileCoordinate(zoom, x, y))
                    }
                }
            }
        }
        return tiles
    }

    fun estimate(
        rectangles: List<LatLngRect>,
        minZoom: Int,
        maxZoom: Int,
        avgTileBytes: Long = 25_000L
    ): Estimate {
        val count = calculateTiles(rectangles, minZoom, maxZoom).size
        return Estimate(tileCount = count, sizeBytes = count * avgTileBytes)
    }

    fun lngToTileX(lon: Double, zoom: Int): Int {
        return floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val rad = Math.toRadians(lat)
        return floor((1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
    }
}
