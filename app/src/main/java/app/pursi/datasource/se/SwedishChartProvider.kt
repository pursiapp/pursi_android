package app.pursi.datasource.se

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.ChartLayer
import app.pursi.datasource.core.ChartProvider

/**
 * Sjöfartsverket (Swedish Maritime Administration) publishes its
 * nautical chart data under CC0 license, but the WMTS/WMS online
 * tile service is a paid product.
 *
 * The free CC0 data is available as S-57 / GeoJSON / Shapefile downloads:
 * https://www.sjofartsverket.se/sv/tjanster/sjokortsprodukter/digital-data/
 *
 * To use Swedish charts in Pursi, we would need to either:
 *   - Host our own tile server from the free S-57 data
 *   - Use Sjöfartsverket's paid WMTS (requires license fee)
 *
 * This provider is a template for when a suitable WMTS endpoint becomes
 * available. Do NOT enable until a verified free WMTS URL is configured.
 */
class SwedishChartProvider : ChartProvider {
    override val providerId = "se-sjofartsverket"
    override val displayName = "Sjöfartsverket"
    override val attribution = "Sjöfartsverket CC0"
    override val coverage = BoundingBox(55.0, 69.0, 10.0, 24.0)

    override val layers = listOf(
        ChartLayer(
            id = "se-enc",
            layerId = "layer-se-enc",
            name = "Sjökort",
            tileUrl = "PLACEHOLDER — no free WMTS endpoint available",
            minZoom = 4f,
            subdir = "se_enc"
        )
    )
}
