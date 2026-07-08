package app.pursi.map.ais

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.pursi.ais.AisVessel
import app.pursi.map.SpriteCacheRegistry
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object AisVesselOverlay {

    private const val SRC = "pursi-vessels"
    const val L_STOP = "lv-stop"
    const val L_SAIL = "lv-sail"
    const val L_FAST = "lv-fast"
    const val L_MOVE = "lv-move"

    val ALL_LAYERS: List<String> = listOf(L_STOP, L_SAIL, L_FAST, L_MOVE)

    fun update(style: Style, vessels: List<AisVessel>) {
        if (vessels.isEmpty()) { remove(style); return }

        val fc = FeatureCollection.fromFeatures(vessels.map { v ->
            Feature.fromGeometry(Point.fromLngLat(v.lon, v.lat)).apply {
                addNumberProperty("mmsi", v.mmsi.toDouble())
                addNumberProperty("sog", v.sog.toDouble())
                addNumberProperty("cog", v.cog.toDouble())
                addNumberProperty("navStat", v.navStat.toDouble())
                addNumberProperty("heading", if (v.heading in 0..359) v.heading.toDouble() else v.cog.toDouble())
            }
        })

        val existing = style.getSource(SRC) as? GeoJsonSource
        if (existing != null) { existing.setGeoJson(fc); return }

        style.addSource(GeoJsonSource(SRC).also { it.setGeoJson(fc) })

        val sog = Expression.get("sog")
        val ns = Expression.get("navStat")

        fun add(id: String, icon: String, color: String, shape: (Canvas, Paint, Paint) -> Unit, filter: Expression) {
            if (style.getImage(icon) == null) {
                val bmp = mkIcon(color, shape)
                SpriteCacheRegistry.track(bmp, "ais-icon")
                style.addImage(icon, bmp)
            }
            SymbolLayer(id, SRC).apply {
                setProperties(
                    PropertyFactory.iconImage(icon),
                    PropertyFactory.iconSize(
                        Expression.interpolate(Expression.linear(), Expression.zoom(),
                            Expression.stop(6, Expression.literal(0.5f)),
                            Expression.stop(10, Expression.literal(0.85f)),
                            Expression.stop(14, Expression.literal(1.3f)),
                            Expression.stop(18, Expression.literal(2.2f))
                        )
                    ),
                    PropertyFactory.iconRotate(Expression.get("heading")),
                    PropertyFactory.iconRotationAlignment("map"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
                withFilter(filter)
            }.also { style.addLayerAbove(it, "layer-openseamap") }
        }

        // Stopped: sog == 0
        add(L_STOP, "ai-stop", "#888888", ::drawCircle,
            Expression.eq(sog, Expression.literal(0)))

        // Fast: sog >= 15
        add(L_FAST, "ai-fast", "#FF3333", ::drawTriangle,
            Expression.gte(sog, Expression.literal(15.0)))

        // Sailing: 0 < sog < 15 and navStat == 8
        add(L_SAIL, "ai-sail", "#E0E0E0", ::drawSail,
            Expression.all(
                Expression.gt(sog, Expression.literal(0)),
                Expression.lt(sog, Expression.literal(15.0)),
                Expression.eq(ns, Expression.literal(8))
            ))

        // Moving: 0 < sog < 15 and navStat != 8
        add(L_MOVE, "ai-move", "#3388FF", ::drawTriangle,
            Expression.all(
                Expression.gt(sog, Expression.literal(0)),
                Expression.lt(sog, Expression.literal(15.0)),
                Expression.neq(ns, Expression.literal(8))
            ))
    }

    fun remove(style: Style) {
        for (lid in ALL_LAYERS) try { style.removeLayer(lid) } catch (_: Exception) {}
        try { style.removeSource(SRC) } catch (_: Exception) {}
    }

    // ── Icon builder ──────────────────────────────────────────────

    private fun mkIcon(color: String, draw: (Canvas, Paint, Paint) -> Unit): Bitmap {
        val s = 36; val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        val f = Paint().apply { this.color = Color.parseColor(color); isAntiAlias = true; style = Paint.Style.FILL }
        val st = Paint().apply { this.color = 0xFF000000.toInt(); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f }
        draw(c, f, st); return b
    }

    private fun drawTriangle(c: Canvas, f: Paint, st: Paint) {
        val p = Path().apply { moveTo(18f, 2f); lineTo(33f, 30f); lineTo(18f, 18f); lineTo(3f, 30f); close() }
        c.drawPath(p, f); c.drawPath(p, st)
    }

    private fun drawCircle(c: Canvas, f: Paint, st: Paint) {
        c.drawCircle(18f, 18f, 13f, f); c.drawCircle(18f, 18f, 13f, st)
    }

    private fun drawDiamond(c: Canvas, f: Paint, st: Paint) {
        val p = Path().apply { moveTo(18f, 2f); lineTo(34f, 18f); lineTo(18f, 34f); lineTo(2f, 18f); close() }
        c.drawPath(p, f); c.drawPath(p, st)
    }

    private fun drawSail(c: Canvas, f: Paint, st: Paint) {
        c.drawLine(18f, 4f, 18f, 30f, st)
        val hu = Path().apply { moveTo(5f, 30f); lineTo(18f, 24f); lineTo(31f, 30f); close() }
        c.drawPath(hu, f); c.drawPath(hu, st)
        val sl = Path().apply { moveTo(18f, 6f); lineTo(8f, 22f); lineTo(18f, 18f); close() }
        c.drawPath(sl, f); c.drawPath(sl, st)
        val sr = Path().apply { moveTo(18f, 6f); lineTo(28f, 22f); lineTo(18f, 18f); close() }
        c.drawPath(sr, f); c.drawPath(sr, st)
    }
}
