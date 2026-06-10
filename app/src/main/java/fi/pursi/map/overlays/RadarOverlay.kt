package fi.pursi.map.overlays

import fi.pursi.datasource.core.RadarProvider
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

object RadarOverlay {
    fun update(style: Style, provider: RadarProvider, url: String, opacity: Float = 0.7f) {
        val srcId = "radar-${provider.providerId}"
        val layerId = "radar-${provider.providerId}"

        OverlayUtils.safeRemoveLayer(style, layerId)
        OverlayUtils.safeRemoveSource(style, srcId)

        val tileSet = TileSet("xyz", url)
        tileSet.attribution = provider.attribution
        tileSet.maxZoom = provider.maxZoom
        style.addSource(RasterSource(srcId, tileSet, 256))
        val rasterLayer = RasterLayer(layerId, srcId)
        rasterLayer.setMinZoom(provider.minZoom)
        rasterLayer.setProperties(
            PropertyFactory.rasterOpacity(opacity),
            PropertyFactory.rasterResampling("linear")
        )
        style.addLayerBelow(rasterLayer, "layer-seamark-bottom")
    }

    fun remove(style: Style) {
        for (pid in listOf("fi-fmi-radar", "global-rainviewer")) {
            OverlayUtils.safeRemoveLayer(style, "radar-$pid")
            OverlayUtils.safeRemoveSource(style, "radar-$pid")
        }
    }
}
