package app.pursi.datasource.fi

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.WarningProvider
import app.pursi.weather.MarineWarning
import javax.inject.Inject

class FintrafficSeaWarningsProvider @Inject constructor(
    private val client: FintrafficSeaWarningsClient
) : WarningProvider {
    override val providerId = "fi-fintraffic-seawarnings"
    override val displayName = "Fintraffic merivaroitukset"
    override val coverage = BoundingBox(58.5, 70.5, 19.0, 32.0)
    override val supportedLanguages = listOf("fi", "sv", "en")
    override val priority = 3

    override suspend fun getMarineWarnings(
        language: String, latitude: Double, longitude: Double
    ): List<MarineWarning> {
        val all = client.fetchAll()
        val now = System.currentTimeMillis()
        return all
            .filter { isActive(it, now) }
            .map { toMarineWarning(it, language) }
    }

    private fun isActive(w: SeaWarning, nowMs: Long): Boolean {
        val start = w.validityStartEpochMs ?: return true
        val end = w.validityEndEpochMs
        return nowMs >= start && (end == null || nowMs <= end)
    }

    private fun toMarineWarning(w: SeaWarning, lang: String): MarineWarning {
        val sev = severity(w.validityStartEpochMs, w.validityEndEpochMs)
        val col = color(sev)
        val now = System.currentTimeMillis()
        val expiresStr = w.validityEndEpochMs?.let {
            val remaining = it - now
            when {
                remaining <= 0 -> "Päättynyt"
                remaining < 60_000L -> "Päättyy hetkenä minä hyvänsä"
                remaining < 3_600_000L -> "Päättyy ${remaining / 60_000L} min kuluttua"
                remaining < 86_400_000L -> "Päättyy ${remaining / 3_600_000L} h kuluttua"
                else -> "Päättyy ${remaining / 86_400_000L} pv kuluttua"
            }
        } ?: ""

        val content = when (lang) {
            "sv" -> w.contentSv
            "en" -> w.contentEn
            else -> w.contentFi
        }.ifEmpty { w.contentFi }

        val area = when (lang) {
            "sv" -> w.locationSv
            "en" -> w.locationEn
            else -> w.locationFi
        }.ifEmpty { w.locationFi }

        val type = when (lang) {
            "sv" -> w.typeSv
            "en" -> w.typeEn
            else -> w.typeFi
        }.ifEmpty { w.typeFi }

        return MarineWarning(
            event = type,
            eventCode = "sea",
            severity = sev,
            color = col,
            description = content.replace("\n", " "),
            headline = "$area - $type",
            areaDesc = area,
            onset = "",
            expires = expiresStr,
            centroidLat = w.centroidLat,
            centroidLon = w.centroidLon,
            source = "Fintraffic",
            validityStartEpochMs = w.validityStartEpochMs,
            validityEndEpochMs = w.validityEndEpochMs,
            publishedEpochMs = w.publishedEpochMs,
            polygonCoords = if (w.geometryType.contains("Polygon", ignoreCase = true)) w.geometry else ""
        )
    }

    private fun severity(start: Long?, end: Long?): String {
        val now = System.currentTimeMillis()
        if (end != null && end - now < 3_600_000L && end > now) return "Extreme"
        if (end != null || start != null) return "Severe"
        return "Moderate"
    }

    private fun color(severity: String): String {
        return when (severity) {
            "Extreme" -> "red"
            "Severe" -> "orange"
            else -> "yellow"
        }
    }
}
