package fi.pursi.datasource.core

data class ChartLayer(
    val id: String,
    val layerId: String,
    val name: String,
    val tileUrl: String,
    val minZoom: Float,
    val maxZoom: Float = Float.MAX_VALUE,
    val subdir: String,
    val styleSource: Boolean = false
)

interface ChartProvider {
    val providerId: String
    val displayName: String
    val attribution: String
    val coverage: BoundingBox
    val layers: List<ChartLayer>
    val supportsOfflineCache: Boolean
        get() = true
    val needsTileServer: Boolean
        get() = false
    val priority: Int
        get() = 0
}
