package fi.pursi.datasource.global

import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.core.RadarProvider
import fi.pursi.datasource.core.RadarTileUrl
import javax.inject.Inject

class RainViewerRadarProvider @Inject constructor(
    private val timestampSource: RainViewerTimestampSource
) : RadarProvider {
    override val providerId = "global-rainviewer"
    override val displayName = "RainViewer"
    override val attribution = "© RainViewer"
    override val coverage = BoundingBox.WORLD
    override val priority = -1
    override val minZoom = 1.0f
    override val maxZoom = 7.0f

    override suspend fun getRadarTileUrl(timeOffsetMinutes: Int): RadarTileUrl? {
        val now = System.currentTimeMillis() / 1000
        val targetTime = now - (timeOffsetMinutes.toLong() * 60)
        val (_, path) = timestampSource.getNearestFrame(targetTime) ?: return null
        return RadarTileUrl("https://tilecache.rainviewer.com$path/256/{z}/{x}/{y}/2/1_1.png")
    }

    override val maxHistoryMinutes = 120
}
