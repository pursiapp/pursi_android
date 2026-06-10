package fi.pursi.map.overlays

import fi.pursi.data.model.WfsFeature
import fi.pursi.water.WaterObservation
import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.fi.TurvalaiteIconMapper
import fi.pursi.datasource.fi.VesiLiikennemerkkiIconMapper
import fi.pursi.map.WfsLayerManager
import fi.pursi.ui.viewmodel.NavmarkSize
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object WfsOverlay {

    // ── Depth ──────────────────────────────────────────────────────

    fun prepareDepth(
        showDepth: Boolean,
        depthFeatures: Map<String, List<WfsFeature>>
    ): Map<String, FeatureCollection>? {
        if (!showDepth || depthFeatures.isEmpty()) return null
        return depthFeatures.mapValues { (type, features) ->
            val geoFeatures = features.mapNotNull { feature ->
                try {
                    val feat = when {
                        feature.geometry.contains("\"Point\"") ->
                            Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
                        feature.geometry.contains("\"LineString\"") -> {
                            val coords = WfsLayerManager.parseLineStringCoords(feature.geometry)
                            if (coords.size >= 2) Feature.fromGeometry(LineString.fromLngLats(coords)) else null
                        }
                        feature.geometry.contains("\"Polygon\"") ->
                            WfsLayerManager.parsePolygonCoords(feature.geometry)
                                ?.let { Feature.fromGeometry(Polygon.fromLngLats(it)) }
                        else -> null
                    }
                    if (feat != null) {
                        feat.addStringProperty("label", WfsLayerManager.extractLabel(feature, type))
                        feat.addStringProperty("type", type)
                    }
                    feat
                } catch (_: Exception) { null }
            }
            FeatureCollection.fromFeatures(geoFeatures)
        }
    }

    fun applyDepth(style: Style, prepared: Map<String, FeatureCollection>?, isNightMode: Boolean) {
        if (prepared == null) {
            WfsLayerManager.removeFeatures(style, "depth_sounding")
            WfsLayerManager.removeFeatures(style, "depth_contour")
            WfsLayerManager.removeFeatures(style, "depth_area")
            WfsLayerManager.removeFeatures(style, "unsurveyed_area")
        } else {
            val activeTypes = prepared.keys
            for (featureType in listOf("depth_sounding", "depth_contour", "depth_area", "unsurveyed_area")) {
                if (featureType in activeTypes) {
                    prepared[featureType]?.let { collection ->
                        WfsLayerManager.addPreparedFeatures(style, collection, featureType, isNightMode = isNightMode)
                    }
                } else {
                    WfsLayerManager.removeFeatures(style, featureType)
                }
            }
        }
    }

    // ── Turvalaite ─────────────────────────────────────────────────

    fun prepareTurvalaite(
        showVvNavmarks: Boolean,
        turvalaiteFeatures: Map<String, List<WfsFeature>>,
        turvalaitevikaFeatures: Map<String, List<WfsFeature>>
    ): Map<String, FeatureCollection>? {
        if (!showVvNavmarks) return null

        val allFeatures = mutableMapOf<String, FeatureCollection>()
        for ((sourceName, features) in turvalaiteFeatures) {
            val geoFeatures = features.mapNotNull { feature ->
                try {
                    val feat = when {
                        feature.geometry.contains("\"Point\"") ->
                            Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
                        else -> null
                    }
                    if (feat != null) {
                        feat.addStringProperty("label", WfsLayerManager.extractLabel(feature, "turvalaite"))
                        feat.addStringProperty("type", "turvalaite")
                        feat.addNumberProperty("_vv_id", feature.id.toDouble())
                        val turvalaiteType = TurvalaiteIconMapper.extractProperty(feature.properties, "turvalaitetyyppifi") ?: ""
                        val alityyppi = TurvalaiteIconMapper.extractProperty(feature.properties, "alityyppi") ?: ""
                        val symboli = TurvalaiteIconMapper.extractProperty(feature.properties, "symboli") ?: ""
                        val navigointilajikoodi = TurvalaiteIconMapper.extractProperty(feature.properties, "navigointilajikoodi") ?: ""
                        feat.addStringProperty("turvalaite_icon", TurvalaiteIconMapper.toIconName(turvalaiteType, alityyppi, symboli, navigointilajikoodi))
                        feat.addStringProperty("nimifi", TurvalaiteIconMapper.extractProperty(feature.properties, "nimifi") ?: "")
                    }
                    feat
                } catch (_: Exception) { null }
            }
            if (geoFeatures.isNotEmpty()) {
                allFeatures[sourceName] = FeatureCollection.fromFeatures(geoFeatures)
            }
        }
        for ((sourceName, features) in turvalaitevikaFeatures) {
            val geoFeatures = features.mapNotNull { feature ->
                try {
                    val feat = Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
                    feat.addStringProperty("label", WfsLayerManager.extractLabel(feature, "turvalaitevika"))
                    feat.addStringProperty("type", "turvalaitevika")
                    feat.addNumberProperty("_vv_id", feature.id.toDouble())
                    feat
                } catch (_: Exception) { null }
            }
            if (geoFeatures.isNotEmpty()) {
                allFeatures["vika_$sourceName"] = FeatureCollection.fromFeatures(geoFeatures)
            }
        }
        return allFeatures
    }

    fun hasTurvalaiteInView(prepared: Map<String, FeatureCollection>, viewport: BoundingBox?): Boolean {
        if (prepared.isEmpty()) return false
        if (viewport == null) return true
        return prepared.values.any { collection ->
            collection.features()?.any { feature ->
                val pt = feature.geometry() as? Point ?: return@any false
                viewport.contains(pt.latitude(), pt.longitude())
            } ?: false
        }
    }

    fun applyTurvalaite(
        style: Style,
        prepared: Map<String, FeatureCollection>?,
        hasFeaturesInView: Boolean,
        navmarkSizeMultiplier: Float,
        isNightMode: Boolean
    ) {
        if (prepared == null || !hasFeaturesInView) {
            WfsLayerManager.removeFeatures(style, "turvalaite")
            WfsLayerManager.removeFeatures(style, "turvalaitevika")
            style.getLayer("DYNAMIC_icon_fixed_rotation")?.setProperties(
                PropertyFactory.visibility(Property.VISIBLE)
            )
            style.getLayer("DYNAMIC_icon_free_rotation")?.setProperties(
                PropertyFactory.visibility(Property.VISIBLE)
            )
        } else {
            style.getLayer("DYNAMIC_icon_fixed_rotation")?.setProperties(
                PropertyFactory.visibility(Property.NONE)
            )
            style.getLayer("DYNAMIC_icon_free_rotation")?.setProperties(
                PropertyFactory.visibility(Property.NONE)
            )
            for ((sourceName, collection) in prepared) {
                val featureType = if (sourceName.startsWith("vika_")) "turvalaitevika" else "turvalaite"
                WfsLayerManager.addPreparedFeatures(style, collection, featureType,
                    navmarkSizeMultiplier = navmarkSizeMultiplier, isNightMode = isNightMode)
            }
        }
    }

    // ── VV overlay features (navlines, fairways, sectors, signs) ───

    fun updateVvFeatures(
        style: Style,
        showVvNavmarks: Boolean,
        showSectors: Boolean,
        navlineFeatures: Map<String, List<WfsFeature>>,
        fairwayFeatures: Map<String, List<WfsFeature>>,
        valosektoriFeatures: Map<String, List<WfsFeature>>,
        vesiliikennemerkkiFeatures: Map<String, List<WfsFeature>>,
        isNightMode: Boolean
    ) {
        if (!showVvNavmarks) {
            WfsLayerManager.removeFeatures(style, "navline")
            WfsLayerManager.removeFeatures(style, "fairway")
            WfsLayerManager.removeFeatures(style, "valosektori")
            WfsLayerManager.removeFeatures(style, "vesiliikennemerkki")
            return
        }

        for ((_, features) in navlineFeatures) {
            WfsLayerManager.addOrUpdateFeatures(style, features, "navline", isNightMode = isNightMode)
        }
        for ((_, features) in fairwayFeatures) {
            WfsLayerManager.addOrUpdateFeatures(style, features, "fairway", isNightMode = isNightMode)
        }
        if (showSectors) {
            for ((_, features) in valosektoriFeatures) {
                val polygons = valosektoriPolygons(features)
                if (polygons != null) {
                    WfsLayerManager.addPreparedFeatures(style, polygons, "valosektori", isNightMode = isNightMode)
                } else {
                    WfsLayerManager.removeFeatures(style, "valosektori")
                }
            }
        } else {
            WfsLayerManager.removeFeatures(style, "valosektori")
        }
        for ((_, features) in vesiliikennemerkkiFeatures) {
            val enriched = features.map { f ->
                val vlmla = f.properties.lines().firstOrNull { it.startsWith("vlmlajityyppi=") }
                    ?.substringAfter("=")?.trim()?.toIntOrNull() ?: 0
                WfsFeature(
                    id = f.id, source = f.source, featureType = f.featureType,
                    geometry = f.geometry,
                    properties = f.properties + "\nvesiliikennemerkki_icon=${VesiLiikennemerkkiIconMapper.toIconName(vlmla)}",
                    latitude = f.latitude, longitude = f.longitude,
                    minLat = f.minLat, minLng = f.minLng,
                    maxLat = f.maxLat, maxLng = f.maxLng
                )
            }
            WfsLayerManager.addOrUpdateFeatures(style, enriched, "vesiliikennemerkki", isNightMode = isNightMode)
        }
    }

    // ── Water observations ─────────────────────────────────────────

    fun updateWaterObservations(style: Style, showAlgae: Boolean, waterObservations: List<WaterObservation>) {
        val srcA = "water-obs-a"
        val srcB = "water-obs-b"
        val layerA = "water-obs-layer-a"
        val layerB = "water-obs-layer-b"
        val labelLayer = "water-obs-label"

        if (!showAlgae || waterObservations.isEmpty()) {
            OverlayUtils.safeRemoveLayers(style, layerA, layerB, labelLayer)
            OverlayUtils.safeRemoveSources(style, srcA, srcB)
            return
        }

        val seen = mutableSetOf<String>()
        val primary = mutableListOf<Pair<Int, WaterObservation>>()
        val secondary = mutableListOf<Pair<Int, WaterObservation>>()

        waterObservations.forEachIndexed { idx, obs ->
            val key = "%.5f_%.5f".format(obs.latitude, obs.longitude)
            if (seen.add(key)) primary.add(idx to obs)
            else secondary.add(idx to obs)
        }

        fun makeFeatures(list: List<Pair<Int, WaterObservation>>): List<Feature> =
            list.map { (idx, obs) ->
                Feature.fromGeometry(Point.fromLngLat(obs.longitude, obs.latitude)).apply {
                    addStringProperty("circleColor", obs.circleColor)
                    addStringProperty("type", obs.type.name)
                    addNumberProperty("algaeLevel", obs.algaeLevel.toDouble())
                    addStringProperty("source", obs.sourceFormatted)
                    addNumberProperty("timestamp", obs.timestamp.toDouble())
                    addNumberProperty("obsIndex", idx.toDouble())
                    addStringProperty("dateFormatted", obs.dateFormatted)
                    addStringProperty("tempLabel", obs.tempLabel)
                    addStringProperty("titleLine", obs.titleLine)
                }
            }

        val featuresA = makeFeatures(primary)
        val featuresB = makeFeatures(secondary)

        OverlayUtils.safeRemoveLayers(style, layerA, layerB, "$labelLayer-a", "$labelLayer-b")
        OverlayUtils.safeRemoveSources(style, srcA, srcB, "water-obs-label-src")

        val radiusExpr = Expression.interpolate(Expression.linear(), Expression.zoom(),
            Expression.stop(5, Expression.literal(5f)),
            Expression.stop(8, Expression.literal(7f)),
            Expression.stop(11, Expression.literal(10f)),
            Expression.stop(14, Expression.literal(14f))
        )

        val src1 = GeoJsonSource(srcA)
        src1.setGeoJson(FeatureCollection.fromFeatures(featuresA))
        style.addSource(src1)
        val circleA = org.maplibre.android.style.layers.CircleLayer(layerA, srcA)
        circleA.setProperties(
            PropertyFactory.circleRadius(radiusExpr),
            PropertyFactory.circleColor(Expression.get("circleColor")),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleOpacity(0.85f)
        )
        circleA.setMinZoom(5f)
        style.addLayerAbove(circleA, "layer-openseamap")

        if (featuresB.isNotEmpty()) {
            val src2 = GeoJsonSource(srcB)
            src2.setGeoJson(FeatureCollection.fromFeatures(featuresB))
            style.addSource(src2)
            val circleB = org.maplibre.android.style.layers.CircleLayer(layerB, srcB)
            circleB.setProperties(
                PropertyFactory.circleRadius(radiusExpr),
                PropertyFactory.circleColor(Expression.get("circleColor")),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleOpacity(0.85f),
                PropertyFactory.circleTranslate(arrayOf(18f, -18f)),
                PropertyFactory.circleTranslateAnchor("map")
            )
            circleB.setMinZoom(5f)
            style.addLayerAbove(circleB, layerA)
        }

        val tempFeaturesA = featuresA.filter {
            it.getStringProperty("type") == "TEMPERATURE"
        }
        if (tempFeaturesA.isNotEmpty()) {
            val labelSrcA = "water-obs-label-src-a"
            OverlayUtils.safeRemoveSource(style, labelSrcA)
            val srcLa = GeoJsonSource(labelSrcA)
            srcLa.setGeoJson(FeatureCollection.fromFeatures(tempFeaturesA))
            style.addSource(srcLa)
            val tlA = org.maplibre.android.style.layers.SymbolLayer("$labelLayer-a", labelSrcA)
            tlA.setProperties(
                PropertyFactory.textField(Expression.get("tempLabel")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textHaloColor("#000000"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textOptional(true)
            )
            style.addLayerAbove(tlA, layerA)
        }

        if (featuresB.isNotEmpty()) {
            val tempFeaturesB = featuresB.filter {
                it.getStringProperty("type") == "TEMPERATURE"
            }
            if (tempFeaturesB.isNotEmpty()) {
                val labelSrcB = "water-obs-label-src-b"
                OverlayUtils.safeRemoveSource(style, labelSrcB)
                val srcLb = GeoJsonSource(labelSrcB)
                srcLb.setGeoJson(FeatureCollection.fromFeatures(tempFeaturesB))
                style.addSource(srcLb)
                val tlB = org.maplibre.android.style.layers.SymbolLayer("$labelLayer-b", labelSrcB)
                tlB.setProperties(
                    PropertyFactory.textField(Expression.get("tempLabel")),
                    PropertyFactory.textSize(13f),
                    PropertyFactory.textColor("#FFFFFF"),
                    PropertyFactory.textHaloColor("#000000"),
                    PropertyFactory.textHaloWidth(1.5f),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textOptional(true),
                    PropertyFactory.textTranslate(arrayOf(18f, -18f))
                )
                style.addLayerAbove(tlB, layerB)
            }
        }
    }

    // ── Valosektori polygon conversion ─────────────────────────────

    private fun valosektoriPolygons(features: List<WfsFeature>): FeatureCollection? {
        val sectorRadius = 0.002
        val colorMap = mapOf("v" to "#4CAF50", "p" to "#F44336", "vi" to "#FFFFFF", "k" to "#FFEB3B")
        val geoFeatures = features.mapNotNull { f ->
            try {
                val props = f.properties
                fun prop(key: String) = props.lines().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
                val alku = prop("alkukulma")?.toDoubleOrNull() ?: return@mapNotNull null
                val loppu = prop("loppukulma")?.toDoubleOrNull() ?: return@mapNotNull null
                val vari = prop("vari") ?: "vi"
                if (alku == 0.0 && loppu == 360.0) {
                    val points = mutableListOf<Point>()
                    for (a in 0..360 step 15) {
                        val rad = Math.toRadians(a.toDouble())
                        points.add(Point.fromLngLat(
                            f.longitude + sectorRadius * Math.cos(rad) / Math.cos(Math.toRadians(f.latitude)),
                            f.latitude + sectorRadius * Math.sin(rad)
                        ))
                    }
                    val feat = Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
                    feat.addStringProperty("sektori_color", colorMap[vari] ?: "#FFFFFF")
                    feat
                } else {
                    var start = alku; var end = loppu
                    if (end < start) end += 360.0
                    val points = mutableListOf(Point.fromLngLat(f.longitude, f.latitude))
                    val steps = ((end - start) / 10.0).toInt().coerceAtLeast(3)
                    for (i in 0..steps) {
                        val a = start + (end - start) * i / steps
                        val rad = Math.toRadians(a)
                        points.add(Point.fromLngLat(
                            f.longitude + sectorRadius * Math.cos(rad) / Math.cos(Math.toRadians(f.latitude)),
                            f.latitude + sectorRadius * Math.sin(rad)
                        ))
                    }
                    points.add(Point.fromLngLat(f.longitude, f.latitude))
                    val feat = Feature.fromGeometry(Polygon.fromLngLats(listOf(points)))
                    feat.addStringProperty("sektori_color", colorMap[vari] ?: "#FFFFFF")
                    feat
                }
            } catch (_: Exception) { null }
        }
        return if (geoFeatures.isNotEmpty()) FeatureCollection.fromFeatures(geoFeatures) else null
    }
}
