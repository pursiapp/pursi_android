package app.pursi.map.weather

import app.pursi.weather.MarineWarning
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object WarningPolygonOverlay {

    private const val SOURCE_ID = "pursi-warnings"
    private const val FILL_LAYER_ID = "layer-warnings-fill"
    private const val OUTLINE_LAYER_ID = "layer-warnings-outline"

    fun update(style: Style, warnings: List<MarineWarning>) {
        val features = warnings.mapNotNull { warning ->
            val coords = parsePolygon(warning.polygonCoords) ?: return@mapNotNull null
            val fillColor = when (warning.color) {
                "red" -> "#D50000"
                "orange" -> "#FF6F00"
                "yellow" -> "#FFB300"
                else -> "#9E9E9E"
            }
            val fillOpacity = when (warning.color) {
                "red" -> 0.30f
                "orange" -> 0.25f
                "yellow" -> 0.20f
                else -> 0.15f
            }

            Feature.fromGeometry(Polygon.fromLngLats(listOf(coords))).apply {
                addStringProperty("fillColor", fillColor)
                addNumberProperty("fillOpacity", fillOpacity.toDouble())
                addStringProperty("outlineColor", fillColor)
                addStringProperty("event", warning.event)
                addStringProperty("eventCode", warning.eventCode)
                addStringProperty("headline", warning.headline)
                addStringProperty("areaDesc", warning.areaDesc)
                addStringProperty("onset", warning.onset)
                addStringProperty("expires", warning.expires)
                warning.windSpeedMs?.let { addNumberProperty("windSpeedMs", it.toDouble()) }
                warning.windDirectionDeg?.let { addNumberProperty("windDir", it.toDouble()) }
            }
        }

        val existingSource = style.getSource(SOURCE_ID) as? GeoJsonSource
        if (existingSource != null) {
            if (features.isEmpty()) {
                remove(style)
                return
            }
            existingSource.setGeoJson(FeatureCollection.fromFeatures(features))
            return
        }

        if (features.isEmpty()) return

        val source = GeoJsonSource(SOURCE_ID)
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        style.addSource(source)

        val fillLayer = FillLayer(FILL_LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.fillColor(Expression.get("fillColor")),
                PropertyFactory.fillOpacity(Expression.get("fillOpacity"))
            )
        }
        style.addLayerBelow(fillLayer, "layer-openseamap")

        val outlineLayer = LineLayer(OUTLINE_LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineColor(Expression.get("outlineColor")),
                PropertyFactory.lineOpacity(0.8f)
            )
        }
        style.addLayerAbove(outlineLayer, FILL_LAYER_ID)
    }

    fun remove(style: Style) {
        try { style.removeLayer(OUTLINE_LAYER_ID) } catch (_: Exception) {}
        try { style.removeLayer(FILL_LAYER_ID) } catch (_: Exception) {}
        try { style.removeSource(SOURCE_ID) } catch (_: Exception) {}
    }

    private fun parsePolygon(coords: String): List<Point>? {
        if (coords.isBlank()) return null
        val points = coords.trim().split("\\s+".toRegex()).mapNotNull { c ->
            val parts = c.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lon = parts[1].trim().toDoubleOrNull()
                if (lat != null && lon != null) Point.fromLngLat(lon, lat) else null
            } else null
        }
        return if (points.size >= 3) points else null
    }
}
