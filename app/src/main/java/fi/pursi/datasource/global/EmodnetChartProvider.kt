package fi.pursi.datasource.global

import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.core.ChartLayer
import fi.pursi.datasource.core.ChartProvider

class EmodnetChartProvider : ChartProvider {
    override val providerId = "global-emodnet-bathy"
    override val displayName = "EMODnet Bathymetry"
    override val attribution = "EMODnet (CC BY 4.0)"
    override val coverage = BoundingBox(11.0, 90.0, -70.5, 43.0) // Euroopan meret ~125 m resoluutio
    override val priority = -1
    override val supportsOfflineCache = false

    override val layers = listOf(
        ChartLayer(
            id = "emodnet-depth",
            layerId = "layer-emodnet-depth",
            name = "Syvyysmalli (EMODnet)",
            tileUrl = "https://tiles.emodnet-bathymetry.eu/v11/mean_atlas_land/web_mercator/{z}/{x}/{y}.png",
            minZoom = 4f,
            subdir = "emodnet_depth"
        )
    )
}
