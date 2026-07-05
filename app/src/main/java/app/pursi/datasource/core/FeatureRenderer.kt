package app.pursi.datasource.core

import app.pursi.data.model.WfsFeature
import app.pursi.ui.viewmodel.SeamarkDetail
import org.maplibre.geojson.Feature

data class LayerDefinition(
    val layerId: String,
    val sourceId: String,
    val type: LayerType,
    val styleProperties: Map<String, Any> = emptyMap(),
    val minZoom: Float? = null
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
