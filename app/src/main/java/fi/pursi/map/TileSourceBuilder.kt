package fi.pursi.map

import fi.pursi.datasource.core.ChartProvider

object TileSourceBuilder {
    fun buildFromProviders(
        providers: List<ChartProvider>,
        extraSources: List<TileSource> = emptyList()
    ): List<TileSource> {
        val fromProviders = providers.flatMap { provider ->
            val layerNames = provider.layers.map { it.name }
            val desc = layerNames.joinToString(", ")
            val covName = when {
                provider.coverage.maxLat >= 85f -> "Maailmanlaajuinen"
                else -> provider.displayName
            }
            provider.layers.map { layer ->
                TileSource(
                    providerId = provider.providerId,
                    displayName = provider.displayName,
                    urlTemplate = layer.tileUrl,
                    extension = "png",
                    minZoom = layer.minZoom.toInt(),
                    maxZoom = if (layer.maxZoom < Float.MAX_VALUE) layer.maxZoom.toInt() else 14,
                    avgTileBytes = 25_000L,
                    coverageName = covName,
                    description = desc,
                    category = "chart",
                    coverage = provider.coverage
                )
            }
        }
        return fromProviders + extraSources
    }
}
