package app.pursi.datasource.no

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.WarningProvider
import app.pursi.weather.MarineWarning
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class MetNorwayWarningProvider @Inject constructor(
    private val client: OkHttpClient
) : WarningProvider {
    override val providerId = "no-met-warnings"
    override val displayName = "MET Norway"
    override val coverage = BoundingBox(57.0, 72.0, 4.0, 32.0)
    override val supportedLanguages = listOf("en", "no", "nn")
    override val priority = 0

    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun getMarineWarnings(
        language: String, latitude: Double, longitude: Double
    ): List<MarineWarning> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.met.no/weatherapi/metalerts/2.0/current.json" +
                "?lat=$latitude&lon=$longitude"
            val request = Request.Builder().url(url)
                .header("User-Agent", "Pursi/1.0 (marine navigation app; https://github.com/pursiapp/pursi_android)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parseAlerts(body, language)
        } catch (_: Exception) { emptyList() }
    }

    private fun parseAlerts(jsonStr: String, language: String): List<MarineWarning> {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { feature ->
            parseFeature(feature, language)
        }
    }

    private fun parseFeature(element: JsonElement, language: String): MarineWarning? {
        try {
            val feature = element.jsonObject
            val props = feature["properties"]?.jsonObject ?: return null

            val event = props["event"]?.jsonPrimitive?.content ?: return null
            val eventCode = detectEventCode(event, props)

            val severity = props["severity"]?.jsonPrimitive?.content ?: "unknown"
            val certainty = props["certainty"]?.jsonPrimitive?.content ?: "unknown"

            val headline = extractLocalized(props, "eventAwareName", language) ?: event
            val description = extractLocalized(props, "description", language) ?: event

            val onset = props["onset"]?.jsonPrimitive?.content
            val expires = props["expires"]?.jsonPrimitive?.content

            val color = mapSeverityToColor(severity)

            val geom = feature["geometry"]?.jsonObject
            val (centroidLat, centroidLon, polygonCoords) = parseGeometry(geom)

            return MarineWarning(
                event = event,
                eventCode = eventCode,
                severity = severity,
                color = color,
                description = description ?: event,
                headline = headline ?: event,
                areaDesc = headline ?: event,
                onset = parseTimestamp(onset) ?: "",
                expires = parseTimestamp(expires) ?: "",
                windSpeedMs = null,
                windDirectionDeg = null,
                centroidLat = centroidLat,
                centroidLon = centroidLon,
                polygonCoords = polygonCoords ?: ""
            )
        } catch (_: Exception) { return null }
    }

    private fun extractLocalized(props: JsonObject, key: String, language: String): String? {
        val obj = props[key]?.jsonObject ?: return null
        val langKeys = listOf(
            language,
            if (language == "no" || language == "nn") "nb" else "en",
            "en", "no"
        ).distinct()
        for (lk in langKeys) {
            val v = obj[lk]?.jsonPrimitive?.content
            if (v != null) return v
        }
        return null
    }

    private fun detectEventCode(event: String, props: JsonObject): String {
        val codeObj = props["eventCode"]?.jsonObject
        if (codeObj != null) {
            val value = codeObj["value"]?.jsonPrimitive?.content ?: ""
            if (value.isNotEmpty()) return value
        }
        val categories = props["category"]?.jsonPrimitive?.content ?: ""
        if (categories.contains("Mar", ignoreCase = true) ||
            categories.contains("Sea", ignoreCase = true) ||
            categories.contains("Ocean", ignoreCase = true) ||
            event.contains("Gale", ignoreCase = true) ||
            event.contains("Storm", ignoreCase = true) ||
            event.contains("Wind", ignoreCase = true) ||
            event.contains("Wave", ignoreCase = true) ||
            event.contains("High tide", ignoreCase = true) ||
            event.contains("Flood", ignoreCase = true)) {
            return "seaWeather"
        }
        if (event.contains("thunder", ignoreCase = true) ||
            event.contains("lightning", ignoreCase = true)) {
            return "thunderstorm"
        }
        return "met"
    }

    private fun mapSeverityToColor(severity: String): String = when {
        severity.contains("extreme", ignoreCase = true) -> "red"
        severity.contains("severe", ignoreCase = true) -> "orange"
        severity.contains("moderate", ignoreCase = true) -> "yellow"
        severity.contains("minor", ignoreCase = true) -> "yellow"
        else -> "grey"
    }

    private fun parseGeometry(geom: JsonObject?): Triple<Double?, Double?, String?> {
        if (geom == null) return Triple(null, null, null)
        val coords = geom["coordinates"]?.jsonArray ?: return Triple(null, null, null)
        val type = geom["type"]?.jsonPrimitive?.content ?: ""

        return when (type) {
            "Polygon" -> parsePolygon(coords)
            "MultiPolygon" -> parseMultiPolygon(coords)
            else -> Triple(null, null, null)
        }
    }

    private fun parsePolygon(coords: JsonArray): Triple<Double?, Double?, String?> {
        val ring = coords[0]?.jsonArray ?: return Triple(null, null, null)
        var centerLat = 0.0; var centerLon = 0.0; var count = 0
        val pairs = mutableListOf<String>()
        for (point in ring) {
            val pt = point.jsonArray
            if (pt.size >= 2) {
                val lon = pt[0].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                val lat = pt[1].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                centerLat += lat; centerLon += lon; count++
                pairs.add("$lat,$lon")
            }
        }
        if (count == 0) return Triple(null, null, null)
        val centroidLat = centerLat / count
        val centroidLon = centerLon / count
        return Triple(centroidLat, centroidLon, pairs.joinToString(" "))
    }

    private fun parseMultiPolygon(coords: JsonArray): Triple<Double?, Double?, String?> {
        val polygon = coords[0]?.jsonArray ?: return Triple(null, null, null)
        return parsePolygon(polygon)
    }

    private fun parseTimestamp(iso: String?): String? {
        if (iso == null) return null
        return try { dateFormat.format(dateFormat.parse(iso) ?: return iso) } catch (_: Exception) { iso }
    }
}
