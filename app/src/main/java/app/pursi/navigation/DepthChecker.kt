package app.pursi.navigation

import android.util.Log
import app.pursi.datasource.core.SourceResolver
import javax.inject.Inject

data class LatLng(val latitude: Double, val longitude: Double)

class DepthChecker @Inject constructor(
    private val resolver: SourceResolver
) {
    private val tag = "Pursi.DepthChecker"

    suspend fun getDepthAt(lat: Double, lon: Double): Float? {
        val provider = resolver.depthProviderFor(lat, lon) ?: return null
        val sample = provider.getDepthAt(lat, lon)
        if (sample?.meanDepthM != null) {
            Log.d(tag, "Depth at $lat,$lon: ${sample.meanDepthM}m from ${sample.source}")
        }
        return sample?.meanDepthM
    }

    suspend fun minDepthAlongPath(points: List<LatLng>): Float? {
        if (points.isEmpty()) return null
        var minDepth: Float? = null
        for (i in 0 until points.size - 1) {
            val from = points[i]
            val to = points[i + 1]
            val steps = maxOf(1, (haversineNm(from.latitude, from.longitude, to.latitude, to.longitude) / 0.027).toInt())

            for (step in 0..steps) {
                val t = if (steps == 0) 0.0 else step.toDouble() / steps
                val lat = from.latitude + (to.latitude - from.latitude) * t
                val lon = from.longitude + (to.longitude - from.longitude) * t
                val depth = getDepthAt(lat, lon)
                if (depth != null && (minDepth == null || depth < minDepth)) {
                    minDepth = depth
                }
            }
        }
        return minDepth
    }

    suspend fun isPointTooShallow(lat: Double, lon: Double, draughtM: Float, safetyMarginM: Float = 1.0f): Boolean {
        val depth = getDepthAt(lat, lon) ?: return false
        return depth < draughtM + safetyMarginM
    }

    suspend fun getDepthSample(lat: Double, lon: Double): Pair<Float?, String> {
        val provider = resolver.depthProviderFor(lat, lon)
        if (provider == null) return Pair(null, "no_provider")
        val sample = provider.getDepthAt(lat, lon)
        return Pair(sample?.meanDepthM, sample?.source ?: "no_data")
    }

    private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dlat = Math.toRadians(lat2 - lat1)
        val dlon = Math.toRadians(lon2 - lon1)
        val sinHalfDlat = Math.sin(dlat / 2)
        val sinHalfDlon = Math.sin(dlon / 2)
        val a = sinHalfDlat * sinHalfDlat + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinHalfDlon * sinHalfDlon
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return c * 3440.065
    }
}
