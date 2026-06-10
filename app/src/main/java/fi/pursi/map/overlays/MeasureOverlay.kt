package fi.pursi.map.overlays

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

object MeasureOverlay {
    private const val MEASURE_SRC = "measure-line"
    private const val MEASURE_LAYER = "layer-measure-line"
    private const val POI_SRC = "poi-marker"
    private const val POI_LAYER = "layer-poi-marker"

    fun updateMeasureLine(style: Style, map: MapLibreMap, points: Pair<LatLng, LatLng>?) {
        if (points == null) {
            OverlayUtils.safeRemoveLayer(style, MEASURE_LAYER)
            OverlayUtils.safeRemoveSource(style, MEASURE_SRC)
            return
        }

        val (p1, p2) = points

        val proj = map.projection
        val sp1 = proj.toScreenLocation(org.maplibre.android.geometry.LatLng(p1.latitude, p1.longitude))
        val sp2 = proj.toScreenLocation(org.maplibre.android.geometry.LatLng(p2.latitude, p2.longitude))
        val dx = sp2.x - sp1.x
        val dy = sp2.y - sp1.y
        val len = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
        val capPixels = 20f
        val nx = -dy / len * capPixels
        val ny = dx / len * capPixels

        val cap1a = proj.fromScreenLocation(android.graphics.PointF(sp1.x - nx, sp1.y - ny))
        val cap1b = proj.fromScreenLocation(android.graphics.PointF(sp1.x + nx, sp1.y + ny))
        val cap2a = proj.fromScreenLocation(android.graphics.PointF(sp2.x - nx, sp2.y - ny))
        val cap2b = proj.fromScreenLocation(android.graphics.PointF(sp2.x + nx, sp2.y + ny))

        val features = listOf(
            Feature.fromGeometry(LineString.fromLngLats(listOf(
                Point.fromLngLat(p1.longitude, p1.latitude),
                Point.fromLngLat(p2.longitude, p2.latitude)
            ))),
            Feature.fromGeometry(LineString.fromLngLats(listOf(
                Point.fromLngLat(cap1a.longitude, cap1a.latitude),
                Point.fromLngLat(cap1b.longitude, cap1b.latitude)
            ))),
            Feature.fromGeometry(LineString.fromLngLats(listOf(
                Point.fromLngLat(cap2a.longitude, cap2a.latitude),
                Point.fromLngLat(cap2b.longitude, cap2b.latitude)
            )))
        )

        val existingSrc = style.getSource(MEASURE_SRC) as? GeoJsonSource
        if (existingSrc != null) {
            existingSrc.setGeoJson(FeatureCollection.fromFeatures(features))
        } else {
            val src = GeoJsonSource(MEASURE_SRC)
            src.setGeoJson(FeatureCollection.fromFeatures(features))
            style.addSource(src)
            style.addLayerAbove(org.maplibre.android.style.layers.LineLayer(MEASURE_LAYER, MEASURE_SRC).apply {
                setProperties(
                    PropertyFactory.lineWidth(2f),
                    PropertyFactory.lineColor("#FF6F00"),
                    PropertyFactory.lineOpacity(0.9f)
                )
            }, "layer-openseamap")
        }
    }

    fun updatePoiMarker(style: Style, marker: LatLng?) {
        OverlayUtils.safeRemoveLayer(style, POI_LAYER)
        OverlayUtils.safeRemoveSource(style, POI_SRC)

        val pos = marker ?: return
        val src = GeoJsonSource(POI_SRC)
        src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(pos.longitude, pos.latitude)))
        style.addSource(src)
        style.addLayerAbove(
            org.maplibre.android.style.layers.CircleLayer(POI_LAYER, POI_SRC).apply {
                setProperties(
                    PropertyFactory.circleRadius(10f),
                    PropertyFactory.circleColor("#D32F2F"),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleOpacity(0.9f)
                )
            }, "layer-openseamap"
        )
    }
}
