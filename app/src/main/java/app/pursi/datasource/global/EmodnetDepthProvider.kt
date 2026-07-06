package app.pursi.datasource.global

import android.util.Log
import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.DepthProvider
import app.pursi.datasource.core.DepthSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class EmodnetDepthProvider @Inject constructor(
    private val client: OkHttpClient
) : DepthProvider {

    override val providerId = "global-emodnet-depth"
    override val displayName = "EMODnet syvyysmalli"
    override val coverage = BoundingBox(11.0, 90.0, -70.5, 43.0)
    override val priority = 1

    private val json = Json { ignoreUnknownKeys = true }
    private val tag = "Pursi.EmodnetDepth"
    private val depthSampleUrl = "https://rest.emodnet-bathymetry.eu/depth_sample"

    override suspend fun getDepthAt(lat: Double, lon: Double): DepthSample? = withContext(Dispatchers.IO) {
        try {
            val url = "$depthSampleUrl?geom=POINT($lon $lat)"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(tag, "EMODnet error ${response.code} for $lat,$lon")
                return@withContext null
            }
            val body = response.body?.string() ?: return@withContext null

            val parsed = parseDepthResponse(body, lat, lon)
            if (parsed != null) {
                Log.d(tag, "Depth at $lat,$lon: mean=${parsed.meanDepthM}m (${parsed.source})")
            } else {
                Log.w(tag, "No depth data at $lat,$lon from EMODnet")
            }
            parsed
        } catch (e: Exception) {
            Log.e(tag, "EMODnet query failed at $lat,$lon: ${e.message}")
            null
        }
    }

    internal fun parseDepthResponse(body: String, lat: Double, lon: Double): DepthSample? {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val min = root["min"]?.jsonPrimitive?.content?.toFloatOrNull()
            val max = root["max"]?.jsonPrimitive?.content?.toFloatOrNull()
            val mean = root["mean"]?.jsonPrimitive?.content?.toFloatOrNull()
            val stdev = root["stdev"]?.jsonPrimitive?.content?.toFloatOrNull()

            if (mean == null && min == null) return null

            DepthSample(
                latitude = lat,
                longitude = lon,
                meanDepthM = mean ?: min,
                minDepthM = min,
                maxDepthM = max,
                stdevM = stdev,
                source = "$providerId/dtm"
            )
        } catch (_: Exception) {
            null
        }
    }
}
