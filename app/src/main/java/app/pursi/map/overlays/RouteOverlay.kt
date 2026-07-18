package app.pursi.map.overlays

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

object RouteOverlay {
    private const val PLANNING_SRC = "route-planning"
    private const val PLANNING_LINE = "layer-route-planning"
    private const val PLANNING_DOTS = "layer-route-dots"
    private const val RECORDING_SRC = "recording-trail"
    private const val RECORDING_LAYER = "layer-recording-trail"
    private const val SAVED_SRC = "saved-routes"
    private const val SAVED_DOTS_SRC = "saved-routes-dots"
    private const val SAVED_LINE = "saved-routes-line"
    private const val SAVED_DOTS = "saved-routes-dots"

    fun updatePlanning(style: Style, waypoints: List<org.maplibre.android.geometry.LatLng>, dragIndex: Int? = null) {
        OverlayUtils.safeRemoveLayers(style, PLANNING_LINE, PLANNING_DOTS)
        OverlayUtils.safeRemoveSources(style, PLANNING_SRC, "$PLANNING_SRC-dots")

        if (waypoints.size >= 2) {
            val linePoints = waypoints.map { Point.fromLngLat(it.longitude, it.latitude) }
            val features = mutableListOf<Feature>()
            features.add(Feature.fromGeometry(LineString.fromLngLats(linePoints)))

            val src = GeoJsonSource(PLANNING_SRC)
            src.setGeoJson(FeatureCollection.fromFeatures(features))
            style.addSource(src)

            val lineLayer = LineLayer(PLANNING_LINE, PLANNING_SRC).apply {
                setProperties(
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineColor("#1565C0"),
                    PropertyFactory.lineOpacity(0.8f),
                    PropertyFactory.lineDasharray(arrayOf(4f, 2f))
                )
            }
            style.addLayerAbove(lineLayer, "layer-openseamap")
        }

        if (waypoints.size >= 1) {
            OverlayUtils.safeRemoveLayer(style, PLANNING_DOTS)
            OverlayUtils.safeRemoveSource(style, "$PLANNING_SRC-dots")
            val dotFeatures = waypoints.mapIndexed { i, p ->
                Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude))
                    .apply { addNumberProperty("order", i.toDouble()) }
            }
            val dotSrc = GeoJsonSource("$PLANNING_SRC-dots")
            dotSrc.setGeoJson(FeatureCollection.fromFeatures(dotFeatures))
            style.addSource(dotSrc)
            val dotsLayer = CircleLayer(PLANNING_DOTS, "$PLANNING_SRC-dots").apply {
                if (dragIndex != null) {
                    setProperties(
                        PropertyFactory.circleRadius(
                            Expression.switchCase(
                                Expression.eq(Expression.get("order"), Expression.literal(dragIndex.toDouble())),
                                Expression.literal(12f),
                                Expression.literal(6f)
                            )
                        ),
                        PropertyFactory.circleColor(
                            Expression.switchCase(
                                Expression.eq(Expression.get("order"), Expression.literal(dragIndex.toDouble())),
                                Expression.literal("#FF6F00"),
                                Expression.literal("#1565C0")
                            )
                        ),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor("#FFFFFF")
                    )
                } else {
                    setProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor("#1565C0"),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor("#FFFFFF")
                    )
                }
            }
            style.addLayerAbove(
                dotsLayer, PLANNING_LINE.let { if (style.getLayer(it) != null) it else "layer-openseamap" }
            )
        }
    }

    fun updateRecordingTrail(style: Style, trail: List<org.maplibre.android.geometry.LatLng>) {
        OverlayUtils.safeRemoveLayer(style, RECORDING_LAYER)
        OverlayUtils.safeRemoveSource(style, RECORDING_SRC)

        if (trail.size >= 2) {
            val linePoints = trail.map { Point.fromLngLat(it.longitude, it.latitude) }
            val features = listOf(
                Feature.fromGeometry(LineString.fromLngLats(linePoints))
            )
            val src = GeoJsonSource(RECORDING_SRC)
            src.setGeoJson(FeatureCollection.fromFeatures(features))
            style.addSource(src)
            val lineLayer = LineLayer(RECORDING_LAYER, RECORDING_SRC).apply {
                setProperties(
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineColor("#D32F2F"),
                    PropertyFactory.lineOpacity(0.8f),
                    PropertyFactory.lineDasharray(arrayOf(1f, 0f))
                )
            }
            style.addLayerAbove(lineLayer, "layer-openseamap")
        }
    }

    fun updateSavedRoutes(style: Style, routes: List<List<org.maplibre.android.geometry.LatLng>>) {
        OverlayUtils.safeRemoveLayers(style, SAVED_LINE, SAVED_DOTS)
        OverlayUtils.safeRemoveSources(style, SAVED_SRC, SAVED_DOTS_SRC)

        if (routes.isNotEmpty()) {
            val lineFeatures = routes.map { pts ->
                Feature.fromGeometry(LineString.fromLngLats(
                    pts.map { Point.fromLngLat(it.longitude, it.latitude) }
                ))
            }
            val src = GeoJsonSource(SAVED_SRC)
            src.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))
            style.addSource(src)
            style.addLayerAbove(LineLayer(SAVED_LINE, SAVED_SRC).apply {
                setProperties(PropertyFactory.lineWidth(2f), PropertyFactory.lineColor("#2E7D32"),
                    PropertyFactory.lineOpacity(0.7f), PropertyFactory.lineDasharray(arrayOf(4f, 3f)))
            }, "layer-openseamap")

            val dotFeatures = routes.flatMap { pts ->
                pts.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
            }
            val dotSrc = GeoJsonSource(SAVED_DOTS_SRC)
            dotSrc.setGeoJson(FeatureCollection.fromFeatures(dotFeatures))
            style.addSource(dotSrc)
            style.addLayerAbove(org.maplibre.android.style.layers.CircleLayer(SAVED_DOTS, SAVED_DOTS_SRC).apply {
                setProperties(PropertyFactory.circleRadius(4f), PropertyFactory.circleColor("#2E7D32"),
                    PropertyFactory.circleStrokeWidth(1.5f), PropertyFactory.circleStrokeColor("#FFFFFF"))
            }, SAVED_LINE)
        }
    }
}
