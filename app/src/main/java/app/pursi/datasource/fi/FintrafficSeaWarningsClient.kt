package app.pursi.datasource.fi

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

data class SeaWarning(
    val id: String,
    val typeFi: String,
    val typeSv: String,
    val typeEn: String,
    val locationFi: String,
    val locationSv: String,
    val locationEn: String,
    val contentFi: String,
    val contentSv: String,
    val contentEn: String,
    val validityStartEpochMs: Long?,
    val validityEndEpochMs: Long?,
    val publishedEpochMs: Long,
    val geometryType: String,
    val geometry: String,
    val centroidLat: Double,
    val centroidLon: Double,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
)

class FintrafficSeaWarningsClient @Inject constructor(
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "FintrafficSW"
        private const val BASE_URL = "https://services1.arcgis.com/rhs5fjYxdOG1Et61/ArcGIS/rest/services/SeaWarnings/FeatureServer"
        private val LAYERS = listOf(0, 1, 2)
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAll(): List<SeaWarning> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SeaWarning>()
        for (layer in LAYERS) {
            try {
                val layerResult = queryLayer(layer)
                results.addAll(layerResult)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.w(TAG, "Layer $layer failed: ${e.message}")
            }
        }
        results
    }

    private suspend fun queryLayer(layerId: Int): List<SeaWarning> {
        val url = buildString {
            append("$BASE_URL/$layerId/query")
            append("?where=1%3D1")
            append("&outFields=*")
            append("&f=json")
            append("&returnGeometry=true")
            append("&outSR=4326")
        }
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return parseFeatureCollection(body, layerId)
    }

    private fun parseFeatureCollection(jsonStr: String, layerId: Int): List<SeaWarning> {
        val root = try {
            json.parseToJsonElement(jsonStr).jsonObject
        } catch (_: Exception) { return emptyList() }
        val features = root["features"]?.jsonArray ?: return emptyList()
        val geometryType = root["geometryType"]?.jsonPrimitive?.content ?: ""
        return features.mapNotNull { parseFeature(it, geometryType, layerId) }
    }

    private fun parseFeature(element: JsonElement, geometryType: String, layerId: Int): SeaWarning? {
        return try {
            val obj = element.jsonObject
            val attrs = obj["attributes"]?.jsonObject ?: return null
            val geometry = obj["geometry"] ?: return null

            val typeFi = safeString(attrs["TYYPPI_FI"])
            val typeSv = safeString(attrs["TYYPPI_SV"])
            val typeEn = safeString(attrs["TYYPPI_EN"])
            val locationFi = safeString(attrs["SIJAINTI_FI"])
            val locationSv = safeString(attrs["SIJAINTI_SV"])
            val locationEn = safeString(attrs["SIJAINTI_EN"])
            val contentFi = safeString(attrs["SISALTO_FI"])
            val contentSv = safeString(attrs["SISALTO_SV"])
            val contentEn = safeString(attrs["SISALTO_EN"])
            val objId = safeString(attrs["OBJECTID"], "0")
            val id = "fintraffic-$layerId-$objId"

            val validityStart = safeLong(attrs["validity_start_time"])
            val validityEnd = safeLong(attrs["validity_end_time"])

            val paivays = safeLong(attrs["PaivaysAikaFi"])
                ?: safeLong(attrs["PaivaysAikaEn"])
                ?: validityStart ?: System.currentTimeMillis()

            val geoStr = geometry.toString()
            val (centroid, bbox) = extractGeometryInfo(geometry, geometryType)

            SeaWarning(
                id = id,
                typeFi = typeFi,
                typeSv = typeSv,
                typeEn = typeEn,
                locationFi = locationFi,
                locationSv = locationSv,
                locationEn = locationEn,
                contentFi = contentFi,
                contentSv = contentSv,
                contentEn = contentEn,
                validityStartEpochMs = validityStart,
                validityEndEpochMs = validityEnd,
                publishedEpochMs = paivays,
                geometryType = geometryType,
                geometry = geoStr,
                centroidLat = centroid.first,
                centroidLon = centroid.second,
                minLat = bbox.first,
                minLng = bbox.second,
                maxLat = bbox.third,
                maxLng = bbox.fourth
            )
        } catch (_: Exception) { null }
    }

    private fun extractGeometryInfo(geometry: JsonElement, geometryType: String): Pair<Pair<Double, Double>, Quadruple<Double, Double, Double, Double>> {
        return try {
            val obj = geometry.jsonObject
            when {
                "x" in obj && "y" in obj -> {
                    val lon = obj["x"]!!.jsonPrimitive.content.toDouble()
                    val lat = obj["y"]!!.jsonPrimitive.content.toDouble()
                    Pair(Pair(lat, lon), Quadruple(lat, lon, lat, lon))
                }
                "rings" in obj -> {
                    val ring = obj["rings"]!!.jsonArray[0].jsonArray
                    val coords = ring.map { arr ->
                        Pair(arr.jsonArray[1].jsonPrimitive.content.toDouble(), arr.jsonArray[0].jsonPrimitive.content.toDouble())
                    }
                    computeBounds(coords)
                }
                "paths" in obj -> {
                    val path = obj["paths"]!!.jsonArray[0].jsonArray
                    val coords = path.map { arr ->
                        Pair(arr.jsonArray[1].jsonPrimitive.content.toDouble(), arr.jsonArray[0].jsonPrimitive.content.toDouble())
                    }
                    computeBounds(coords)
                }
                else -> Pair(Pair(60.0, 24.0), Quadruple(60.0, 24.0, 60.0, 24.0))
            }
        } catch (_: Exception) {
            Pair(Pair(60.0, 24.0), Quadruple(60.0, 24.0, 60.0, 24.0))
        }
    }

    private fun computeBounds(coords: List<Pair<Double, Double>>): Pair<Pair<Double, Double>, Quadruple<Double, Double, Double, Double>> {
        if (coords.isEmpty()) return Pair(Pair(60.0, 24.0), Quadruple(60.0, 24.0, 60.0, 24.0))
        val lats = coords.map { it.first }
        val lngs = coords.map { it.second }
        val centroidLat = lats.average()
        val centroidLon = lngs.average()
        return Pair(
            Pair(centroidLat, centroidLon),
            Quadruple(lats.min(), lngs.min(), lats.max(), lngs.max())
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun safeString(el: JsonElement?, default: String = ""): String {
        return try {
            (el as? JsonPrimitive)?.content ?: default
        } catch (_: Exception) { default }
    }

    private fun safeLong(el: JsonElement?): Long? {
        return try {
            (el as? JsonPrimitive)?.content?.toLongOrNull()
        } catch (_: Exception) { null }
    }

}
