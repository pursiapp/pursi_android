package app.pursi.data.wfs

import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.InputStreamReader
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
                val body = response.body ?: return@use emptyList()
                parseResult(body.byteStream()).features
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
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
                val body = response.body ?: return@use WfsClientResult(emptyList(), false)
                val result = parseResult(body.byteStream())
                Log.d(TAG, "Result: ${result.features.size} features for $typeName${if (result.truncated) " (TRUNCATED!)" else ""}")
                result
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { WfsClientResult(emptyList(), false) }
    }

    companion object {
        private const val TAG = "Pursi.WFS"
        private const val MAX_GEOMETRY_BYTES = 500_000
    }

    internal fun parseResult(stream: InputStream): WfsClientResult {
        val reader = JsonReader(InputStreamReader(stream, "UTF-8"))
        val features = mutableListOf<WfsFeatureData>()
        var numberReturned: Int? = null
        var numberMatched: Int? = null

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "numberReturned" -> numberReturned = reader.nextInt()
                    "numberMatched" -> numberMatched = reader.nextInt()
                    "features" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            try {
                                readGeoJsonFeature(reader)?.let { features.add(it) }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse feature: ${e.message}")
                                skipNestedValue(reader)
                            }
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            Log.w(TAG, "Stream parsing error (partial result: ${features.size}): ${e.message}")
        }

        val truncated = numberMatched != null && numberReturned != null && numberReturned < numberMatched
        return WfsClientResult(features, truncated)
    }

    private fun readGeoJsonFeature(reader: JsonReader): WfsFeatureData? {
        var id = ""
        var geometryStr: String? = null
        val props = mutableMapOf<String, String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "geometry" -> geometryStr = readNestedJsonValue(reader)
                "properties" -> readPropertiesToMap(reader, props)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (geometryStr == null) return null

        if (geometryStr.length > MAX_GEOMETRY_BYTES) {
            Log.w(TAG, "Skipping feature $id: geometry ${geometryStr.length} bytes exceeds $MAX_GEOMETRY_BYTES")
            return null
        }

        val coords = extractBbox(geometryStr) ?: return null
        return WfsFeatureData(
            id = id,
            geometry = geometryStr,
            properties = props,
            latitude = coords.lat,
            longitude = coords.lng,
            minLat = coords.minLat,
            minLng = coords.minLng,
            maxLat = coords.maxLat,
            maxLng = coords.maxLng
        )
    }

    private fun readPropertiesToMap(reader: JsonReader, map: MutableMap<String, String>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            val value = when (reader.peek()) {
                JsonToken.STRING -> reader.nextString()
                JsonToken.NUMBER -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                JsonToken.NULL -> { reader.nextNull(); null }
                else -> { reader.skipValue(); null }
            }
            if (value != null && value != "null" && value.isNotBlank()) {
                map[key] = value
            }
        }
        reader.endObject()
    }

    private fun readNestedJsonValue(reader: JsonReader): String? {
        val sb = StringBuilder()
        if (!appendJsonValue(reader, sb)) return null
        return sb.toString()
    }

    private fun appendJsonValue(reader: JsonReader, sb: StringBuilder): Boolean {
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                sb.append('{')
                var first = true
                while (reader.hasNext()) {
                    if (!first) sb.append(',')
                    first = false
                    sb.append('"').append(reader.nextName()).append('"').append(':')
                    appendJsonValue(reader, sb)
                }
                reader.endObject()
                sb.append('}')
                true
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                sb.append('[')
                var first = true
                while (reader.hasNext()) {
                    if (!first) sb.append(',')
                    first = false
                    appendJsonValue(reader, sb)
                }
                reader.endArray()
                sb.append(']')
                true
            }
            JsonToken.STRING -> {
                val s = reader.nextString()
                sb.append('"').append(jsonEscape(s)).append('"')
                true
            }
            JsonToken.NUMBER -> {
                sb.append(reader.nextString())
                true
            }
            JsonToken.BOOLEAN -> {
                sb.append(reader.nextBoolean().toString())
                true
            }
            JsonToken.NULL -> {
                reader.nextNull()
                sb.append("null")
                true
            }
            else -> {
                reader.skipValue()
                false
            }
        }
    }

    private fun jsonEscape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun skipNestedValue(reader: JsonReader) {
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) { reader.skipValue() }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) { skipNestedValue(reader) }
                reader.endArray()
            }
            else -> reader.skipValue()
        }
    }

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

    private data class Bbox(
        val lat: Double, val lng: Double,
        val minLat: Double, val minLng: Double,
        val maxLat: Double, val maxLng: Double
    )

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
}
