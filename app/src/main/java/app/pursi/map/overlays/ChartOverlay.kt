package app.pursi.map.overlays

import app.pursi.datasource.core.ChartProvider
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

object ChartOverlay {
    private const val OPENSEAMAP_SOURCE_ID = "openseamap"
    private const val OPENSEAMAP_LAYER_ID = "layer-openseamap"
    private const val OPENSEAMAP_SUBDIR = "openseamap"
    private const val OPENSEAMAP_MINZOOM = 4f

    fun updateLayers(
        style: Style,
        chartProviders: List<ChartProvider>,
        allRegisteredProviders: List<ChartProvider>,
        offlineMode: Boolean,
        tilesDirPath: String?,
        chartOpacity: Float,
        proxyBaseUrl: String?
    ) {
        for (provider in allRegisteredProviders) {
            for (l in provider.layers) {
                val prefixedLayerId = "${provider.providerId}-${l.layerId}"
                val prefixedSrcId = "${provider.providerId}-${l.id}"
                OverlayUtils.safeRemoveLayer(style, prefixedLayerId)
                OverlayUtils.safeRemoveLayer(style, "$prefixedLayerId-offline")
                OverlayUtils.safeRemoveSource(style, prefixedSrcId)
                OverlayUtils.safeRemoveSource(style, "$prefixedSrcId-offline")
            }
            OverlayUtils.safeRemoveLayer(style, "${provider.providerId}-composite")
            OverlayUtils.safeRemoveSource(style, "${provider.providerId}-composite")
        }

        if (tilesDirPath != null) {
            val osDir = java.io.File(tilesDirPath, OPENSEAMAP_SUBDIR)
            if (osDir.exists() && osDir.listFiles()?.isNotEmpty() == true) {
                OverlayUtils.safeRemoveLayer(style, OPENSEAMAP_LAYER_ID)
                OverlayUtils.safeRemoveSource(style, OPENSEAMAP_SOURCE_ID)
                val ts = TileSet("xyz", "file://$tilesDirPath/$OPENSEAMAP_SUBDIR/{z}/{y}/{x}.png")
                ts.attribution = "© OpenSeaMap contributors"
                style.addSource(RasterSource(OPENSEAMAP_SOURCE_ID, ts, 256))
                val osLayer = RasterLayer(OPENSEAMAP_LAYER_ID, OPENSEAMAP_SOURCE_ID)
                osLayer.setProperties(PropertyFactory.rasterOpacity(0.7f), PropertyFactory.rasterResampling("nearest"))
                osLayer.setMinZoom(OPENSEAMAP_MINZOOM)
                style.addLayerBelow(osLayer, "layer-seamark-bottom")
            }
        }

        val osmBaseId = "fallback-osm-base"
        val osmSeamarkId = "fallback-osm-seamark"
        OverlayUtils.safeRemoveLayer(style, osmBaseId)
        OverlayUtils.safeRemoveSource(style, osmBaseId)
        val osmTs = TileSet("xyz", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")
        osmTs.attribution = "© OpenStreetMap contributors"
        style.addSource(RasterSource(osmBaseId, osmTs, 256))
        val osmLayer = RasterLayer(osmBaseId, osmBaseId)
        osmLayer.setProperties(
            PropertyFactory.rasterResampling("linear"),
            PropertyFactory.rasterOpacity(chartOpacity)
        )
        osmLayer.setMinZoom(2f)
        style.addLayerBelow(osmLayer, "layer-seamark-bottom")

        OverlayUtils.safeRemoveLayer(style, osmSeamarkId)
        OverlayUtils.safeRemoveSource(style, osmSeamarkId)
        val osmSeaTs = TileSet("xyz", "https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png")
        osmSeaTs.attribution = "© OpenSeaMap contributors"
        style.addSource(RasterSource(osmSeamarkId, osmSeaTs, 256))
        val osmSeaLayer = RasterLayer(osmSeamarkId, osmSeamarkId)
        osmSeaLayer.setProperties(
            PropertyFactory.rasterResampling("nearest"),
            PropertyFactory.rasterOpacity(chartOpacity)
        )
        osmSeaLayer.setMinZoom(4f)
        style.addLayerBelow(osmSeaLayer, "layer-seamark-bottom")

        val chartRefLayer = "layer-seamark-bottom"
        for (provider in chartProviders) {
            if (provider.needsTileServer && !offlineMode && proxyBaseUrl != null) {
                val srcId = "${provider.providerId}-composite"
                val layerId = "${provider.providerId}-composite"
                val tileSet = TileSet("xyz", proxyBaseUrl)
                tileSet.attribution = provider.attribution
                style.addSource(RasterSource(srcId, tileSet, 256))
                val rasterLayer = RasterLayer(layerId, srcId)
                rasterLayer.setProperties(
                    PropertyFactory.rasterResampling("nearest"),
                    PropertyFactory.rasterOpacity(chartOpacity)
                )
                rasterLayer.setMinZoom(4f)
                style.addLayerBelow(rasterLayer, chartRefLayer)
            } else {
                for (l in provider.layers) {
                    val url = when {
                        tilesDirPath == null -> l.tileUrl
                        java.io.File(tilesDirPath, "${l.subdir}").exists() ->
                            "file://$tilesDirPath/${l.subdir}/{z}/{y}/{x}.png"
                        java.io.File(tilesDirPath, provider.providerId).exists() ->
                            "file://$tilesDirPath/${provider.providerId}/{z}/{y}/{x}.png"
                        else -> l.tileUrl
                    }
                    val srcId = "${provider.providerId}-${l.id}" + (if (offlineMode) "-offline" else "")
                    val layerId = "${provider.providerId}-${l.layerId}" + (if (offlineMode) "-offline" else "")
                    val tileSet = TileSet("xyz", url)
                    tileSet.attribution = provider.attribution
                    try {
                        style.addSource(RasterSource(srcId, tileSet, 256))
                        val rasterLayer = RasterLayer(layerId, srcId)
                        rasterLayer.setProperties(
                            PropertyFactory.rasterResampling("nearest"),
                            PropertyFactory.rasterOpacity(chartOpacity)
                        )
                        rasterLayer.setMinZoom(l.minZoom)
                        if (l.maxZoom < Float.MAX_VALUE) {
                            rasterLayer.setMaxZoom(l.maxZoom)
                        }
                        style.addLayerBelow(rasterLayer, chartRefLayer)
                    } catch (e: Exception) {
                        android.util.Log.e("ChartOverlay", "Failed to add layer $layerId: ${e.message}")
                    }
                }
            }
        }

        // When a local chart provider is active (e.g. Traficom in Finland,
        // Kartverket in Norway), hide the global OpenSeaMap seamark raster.
        // It was added as a fallback earlier, but now that we know a local
        // chart covers this area we should not show both — they visually
        // double-up at mid-opacity slider positions.
        if (chartProviders.isNotEmpty()) {
            style.getLayer(osmSeamarkId)?.setProperties(
                PropertyFactory.rasterOpacity(0f)
            )
        }
    }

    fun updateNightMode(
        style: Style,
        allRegisteredProviders: List<ChartProvider>,
        isNightMode: Boolean,
        chartOpacity: Float
    ) {
        val brightMin = PropertyFactory.rasterBrightnessMin(0.0f)
        val brightMax = if (isNightMode) PropertyFactory.rasterBrightnessMax(0.55f) else PropertyFactory.rasterBrightnessMax(1.0f)
        val contrast = if (isNightMode) PropertyFactory.rasterContrast(0.25f) else PropertyFactory.rasterContrast(0.0f)
        val saturation = if (isNightMode) PropertyFactory.rasterSaturation(-0.4f) else PropertyFactory.rasterSaturation(0.0f)

        for (baseLayerId in listOf("layer-openseamap", "fallback-osm-base", "fallback-osm-seamark")) {
            val layer = style.getLayer(baseLayerId)
            if (layer is RasterLayer) {
                layer.setProperties(brightMin, brightMax, contrast, saturation)
            }
        }

        for (provider in allRegisteredProviders) {
            for (l in provider.layers) {
                val layerId = "${provider.providerId}-${l.layerId}"
                val layer = style.getLayer(layerId)
                if (layer is RasterLayer) {
                    layer.setProperties(brightMin, brightMax, contrast, saturation)
                }
                val offlineLayerId = "$layerId-offline"
                val offlineLayer = style.getLayer(offlineLayerId)
                if (offlineLayer is RasterLayer) {
                    offlineLayer.setProperties(brightMin, brightMax, contrast, saturation)
                }
            }
            val compositeLayerId = "${provider.providerId}-composite"
            val compositeLayer = style.getLayer(compositeLayerId)
            if (compositeLayer is RasterLayer) {
                compositeLayer.setProperties(brightMin, brightMax, contrast, saturation)
            }
        }
    }

    fun updateSeamarkVisibility(style: Style, seamarkLayerIds: List<String>, chartOpacity: Float) {
        // The OSM seamark layers (DYNAMIC_icon_*) and WFS layers are no longer
        // toggled by chart opacity; they are always visible unless a VV feature
        // overrides them via setDynamicIconVisibility. This function now only
        // toggles the seamark raster fallback layers that are passed via
        // seamarkLayerIds, if any caller still needs that.
        val shouldHide = chartOpacity > 0.85f
        val v = if (shouldHide) Property.NONE else Property.VISIBLE
        for (id in seamarkLayerIds) {
            style.getLayer(id)?.setProperties(
                PropertyFactory.visibility(v)
            )
        }
    }

    /**
     * Set the visibility of the OSM seamark icon layers (DYNAMIC_icon_fixed_rotation
     * and DYNAMIC_icon_free_rotation) based on whether VV features are in view AND
     * the chart opacity.
     *
     * Call this from both the turvalaite and the notice render path so we have a
     * single source of truth for the icon-layer visibility. The DYNAMIC_icon layers
     * contain OSM seamark icons of many types (tuvalaite, notice, light, daymark, …).
     * When any VV feature is in view the OSM icons should be hidden (we show the
     * more accurate VV data instead of the OSM copy). At full-raster the OSM icons
     * are also hidden; they would overlap the chart's own seamark raster.
     */
    internal fun setDynamicIconVisibility(
        style: Style,
        hideForTurvalaite: Boolean,
        hideForNotice: Boolean
    ) {
        val hide = hideForTurvalaite || hideForNotice
        val visibility = if (hide) Property.NONE else Property.VISIBLE
        for (layerId in listOf("DYNAMIC_icon_fixed_rotation", "DYNAMIC_icon_free_rotation")) {
            style.getLayer(layerId)?.setProperties(
                PropertyFactory.visibility(visibility)
            )
        }
    }

    fun getSeamarkLayerIds(style: Style): List<String> {
        return style.layers
            .filter { it.id.startsWith("seamark_") || it.id.startsWith("DYNAMIC_icon") }
            .map { it.id }
    }
}
