package app.pursi.datasource.fi

import android.util.Log
import app.pursi.data.dao.WfsFeatureDao
import app.pursi.data.model.WfsFeature
import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.DepthProvider
import app.pursi.datasource.core.DepthSample
import javax.inject.Inject

class TraficomDepthProvider @Inject constructor(
    private val dao: WfsFeatureDao
) : DepthProvider {

    override val providerId = "fi-traficom-depth"
    override val displayName = "Traficom syvyysdata"
    override val coverage = BoundingBox(58.5, 70.5, 19.0, 32.0)
    override val priority = 2

    private val tag = "Pursi.TraficomDepth"

    override suspend fun getDepthAt(lat: Double, lon: Double): DepthSample? {
        val searchBox = 0.02

        val areas = dao.getFeatures("depth_area", lat - searchBox, lon - searchBox, lat + searchBox, lon + searchBox)
        for (area in areas) {
            val depth = depthFromArea(area, lat, lon)
            if (depth != null) return depth
        }

        val soundings = dao.getFeatures("depth_sounding", lat - searchBox, lon - searchBox, lat + searchBox, lon + searchBox)
        if (soundings.isNotEmpty()) {
            val interpolated = interpolateDepth(lat, lon, soundings)
            if (interpolated != null) return interpolated
        }

        val contours = dao.getFeatures("depth_contour", lat - searchBox, lon - searchBox, lat + searchBox, lon + searchBox)
        if (contours.isNotEmpty()) {
            val nearest = nearestContourDepth(lat, lon, contours)
            if (nearest != null) return nearest
        }

        val unsurveyed = dao.getFeatures("unsurveyed_area", lat - searchBox, lon - searchBox, lat + searchBox, lon + searchBox)
        for (area in unsurveyed) {
            if (pointInPolygon(lat, lon, parsePolygonPoints(area.geometry))) {
                return null
            }
        }

        return null
    }

    private fun depthFromArea(feature: WfsFeature, lat: Double, lon: Double): DepthSample? {
        val polygonPoints = parsePolygonPoints(feature.geometry) ?: return null
        if (!pointInPolygon(lat, lon, polygonPoints)) return null

        val drval1 = extractDouble(feature.properties, "DRVAL1")
        val drval2 = extractDouble(feature.properties, "DRVAL2")
        if (drval1 != null && drval2 != null) {
            return DepthSample(
                latitude = lat, longitude = lon,
                meanDepthM = (drval1 + drval2) / 2f,
                minDepthM = drval1.coerceAtMost(drval2),
                maxDepthM = drval1.coerceAtLeast(drval2),
                source = "$providerId/area"
            )
        }
        return null
    }

    private fun interpolateDepth(lat: Double, lon: Double, soundings: List<WfsFeature>): DepthSample? {
        val points = soundings.mapNotNull { feat ->
            val depth = extractDouble(feat.properties, "DEPTH") ?: return@mapNotNull null
            DepthPoint(feat.latitude, feat.longitude, depth)
        }
        if (points.isEmpty()) return null

        val sorted = points.sortedBy { haversineDeg(lat, lon, it.lat, it.lon) }
        val nearest = sorted.take(3)
        if (nearest.isEmpty()) return null

        val totalWeight = nearest.sumOf { 1.0 / (it.distSq(lat, lon).coerceAtLeast(1e-10)) }
        val weightedDepth = nearest.sumOf {
            (1.0 / it.distSq(lat, lon).coerceAtLeast(1e-10)) * it.depth.toDouble()
        }.toFloat() / totalWeight.toFloat()

        val minDepth = nearest.minOf { it.depth }
        val maxDepth = nearest.maxOf { it.depth }

        return DepthSample(
            latitude = lat, longitude = lon,
            meanDepthM = weightedDepth,
            minDepthM = minDepth,
            maxDepthM = maxDepth,
            source = "$providerId/sounding"
        )
    }

    private fun nearestContourDepth(lat: Double, lon: Double, contours: List<WfsFeature>): DepthSample? {
        val contourPoints = contours.mapNotNull { feat ->
            val depth = extractDouble(feat.properties, "DEPTH") ?: extractDouble(feat.properties, "DRVAL1") ?: return@mapNotNull null
            val linePoints = parseLineStringPoints(feat.geometry) ?: return@mapNotNull null
            val nearestOnLine = linePoints.minByOrNull { haversineDeg(lat, lon, it.first, it.second) } ?: return@mapNotNull null
            DepthPoint(nearestOnLine.first, nearestOnLine.second, depth)
        }
        if (contourPoints.isEmpty()) return null

        val nearest = contourPoints.minByOrNull { haversineDeg(lat, lon, it.lat, it.lon) } ?: return null
        return DepthSample(
            latitude = lat, longitude = lon,
            meanDepthM = nearest.depth,
            source = "$providerId/contour"
        )
    }

    private fun extractDouble(props: String, key: String): Float? {
        val line = props.lines().firstOrNull { it.uppercase().startsWith("$key=", ignoreCase = true) } ?: return null
        val value = line.substringAfter("=").trim()
        return value.toFloatOrNull()
    }

    private fun haversineDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dlat = Math.toRadians(lat2 - lat1)
        val dlon = Math.toRadians(lon2 - lon1)
        val sinHalfDlat = Math.sin(dlat / 2)
        val sinHalfDlon = Math.sin(dlon / 2)
        val a = sinHalfDlat * sinHalfDlat + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinHalfDlon * sinHalfDlon
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private data class DepthPoint(val lat: Double, val lon: Double, val depth: Float) {
        fun distSq(lat2: Double, lon2: Double): Double {
            val dlat = lat - lat2
            val dlon = lon - lon2
            return dlat * dlat + dlon * dlon
        }
    }

    internal fun parsePolygonPoints(geometry: String): List<Pair<Double, Double>>? {
        return try {
            val ring = extractFirstRing(geometry) ?: return null
            ring.map { pair ->
                val lng = pair.first
                val lat = pair.second
                Pair(lat, lng)
            }
        } catch (_: Exception) { null }
    }

    internal fun parseLineStringPoints(geometry: String): List<Pair<Double, Double>>? {
        return try {
            collectCoordinates(geometry)
        } catch (_: Exception) { null }
    }

    private fun extractFirstRing(geometry: String): List<Pair<Double, Double>>? {
        val type = extractJsonValue(geometry, "\"type\"")
        return when (type) {
            "\"Polygon\"" -> {
                val coords = extractCoordinatesArray(geometry) ?: return null
                extractRing(coords)
            }
            else -> null
        }
    }

    private fun collectCoordinates(geometry: String): List<Pair<Double, Double>>? {
        val coords = extractCoordinatesArray(geometry) ?: return null
        val result = mutableListOf<Pair<Double, Double>>()
        val trimmed = coords.trim('[', ']')
        var i = 0
        val chars = trimmed.toCharArray()
        while (i < chars.size) {
            if (chars[i] == '[') {
                val end = findMatchingBracket(trimmed, i)
                if (end < 0) break
                val pair = trimmed.substring(i + 1, end)
                val parts = pair.split(",").map { it.trim().toDoubleOrNull() }
                if (parts.size >= 2 && parts[0] != null && parts[1] != null) {
                    result.add(Pair(parts[1]!!, parts[0]!!))
                }
                i = end + 1
            } else {
                i++
            }
        }
        return result.ifEmpty { null }
    }

    private fun extractRing(coords: String): List<Pair<Double, Double>>? {
        val trimmed = coords.trim('[', ']')
        val result = mutableListOf<Pair<Double, Double>>()
        var i = 0
        val chars = trimmed.toCharArray()
        while (i < chars.size) {
            if (chars[i] == '[') {
                val end = findMatchingBracket(trimmed, i)
                if (end < 0) break
                val pair = trimmed.substring(i + 1, end)
                val parts = pair.split(",").map { it.trim().toDoubleOrNull() }
                if (parts.size >= 2 && parts[0] != null && parts[1] != null) {
                    result.add(Pair(parts[1]!!, parts[0]!!))
                }
                i = end + 1
            } else {
                i++
            }
        }
        return result.ifEmpty { null }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val after = json.substring(idx + key.length)
        val colonIdx = after.indexOf(':')
        if (colonIdx < 0) return null
        val valueStart = colonIdx + 1
        val trimmed = after.substring(valueStart).trim()
        if (trimmed.startsWith('"')) {
            val end = trimmed.indexOf('"', 1)
            if (end < 0) return null
            return trimmed.substring(0, end + 1)
        }
        val end = trimmed.indexOfAny(charArrayOf(',', '}', ']'))
        return if (end < 0) trimmed.trim() else trimmed.substring(0, end).trim()
    }

    private fun extractCoordinatesArray(geometry: String): String? {
        val key = "\"coordinates\""
        val idx = geometry.indexOf(key)
        if (idx < 0) return null
        val after = geometry.substring(idx + key.length)
        val colonIdx = after.indexOf(':')
        if (colonIdx < 0) return null
        val valueStart = colonIdx + 1
        val trimmed = after.substring(valueStart).trim()
        if (!trimmed.startsWith('[')) return null
        val end = findMatchingBracket(trimmed, 0)
        if (end < 0) return null
        return trimmed.substring(1, end)
    }

    private fun findMatchingBracket(s: String, start: Int): Int {
        val c = s[start]
        val close = when (c) {
            '[' -> ']'
            '{' -> '}'
            else -> return -1
        }
        var depth = 0
        var inString = false
        for (i in start until s.length) {
            val ch = s[i]
            if (ch == '"' && (i == 0 || s[i - 1] != '\\')) inString = !inString
            if (inString) continue
            when (ch) {
                c -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    internal fun pointInPolygon(lat: Double, lon: Double, polygon: List<Pair<Double, Double>>?): Boolean {
        if (polygon == null || polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (y1, x1) = polygon[i]
            val (y2, x2) = polygon[j]
            if ((y1 > lat) != (y2 > lat) &&
                lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
