package app.pursi.datasource.core

data class RadarTileUrl(
    val url: String,
    val effectiveDelayMinutes: Int = 0
)

interface RadarProvider {
    val providerId: String
    val displayName: String
    val attribution: String
    val coverage: BoundingBox
    val priority: Int
        get() = 0

    val minZoom: Float
        get() = 1.0f

    val maxZoom: Float
        get() = 14.0f

    suspend fun getRadarTileUrl(timeOffsetMinutes: Int): RadarTileUrl?

    val maxHistoryMinutes: Int
        get() = 60

    /**
     * Invalidate any provider-internal cache (timestamp lists, capabilities, etc.).
     * Called by [WeatherRepository] on network-restore. Default no-op so providers
     * without cache don't need to override.
     */
    fun refreshCache() {}
}
