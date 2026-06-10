package fi.pursi.datasource.fi

import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.core.RadarProvider
import fi.pursi.datasource.core.RadarTileUrl
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class FmiRadarProvider @Inject constructor() : RadarProvider {
    override val providerId = "fi-fmi-radar"
    override val displayName = "Ilmatieteen laitos"
    override val attribution = "© Ilmatieteen laitos"
    override val coverage = BoundingBox(59.0, 70.5, 19.0, 31.0)
    override val priority = 1
    override val minZoom = 1.0f
    override val maxZoom = 14.0f

    override suspend fun getRadarTileUrl(timeOffsetMinutes: Int): RadarTileUrl? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.MINUTE, -timeOffsetMinutes)
        cal.add(Calendar.MINUTE, -(cal.get(Calendar.MINUTE) % 5))
        cal.add(Calendar.MINUTE, -5)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return RadarTileUrl(buildWmsUrl(cal), effectiveDelayMinutes = timeOffsetMinutes + 5)
    }

    override val maxHistoryMinutes = 60

    private fun buildWmsUrl(cal: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timeParam = "&TIME=${sdf.format(cal.time)}"

        return "https://openwms.fmi.fi/geoserver/ows?SERVICE=WMS&VERSION=1.3.0" +
            "&REQUEST=GetMap&LAYERS=Radar:suomi_rr_eureffin&STYLES=" +
            "&CRS=EPSG:3857&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256" +
            "&FORMAT=image/png&TRANSPARENT=true$timeParam"
    }
}
