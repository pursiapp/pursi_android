package app.pursi.map.weather

import app.pursi.weather.LightningStrike
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object LightningOverlay {

    private const val SOURCE_ID = "pursi-lightning"
    private const val LAYER_ID = "layer-lightning"
    private const val LIGHTNING_SYMBOL = "⚡"

    fun update(style: Style, strikes: List<LightningStrike>, nowEpoch: Long = System.currentTimeMillis() / 1000) {
        if (strikes.isEmpty()) {
            remove(style)
            return
        }

        val features = strikes.mapNotNull { strike ->
            val ageMin = (nowEpoch - strike.epochTimestamp) / 60L
            val color = when {
                ageMin < 5 -> "#FF0000"
                ageMin < 30 -> "#FF8800"
                ageMin < 60 -> "#FFCC00"
                else -> return@mapNotNull null
            }

            Feature.fromGeometry(Point.fromLngLat(strike.longitude, strike.latitude)).apply {
                addStringProperty("color", color)
                addNumberProperty("ageMin", ageMin.toDouble())
            }
        }

        if (features.isEmpty()) {
            remove(style)
            return
        }

        val existingSource = style.getSource(SOURCE_ID) as? GeoJsonSource
        if (existingSource != null) {
            existingSource.setGeoJson(FeatureCollection.fromFeatures(features))
            return
        }

        val source = GeoJsonSource(SOURCE_ID)
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        style.addSource(source)

        val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.textField(LIGHTNING_SYMBOL),
                PropertyFactory.textColor(Expression.get("color")),
                PropertyFactory.textSize(
                    Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(6, Expression.literal(10f)),
                        Expression.stop(10, Expression.literal(16f)),
                        Expression.stop(14, Expression.literal(22f)))
                ),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textAnchor("center")
            )
        }
        style.addLayerAbove(layer, "layer-openseamap")
    }

    fun remove(style: Style) {
        try { style.removeLayer(LAYER_ID) } catch (_: Exception) {}
        try { style.removeSource(SOURCE_ID) } catch (_: Exception) {}
    }
}
