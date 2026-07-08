package app.pursi.map.overlays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.pursi.map.SpriteCacheRegistry
import app.pursi.ui.viewmodel.BoatIconSize
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

object BoatOverlay {
    private const val BOAT_SOURCE = "pursi-boat"
    private const val BOAT_LAYER = "layer-boat"
    private const val COURSE_LAYER = "layer-course"
    private const val BOAT_ICON = "icon-boat"

    fun setupLayers(style: Style, boatIconSize: BoatIconSize, boatIconColor: String) {
        try { if (style.getSource(BOAT_SOURCE) != null) return } catch (_: IllegalStateException) { return }

        val boatBitmap = createBoatIcon(boatIconColor)
        SpriteCacheRegistry.track(boatBitmap, "boat-icon")
        style.addImage(BOAT_ICON, boatBitmap)

        val source = GeoJsonSource(BOAT_SOURCE)
        style.addSource(source)

        val courseLayer = LineLayer(COURSE_LAYER, BOAT_SOURCE).apply {
            setProperties(
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineColor("#1976D2"),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineDasharray(arrayOf(2f, 3f))
            )
        }
        style.addLayerBelow(courseLayer, "layer-openseamap")

        val courseLabelLayer = SymbolLayer("layer-course-labels", BOAT_SOURCE).apply {
            setFilter(Expression.has("courseLabel"))
            setProperties(
                PropertyFactory.textField(Expression.get("courseLabel")),
                PropertyFactory.textSize(10f),
                PropertyFactory.textColor("#1976D2"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textAnchor("left"),
                PropertyFactory.textOffset(arrayOf(0.5f, 0f))
            )
        }
        style.addLayerBelow(courseLabelLayer, "layer-boat-top")

        val boatLayer = SymbolLayer(BOAT_LAYER, BOAT_SOURCE)
        style.addLayerBelow(boatLayer, "layer-boat-top")

        refreshProperties(style, boatIconSize, boatIconColor)
    }

    fun refreshProperties(style: Style, boatIconSize: BoatIconSize, boatIconColor: String) {
        val layer = style.getLayer(BOAT_LAYER) as? SymbolLayer ?: return

        SpriteCacheRegistry.recycleByLabel("boat-icon")
        val newBitmap = createBoatIcon(boatIconColor)
        SpriteCacheRegistry.track(newBitmap, "boat-icon")
        style.addImage(BOAT_ICON, newBitmap)

        val expr = Expression.interpolate(Expression.linear(), Expression.zoom(),
            Expression.stop(5, Expression.literal(0.6f * boatIconSize.multiplier)),
            Expression.stop(10, Expression.literal(1.2f * boatIconSize.multiplier)),
            Expression.stop(15, Expression.literal(1.8f * boatIconSize.multiplier)))

        layer.setProperties(
            PropertyFactory.iconImage(BOAT_ICON),
            PropertyFactory.iconSize(expr),
            PropertyFactory.iconRotate(Expression.get("course")),
            PropertyFactory.iconRotationAlignment("map"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
        layer.setFilter(Expression.has("course"))
    }

    fun updateBoatAndCourse(
        style: Style,
        locationLat: Double,
        locationLon: Double,
        bearingDeg: Float?,
        speedMps: Float,
        courseLineMinutes: List<Int>
    ) {
        val course = bearingDeg ?: 0f
        val features = mutableListOf<Feature>()

        val boatPoint = Point.fromLngLat(locationLon, locationLat)
        val boatFeature = Feature.fromGeometry(boatPoint)
        boatFeature.addNumberProperty("course", course.toDouble())
        features.add(boatFeature)

        if (speedMps > 0.5f && courseLineMinutes.isNotEmpty()) {
            for (m in courseLineMinutes) {
                val distanceM = speedMps * m * 60.0
                val endPoint = projectPoint(
                    locationLat, locationLon,
                    course.toDouble(), distanceM
                )
                val line = LineString.fromLngLats(listOf(
                    Point.fromLngLat(locationLon, locationLat),
                    Point.fromLngLat(endPoint.longitude(), endPoint.latitude())
                ))
                val lineFeature = Feature.fromGeometry(line)
                lineFeature.addNumberProperty("minutes", m.toDouble())
                features.add(lineFeature)

                val labelFeature = Feature.fromGeometry(Point.fromLngLat(endPoint.longitude(), endPoint.latitude()))
                labelFeature.addStringProperty("courseLabel", "${m}min")
                features.add(labelFeature)
            }
        }

        val source = try {
            style.getSource(BOAT_SOURCE) as? GeoJsonSource
        } catch (_: IllegalStateException) {
            null
        }
        source?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun createBoatIcon(colorHex: String): Bitmap {
        val size = 36
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shadowPaint = Paint().apply {
            color = Color.argb(80, 0, 0, 0)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val fillPaint = Paint().apply {
            color = Color.parseColor(colorHex)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val strokePaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val path = Path().apply {
            moveTo(size / 2f, 2f)
            lineTo(size * 0.67f, size * 0.25f)
            lineTo(size * 0.72f, size * 0.50f)
            lineTo(size * 0.67f, size * 0.90f)
            lineTo(size * 0.33f, size * 0.90f)
            lineTo(size * 0.28f, size * 0.50f)
            lineTo(size * 0.33f, size * 0.25f)
            close()
        }
        val shadowPath = Path(path)
        shadowPath.offset(2f, 2f)
        canvas.drawPath(shadowPath, shadowPaint)
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        return bitmap
    }

    private fun projectPoint(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Point {
        val R = 6371000.0
        val bearingRad = Math.toRadians(bearingDeg)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val d = distanceM / R
        val newLat = Math.asin(
            Math.sin(latRad) * Math.cos(d) +
            Math.cos(latRad) * Math.sin(d) * Math.cos(bearingRad)
        )
        val newLon = lonRad + Math.atan2(
            Math.sin(bearingRad) * Math.sin(d) * Math.cos(latRad),
            Math.cos(d) - Math.sin(latRad) * Math.sin(newLat)
        )
        return Point.fromLngLat(Math.toDegrees(newLon), Math.toDegrees(newLat))
    }
}
