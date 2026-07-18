package app.pursi.datasource.fi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

object AlgaeSatelliteCapabilities {

    @Volatile
    private var cachedTimeIso: String? = null

    @Volatile
    private var lastFetchMs: Long = 0L

    private const val CACHE_MS = 60L * 60L * 1000L

    private const val CAPABILITIES_URL =
        "https://geoserver2.ymparisto.fi/geoserver/eo/ows?SERVICE=WMS&REQUEST=GetCapabilities"

    suspend fun latestTimeIso(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && cachedTimeIso != null && now - lastFetchMs < CACHE_MS) {
            return@withContext cachedTimeIso
        }
        try {
            val url = URL(CAPABILITIES_URL)
            val xml = BufferedReader(InputStreamReader(url.openStream())).readText()

            // Find EO_HR_WQ_S2_ALGAE layer section, then parse its Dimension time list
            val nameTag = "<Name>EO_HR_WQ_S2_ALGAE</Name>"
            val nameIdx = xml.indexOf(nameTag)
            if (nameIdx < 0) return@withContext cachedTimeIso

            val layerEnd = xml.indexOf("</Layer>", nameIdx)
            if (layerEnd < 0) return@withContext cachedTimeIso

            val layerSection = xml.substring(nameIdx, layerEnd)
            val dimRe = Regex("""<Dimension\s+name="time"[^>]*>\s*([^<]+)</Dimension>""")
            val dimMatch = dimRe.find(layerSection)

            if (dimMatch != null) {
                val times = dimMatch.groupValues[1].split(",").map { it.trim() }
                val latest = times.lastOrNull()
                if (latest != null) {
                    cachedTimeIso = latest
                    lastFetchMs = System.currentTimeMillis()
                    return@withContext latest
                }
            }
        } catch (_: Exception) { }
        cachedTimeIso
    }

    fun clearCache() {
        cachedTimeIso = null
        lastFetchMs = 0L
    }
}
