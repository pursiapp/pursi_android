package fi.pursi.datasource.core

import fi.pursi.data.model.WfsFeature
import fi.pursi.ui.viewmodel.SeamarkDetail
import org.maplibre.geojson.Feature

data class LayerDefinition(
    val layerId: String,
    val sourceId: String,
    val type: LayerType,
    val styleProperties: Map<String, Any> = emptyMap()
)

enum class LayerType {
    SYMBOL, LINE, FILL
}

interface FeatureRenderer {
    fun canRender(featureType: String, providerId: String): Boolean

    fun toMapLibreFeature(feature: WfsFeature): Feature?

    fun getLayerDefinition(featureType: String): LayerDefinition?

    fun handleClick(feature: WfsFeature): SeamarkDetail?
}
