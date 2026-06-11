package app.pursi.datasource.core

import app.pursi.data.model.WfsFeature
import app.pursi.ui.viewmodel.SeamarkDetail
import app.pursi.ui.viewmodel.SeamarkSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IalaFeatureRenderer @Inject constructor(
    private val mapperRegistry: PropertyMapperRegistry
) : FeatureRenderer {

    companion object {
        val IALA_FEATURE_TYPES = setOf(
            "navigation_aid", "light", "daymark", "light_sector", "notice",
            "navigation_line", "fairway", "restricted_area",
            "depth_sounding", "depth_contour", "depth_area", "unsurveyed_area",
            "aton_fault"
        )

        private val NAV_CODE_TO_IALA = mapOf(
            1 to "lateral", 2 to "lateral", 3 to "cardinal", 4 to "cardinal",
            5 to "cardinal", 6 to "cardinal", 7 to "isolated_danger",
            8 to "safe_water", 9 to "special_purpose"
        )

        private val NAV_CODE_TO_ICON_FLOAT = mapOf(
            1 to "bl_red", 2 to "bl_green", 3 to "bc_north", 4 to "bc_south",
            5 to "bc_west", 6 to "bc_east", 7 to "bid", 8 to "bsw", 9 to "bsp"
        )

        private val NAV_CODE_TO_ICON_FIXED = mapOf(
            1 to "bl_bcn_red", 2 to "bl_bcn_green", 3 to "bc_bcn_north",
            4 to "bc_bcn_south", 5 to "bc_bcn_west", 6 to "bc_bcn_east",
            7 to "bid_bcn", 8 to "bsw_bcn", 9 to "bsp_bcn"
        )
    }

    override fun canRender(featureType: String, providerId: String): Boolean =
        featureType in IALA_FEATURE_TYPES

    override fun toMapLibreFeature(feature: WfsFeature): Feature? {
        val mapper = mapperRegistry.getMapper(feature.source) ?: return null
        val rawProps = parseProperties(feature.properties)

        val props = rawProps.mapKeys { (k, _) -> mapper.mapKey(k) ?: k }
            .mapValues { (k, v) -> mapper.mapValue(k, v) ?: v }

        return when (feature.featureType) {
            "navigation_aid", "light", "daymark" -> renderAtoN(feature, props)
            "light_sector" -> renderLightSector(feature, props)
            "notice" -> renderNotice(feature, props)
            "navigation_line" -> renderLine(feature, props, "#2196F3", 2f, 0.7f, null)
            "fairway" -> renderLine(feature, props, "#1976D2", 3f, 0.5f, arrayOf(4f, 4f))
            "restricted_area" -> renderFill(feature, props, "#E53935", 0.2f)
            "depth_sounding", "depth_contour", "depth_area", "unsurveyed_area" ->
                renderDepth(feature, props)
            "aton_fault" -> renderAtonFault(feature, props)
            else -> null
        }
    }

    override fun getLayerDefinition(featureType: String): LayerDefinition? = when (featureType) {
        "navigation_aid", "light", "daymark" -> LayerDefinition(
            layerId = "layer-wfs-aton", sourceId = "source-wfs-aton",
            type = LayerType.SYMBOL,
            styleProperties = mapOf("iconImage" to "{icon}", "iconSize" to 0.8f,
                "iconAllowOverlap" to true, "iconIgnorePlacement" to true)
        )
        "aton_fault" -> LayerDefinition(
            layerId = "layer-wfs-aton-fault", sourceId = "source-wfs-aton-fault",
            type = LayerType.SYMBOL,
            styleProperties = mapOf("iconImage" to "warning_triangle", "iconSize" to 0.5f,
                "iconAllowOverlap" to true, "iconColor" to "#E53935")
        )
        "light_sector" -> LayerDefinition(
            layerId = "layer-wfs-light-sector", sourceId = "source-wfs-light-sector",
            type = LayerType.FILL,
            styleProperties = mapOf("fillColor" to "{colour}", "fillOpacity" to 0.15f)
        )
        "notice" -> LayerDefinition(
            layerId = "layer-wfs-notice", sourceId = "source-wfs-notice",
            type = LayerType.SYMBOL,
            styleProperties = mapOf("iconImage" to "{icon}", "iconSize" to 0.6f,
                "iconAllowOverlap" to true)
        )
        "navigation_line" -> LayerDefinition(
            layerId = "layer-wfs-navline", sourceId = "source-wfs-navline",
            type = LayerType.LINE,
            styleProperties = mapOf("lineColor" to "#2196F3", "lineWidth" to 2f, "lineOpacity" to 0.7f)
        )
        "fairway" -> LayerDefinition(
            layerId = "layer-wfs-fairway", sourceId = "source-wfs-fairway",
            type = LayerType.LINE,
            styleProperties = mapOf("lineColor" to "#1976D2", "lineWidth" to 3f,
                "lineDasharray" to arrayOf(4f, 4f), "lineOpacity" to 0.5f)
        )
        "restricted_area" -> LayerDefinition(
            layerId = "layer-wfs-restricted", sourceId = "source-wfs-restricted",
            type = LayerType.FILL,
            styleProperties = mapOf("fillColor" to "#E53935", "fillOpacity" to 0.2f)
        )
        "depth_sounding" -> LayerDefinition(
            layerId = "layer-wfs-depth-sounding", sourceId = "source-wfs-depth-sounding",
            type = LayerType.SYMBOL,
            styleProperties = mapOf("textField" to "{label}", "textSize" to 11f, "textColor" to "#00695C")
        )
        "depth_contour" -> LayerDefinition(
            layerId = "layer-wfs-depth-contour", sourceId = "source-wfs-depth-contour",
            type = LayerType.LINE,
            styleProperties = mapOf("lineColor" to "#00695C", "lineWidth" to 1.5f, "lineOpacity" to 0.5f)
        )
        "depth_area" -> LayerDefinition(
            layerId = "layer-wfs-depth-area", sourceId = "source-wfs-depth-area",
            type = LayerType.FILL,
            styleProperties = mapOf("fillColor" to "#1565C0", "fillOpacity" to 0.15f)
        )
        "unsurveyed_area" -> LayerDefinition(
            layerId = "layer-wfs-unsurveyed", sourceId = "source-wfs-unsurveyed",
            type = LayerType.FILL,
            styleProperties = mapOf("fillColor" to "#9E9E9E", "fillOpacity" to 0.08f)
        )
        else -> null
    }

    override fun handleClick(feature: WfsFeature): SeamarkDetail? {
        val mapper = mapperRegistry.getMapper(feature.source) ?: return null
        val rawProps = parseProperties(feature.properties)
        val props = rawProps.mapKeys { (k, _) -> mapper.mapKey(k) ?: k }
            .mapValues { (k, v) -> mapper.mapValue(k, v) ?: v }

        val name = props["name"] ?: props["name_sv"] ?: ""
        val atonType = props["aton_type"] ?: ""
        val status = props["status"]
        val structure = props["structure_type"]

        return SeamarkDetail(
            source = SeamarkSource.VV,
            id = feature.id,
            name = name,
            typeLabel = atonType.replaceFirstChar { it.uppercase() },
            statusLabel = status,
            structureLabel = structure,
            hasLight = props["lit"] == "yes",
            lightCharacteristic = props["light_character"],
            sectorInfo = props["sector_info"],
            extraInfo = listOfNotNull(
                props["fairway_name"]?.let { "Väylä: $it" },
                props["aton_number"]?.let { "Numero: $it" },
                props["owner"]?.let { "Omistaja: $it" },
                props["construction_year"]?.let { "Rakennettu: $it" },
                if (props["ais"] == "yes") "AIS" else null
            ),
            latitude = feature.latitude,
            longitude = feature.longitude
        )
    }

    private fun renderAtoN(feature: WfsFeature, props: Map<String, String>): Feature? {
        val atonType = props["aton_type"] ?: return null
        val structure = props["structure_type"]
        val navCode = props["nav_category"]?.toIntOrNull()
        val symbol = props["symbol"]
        val name = props["name"] ?: props["name_sv"] ?: ""
        val isFloating = structure == "floating"

        val icon = when {
            atonType in setOf("beacon", "buoy", "lateral_mark") && navCode != null -> {
                val icons = if (isFloating) NAV_CODE_TO_ICON_FLOAT else NAV_CODE_TO_ICON_FIXED
                icons[navCode] ?: (if (isFloating) "bsp" else "bsp_bcn")
            }
            atonType == "buoy" && navCode == null && symbol != null -> {
                when (symbol.lowercase()) {
                    "a", "b", "g" -> "bl_red"
                    "c", "d", "i" -> "bl_green"
                    "e", "f", "k" -> "bc_north"
                    "g", "h", "m" -> "bc_south"
                    "i", "j", "o" -> "bc_west"
                    "k", "l", "q" -> "bc_east"
                    "n" -> "bid"
                    "p" -> "bsw"
                    "r", "w" -> "bsp"
                    else -> "bsp"
                }
            }
            else -> when (atonType) {
                "major_light" -> "lmaj_red"
                "sector_light", "directional_light", "light", "leading_light", "side_light" -> "lmaj_red"
                "auxiliary_light" -> "lmin_yellow"
                "daymark", "leading_mark" -> "daymark"
                "fog_signal", "radar_beacon" -> "fog"
                "virtual_aton" -> "virt"
                "mooring" -> "moor_buoy"
                else -> "lmaj_red"
            }
        }

        val feat = Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
        feat.addStringProperty("icon", icon)
        feat.addStringProperty("name", name)
        feat.addStringProperty("providerId", feature.source)
        feat.addStringProperty("featureType", feature.featureType)
        feat.addNumberProperty("sourceId", feature.id.toDouble())
        return feat
    }

    private fun renderLightSector(feature: WfsFeature, props: Map<String, String>): Feature? {
        return null
    }

    private fun renderNotice(feature: WfsFeature, props: Map<String, String>): Feature? {
        val signType = props["sign_type"]?.toIntOrNull() ?: 0
        val icon = waterwaySignIcon(signType)
        val feat = Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
        feat.addStringProperty("icon", icon)
        feat.addStringProperty("providerId", feature.source)
        feat.addStringProperty("featureType", feature.featureType)
        return feat
    }

    private fun renderLine(
        feature: WfsFeature, props: Map<String, String>,
        color: String, width: Float, opacity: Float, dash: Array<Float>?
    ): Feature? = null

    private fun renderFill(
        feature: WfsFeature, props: Map<String, String>,
        color: String, opacity: Float
    ): Feature? = null

    private fun renderDepth(feature: WfsFeature, props: Map<String, String>): Feature? = null

    private fun renderAtonFault(feature: WfsFeature, props: Map<String, String>): Feature? {
        val feat = Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
        feat.addStringProperty("providerId", feature.source)
        feat.addStringProperty("featureType", feature.featureType)
        feat.addNumberProperty("sourceId", feature.id.toDouble())
        return feat
    }

    internal fun parseProperties(props: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in props.lines()) {
            val idx = line.indexOf("=")
            if (idx > 0) {
                val key = line.substring(0, idx)
                val value = line.substring(idx + 1)
                if (value != "null" && value.isNotBlank()) {
                    result[key] = value
                }
            }
        }
        return result
    }

    private fun waterwaySignIcon(typeCode: Int): String = when (typeCode) {
        0, 13, 17, 18, 19, 20, 35, 36, 37 -> "josm_Q126_generic_crossing"
        1 -> "josm_Q126_BNIWR_no_anchoring"
        2, 3 -> "josm_Q126_generic_no_berthing"
        4 -> "josm_Q126_CEVNI_no_convoy_overtaking"
        5 -> "josm_Q126_CEVNI_no_convoy_passing"
        6, 11 -> "josm_Q126_generic_speed_limit"
        7 -> "josm_Q126_generic_no_waterskiing"
        8 -> "josm_Q126_generic_no_sailboards"
        9 -> "josm_Q126_CEVNI_no_entry"
        10 -> "josm_Q126_CEVNI_no_waterbikes"
        12 -> "josm_Q126_CEVNI_stop"
        14 -> "josm_Q126_generic_make_radio_contact"
        15 -> "josm_Q126_BNIWR_limited_headroom"
        16 -> "josm_Q126_generic_limited_depth"
        21, 28 -> "josm_Q126_CEVNI_radio_information"
        22, 23 -> "josm_Q126_CEVNI_berthing_permitted"
        24, 31, 32 -> "josm_Q126_CEVNI_overhead_cable"
        25 -> "josm_Q126_generic_telephone"
        26, 27 -> "josm_Q126_generic_ferry_independent"
        29 -> "josm_Q126_generic_drinking_water"
        30 -> "josm_Q126_CEVNI_prohibition_ends"
        33, 34 -> "josm_Q126_generic_alignment"
        else -> "josm_Q126_generic_crossing"
    }
}
