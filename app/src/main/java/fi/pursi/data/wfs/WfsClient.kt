package fi.pursi.data.wfs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import java.net.URLEncoder
import javax.inject.Inject

data class WfsFeatureData(
    val id: String,
    val geometry: String,
    val properties: Map<String, String>,
    val latitude: Double,
    val longitude: Double,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
)

data class WfsClientResult(
    val features: List<WfsFeatureData>,
    val truncated: Boolean
)

class WfsClient @Inject constructor(
    private val client: OkHttpClient
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchTile(tileUrl: String): List<WfsFeatureData> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(tileUrl).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use emptyList()
                parseGeoJson(body).features
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    companion object {
        private const val TAG = "Pursi.WFS"
    }

    suspend fun query(
        baseUrl: String,
        typeName: String,
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double,
        maxFeatures: Int = 1000,
        extraParams: Map<String, String> = emptyMap(),
        bboxSrs4326: Boolean = true
    ): WfsClientResult = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(baseUrl, typeName, minLat, minLng, maxLat, maxLng, maxFeatures, extraParams, bboxSrs4326)
            Log.d(TAG, "Query: $typeName, bbox=${minLng},${minLat},${maxLng},${maxLat}")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "WFS error ${response.code} for $typeName")
                    return@use WfsClientResult(emptyList(), false)
                }
                val body = response.body?.string() ?: return@use WfsClientResult(emptyList(), false)
                val result = parseGeoJson(body)
                Log.d(TAG, "Result: ${result.features.size} features for $typeName${if (result.truncated) " (TRUNCATED!)" else ""}")
                result
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { WfsClientResult(emptyList(), false) }
    }

    private fun buildUrl(
        baseUrl: String, typeName: String,
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double,
        maxFeatures: Int,
        extraParams: Map<String, String> = emptyMap(),
        bboxSrs4326: Boolean = true
    ): String {
        val bbox = if (bboxSrs4326) {
            "$minLng,$minLat,$maxLng,$maxLat,EPSG:4326"
        } else {
            val (sw, ne) = EtrsTm35finConverter.bboxToEtrsTm35fin(minLat, minLng, maxLat, maxLng)
            val bboxStr = "${sw.first},${sw.second},${ne.first},${ne.second},EPSG:3067"
            Log.d(TAG, "EPSG:4326 bbox ${minLng},${minLat},${maxLng},${maxLat} -> EPSG:3067 $bboxStr")
            bboxStr
        }
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val allParams = mutableMapOf(
            "service" to "WFS",
            "version" to "2.0.0",
            "request" to "GetFeature",
            "typenames" to typeName,
            "bbox" to bbox,
            "srsName" to "EPSG:4326",
            "outputFormat" to "application/json",
        )
        if (maxFeatures > 0) allParams["count"] = maxFeatures.toString()
        allParams.putAll(extraParams)
        val queryString = allParams.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$baseUrl$separator$queryString"
    }

    private fun parseGeoJson(jsonStr: String): WfsClientResult {
        val root = try {
            json.parseToJsonElement(jsonStr).jsonObject
        } catch (_: Exception) { return WfsClientResult(emptyList(), false) }
        val features = root["features"]?.jsonArray ?: return WfsClientResult(emptyList(), false)

        val numberReturned = try {
            root["numberReturned"]?.jsonPrimitive?.content?.toIntOrNull()
        } catch (_: Exception) { null }
        val numberMatched = try {
            root["numberMatched"]?.jsonPrimitive?.content?.toIntOrNull()
        } catch (_: Exception) { null }
        val truncated = numberMatched != null && numberReturned != null && numberReturned < numberMatched

        val result = mutableListOf<WfsFeatureData>()
        for (feature in features) {
            try {
                val obj = feature.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: ""
                val geometry = obj["geometry"]?.toString() ?: continue
                val props = obj["properties"]?.jsonObject
                val properties = mutableMapOf<String, String>()
                props?.forEach { (k, v) ->
                    try { properties[k] = v.jsonPrimitive.content } catch (_: Exception) { }
                }
                val coords = extractBbox(geometry) ?: continue
                result.add(WfsFeatureData(
                    id = id, geometry = geometry, properties = properties,
                    latitude = coords.lat, longitude = coords.lng,
                    minLat = coords.minLat, minLng = coords.minLng,
                    maxLat = coords.maxLat, maxLng = coords.maxLng
                ))
            } catch (_: Exception) { }
        }
        return WfsClientResult(result, truncated)
    }

    private data class Bbox(
        val lat: Double, val lng: Double,
        val minLat: Double, val minLng: Double,
        val maxLat: Double, val maxLng: Double
    )

    private fun extractBbox(geometry: String): Bbox? {
        return try {
            val geo = json.parseToJsonElement(geometry).jsonObject
            val coords = geo["coordinates"] ?: return null
            val type = geo["type"]?.jsonPrimitive?.content ?: return null
            when (type) {
                "Point" -> {
                    val arr = coords.jsonArray
                    val lng = arr[0].jsonPrimitive.content.toDoubleOrNull() ?: return null
                    val lat = arr[1].jsonPrimitive.content.toDoubleOrNull() ?: return null
                    Bbox(lat, lng, lat, lng, lat, lng)
                }
                "LineString" -> {
                    val points = coords.jsonArray.mapNotNull { arr ->
                        val lng = arr.jsonArray[0].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                        val lat = arr.jsonArray[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                        Pair(lat, lng)
                    }
                    if (points.isEmpty()) return null
                    val lats = points.map { it.first }
                    val lngs = points.map { it.second }
                    Bbox(lats.average(), lngs.average(), lats.min(), lngs.min(), lats.max(), lngs.max())
                }
                "Polygon" -> {
                    val ring = coords.jsonArray[0].jsonArray
                    val points = ring.mapNotNull { arr ->
                        val lng = arr.jsonArray[0].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                        val lat = arr.jsonArray[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                        Pair(lat, lng)
                    }
                    if (points.isEmpty()) return null
                    val lats = points.map { it.first }
                    val lngs = points.map { it.second }
                    Bbox(lats.average(), lngs.average(), lats.min(), lngs.min(), lats.max(), lngs.max())
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
