package app.pursi.map.overlays

import app.pursi.map.weather.LightningOverlay
import app.pursi.map.weather.WarningPolygonOverlay
import app.pursi.weather.LightningStrike
import app.pursi.weather.MarineWarning
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

object WeatherOverlay {
    fun updateLightning(style: Style, show: Boolean, strikes: List<LightningStrike>) {
        if (show) {
            LightningOverlay.update(style, strikes)
        } else {
            LightningOverlay.remove(style)
        }
    }

    fun updateWarnings(style: Style, show: Boolean, warnings: List<MarineWarning>) {
        if (show) {
            WarningPolygonOverlay.update(style, warnings)
        } else {
            WarningPolygonOverlay.remove(style)
        }
    }

    fun updateAlgaeSatellite(style: Style, show: Boolean) {
        val srcId = "algae-satellite"
        val layerId = "algae-satellite-layer"

        if (!show) {
            OverlayUtils.safeRemoveLayers(style, layerId,
                "water-obs-label-b", "water-obs-label-a",
                "water-obs-temp-b", "water-obs-temp-a",
                "water-obs-algae-b", "water-obs-algae-a")
            OverlayUtils.safeRemoveSources(style, srcId,
                "water-obs-label-src-b", "water-obs-label-src-a",
                "water-obs-b", "water-obs-a")
            return
        }

        OverlayUtils.safeRemoveLayer(style, layerId)
        OverlayUtils.safeRemoveSource(style, srcId)

        val tileSet = TileSet("xyz", "https://geoserver2.ymparisto.fi/geoserver/eo/wms?" +
            "SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&" +
            "FORMAT=image%2Fpng&TRANSPARENT=true&" +
            "LAYERS=eo:EO_MR_OLCI_ALGAE&" +
            "STYLES=&SRS=EPSG:3857&" +
            "WIDTH=256&HEIGHT=256&" +
            "BBOX={bbox-epsg-3857}")
        tileSet.attribution = "© SYKE (CC BY 4.0)"
        tileSet.minZoom = 5f
        tileSet.maxZoom = 12f

        style.addSource(RasterSource(srcId, tileSet, 256))
        val rasterLayer = RasterLayer(layerId, srcId)
        rasterLayer.setProperties(
            PropertyFactory.rasterOpacity(0.6f),
            PropertyFactory.rasterResampling("linear")
        )
        rasterLayer.setMinZoom(5f)
        style.addLayerBelow(rasterLayer, "layer-seamark-bottom")
    }
}
