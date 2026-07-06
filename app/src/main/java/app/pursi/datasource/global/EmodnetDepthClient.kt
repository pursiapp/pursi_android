package app.pursi.datasource.global

import android.util.Log
import app.pursi.data.model.EmodnetDepthSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject

data class DepthResult(
    val avg: Float,
    val min: Float,
    val max: Float
)

class EmodnetDepthClient @Inject constructor(
    private val client: OkHttpClient
) {
    private val tag = "Pursi.EmodnetREST"
    private val userAgent = "Pursi/0.5 (Android; +https://pursi.fi)"
    private val restUrl = "https://rest.emodnet-bathymetry.eu/depth_sample"
    private val stepDeg = 0.02
    private val maxKeys = 50

    fun gridKeysForBbox(
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double
    ): List<String> {
        val keys = mutableListOf<String>()
        val startLat = (minLat / stepDeg).toInt() * stepDeg
        val startLng = (minLng / stepDeg).toInt() * stepDeg
        var lat = startLat
        while (lat <= maxLat) {
            var lng = startLng
            while (lng <= maxLng) {
                keys.add(String.format(Locale.US, "lat=%.4f_lng=%.4f", lat, lng))
                lng += stepDeg
            }
            lat += stepDeg
        }
        return keys
    }

    internal fun parseGridKey(key: String): Pair<Double, Double>? {
        return try {
            val parts = key.removePrefix("lat=").split("_lng=")
            if (parts.size != 2) return null
            val lat = parts[0].replace(",", ".").toDoubleOrNull() ?: return null
            val lng = parts[1].replace(",", ".").toDoubleOrNull() ?: return null
            Pair(lat, lng)
        } catch (_: Exception) { null }
    }

    suspend fun fetchMissingGridKeys(
        gridKeys: List<String>
    ): List<EmodnetDepthSample> = withContext(Dispatchers.IO) {
        val keys = gridKeys.take(maxKeys)
        if (keys.isEmpty()) return@withContext emptyList()

        Log.d(tag, "Fetching ${keys.size} missing grid keys")

        val results = mutableListOf<EmodnetDepthSample>()
        val requestBuilder = Request.Builder()
            .header("User-Agent", userAgent)

        for (key in keys) {
            val pair = parseGridKey(key)
            if (pair == null) {
                Log.w(tag, "Failed to parse key: $key")
                continue
            }
            val (lat, lng) = pair

            try {
                val lngEncoded = java.net.URLEncoder.encode("POINT($lng $lat)", "UTF-8")
                val url = "$restUrl?geom=$lngEncoded"
                Log.d(tag, "Fetching $lat,$lng")
                val request = requestBuilder.url(url).build()
                val response = client.newCall(request).execute()
                Log.d(tag, "Response ${response.code} for $lat,$lng")

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Log.d(tag, "Body: ${body.take(150)}")
                    val result = parseRestDepth(body)
                    if (result != null) {
                        results.add(EmodnetDepthSample(
                            gridKey = key,
                            latitude = lat,
                            longitude = lng,
                            depthAvgM = result.avg,
                            depthMinM = result.min,
                            depthMaxM = result.max
                        ))
                        Log.d(tag, "Parsed: avg=${result.avg} min=${result.min} max=${result.max}")
                    } else {
                        Log.w(tag, "parseRestDepth null for: ${body.take(80)}")
                    }
                } else {
                    Log.w(tag, "Bad response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception: ${e.message}")
            }
        }

        Log.d(tag, "Fetched ${results.size}/${keys.size} samples")
        results
    }

    internal fun parseRestDepth(body: String): DepthResult? {
        if (body.isBlank()) return null
        return try {
            val json = org.json.JSONObject(body)
            val avg = json.optDouble("avg", Double.NaN)
            val smoothed = json.optDouble("smoothed", Double.NaN)
            val min = json.optDouble("min", Double.NaN)
            val max = json.optDouble("max", Double.NaN)

            val primary = when {
                !avg.isNaN() && avg != 0.0 -> Math.abs(avg)
                !smoothed.isNaN() && smoothed != 0.0 -> Math.abs(smoothed)
                !avg.isNaN() && avg != 0.0 -> Math.abs(avg)
                else -> return null
            }.toFloat()

            val absMin = if (!min.isNaN()) Math.abs(min).toFloat() else 0f
            val absMax = if (!max.isNaN()) Math.abs(max).toFloat() else 0f

            DepthResult(avg = primary, min = absMin, max = absMax)
        } catch (_: Exception) { null }
    }
}
