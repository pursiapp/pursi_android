package app.pursi.weather

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

class FmiClient @Inject constructor(
    private val client: OkHttpClient
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val wfsBase = "https://opendata.fmi.fi/wfs"
    private val tsBase = "https://opendata.fmi.fi/timeseries"

    suspend fun getNearestWeatherStations(
        latitude: Double, longitude: Double, maxStations: Int = 5
    ): List<StationWeatherData> = withContext(Dispatchers.IO) {
        try {
            val xml = fetchXml("$wfsBase?request=GetFeature&storedquery_id=fmi::observations::weather::multipointcoverage" +
                "&bbox=${longitude - 1},${latitude - 1},${longitude + 1},${latitude + 1}&maxfeatures=$maxStations&format=text/xml")
            parseStationData(xml, latitude, longitude, maxStations)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun getWaveObservations(
        latitude: Double, longitude: Double
    ): List<WaveStation> = withContext(Dispatchers.IO) {
        try {
            val xml = fetchXml("$wfsBase?request=GetFeature&storedquery_id=fmi::observations::wave::multipointcoverage" +
                "&bbox=${longitude - 2},${latitude - 2},${longitude + 2},${latitude + 2}&maxfeatures=3&format=text/xml")
            parseWaveData(xml, latitude, longitude)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun getForecast(
        latitude: Double, longitude: Double
    ): List<ForecastPoint> = withContext(Dispatchers.IO) {
        try {
            // Compute endtime 5 days from now in ISO format
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, 5)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val endtime = sdf.format(cal.time)
            val json = fetchUrl("$tsBase?latlon=$latitude,$longitude&param=t2m,FF,DD,Pressure,WeatherSymbol,Cloudiness,Precipitation1h&format=json&endtime=$endtime")
            parseForecastJson(json)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun getMareographData(
        latitude: Double, longitude: Double
    ): List<WaterLevelStation> = withContext(Dispatchers.IO) {
        try {
            val xml = fetchXml("$wfsBase?request=GetFeature&storedquery_id=fmi::observations::mareograph::multipointcoverage" +
                "&bbox=${longitude - 2},${latitude - 2},${longitude + 2},${latitude + 2}&maxfeatures=3&format=text/xml")
            parseMareographData(xml, latitude, longitude)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun getLightningData(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double
    ): List<LightningStrike> = withContext(Dispatchers.IO) {
        try {
            val xml = fetchXml("$wfsBase?request=GetFeature&storedquery_id=fmi::observations::lightning::multipointcoverage" +
                "&bbox=$minLng,$minLat,$maxLng,$maxLat&format=text/xml")
            parseLightning(xml)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun getMarineWarnings(language: String = "fi", latitude: Double = 0.0, longitude: Double = 0.0): List<MarineWarning> = withContext(Dispatchers.IO) {
        try {
            val langSuffix = when (language) { "sv" -> "sv-FI"; "en" -> "en-GB"; else -> "fi-FI" }
            val xml = fetchUrl("https://alerts.fmi.fi/cap/feed/atom_$langSuffix.xml")
            parseCapWarnings(xml, latitude, longitude, langSuffix)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    private fun fetchXml(url: String): String = fetchUrl(url)

    private fun fetchUrl(url: String): String {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} from $url")
            }
            resp.body?.string() ?: throw IOException("Empty response from $url")
        }
    }

    // ── Station weather (WFS) ──────────────────────────────────────────

    private fun parseStationData(xml: String, refLat: Double, refLon: Double, max: Int): List<StationWeatherData> {
        val names = mutableListOf<String>()
        val fieldRe = Regex("""<swe:field\s+name="([^"]+)"""")
        for (m in fieldRe.findAll(xml)) names.add(m.groupValues[1])

        // Station names + positions from pointMembers
        val stationMap = mutableMapOf<String, DoubleArray>() // name -> [lat, lon]
        val stationFmisid = mutableMapOf<String, String>()   // name -> stationId
        val ptRe = Regex(
            """<gml:pointMember>\s*<gml:Point[^>]*>\s*<gml:name[^>]*>([^<]+)</gml:name>\s*<gml:pos>([\d.]+)\s+([\d.]+)""",
            RegexOption.MULTILINE
        )
        for (m in ptRe.findAll(xml)) {
            stationMap[m.groupValues[1].trim()] = doubleArrayOf(
                m.groupValues[2].toDouble(), m.groupValues[3].toDouble()
            )
        }
        // station IDs from target:Location identifiers
        val idRe = Regex(
            """<target:Location[^>]*>.*?<gml:identifier[^>]*>(\d+)</gml:identifier>.*?<gml:name[^>]*>([^<]+)</gml:name>""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (m in idRe.findAll(xml)) {
            val name = m.groupValues[2].trim()
            if (name in stationMap) stationFmisid[name] = m.groupValues[1]
        }

        // Positions block
        val posBlock = Regex("""<gmlcov:positions>([\s\S]*?)</gmlcov:positions>""")
            .find(xml)?.groupValues?.get(1)?.trim() ?: return emptyList()
        // Values block
        val valBlock = Regex("""<gml:doubleOrNilReasonTupleList>([\s\S]*?)</gml:doubleOrNilReasonTupleList>""")
            .find(xml)?.groupValues?.get(1)?.trim() ?: return emptyList()

        val posLines = posBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val valLines = valBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (posLines.size != valLines.size || posLines.isEmpty()) return emptyList()

        // Group all entries by (lat, lon)
        val groups = mutableMapOf<Pair<Double, Double>, MutableList<Pair<Long, List<Float>>>>()
        for (i in posLines.indices) {
            val pp = posLines[i].split("\\s+".toRegex())
            if (pp.size < 3) continue
            val lat = pp[0].toDoubleOrNull() ?: continue
            val lon = pp[1].toDoubleOrNull() ?: continue
            val ts = pp[2].toLongOrNull() ?: continue
            val vals = valLines[i].split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            if (vals.isNotEmpty()) {
                // Merge with previous entry for this station: keep earlier values when current is NaN
                val list = groups.getOrPut(Pair(lat, lon)) { mutableListOf() }
                if (list.isNotEmpty()) {
                    val prev = list.last().second
                    val merged = vals.mapIndexed { idx, v ->
                        if (v.isNaN()) prev.getOrNull(idx) ?: v else v
                    }
                    list.add(Pair(ts, merged))
                } else {
                    list.add(Pair(ts, vals))
                }
            }
        }

        // Build per-station data
        val result = mutableListOf<StationWeatherData>()
        for ((key, entries) in groups) {
            val (lat, lon) = key
            val sorted = entries.sortedBy { it.first }

            // Find station name
            var stName = "Station"
            var minD = Double.MAX_VALUE
            for ((n, p) in stationMap) {
                val d = haversine(p[0], p[1], lat, lon)
                if (d < minD) { minD = d; stName = n }
            }

            val stationId = stationFmisid[stName] ?: ""
            val dist = haversine(refLat, refLon, lat, lon) / 1000.0
            val history = sorted.map { (ts, vals) ->
                val fields = mapValues(names, vals)
                val t = fields[0]; val ws = fields[1]; val wd = fields[2]
                val wg = fields[3]; val rh = fields[4]
                val pr = fields[9]; val vi = fields[10]; val wawa = fields[12]; val sea = fields[13]
                WeatherObservation(
                    stationName = stName, stationId = stationId,
                    stationLatitude = lat, stationLongitude = lon,
                    timestamp = formatTimestamp(ts), distanceKm = dist.toFloat(),
                    temperatureC = t, windSpeedMs = ws, windDirectionDeg = wd,
                    windGustMs = wg, humidityPercent = rh, pressureHPa = pr, visibilityM = vi,
                    weatherCode = wawa?.toInt(), seaLevelM = sea
                )
            }

            val latest = history.lastOrNull() ?: continue
            val recent = history.takeLast(12) // ~last 2 hours (10min intervals)
            result.add(StationWeatherData(station = latest, history = recent))
        }

        return result.sortedBy { haversine(refLat, refLon, it.station.stationLatitude, it.station.stationLongitude) }.take(max)
    }

    private fun mapValues(names: List<String>, vals: List<Float>): Array<Float?> {
        val out = arrayOfNulls<Float>(14)
        for ((i, name) in names.withIndex()) {
            val v = vals.getOrNull(i) ?: continue
            if (v.isNaN() || v.isInfinite()) continue
            when (name) {
                "t2m" -> out[0] = v
                "ws_10min" -> out[1] = v
                "wd_10min" -> out[2] = v
                "wg_10min" -> out[3] = v
                "rh" -> out[4] = v
                "td" -> out[5] = v
                "r_1h" -> out[6] = v
                "ri_10min" -> out[7] = v
                "snow_aws" -> out[8] = v
                "p_sea" -> out[9] = v
                "vis" -> out[10] = v
                "n_man" -> out[11] = v
                "wawa" -> out[12] = v
                "water_level", "sea_level", "sea_level_(n2000)" -> out[13] = v
            }
        }
        return out
    }

    // ── Wave data (WFS) ───────────────────────────────────────────────

    private fun parseWaveData(xml: String, refLat: Double, refLon: Double): List<WaveStation> {
        val fieldRe = Regex("""<swe:field\s+name="([^"]+)"""")
        val names = mutableListOf<String>()
        for (m in fieldRe.findAll(xml)) names.add(m.groupValues[1])

        val posBlock = Regex("""<gmlcov:positions>([\s\S]*?)</gmlcov:positions>""")
            .find(xml)?.groupValues?.get(1)?.trim() ?: return emptyList()
        val valBlock = Regex("""<gml:doubleOrNilReasonTupleList>([\s\S]*?)</gml:doubleOrNilReasonTupleList>""")
            .find(xml)?.groupValues?.get(1)?.trim() ?: return emptyList()

        val groups = mutableMapOf<Pair<Double, Double>, Pair<Long, List<Float>>>()
        val posLines = posBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val valLines = valBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (posLines.size != valLines.size) return emptyList()

        for (i in posLines.indices) {
            val pp = posLines[i].split("\\s+".toRegex())
            if (pp.size < 3) continue
            val lat = pp[0].toDoubleOrNull() ?: continue
            val lon = pp[1].toDoubleOrNull() ?: continue
            val ts = pp[2].toLongOrNull() ?: continue
            val vals = valLines[i].split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            val key = Pair(lat, lon)
            val existing = groups[key]
            if (existing == null || ts > existing.first) {
                // Merge: keep previous non-NaN values for fields that are NaN now
                val merged = if (existing != null) {
                    vals.mapIndexed { idx, v ->
                        if (v.isNaN()) existing.second.getOrNull(idx) ?: v else v
                    }
                } else vals
                groups[key] = Pair(ts, merged)
            }
        }

        // Point member names
        val stationNames = mutableMapOf<Pair<Double, Double>, String>()
        val ptRe = Regex(
            """<gml:pointMember>\s*<gml:Point[^>]*>\s*<gml:name[^>]*>([^<]+)</gml:name>\s*<gml:pos>([\d.]+)\s+([\d.]+)""",
            RegexOption.MULTILINE
        )
        for (m in ptRe.findAll(xml)) {
            val lat = m.groupValues[2].toDouble()
            val lon = m.groupValues[3].toDouble()
            stationNames[Pair(lat, lon)] = m.groupValues[1].trim()
        }

        val result = mutableListOf<WaveStation>()
        for ((key, data) in groups) {
            val (lat, lon) = key
            val name = stationNames[key] ?: "Buoy"
            val values = data.second
            var waveH: Float? = null; var waveD: Float? = null
            var waterT: Float? = null; var waveP: Float? = null
            for ((i, n) in names.withIndex()) {
                val v = values.getOrNull(i) ?: continue
                if (v.isNaN()) continue
                when (n) { "WaveHs" -> waveH = v; "ModalWDi" -> waveD = v; "TWATER" -> waterT = v; "WTP" -> waveP = v }
            }
            result.add(WaveStation(name, lat, lon, formatTimestamp(data.first), waveH, waveD, waterT, waveP))
        }
        return result.sortedBy { haversine(refLat, refLon, it.latitude, it.longitude) }.take(3)
    }

    // ── Mareograph (sea level) data (WFS) ─────────────────────────────

    private fun parseMareographData(xml: String, refLat: Double, refLon: Double): List<WaterLevelStation> {
        val fieldRe = Regex("""<swe:field\s+name="([^"]+)"""")
        val names = mutableListOf<String>()
        for (m in fieldRe.findAll(xml)) names.add(m.groupValues[1])

        val posBlock = Regex("""<gmlcov:positions>([\s\S]*?)</gmlcov:positions>""")
            .find(xml)?.groupValues?.get(1)?.trim() ?: return emptyList()
        val valBlock = Regex("""<gml:doubleOrNilReasonTupleList>([\s\S]*?)</gml:doubleOrNilReasonTupleList>""")
            .find(xml)?.groupValues?.get(1)?.trim() ?: return emptyList()

        val posLines = posBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val valLines = valBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (posLines.size != valLines.size) return emptyList()

        // Group by (lat, lon) keeping the latest entry
        val groups = mutableMapOf<Pair<Double, Double>, Pair<Long, List<Float>>>()
        for (i in posLines.indices) {
            val pp = posLines[i].split("\\s+".toRegex())
            if (pp.size < 3) continue
            val lat = pp[0].toDoubleOrNull() ?: continue
            val lon = pp[1].toDoubleOrNull() ?: continue
            val ts = pp[2].toLongOrNull() ?: continue
            val vals = valLines[i].split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            val key = Pair(lat, lon)
            val existing = groups[key]
            if (existing == null || ts > existing.first) {
                groups[key] = Pair(ts, vals)
            }
        }

        // Station names from point members
        val stationNames = mutableMapOf<Pair<Double, Double>, String>()
        val ptRe = Regex(
            """<gml:pointMember>\s*<gml:Point[^>]*>\s*<gml:name[^>]*>([^<]+)</gml:name>\s*<gml:pos>([\d.]+)\s+([\d.]+)""",
            RegexOption.MULTILINE
        )
        for (m in ptRe.findAll(xml)) {
            val lat = m.groupValues[2].toDouble()
            val lon = m.groupValues[3].toDouble()
            stationNames[Pair(lat, lon)] = m.groupValues[1].trim()
        }

        val result = mutableListOf<WaterLevelStation>()
        for ((key, data) in groups) {
            val (lat, lon) = key
            val name = stationNames[key] ?: "Mareograph"
            val values = data.second
            var waterLevel: Float? = null
            for ((i, n) in names.withIndex()) {
                val v = values.getOrNull(i) ?: continue
                if (v.isNaN()) continue
                if (n == "WATLEV") waterLevel = v / 10f // mm → cm (teoreettinen keskivesi MW)
            }
            val dist = haversine(refLat, refLon, lat, lon) / 1000.0
            result.add(WaterLevelStation(name, lat, lon, formatTimestamp(data.first), waterLevel, dist.toFloat()))
        }
        return result.sortedBy { haversine(refLat, refLon, it.latitude, it.longitude) }.take(3)
    }

    @Serializable
    private data class ForecastEntry(
        val t2m: Float? = null,
        val FF: Float? = null,
        val DD: Float? = null,
        val Pressure: Float? = null,
        val WeatherSymbol: Float? = null,
        val Cloudiness: Float? = null,
        val Precipitation1h: Float? = null
    )

    // ── Forecast (timeseries JSON) ─────────────────────────────────────

    private fun parseForecastJson(jsonStr: String): List<ForecastPoint> {
        val entries = json.decodeFromString<List<ForecastEntry>>(jsonStr)
        val now = System.currentTimeMillis() / 1000
        return entries.mapIndexed { i, e ->
            ForecastPoint(
                timestamp = now + i * 3600,
                temperatureC = e.t2m,
                windSpeedMs = e.FF,
                windDirectionDeg = e.DD,
                pressureHPa = e.Pressure,
                weatherSymbol = e.WeatherSymbol,
                cloudiness = e.Cloudiness,
                precipitationMm = e.Precipitation1h
            )
        }
    }

    // ── CAP Warnings (Atom feed) ────────────────────────────────────────

    private fun parseCapWarnings(xml: String, refLat: Double, refLon: Double, language: String): List<MarineWarning> {
        val warnings = mutableListOf<MarineWarning>()
        try {
            // Extract entry blocks using simple string splitting (Atom entries don't nest)
            val entries = xml.split("</entry>")
            for (entryBlock in entries) {
                val entryStart = entryBlock.indexOf("<entry")
                if (entryStart == -1) continue
                val entryXml = entryBlock.substring(entryStart) + "</entry>"

                // Extract content XML
                val contentStart = entryXml.indexOf("<content")
                if (contentStart == -1) continue
                val contentTagEnd = entryXml.indexOf(">", contentStart)
                if (contentTagEnd == -1) continue
                val contentEnd = entryXml.lastIndexOf("</content>")
                if (contentEnd == -1 || contentEnd <= contentTagEnd) continue
                val capXml = entryXml.substring(contentTagEnd + 1, contentEnd).trim()

                val entry = parseCapEntry(capXml, language)
                if (entry != null) {
                    warnings.add(entry)
                }
            }
        } catch (_: Exception) { }

        return warnings.sortedWith(
            compareBy<MarineWarning> { w ->
                when {
                    w.polygonCoords.isNotEmpty() && pointInPolygon(refLat, refLon, w.polygonCoords) -> 0
                    w.centroidLat != null && w.centroidLon != null &&
                        haversine(refLat, refLon, w.centroidLat, w.centroidLon) < 100_000.0 -> 1
                    w.centroidLat != null && w.centroidLon != null -> 2
                    else -> 3
                }
            }.thenBy { w ->
                if (w.centroidLat != null && w.centroidLon != null)
                    haversine(refLat, refLon, w.centroidLat, w.centroidLon)
                else Double.MAX_VALUE
            }
        )
    }

    private fun parseCapEntry(capXml: String, language: String): MarineWarning? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(capXml))

            var eventCode = ""
            var event = ""
            var severity = ""
            var description = ""
            var headline = ""
            var onset = ""
            var expires = ""
            var areaDescs = mutableListOf<String>()
            var color = "yellow"
            var windSpeed: Float? = null
            var windDir: Float? = null
            var polygonCoords = ""
            var currentValueName = ""
            var valueNameTagDepth = 0
            var currentSection = ""
            var currentElement = ""
            var skip = false

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        currentElement = parser.name ?: ""
                        when (currentElement) {
                            "eventCode" -> if (!skip) currentSection = "eventCode"
                            "parameter" -> if (!skip) currentSection = "parameter"
                            "info" -> { currentSection = "info"; skip = true }
                            "area" -> if (!skip) currentSection = "area"
                            "event" -> if (!skip) event = ""
                            "severity" -> if (!skip) severity = ""
                            "description" -> if (!skip) description = ""
                            "headline" -> if (!skip) headline = ""
                            "onset" -> if (!skip) onset = ""
                            "expires" -> if (!skip) expires = ""
                            "areaDesc" -> Unit
                            "polygon" -> if (!skip) polygonCoords = ""
                            "valueName" -> if (!skip) { currentValueName = ""; valueNameTagDepth++ }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when (currentElement) {
                                "language" -> {
                                    if (text.equals(language, ignoreCase = true)) skip = false
                                }
                                else -> if (!skip) {
                                    when (currentElement) {
                                        "event" -> event = text
                                        "severity" -> severity = text
                                        "description" -> description = text
                                        "headline" -> headline = text
                                        "onset" -> onset = text
                                        "expires" -> expires = text
                                        "areaDesc" -> areaDescs.add(text)
                                        "polygon" -> polygonCoords = text
                                        "valueName" -> currentValueName = text
                                        "value" -> {
                                            if (currentSection == "eventCode") {
                                                eventCode = text
                                            } else {
                                                when (currentValueName) {
                                                    "color" -> color = text
                                                    "windIntensity" -> windSpeed = text.toFloatOrNull()
                                                    "windDirection" -> windDir = text.toFloatOrNull()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "valueName") valueNameTagDepth--
                        else if (parser.name == "eventCode" || parser.name == "parameter" || parser.name == "info" || parser.name == "area") {
                            currentSection = ""
                        }
                    }
                }
                parser.next()
            }

            val areaDesc = areaDescs.joinToString(", ")

            if (!eventCode.startsWith("sea")) return null

            // Parse polygon centroid
            var centroidLat: Double? = null
            var centroidLon: Double? = null
            if (polygonCoords.isNotEmpty()) {
                val coords = polygonCoords.trim().split("\\s+".toRegex())
                var sumLat = 0.0; var sumLon = 0.0; var count = 0
                for (c in coords) {
                    val parts = c.split(",")
                    if (parts.size >= 2) {
                        val lat = parts[0].toDoubleOrNull() ?: continue
                        val lon = parts[1].toDoubleOrNull() ?: continue
                        sumLat += lat; sumLon += lon; count++
                    }
                }
                if (count > 0) { centroidLat = sumLat / count; centroidLon = sumLon / count }
            }

            MarineWarning(
                event = event, eventCode = eventCode, severity = severity,
                color = color, description = description, headline = headline,
                areaDesc = areaDesc, onset = onset, expires = expires,
                windSpeedMs = windSpeed, windDirectionDeg = windDir,
                centroidLat = centroidLat, centroidLon = centroidLon,
                polygonCoords = polygonCoords
            )
        } catch (_: Exception) { null }
    }

    // ── Lightning ──────────────────────────────────────────────────────

    private fun parseLightning(xml: String): List<LightningStrike> {
        val strikes = mutableListOf<LightningStrike>()
        try {
            val m = Regex("""<gmlcov:positions>([\s\S]*?)</gmlcov:positions>""").find(xml) ?: return emptyList()
            for (line in m.groupValues[1].trim().lines()) {
                val p = line.trim().split("\\s+".toRegex())
                if (p.size >= 3) {
                    val lat = p[0].toDoubleOrNull() ?: continue
                    val lon = p[1].toDoubleOrNull() ?: continue
                    val ts = p[2].toLongOrNull() ?: 0L
                    strikes.add(LightningStrike(
                        latitude = lat, longitude = lon, timestamp = formatTimestamp(ts), epochTimestamp = ts
                    ))
                }
            }
        } catch (_: Exception) {}
        return strikes
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun formatTimestamp(unix: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getDefault()
        return sdf.format(java.util.Date(unix * 1000L))
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2.0).pow(2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0).pow(2.0)
        return R * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
    }

    private fun Double.pow(e: Double): Double = Math.pow(this, e)
}

/** Ray casting point-in-polygon test.
 *  [polygonCoords] format: "lat1,lon1 lat2,lon2 ... latN,lonN lat1,lon1" */
internal fun pointInPolygon(lat: Double, lon: Double, polygonCoords: String): Boolean {
    val coords = polygonCoords.trim().split("\\s+".toRegex())
        .mapNotNull { c ->
            val parts = c.split(",")
            if (parts.size >= 2) {
                val pl = parts[0].toDoubleOrNull()
                val pn = parts[1].toDoubleOrNull()
                if (pl != null && pn != null) Pair(pl, pn) else null
            } else null
        }
    if (coords.size < 3) return false
    var inside = false
    var j = coords.size - 1
    for (i in coords.indices) {
        val (latI, lonI) = coords[i]
        val (latJ, lonJ) = coords[j]
        val dLat = latJ - latI
        if (kotlin.math.abs(dLat) < 1e-10) continue
        if ((latI > lat) != (latJ > lat) &&
            lon < (lonJ - lonI) * (lat - latI) / dLat + lonI
        ) inside = !inside
        j = i
    }
    return inside
}
