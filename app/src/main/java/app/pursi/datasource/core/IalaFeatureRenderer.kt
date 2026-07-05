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
            styleProperties = mapOf(
                "iconImage" to "{icon}",
                "iconSize" to 1.0f,
                "iconOpacity" to 1.0f,
                "iconAllowOverlap" to true,
                "textField" to "{label}",
                "textSize" to 12f,
                "textColor" to "#000000",
                "textHaloColor" to "#FFFFFF",
                "textHaloWidth" to 1.5f
            ),
            minZoom = 11f
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

        if (feature.featureType !in IALA_FEATURE_TYPES) return null

        // For navigation aids / lights / daymarks we build a rich detail (matching the
        // pre-refactor MapViewModel.buildTurvalaiteDetail output for Finnish data).
        if (feature.featureType == "navigation_aid" || feature.featureType == "light" || feature.featureType == "daymark") {
            return buildAtonDetail(feature, props, rawProps)
        }

        // Vesiliikennemerkit (waterway signs) get a popup with sign type, text, and
        // restriction value.
        if (feature.featureType == "notice") {
            return buildNoticeDetail(feature, props, rawProps)
        }

        // Light sectors, fairways, navigation lines, restricted areas etc. don't get a
        // popup — they're rendered as geometry only. Returning a minimal VV detail is
        // safe in case a country wants to extend later.
        val name = props["name"] ?: props["name_sv"] ?: ""
        return SeamarkDetail(
            source = SeamarkSource.VV,
            id = feature.id,
            name = name,
            latitude = feature.latitude,
            longitude = feature.longitude
        )
    }

    private fun buildAtonDetail(
        feature: WfsFeature,
        props: Map<String, String>,
        rawProps: Map<String, String>
    ): SeamarkDetail {
        val name = props["name"] ?: props["name_sv"] ?: ""
        val nameSv = props["name_sv"]
        val atonType = props["aton_type"] ?: ""
        val status = props["status"]
        val structure = props["structure_type"]
        val lit = props["lit"] == "yes"
        val lightChar = props["light_character"]?.take(200)
        val sector = props["sector_info"]?.take(200)
        val symbol = props["symbol"]
        val navCode = props["nav_category"]?.toIntOrNull()
        val aiskaytossa = props["ais"] == "yes"
        val mmsi = rawProps["mmsi"]?.toLongOrNull()

        val extra = mutableListOf<String>()
        props["owner"]?.let { extra.add("Omistaja: $it") }
        props["fairway_name"]?.let { extra.add("Väylä: $it") }
        props["construction_year"]?.let { extra.add("Rakennettu: $it") }
        if (aiskaytossa && mmsi != null) extra.add("AIS MMSI: $mmsi")

        val description = descriptionFor(atonType, structure, navCode, symbol)

        return SeamarkDetail(
            source = SeamarkSource.VV,
            id = feature.id,
            name = name,
            subtitle = nameSv,
            typeLabel = humanAtonTypeLabel(atonType),
            statusLabel = statusLabelFor(status),
            structureLabel = structureLabelFor(structure),
            hasLight = lit,
            lightCharacteristic = lightChar,
            description = description,
            sectorInfo = sector,
            extraInfo = extra,
            latitude = feature.latitude,
            longitude = feature.longitude,
            turvalaitenumero = props["aton_number"] ?: "",
            kaytossa = status != "removed",
            alityyppi = rawProps["alityyppi"],
            rakennusvuodet = props["construction_year"],
            omistaja = props["owner"],
            vaylanNimi = props["fairway_name"],
            mmsi = mmsi,
            aiskaytossa = aiskaytossa
        )
    }

    private fun buildNoticeDetail(
        feature: WfsFeature,
        props: Map<String, String>,
        rawProps: Map<String, String>
    ): SeamarkDetail {
        val signType = props["sign_type"]?.toIntOrNull() ?: 0
        val signLabel = humanNoticeType(signType)
        val restriction = props["restriction_value"]?.takeIf { it.isNotBlank() }
        val signText = props["sign_text"]?.takeIf { it.isNotBlank() }

        val description = when {
            restriction != null && signText != null -> "$signLabel — $signText ($restriction)"
            restriction != null -> "$signLabel ($restriction)"
            signText != null -> "$signLabel — $signText"
            else -> signLabel
        }

        val extra = mutableListOf<String>()
        restriction?.let { extra.add("Rajoitusarvo: $it") }
        signText?.let { extra.add("Lisäkilpi: $it") }
        rawProps["vlmlajityyppi"]?.let { extra.add("Koodi: $it") }

        return SeamarkDetail(
            source = SeamarkSource.VV,
            id = feature.id,
            name = signLabel,
            typeLabel = "Vesiliikennemerkki",
            description = description,
            hasLight = false,
            extraInfo = extra,
            latitude = feature.latitude,
            longitude = feature.longitude
        )
    }

    private fun humanNoticeType(signType: Int): String = when (signType) {
        1 -> "Ankkurointikielto"
        2, 3 -> "Kiinnittymis-/ankkurointikielto"
        4 -> "Ohittamis-/kohtaamiskielto"
        5 -> "Ohittamiskielto"
        6, 11 -> "Nopeusrajoitus"
        7 -> "Vesihiihdokielto"
        8 -> "Purjelautailukielto"
        9 -> "Ajosuuntakielto"
        10 -> "Vesipyöräilykielto"
         12 -> "Pysähtyminen"
        13 -> "Aallokon aiheuttamisen kielto"
        14 -> "Radioliikenne"
        15 -> "Aukkovaatimus"
        16 -> "Syväysrajoitus"
        17 -> "Silta"
        21, 28 -> "Radiotiedotus"
        22, 23 -> "Laituriin kiinnittyminen sallittu"
        24, 31, 32 -> "Avoin johto"
        25 -> "Puhelin"
        26, 27 -> "Lauttayhteys"
        29 -> "Juomavesi"
        30 -> "Kiellon päättyminen"
        33, 34 -> "Ajojärjestely"
        else -> "Vesiliikennemerkki"
    }

    private fun humanAtonTypeLabel(atonType: String): String = when (atonType) {
        "beacon", "buoy", "lateral_mark" -> "Poiju/viitta"
        "major_light" -> "Majakka"
        "sector_light" -> "Sektoriloisto"
        "directional_light" -> "Suuntaloisto"
        "light" -> "Loisto"
        "leading_light" -> "Linjaloisto"
        "side_light" -> "Sivuloisto"
        "auxiliary_light" -> "Apuloisto"
        "leading_mark" -> "Linjamerkki"
        "daymark" -> "Päivätunnus"
        "fog_signal" -> "Sumumerkki"
        "virtual_aton" -> "AIS-turvalaite"
        "radar_beacon" -> "Tutkamajakka"
        "radar_reflector" -> "Tutkamerkki"
        "topmark" -> "Huippumerkki"
        "mooring" -> "Kiinnitysmerkki"
        "special_mark" -> "Erikoismerkki"
        else -> atonType.replaceFirstChar { it.uppercase() }
    }

    private fun statusLabelFor(status: String?): String? = when (status) {
        "active" -> "Käytössä"
        "removed" -> "Poistettu"
        else -> status
    }

    private fun structureLabelFor(structure: String?): String? = when (structure) {
        "floating" -> "Kelluva"
        "fixed" -> "Kiinteä"
        else -> structure
    }

    private fun descriptionFor(
        atonType: String,
        structure: String?,
        navCode: Int?,
        symbol: String?
    ): String? {
        val rakenne = when (structure) {
            "floating" -> "kelluva"
            "fixed" -> "kiinteä"
            else -> null
        }
        if (atonType in setOf("beacon", "buoy", "lateral_mark") && navCode != null) {
            val tyyppi = when (navCode) {
                1 -> "Lateraalinen (punainen, vasen)"
                2 -> "Lateraalinen (vihreä, oikea)"
                3 -> "Pohjoiskardinaali"
                4 -> "Eteläkardinaali"
                5 -> "Länsikardinaali"
                6 -> "Itäkardinaali"
                7 -> "Yksittäisen vaaran merkki"
                8 -> "Vapaavesimerkki"
                9 -> "Erikoismerkki"
                else -> null
            }
            if (tyyppi != null && rakenne != null) return "$tyyppi, $rakenne"
            if (tyyppi != null) return tyyppi
        }
        val human = humanAtonTypeLabel(atonType)
        return if (rakenne != null) "$human, $rakenne" else human
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
        val icon = VesiLiikennemerkkiIconMapper.toIconName(signType)
        val feat = Feature.fromGeometry(Point.fromLngLat(feature.longitude, feature.latitude))
        feat.addStringProperty("icon", icon)
        feat.addStringProperty("providerId", feature.source)
        feat.addStringProperty("featureType", feature.featureType)
        // Build a label from the restriction value (e.g. "30 km/h" for speed
        // limit, "3.5 m" for height limit).
        val restriction = props["restriction_value"]?.takeIf { it.isNotBlank() }
        feat.addStringProperty("label", restriction ?: "")
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
}

