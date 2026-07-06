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
    private const val LIGHTNING_ICON = "lightning"

    fun update(style: Style, strikes: List<LightningStrike>, nowEpoch: Long = System.currentTimeMillis() / 1000) {
        if (strikes.isEmpty()) {
            remove(style)
            return
        }

        val features = strikes.mapNotNull { strike ->
            val ageMin = (nowEpoch - strike.epochTimestamp) / 60L
            if (ageMin >= 60) return@mapNotNull null
            val color = when {
                ageMin < 5 -> "#FF0000"
                ageMin < 30 -> "#FF8800"
                else -> "#FFCC00"
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
                PropertyFactory.iconImage(LIGHTNING_ICON),
                PropertyFactory.iconColor(Expression.get("color")),
                PropertyFactory.iconSize(
                    Expression.interpolate(Expression.linear(), Expression.zoom(),
                        Expression.stop(6, Expression.literal(0.5f)),
                        Expression.stop(10, Expression.literal(0.8f)),
                        Expression.stop(14, Expression.literal(1.2f)))
                ),
                PropertyFactory.iconOpacity(
                    Expression.interpolate(Expression.linear(), Expression.get("ageMin"),
                        Expression.stop(0, Expression.literal(1.0f)),
                        Expression.stop(60, Expression.literal(0.0f)))
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor("center")
            )
        }
        style.addLayerAbove(layer, "layer-openseamap")
    }

    fun remove(style: Style) {
        try { style.removeLayer(LAYER_ID) } catch (_: Exception) {}
        try { style.removeSource(SOURCE_ID) } catch (_: Exception) {}
    }
}
