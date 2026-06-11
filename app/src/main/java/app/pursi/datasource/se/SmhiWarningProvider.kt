package app.pursi.datasource.se

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.WarningProvider
import app.pursi.weather.MarineWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class SmhiWarningProvider @Inject constructor(
    private val client: OkHttpClient
) : WarningProvider {
    override val providerId = "se-smhi-warnings"
    override val displayName = "SMHI"
    override val coverage = BoundingBox(55.0, 69.0, 10.0, 24.0)
    override val supportedLanguages = listOf("sv", "en")
    override val priority = 0

    private val rssUrl = "https://opendata-download-warnings.smhi.se/ibww/api/version/1/cap"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun getMarineWarnings(
        language: String, latitude: Double, longitude: Double
    ): List<MarineWarning> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(rssUrl)
                .header("User-Agent", "Pursi/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()

            // Hae CAP-URL:t RSS-syötteestä
            val capUrls = parseRssForCapUrls(body)

            // Haeparsii CAP-XML jokaiselle varoitukselle (rajoitetaan max 10)
            val warnings = capUrls.take(10).mapNotNull { capUrl ->
                val capXml = fetchCapXml(capUrl) ?: return@mapNotNull null
                parseCapXml(capXml, language)
            }

            // Suodata merelliset varoitukset
            warnings.filter { isMarineWarning(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseRssForCapUrls(rss: String): List<String> {
        val urls = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(rss))

            var eventType = parser.eventType
            var inItem = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "item") inItem = true
                        if (parser.name == "link" && inItem) {
                            val url = parser.nextText().trim()
                            if (url.contains("/cap/") && !url.contains("CANCEL")) {
                                urls.add(url)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") inItem = false
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) { }
        return urls
    }

    private fun fetchCapXml(url: String): String? {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Pursi/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (_: Exception) { null }
    }

    private fun parseCapXml(xml: String, language: String): MarineWarning? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            parseCapAlert(parser, language)
        } catch (_: Exception) { null }
    }

    private fun parseCapAlert(parser: XmlPullParser, language: String): MarineWarning? {
        var event: String? = null
        var eventCode: String? = null
        var severity: String? = null
        var headline: String? = null
        var description: String? = null
        var onset: String? = null
        var expires: String? = null
        var category: String? = null
        var polygonCoords: String? = null
        var centroidLat: Double? = null
        var centroidLon: Double? = null

        val langPrefixes = when (language) {
            "sv" -> listOf("sv-SE", "sv", "en-US", "en")
            "en" -> listOf("en-US", "en", "sv-SE", "sv")
            else -> listOf(language, "en-US", "en", "sv")
        }

        var currentInfoLang = ""
        var inInfo = false
        var inArea = false
        var inPolygon = false

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "info" -> { inInfo = true; currentInfoLang = "" }
                            "area" -> if (inInfo) inArea = true
                            "polygon" -> if (inArea) inPolygon = true
                            "language" -> if (inInfo) currentInfoLang = parser.nextText().trim()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        val parentTag = parser.name ?: ""
                        when {
                            parentTag == "category" && !inInfo -> category = text
                            parentTag == "event" && inInfo -> {
                                if (event == null && currentInfoLang in langPrefixes) {
                                    event = text
                                }
                            }
                            parentTag == "severity" && inInfo -> {
                                if (severity == null && currentInfoLang in langPrefixes) {
                                    severity = text
                                }
                            }
                            parentTag == "headline" && inInfo -> {
                                if (headline == null && currentInfoLang in langPrefixes) {
                                    headline = text
                                }
                            }
                            parentTag == "description" && inInfo -> {
                                if (description == null && currentInfoLang in langPrefixes) {
                                    description = text
                                }
                            }
                            parentTag == "onset" && inInfo -> onset = text
                            parentTag == "expires" && inInfo -> expires = text
                            parentTag == "valueName" && !inInfo -> {
                                if (text == "type" || text == "eventCode") eventCode = text
                            }
                            parentTag == "value" && !inInfo -> {
                                if (eventCode == null) eventCode = text
                                else eventCode = text // overwrite with actual code
                            }
                            inPolygon && parentTag == "polygon" -> {
                                polygonCoords = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "polygon" -> inPolygon = false
                            "area" -> inArea = false
                            "info" -> inInfo = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) { }

        if (event == null) return null

        // Laske centroid polygonista
        if (polygonCoords != null) {
            val pairs = polygonCoords.trim().split("\\s+".toRegex())
            var latSum = 0.0; var lonSum = 0.0; var count = 0
            for (pair in pairs) {
                val parts = pair.split(",")
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull() ?: 0.0
                    val lon = parts[1].toDoubleOrNull() ?: 0.0
                    latSum += lat; lonSum += lon; count++
                }
            }
            if (count > 0) {
                centroidLat = latSum / count
                centroidLon = lonSum / count
            }
        }

        val color = mapSeverityToColor(severity ?: "Unknown")

        return MarineWarning(
            event = event,
            eventCode = eventCode ?: "",
            severity = severity ?: "Unknown",
            color = color,
            description = description ?: event,
            headline = headline ?: event,
            areaDesc = headline ?: event,
            onset = formatTimestamp(onset) ?: "",
            expires = formatTimestamp(expires) ?: "",
            windSpeedMs = null,
            windDirectionDeg = null,
            centroidLat = centroidLat,
            centroidLon = centroidLon,
            polygonCoords = polygonCoords ?: ""
        )
    }

    private fun isMarineWarning(warning: MarineWarning): Boolean {
        val marineKeywords = listOf("sea", "wind", "wave", "gale", "storm", "coastal",
            "maritime", "high water", "flood", "shipping", "ship", "boat", "harbour",
            "harbor", "port", "fairway", "nav")
        val text = "${warning.event} ${warning.description} ${warning.headline} ${warning.eventCode}"
        return marineKeywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun mapSeverityToColor(severity: String): String = when {
        severity.contains("Extreme", ignoreCase = true) -> "red"
        severity.contains("Severe", ignoreCase = true) -> "orange"
        severity.contains("Moderate", ignoreCase = true) -> "yellow"
        severity.contains("Minor", ignoreCase = true) -> "yellow"
        else -> "grey"
    }

    private fun formatTimestamp(ts: String?): String? {
        if (ts == null) return null
        return try { dateFormat.format(dateFormat.parse(ts) ?: return ts) } catch (_: Exception) { ts }
    }
}
