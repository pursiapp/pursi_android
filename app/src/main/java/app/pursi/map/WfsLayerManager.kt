package app.pursi.map

import app.pursi.data.model.WfsFeature
import app.pursi.datasource.core.FeatureRendererRegistry
import app.pursi.datasource.core.LayerType
import app.pursi.datasource.fi.TurvalaiteIconMapper
import org.maplibre.android.style.layers.Property
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object WfsLayerManager {

    fun addOrUpdateFeatures(
        style: Style, features: List<WfsFeature>, type: String,
        soundingOpacity: Float = 0.6f,
        contourOpacity: Float = 0.5f,
        navmarkSizeMultiplier: Float = 1.0f,
        isNightMode: Boolean = false
    ) {
        val geoFeatures = features.mapNotNull { feature ->
            try {
                val feat = when {
                    feature.geometry.contains("\"Point\"") ->
                        Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
                    feature.geometry.contains("\"LineString\"") -> {
                        val coords = parseLineStringCoords(feature.geometry)
                        if (coords.size >= 2) Feature.fromGeometry(LineString.fromLngLats(coords)) else null
                    }
                    feature.geometry.contains("\"Polygon\"") ->
                        parsePolygonCoords(feature.geometry)?.let { Feature.fromGeometry(Polygon.fromLngLats(it)) }
                    else -> null
                }
                if (feat != null) {
                    feat.addStringProperty("label", extractLabel(feature, type))
                    feat.addStringProperty("type", type)
                    feat.addNumberProperty("_vv_id", feature.id?.toDouble() ?: 0.0)
                    if (type == "turvalaite") {
                        val turvalaiteType = TurvalaiteIconMapper.extractProperty(feature.properties, "turvalaitetyyppifi") ?: ""
                        val alityyppi = TurvalaiteIconMapper.extractProperty(feature.properties, "alityyppi") ?: ""
                        val navigointilajikoodi = TurvalaiteIconMapper.extractProperty(feature.properties, "navigointilajikoodi") ?: ""
                        feat.addStringProperty("seamark:type", TurvalaiteIconMapper.toSeamarkType(turvalaiteType, alityyppi, navigointilajikoodi))
                        feat.addStringProperty("nimifi", TurvalaiteIconMapper.extractProperty(feature.properties, "nimifi") ?: "")
                    }
                }
                feat
            } catch (_: Exception) { null }
        }
        if (geoFeatures.isEmpty()) return
        addPreparedFeatures(style, FeatureCollection.fromFeatures(geoFeatures), type, soundingOpacity, contourOpacity, navmarkSizeMultiplier, isNightMode)
    }

    fun addPreparedFeatures(
        style: Style, collection: FeatureCollection, type: String,
        soundingOpacity: Float = 0.6f,
        contourOpacity: Float = 0.5f,
        navmarkSizeMultiplier: Float = 1.0f,
        isNightMode: Boolean = false
    ) {
        val sourceId = "wfs-$type"
        val layerId = "layer-wfs-$type"
        val labelLayerId = "layer-wfs-${type}-label"

        val existingSource = style.getSource(sourceId)
        if (existingSource is GeoJsonSource) {
            existingSource.setGeoJson(collection)
            return
        }

        val source = GeoJsonSource(sourceId)
        source.setGeoJson(collection)
        style.addSource(source)

        when (type) {
            "light", "daymark" -> {
                val layer = SymbolLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.iconImage("marker-15"),
                        PropertyFactory.iconSize(0.8f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                }
                style.addLayerBelow(layer, "layer-openseamap")
                val labelLayer = SymbolLayer(labelLayerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.textField("{label}"),
                        PropertyFactory.textSize(10f),
                        PropertyFactory.textColor(if (isNightMode) "#FFFFFF" else "#0D47A1"),
                        PropertyFactory.textHaloColor(if (isNightMode) "#1A1A1A" else "#FFFFFF"),
                        PropertyFactory.textHaloWidth(1.2f),
                        PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                        PropertyFactory.textAllowOverlap(false),
                        PropertyFactory.textIgnorePlacement(true)
                    )
                }
                style.addLayerAbove(labelLayer, layerId)
            }
            "navline" -> {
                style.addLayerBelow(LineLayer(layerId, sourceId).apply {
                    setProperties(PropertyFactory.lineWidth(2f), PropertyFactory.lineColor("#1565C0"), PropertyFactory.lineOpacity(0.7f))
                }, "layer-openseamap")
            }
            "fairway" -> {
                style.addLayerBelow(LineLayer(layerId, sourceId).apply {
                    setProperties(PropertyFactory.lineWidth(3f), PropertyFactory.lineColor("#0D47A1"), PropertyFactory.lineOpacity(0.5f), PropertyFactory.lineDasharray(arrayOf(4f, 2f)))
                }, "layer-openseamap")
            }
            "restriction" -> {
                style.addLayerBelow(FillLayer(layerId, sourceId).apply {
                    setProperties(PropertyFactory.fillColor("#E53935"), PropertyFactory.fillOpacity(0.2f), PropertyFactory.fillOutlineColor("#C62828"))
                }, "layer-openseamap")
            }
            "depth_sounding" -> {
                val textLayer = SymbolLayer("${layerId}-label", sourceId).apply {
                    setProperties(
                        PropertyFactory.textField("{label}"),
                        PropertyFactory.textSize(
                            Expression.interpolate(Expression.linear(), Expression.zoom(),
                                Expression.stop(14, Expression.literal(14f)),
                                Expression.stop(16, Expression.literal(18f)))
                        ),
                        PropertyFactory.textColor(if (isNightMode) "#FFFFFF" else "#1A1A1A"),
                        PropertyFactory.textHaloColor(if (isNightMode) "#1A1A1A" else "#FFFFFF"),
                        PropertyFactory.textHaloWidth(2.5f),
                        PropertyFactory.textOpacity(soundingOpacity.coerceAtLeast(0.1f)),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true),
                        PropertyFactory.textOffset(arrayOf(0f, -0.8f)),
                        PropertyFactory.textAnchor("bottom")
                    )
                    minZoom = 13.0f
                }
                style.addLayerAbove(textLayer, "layer-openseamap")
            }
            "depth_contour" -> {
                val lineLayer = LineLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.lineWidth(1.5f),
                        PropertyFactory.lineColor("#00695C"),
                        PropertyFactory.lineOpacity(contourOpacity.coerceAtLeast(0.1f))
                    )
                    minZoom = 13.0f
                }
                style.addLayerBelow(lineLayer, "layer-openseamap")
            }
            "depth_area" -> {
                val fillLayer = FillLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.fillColor("#1565C0"),
                        PropertyFactory.fillOpacity(0.15f),
                        PropertyFactory.fillOutlineColor("#00695C")
                    )
                    minZoom = 11.0f
                }
                style.addLayerBelow(fillLayer, "layer-openseamap")
            }
            "unsurveyed_area" -> {
                val fillLayer = FillLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.fillColor("#9E9E9E"),
                        PropertyFactory.fillOpacity(0.08f),
                        PropertyFactory.fillOutlineColor("#757575")
                    )
                    minZoom = 11.0f
                }
                style.addLayerBelow(fillLayer, "layer-openseamap")
            }
            "turvalaite" -> {
                val layer = SymbolLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.iconImage("{turvalaite_icon}"),
                        PropertyFactory.iconSize(navmarkSizeMultiplier),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                    minZoom = 11.0f
                }
                try {
                    style.addLayerAbove(layer, "layer-openseamap")
                } catch (_: Exception) {
                    style.addLayer(layer)
                }
            }
            "turvalaitevika" -> {
                val layer = SymbolLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.iconImage("warning_triangle"),
                        PropertyFactory.iconSize(0.5f * navmarkSizeMultiplier),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconColor("#E53935")
                    )
                    minZoom = 11.0f
                }
                style.addLayerAbove(layer, "layer-openseamap")
            }
            "vesiliikennemerkki" -> {
                val layer = SymbolLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.iconImage("{vesiliikennemerkki_icon}"),
                        PropertyFactory.iconSize(0.6f * navmarkSizeMultiplier),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                    minZoom = 11.0f
                }
                style.addLayerAbove(layer, "layer-openseamap")
            }
            "valosektori" -> {
                val fillLayer = FillLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.fillColor(Expression.get("sektori_color")),
                        PropertyFactory.fillOpacity(0.15f)
                    )
                    minZoom = 11.0f
                }
                style.addLayerAbove(fillLayer, "layer-openseamap")
            }
            "navmark_fault" -> {
                val layer = SymbolLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.iconImage("warning_triangle"),
                        PropertyFactory.iconSize(0.4f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconColor("#E53935")
                    )
                    minZoom = 11.0f
                }
                style.addLayerAbove(layer, "layer-openseamap")
            }
        }
    }

    fun removeFeatures(style: Style, type: String) {
        try { style.removeLayer("layer-wfs-$type") } catch (_: Exception) { }
        try { style.removeLayer("layer-wfs-${type}-label") } catch (_: Exception) { }
        try { style.removeLayer("layer-wfs-${type}-bg") } catch (_: Exception) { }
        try { style.removeSource("wfs-$type") } catch (_: Exception) { }
    }

    fun extractLabel(feature: WfsFeature, type: String): String {
        val props = feature.properties
        return when (type) {
            "depth_sounding" -> {
                val depth = props.lines().firstOrNull { it.uppercase().startsWith("DEPTH=") }?.substringAfter("=") ?: return ""
                val d = depth.toFloatOrNull() ?: return depth
                "%.1f".format(d)
            }
            "depth_contour" -> ""
            "light" -> {
                val name = getProperty(props, "nimi", "name")
                val light = getProperty(props, "loistotunnus", "light")
                listOfNotNull(name?.take(20), light?.take(10)).filter { it.isNotBlank() }.joinToString("\n")
            }
            "daymark" -> {
                getProperty(props, "nimi", "name")?.take(20) ?: ""
            }
            "turvalaite" -> {
                getProperty(props, "nimifi", "name")?.take(30) ?: ""
            }
            "turvalaitevika" -> {
                getProperty(props, "vayla", "tunnus")?.take(20) ?: "VIKA"
            }
            "vesiliikennemerkki" -> {
                val arvo = getProperty(props, "rajoitusarvo")
                val teksti = getProperty(props, "lisakilventeksti1")
                arvo?.let { "${it} km/h" } ?: teksti?.take(15) ?: ""
            }
            else -> ""
        }
    }

    private fun getProperty(props: String, vararg keys: String): String? {
        for (key in keys) {
            val v = props.lines().firstOrNull { it.startsWith("$key=", ignoreCase = true) }?.substringAfter("=")?.trim()
            if (!v.isNullOrBlank() && v != "null") return v
        }
        return null
    }

    fun parseLineStringCoords(geometry: String): List<Point> =
        org.maplibre.geojson.LineString.fromJson(geometry).coordinates()

    fun parsePolygonCoords(geometry: String): List<List<Point>>? = try {
        org.maplibre.geojson.Polygon.fromJson(geometry).coordinates()
    } catch (_: Exception) { null }

    fun addFeatureLayerUsingRenderer(
        style: Style,
        providerId: String,
        featureType: String,
        features: List<WfsFeature>,
        rendererRegistry: FeatureRendererRegistry
    ) {
        val renderer = rendererRegistry.getRenderer(featureType, providerId) ?: return
        val definition = renderer.getLayerDefinition(featureType) ?: return

        val sourceId = "wfs-${providerId}-${featureType}"
        val layerId = "layer-wfs-${providerId}-${featureType}"

        val geoFeatures = features.mapNotNull { renderer.toMapLibreFeature(it) }
        if (geoFeatures.isEmpty()) return

        try { style.removeLayer(layerId) } catch (_: Exception) { }
        try { style.removeSource(sourceId) } catch (_: Exception) { }

        val source = GeoJsonSource(sourceId)
        source.setGeoJson(FeatureCollection.fromFeatures(geoFeatures))
        style.addSource(source)

        when (definition.type) {
            LayerType.SYMBOL -> {
                val layer = SymbolLayer(layerId, sourceId).apply {
                    val icon = definition.styleProperties["iconImage"] as? String
                    if (icon != null && icon.startsWith("{")) {
                        setProperties(
                            PropertyFactory.iconImage(Expression.get(icon.removeSurrounding("{"))),
                            PropertyFactory.iconSize(definition.styleProperties["iconSize"] as? Float ?: 1.0f),
                            PropertyFactory.iconAllowOverlap(definition.styleProperties["iconAllowOverlap"] as? Boolean ?: false)
                        )
                    } else if (icon != null) {
                        setProperties(
                            PropertyFactory.iconImage(icon),
                            PropertyFactory.iconSize(definition.styleProperties["iconSize"] as? Float ?: 1.0f),
                            PropertyFactory.iconAllowOverlap(definition.styleProperties["iconAllowOverlap"] as? Boolean ?: false)
                        )
                    }
                    val textField = definition.styleProperties["textField"] as? String
                    if (textField != null) {
                        setProperties(
                            PropertyFactory.textField(Expression.get(textField.removeSurrounding("{"))),
                            PropertyFactory.textSize(definition.styleProperties["textSize"] as? Float ?: 11f),
                            PropertyFactory.textColor(definition.styleProperties["textColor"] as? String ?: "#000000")
                        )
                    }
                }
                try {
                    style.addLayerAbove(layer, "layer-openseamap")
                } catch (_: Exception) {
                    style.addLayer(layer)
                }
            }
            LayerType.LINE -> {
                val layer = LineLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.lineColor(definition.styleProperties["lineColor"] as? String ?: "#000000"),
                        PropertyFactory.lineWidth(definition.styleProperties["lineWidth"] as? Float ?: 1.0f),
                        PropertyFactory.lineOpacity(definition.styleProperties["lineOpacity"] as? Float ?: 1.0f)
                    )
                    val dash = definition.styleProperties["lineDasharray"] as? Array<*>
                    if (dash != null) {
                        setProperties(PropertyFactory.lineDasharray(dash.map { (it as? Number)?.toFloat() ?: 1f }.toTypedArray()))
                    }
                }
                style.addLayerBelow(layer, "layer-openseamap")
            }
            LayerType.FILL -> {
                val layer = FillLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.fillColor(definition.styleProperties["fillColor"] as? String ?: "#000000"),
                        PropertyFactory.fillOpacity(definition.styleProperties["fillOpacity"] as? Float ?: 1.0f)
                    )
                }
                style.addLayerBelow(layer, "layer-openseamap")
            }
        }
    }
}
