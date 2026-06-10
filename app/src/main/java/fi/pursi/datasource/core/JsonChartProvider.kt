package fi.pursi.datasource.core

class JsonChartProvider(
    val config: JsonChartConfig
) : ChartProvider {
    override val providerId = config.providerId
    override val displayName = config.displayName
    override val attribution = config.attribution
    override val coverage = config.coverage.toBoundingBox()
    override val layers = config.layers.map { layer ->
        ChartLayer(
            id = layer.id,
            layerId = layer.layerId,
            name = layer.name,
            tileUrl = layer.tileUrl,
            minZoom = layer.minZoom,
            maxZoom = layer.maxZoom,
            subdir = layer.subdir,
            styleSource = false
        )
    }
    override val supportsOfflineCache = config.supportsOfflineCache
    override val needsTileServer = config.needsTileServer
    override val priority = config.priority
}
