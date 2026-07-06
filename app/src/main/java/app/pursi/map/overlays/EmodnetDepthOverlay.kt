package app.pursi.map.overlays

import app.pursi.data.model.EmodnetDepthSample
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object EmodnetDepthOverlay {
    private const val SOURCE_ID = "emodnet-depth-samples"
    private const val LAYER_ID = "layer-emodnet-depth"

    fun setupLayer(style: Style) {
        if (style.getSource(SOURCE_ID) != null) return

        val source = GeoJsonSource(SOURCE_ID)
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        style.addSource(source)

        val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.textField("{depth}"),
                PropertyFactory.textSize(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(14, Expression.literal(11f)),
                        Expression.stop(16, Expression.literal(15f))
                    )
                ),
                PropertyFactory.textColor("#1A237E"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(2.0f),
                PropertyFactory.textAllowOverlap(false),
                PropertyFactory.textIgnorePlacement(true)
            )
            minZoom = 13.0f
        }

        try {
            style.addLayerAbove(layer, "layer-openseamap")
        } catch (_: Exception) {
            style.addLayer(layer)
        }
    }

    fun updateSamples(style: Style, samples: List<EmodnetDepthSample>) {
        val source = style.getSource(SOURCE_ID) as? GeoJsonSource ?: return

        val features = samples.map { sample ->
            val point = Point.fromLngLat(sample.longitude, sample.latitude)
            val feature = Feature.fromGeometry(point)
            feature.addStringProperty("depth", "%.1f".format(sample.depthAvgM))
            feature
        }

        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun remove(style: Style) {
        try { style.removeLayer(LAYER_ID) } catch (_: Exception) { }
        try { style.removeSource(SOURCE_ID) } catch (_: Exception) { }
    }
}
