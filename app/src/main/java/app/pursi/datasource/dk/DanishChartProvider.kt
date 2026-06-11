package app.pursi.datasource.dk

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.ChartLayer
import app.pursi.datasource.core.ChartProvider

class DanishChartProvider : ChartProvider {
    override val providerId = "dk-klimadata"
    override val displayName = "Klimatadatastyrelsen"
    override val attribution = "Klimatadatastyrelsen (CC BY 4.0)"
    override val coverage = BoundingBox(54.0, 58.0, 8.0, 15.0)
    override val priority = 2

    override val layers = listOf(
        ChartLayer(
            id = "dk-sokort",
            layerId = "layer-dk-sokort",
            name = "Søkort",
            tileUrl = "TODO: vaatii rekisteröitymisen (https://kortdata.klimatastyrelsen.dk)",
            minZoom = 4f,
            subdir = "dk_sokort"
        )
    )
}
