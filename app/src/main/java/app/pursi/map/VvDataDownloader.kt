package app.pursi.map

import android.content.Context
import android.util.JsonReader
import android.util.Log
import app.pursi.data.AppDatabase
import app.pursi.data.model.WfsFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

data class VvDataType(
    val name: String,
    val fileName: String,
    val featureType: String,
    val sizeKb: Int
)

class VvDataDownloader(
    private val context: Context,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "VvDataDownloader"
        private const val BATCH_SIZE = 500
        const val RELEASES_BASE =
            "https://github.com/pursiapp/vaylavirasto-data/releases/latest/download"

        val DATA_TYPES = listOf(
            VvDataType("turvalaitteet", "turvalaitteet.geojson.gz", "navigation_aid", 2753),
            VvDataType("turvalaitteet_muut", "turvalaitteet_muut.geojson.gz", "navigation_aid", 664),
            VvDataType("loistot", "loistot.geojson.gz", "light", 224),
            VvDataType("navigointilinjat", "navigointilinjat.geojson.gz", "navigation_line", 1318),
            VvDataType("paivatunnukset", "paivatunnukset.geojson.gz", "daymark", 602),
            VvDataType("rajoitusalueet", "rajoitusalueet.geojson.gz", "restricted_area", 5505),
            VvDataType("vaylat_vl1", "vaylat_vl1.geojson.gz", "fairway", 47),
            VvDataType("vaylat_vl2", "vaylat_vl2.geojson.gz", "fairway", 81),
            VvDataType("valosektorit", "valosektorit.geojson.gz", "light_sector", 200),
            VvDataType("vesiliikennemerkit", "vesiliikennemerkit.geojson.gz", "notice", 305)
        )

        fun typeUrl(fileName: String): String =
            "$RELEASES_BASE/$fileName"
    }

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    private val metaFile: File
        get() = File(context.filesDir, "vv_data.meta")

    private val _lastUpdated = MutableStateFlow<String?>(null)
    val lastUpdated: StateFlow<String?> = _lastUpdated.asStateFlow()

    private val db by lazy { AppDatabase.getInstance(context) }
    private val dao by lazy { db.wfsFeatureDao() }

    init {
        _isDownloaded.value = checkIfDownloaded()
        _lastUpdated.value = loadMeta()["last_updated"]
    }

    private fun checkIfDownloaded(): Boolean {
        val meta = loadMeta()
        return meta["etag"] != null
    }

    suspend fun isUpdateAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = typeUrl(DATA_TYPES.first().fileName)
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                val remoteEtag = response.header("ETag") ?: response.header("etag") ?: return@withContext false
                val localEtag = loadMeta()["etag"]
                remoteEtag != localEtag
            }
        } catch (e: Exception) { Log.w(TAG, "ETag check failed", e); false }
    }

    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext false
        _isDownloading.value = true
        _statusText.value = "Ladataan..."

        try {
            val totalTypes = DATA_TYPES.size
            var successCount = 0
            var totalFeatures = 0
            var etag: String? = null

            for ((index, dataType) in DATA_TYPES.withIndex()) {
                _statusText.value = "Ladataan: ${dataType.name} (${index + 1}/${totalTypes})"
                try {
                    val url = typeUrl(dataType.fileName)
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Failed to download ${dataType.fileName}: HTTP ${response.code}")
                        continue
                    }

                    val body = response.body ?: continue

                    if (etag == null) {
                        etag = response.header("ETag") ?: response.header("etag")
                    }

                    val stream = GZIPInputStream(body.byteStream())
                    val features = parseAndInsertStream(stream, dataType)
                    stream.close()

                    Log.d(TAG, "Downloaded ${dataType.fileName}: $features features (type=${dataType.featureType}, source=vayla_${dataType.name})")

                    if (features > 0) {
                        successCount++
                        totalFeatures += features
                    }

                    _progress.value = ((index + 1).toFloat() / totalTypes * 100f)

                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading ${dataType.fileName}", e)
                }
            }

            if (successCount > 0) {
                saveMeta("etag", etag ?: "")
                saveMeta("last_updated", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
                saveMeta("feature_count", totalFeatures.toString())
                _progress.value = 100f
                _isDownloaded.value = true
                _lastUpdated.value = loadMeta()["last_updated"]
                _statusText.value = "Valmis: $totalFeatures kohdetta"
                true
            } else {
                _statusText.value = "Lataus epäonnistui"
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _statusText.value = "Lataus epäonnistui"
            false
        } finally {
            _isDownloading.value = false
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            dao.clearAll()
        }
        metaFile.delete()
        _isDownloaded.value = false
        _progress.value = 0f
        _lastUpdated.value = null
        _statusText.value = null
    }

    private suspend fun parseAndInsertStream(inputStream: java.io.InputStream, dataType: VvDataType): Int {
        val reader = JsonReader(InputStreamReader(inputStream))
        var featureCount = 0
        val batch = mutableListOf<WfsFeature>()
        val sourceName = "vayla_${dataType.name}"
        val featureType = dataType.featureType

        try {
            reader.use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "features" -> {
                            r.beginArray()
                            var firstFeature = true
                            while (r.hasNext()) {
                                try {
                                    val feature = readGeoJsonFeature(r)
                                    if (feature != null) {
                                        if (firstFeature) {
                                            Log.d(TAG, "First ${sourceName}: lat=${feature.bbox.lat} lng=${feature.bbox.lng} bbox=[${feature.bbox.minLat},${feature.bbox.minLng}]-[${feature.bbox.maxLat},${feature.bbox.maxLng}] geo=${feature.geometry.take(100)}")
                                            firstFeature = false
                                        }
                                        batch.add(WfsFeature(
                                            source = sourceName,
                                            featureType = featureType,
                                            geometry = feature.geometry,
                                            properties = feature.propertiesStr,
                                            latitude = feature.bbox.lat,
                                            longitude = feature.bbox.lng,
                                            minLat = feature.bbox.minLat,
                                            minLng = feature.bbox.minLng,
                                            maxLat = feature.bbox.maxLat,
                                            maxLng = feature.bbox.maxLng
                                        ))
                                        featureCount++
                                        if (batch.size >= BATCH_SIZE) {
                                            dao.insertAll(batch.toList())
                                            batch.clear()
                                        }
                                    }
                                } catch (_: Exception) {
                                    skipNestedValue(r)
                                }
                            }
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }

            if (batch.isNotEmpty()) {
                dao.insertAll(batch)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ${dataType.fileName}", e)
        }

        return featureCount
    }

    private data class ParsedFeature(
        val geometry: String,
        val propertiesStr: String,
        val bbox: Bbox
    )

    private data class Bbox(
        val lat: Double, val lng: Double,
        val minLat: Double, val minLng: Double,
        val maxLat: Double, val maxLng: Double
    )

    private fun readGeoJsonFeature(reader: JsonReader): ParsedFeature? {
        var geometryStr: String? = null
        val props = StringBuilder()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "geometry" -> geometryStr = readNestedJsonValue(reader)
                "properties" -> readPropertiesToBuilder(reader, props)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (geometryStr == null) return null
        val bbox = extractBboxStr(geometryStr) ?: return null
        return ParsedFeature(geometryStr, props.toString(), bbox)
    }

    private fun readPropertiesToBuilder(reader: JsonReader, sb: StringBuilder) {
        if (reader.peek() == android.util.JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            val value = when (reader.peek()) {
                android.util.JsonToken.STRING -> reader.nextString()
                android.util.JsonToken.NUMBER -> reader.nextString()
                android.util.JsonToken.BOOLEAN -> reader.nextBoolean().toString()
                android.util.JsonToken.NULL -> { reader.nextNull(); null }
                else -> { reader.skipValue(); null }
            }
            if (value != null && value != "null" && value.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(key).append('=').append(value)
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
            android.util.JsonToken.BEGIN_OBJECT -> {
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
            android.util.JsonToken.BEGIN_ARRAY -> {
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
            android.util.JsonToken.STRING -> {
                val s = reader.nextString()
                sb.append('"').append(jsonEscape(s)).append('"')
                true
            }
            android.util.JsonToken.NUMBER -> {
                sb.append(reader.nextString())
                true
            }
            android.util.JsonToken.BOOLEAN -> {
                sb.append(reader.nextBoolean().toString())
                true
            }
            android.util.JsonToken.NULL -> {
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
            android.util.JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    reader.skipValue()
                }
                reader.endObject()
            }
            android.util.JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    skipNestedValue(reader)
                }
                reader.endArray()
            }
            else -> reader.skipValue()
        }
    }

    private fun extractBboxStr(geometryStr: String): Bbox? {
        return try {
            val geo = org.json.JSONObject(geometryStr)
            val type = geo.getString("type")
            val coords = geo.getJSONArray("coordinates")
            when (type) {
                "Point" -> {
                    val lng = coords.getDouble(0)
                    val lat = coords.getDouble(1)
                    Bbox(lat, lng, lat, lng, lat, lng)
                }
                "LineString" -> {
                    computeBboxFromCoordArray(coords)
                }
                "Polygon" -> {
                    val ring = coords.getJSONArray(0)
                    computeBboxFromCoordArray(ring)
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun computeBboxFromCoordArray(coords: org.json.JSONArray): Bbox {
        var minLat = Double.MAX_VALUE; var maxLat = Double.MIN_VALUE
        var minLng = Double.MAX_VALUE; var maxLng = Double.MIN_VALUE
        var sumLat = 0.0; var sumLng = 0.0
        val count = coords.length()
        for (i in 0 until count) {
            val pt = coords.getJSONArray(i)
            val lng = pt.getDouble(0)
            val lat = pt.getDouble(1)
            sumLat += lat; sumLng += lng
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
        }
        return Bbox(sumLat / count, sumLng / count, minLat, minLng, maxLat, maxLng)
    }

    private fun loadMeta(): Map<String, String> {
        if (!metaFile.exists()) return emptyMap()
        return try {
            metaFile.readLines().associate { line ->
                val eq = line.indexOf('=')
                if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else "" to ""
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveMeta(key: String, value: String) {
        try {
            val meta = loadMeta().toMutableMap()
            meta[key] = value
            metaFile.writeText(meta.entries.joinToString("\n") { "${it.key}=${it.value}" })
        } catch (_: Exception) {}
    }
}
