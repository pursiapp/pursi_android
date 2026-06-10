package fi.pursi.map

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class OverpassClient @Inject constructor(
    private val client: OkHttpClient
) {
    private val lastQueryTime = AtomicLong(0L)

    suspend fun query(category: PoiCategory, latitude: Double, longitude: Double): List<PoiResult> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val last = lastQueryTime.get()
                val elapsed = now - last
                if (elapsed < 3000) {
                    delay(3000 - elapsed)
                }
                lastQueryTime.set(System.currentTimeMillis())

                val ql = category.overpassQuery
                    .replace("{lat}", latitude.toString())
                    .replace("{lon}", longitude.toString())
                val encoded = URLEncoder.encode(ql, "UTF-8")
                val url = "https://overpass-api.de/api/interpreter?data=$encoded"
                val request = Request.Builder().url(url)
                    .header("User-Agent", "Pursi/1.0 (marine navigation)")
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Overpass error ${response.code} for ${category.id}")
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                if (body.startsWith("<?xml") || body.startsWith("<!DOCTYPE")) {
                    Log.w(TAG, "Overpass non-JSON response for ${category.id}")
                    return@withContext emptyList()
                }
                parseResults(body, category)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(TAG, "Overpass query failed for ${category.id}", e); emptyList() }
        }

    private fun parseResults(json: String, category: PoiCategory): List<PoiResult> {
        val results = mutableListOf<PoiResult>()
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return emptyList()
            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val osmType = el.optString("type", "node")
                val osmId = el.optLong("id", 0)
                val tags = el.optJSONObject("tags") ?: JSONObject()
                val name = tags.optString("name", "")

                val lat: Double
                val lon: Double
                if (osmType == "way") {
                    val center = el.optJSONObject("center")
                    if (center == null) continue
                    lat = center.optDouble("lat", Double.NaN)
                    lon = center.optDouble("lon", Double.NaN)
                } else {
                    lat = el.optDouble("lat", Double.NaN)
                    lon = el.optDouble("lon", Double.NaN)
                }
                if (lat.isNaN() || lon.isNaN()) continue

                val tagMap = mutableMapOf<String, String>()
                for (key in tags.keys()) {
                    tagMap[key] = tags.optString(key, "")
                }

                results.add(PoiResult(
                    osmId = osmId,
                    osmType = osmType,
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    category = category,
                    tags = tagMap
                ))
            }
        } catch (e: Exception) { Log.w(TAG, "Overpass parse error", e) }
        return results
    }

    companion object {
        private const val TAG = "Pursi.Overpass"
    }
}
