package app.pursi.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import javax.inject.Inject

data class SearchResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val country: String = ""
)

class GeocodingClient @Inject constructor(
    private val client: OkHttpClient
) {

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/search" +
                "?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&format=json&limit=10&addressdetails=1"
            val request = Request.Builder().url(url)
                .header("User-Agent", "Pursi/1.0 (marine navigation)")
                .build()
            val json = client.newCall(request).execute().use { resp ->
                resp.body?.string() ?: return@withContext emptyList()
            }
            parseResults(json)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    private fun parseResults(json: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val lat = obj.optDouble("lat", Double.NaN)
            val lon = obj.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val name = obj.optString("display_name", "")
            if (name.isEmpty()) continue
            val type = obj.optString("type", "")
            val country = obj.optString("country", "")
            results.add(SearchResult(name.take(80), lat, lon, type, country))
        }
        return results
    }
}
