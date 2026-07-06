package app.pursi.datasource.fi

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.RadarProvider
import app.pursi.datasource.core.RadarTileUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class FmiRadarProvider @Inject constructor(
    private val capabilities: FmiRadarCapabilities
) : RadarProvider {
    override val providerId = "fi-fmi-radar"
    override val displayName = "Ilmatieteen laitos"
    override val attribution = "© Ilmatieteen laitos"
    override val coverage = BoundingBox(59.0, 70.5, 19.0, 31.0)
    override val priority = 1
    override val minZoom = 1.0f
    override val maxZoom = 14.0f

    override val maxHistoryMinutes = 60

    override suspend fun getRadarTileUrl(timeOffsetMinutes: Int): RadarTileUrl? = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000
        // Cache-bust: MapLibre keys its native tile cache by URL. Keeping
        // TIME=current means the URL would be byte-identical on every refresh
        // tick and return stale tiles forever. Appending a 5-min-grid token
        // makes the URL unique per published frame; FMI WMS ignores unknown
        // query params, so the request still resolves to the actual latest frame.
        val cacheBust = (nowSec / 300L).toString()

        if (timeOffsetMinutes == 0) {
            // Live: FMI's `current` resolves to the actual latest-published frame.
            val latest = capabilities.latestAvailableEpochSec()
            val delay = if (latest != null) {
                ((nowSec - latest).coerceAtLeast(0L) / 60L).toInt().coerceAtLeast(0)
            } else {
                5
            }
            return@withContext RadarTileUrl(
                buildWmsUrl("current") + "&_pursi=$cacheBust",
                effectiveDelayMinutes = delay
            )
        }

        // History: request a frame at (latestAvailable - offset), floored to 5 min.
        val latest = capabilities.latestAvailableEpochSec() ?: return@withContext null
        val targetSec = floorTo5Min(latest - (timeOffsetMinutes.toLong() * 60L))
        val delayMinutes = ((nowSec - targetSec) / 60L).toInt().coerceAtLeast(0)
        val iso = formatIso(targetSec * 1000L)
        RadarTileUrl(
            buildWmsUrl(iso) + "&_pursi=$cacheBust",
            effectiveDelayMinutes = delayMinutes
        )
    }

    override fun refreshCache() {
        capabilities.clearCache()
    }

    private fun floorTo5Min(epochSec: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochSec * 1000L
        cal.set(Calendar.MINUTE, (cal.get(Calendar.MINUTE) / 5) * 5)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    private fun formatIso(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(epochMs)
    }

    private fun buildWmsUrl(timeParam: String): String =
        "https://openwms.fmi.fi/geoserver/ows?SERVICE=WMS&VERSION=1.3.0" +
            "&REQUEST=GetMap&LAYERS=Radar:suomi_rr_eureffin&STYLES=" +
            "&CRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256" +
            "&FORMAT=image/png&TRANSPARENT=true&TIME=$timeParam"
}
