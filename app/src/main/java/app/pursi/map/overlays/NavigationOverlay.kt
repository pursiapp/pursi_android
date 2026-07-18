package app.pursi.map.overlays

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import app.pursi.ui.viewmodel.NavigationState

object NavigationOverlay {
    fun update(style: Style, navState: NavigationState, boatPos: LatLng?) {
        removeAll(style)
        if (!navState.isActive || navState.waypoints.isEmpty()) return

        val idx = navState.currentIndex
        val wps = navState.waypoints
        val passedWps = if (idx > 0) wps.subList(0, idx + 1) else emptyList()
        val upcomingWps = wps.subList(idx, wps.size)

        if (upcomingWps.size >= 2) {
            val linePoints = upcomingWps.map { Point.fromLngLat(it.longitude, it.latitude) }
            val src = GeoJsonSource("nav-upcoming")
            src.setGeoJson(FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(LineString.fromLngLats(linePoints)))
            ))
            style.addSource(src)
            style.addLayerAbove(LineLayer("layer-nav-upcoming", "nav-upcoming").apply {
                setProperties(
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineColor("#1565C0"),
                    PropertyFactory.lineOpacity(0.8f),
                    PropertyFactory.lineDasharray(arrayOf(4f, 2f))
                )
            }, "layer-openseamap")
        }

        if (passedWps.size >= 2) {
            val linePoints = passedWps.map { Point.fromLngLat(it.longitude, it.latitude) }
            val src = GeoJsonSource("nav-passed-line")
            src.setGeoJson(FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(LineString.fromLngLats(linePoints)))
            ))
            style.addSource(src)
            val ref = if (style.getLayer("layer-nav-upcoming") != null) "layer-nav-upcoming" else "layer-openseamap"
            style.addLayerAbove(LineLayer("layer-nav-passed-line", "nav-passed-line").apply {
                setProperties(
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineColor("#9E9E9E"),
                    PropertyFactory.lineOpacity(0.6f),
                    PropertyFactory.lineDasharray(arrayOf(4f, 2f))
                )
            }, ref)
        }

        val currentWp = wps[idx]
        if (boatPos != null) {
            val navPoints = listOf(
                Point.fromLngLat(boatPos.longitude, boatPos.latitude),
                Point.fromLngLat(currentWp.longitude, currentWp.latitude)
            )
            val navSrc = GeoJsonSource("nav-boat-line")
            navSrc.setGeoJson(FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(LineString.fromLngLats(navPoints)))
            ))
            style.addSource(navSrc)
            style.addLayerAbove(LineLayer("layer-nav-boat-line", "nav-boat-line").apply {
                setProperties(
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineColor("#FF6F00"),
                    PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.lineDasharray(arrayOf(6f, 4f))
                )
            }, "layer-openseamap")
        }

        fun renderDots(wps: List<LatLng>, color: String, tag: String) {
            if (wps.isEmpty()) return
            val srcId = "nav-dots-$tag"
            val layerId = "layer-nav-dots-$tag"
            val features = wps.map {
                Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
            }
            val src = GeoJsonSource(srcId)
            src.setGeoJson(FeatureCollection.fromFeatures(features))
            style.addSource(src)
            style.addLayerAbove(
                org.maplibre.android.style.layers.CircleLayer(layerId, srcId).apply {
                    setProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor(color),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor("#FFFFFF")
                    )
                },
                "layer-openseamap"
            )
        }

        renderDots(passedWps, "#9E9E9E", "passed")
        renderDots(upcomingWps, "#1565C0", "upcoming")
    }

    private fun removeAll(style: Style) {
        val layerIds = listOf(
            "layer-nav-upcoming", "layer-nav-passed-line", "layer-nav-boat-line",
            "layer-nav-dots-passed", "layer-nav-dots-upcoming"
        )
        val sourceIds = listOf(
            "nav-upcoming", "nav-passed-line", "nav-boat-line",
            "nav-dots-passed", "nav-dots-upcoming"
        )
        for (t in layerIds) OverlayUtils.safeRemoveLayer(style, t)
        for (t in sourceIds) OverlayUtils.safeRemoveSource(style, t)
    }
}
