package app.pursi.datasource.fi

object TurvalaiteIconMapper {

    fun humanReadableName(osmType: String): String = when (osmType) {
        "beacon_cardinal" -> "Viittamerkki"
        "beacon_isolated_danger" -> "Karioviitta"
        "beacon_lateral" -> "Lateraaliviitta"
        "beacon_safe_water" -> "Turvavesiviitta"
        "beacon_special_purpose" -> "Erikoisviitta"
        "buoy_cardinal" -> "Viittapoiju"
        "buoy_isolated_danger" -> "Kariopoiju"
        "buoy_lateral" -> "Lateraalipoiju"
        "buoy_safe_water" -> "Turvavesipoiju"
        "buoy_special_purpose" -> "Erikoispoiju"
        "buoy_installation" -> "Poiju (asennus)"
        "light_major" -> "Majakka"
        "light_minor" -> "Valo"
        "light_vessel" -> "Valolaiva"
        "daymark" -> "Päivämerkki"
        "fog_signal" -> "Sumumerkki"
        "wreck" -> "Hylky"
        "hulk" -> "Hylky (hylätty alus)"
        "pontoon" -> "Ponttoni"
        "mooring" -> "Kiinnityspoiju"
        "signal_station_warning" -> "Varoitusasema"
        else -> osmType.replace("_", " ")
    }

    private val NAV_CODE_TO_IALA = mapOf(
        1 to "lateral",
        2 to "lateral",
        3 to "cardinal",
        4 to "cardinal",
        5 to "cardinal",
        6 to "cardinal",
        7 to "isolated_danger",
        8 to "safe_water",
        9 to "special_purpose"
    )

    private val NAV_CODE_TO_ICON_FLOAT = mapOf(
        1 to "bl_red",
        2 to "bl_green",
        3 to "bc_north",
        4 to "bc_south",
        5 to "bc_west",
        6 to "bc_east",
        7 to "bid",
        8 to "bsw",
        9 to "bsp"
    )

    private val NAV_CODE_TO_ICON_FIXED = mapOf(
        1 to "bl_bcn_red",
        2 to "bl_bcn_green",
        3 to "bc_bcn_north",
        4 to "bc_bcn_south",
        5 to "bc_bcn_west",
        6 to "bc_bcn_east",
        7 to "bid_bcn",
        8 to "bsw_bcn",
        9 to "bsp_bcn"
    )

    fun markDescription(
        turvalaitetyyppiFi: String?,
        alityyppi: String?,
        navigointilajikoodi: String? = null
    ): String? {
        val isFloating = alityyppi == "KELLUVA"
        val rakenne = if (isFloating) "kelluva" else "kiinteä"
        val navCode = parseNavCode(navigointilajikoodi)

        if (turvalaitetyyppiFi in setOf("Viitta", "Poiju", "Reunamerkki") && navCode != null) {
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
            return if (tyyppi != null) "$tyyppi, $rakenne" else null
        }

        return turvalaitetyyppiFi?.let {
            if (alityyppi != null) "$it, $rakenne" else it
        }
    }

    fun parseNavCode(raw: String?): Int? =
        raw?.replace(".0", "")?.toIntOrNull()

    fun toSeamarkType(
        turvalaitetyyppiFi: String?,
        alityyppi: String?,
        navigointilajikoodi: String? = null
    ): String {
        val isFloating = alityyppi == "KELLUVA"
        val navCode = parseNavCode(navigointilajikoodi)

        if (turvalaitetyyppiFi in setOf("Viitta", "Poiju", "Reunamerkki") && navCode != null) {
            val category = NAV_CODE_TO_IALA[navCode] ?: "special_purpose"
            val prefix = if (isFloating) "buoy" else "beacon"
            return "${prefix}_$category"
        }

        return when (turvalaitetyyppiFi) {
            "Merimajakka" -> "light_major"
            "Sektoriloisto", "Suuntaloisto",
            "Loisto", "Linjaloisto", "Sivuloisto" -> "light_minor"
            "Apuloisto" -> "light_minor"
            "Linjamerkki" -> "daymark"
            "Päivätunnus" -> "daymark"
            "Sumumerkki" -> "fog_signal"
            "AIS-turvalaite" -> "virtual_aton"
            "Tutkamajakka", "Tutkamerkki" -> "fog_signal"
            "Huippumerkki" -> "topmark"
            "Kiinnitysmerkki" -> "mooring"
            "Kummeli", "Muu merkki", "Merkin pohja" -> "beacon_special_purpose"
            else -> "beacon_special_purpose"
        }
    }

    fun extractProperty(props: String, key: String): String? {
        return props.lines().firstOrNull { line ->
            line.startsWith("$key=", ignoreCase = true)
        }?.substringAfter("=")?.trim()?.takeIf { it != "null" && it.isNotBlank() }
    }

    fun isIALAType(seamarkType: String): Boolean = seamarkType in setOf(
        "buoy_cardinal", "buoy_lateral", "buoy_safe_water", "buoy_isolated_danger",
        "buoy_special_purpose", "buoy_installation",
        "beacon_cardinal", "beacon_lateral", "beacon_safe_water", "beacon_isolated_danger",
        "beacon_special_purpose",
        "light_major", "light_minor", "light_float", "light_vessel",
        "daymark", "fog_signal", "virtual_aton", "topmark", "mooring"
    )

    fun roundCoord(value: Double): Long = (value * 1000).toLong()

    fun toIconName(
        turvalaitetyyppiFi: String?,
        alityyppi: String?,
        symboli: String? = null,
        navigointilajikoodi: String? = null
    ): String {
        val isFloating = alityyppi == "KELLUVA"
        val navCode = parseNavCode(navigointilajikoodi)

        if (turvalaitetyyppiFi in setOf("Viitta", "Poiju", "Reunamerkki") && navCode != null) {
            val icons = if (isFloating) NAV_CODE_TO_ICON_FLOAT else NAV_CODE_TO_ICON_FIXED
            return icons[navCode] ?: if (isFloating) "bsp" else "bsp_bcn"
        }

        if (turvalaitetyyppiFi in setOf("Viitta", "Poiju")) {
            return when (symboli?.lowercase()) {
                "a", "b", "g" -> "bl_red"
                "c", "d", "i" -> "bl_green"
                "e", "f", "k" -> "bc_north"
                "g", "h", "m" -> "bc_south"
                "i", "j", "o" -> "bc_west"
                "k", "l", "q" -> "bc_east"
                "n" -> "bid"
                "p" -> "bsw"
                "r", "w" -> "bsp"
                else -> if (isFloating) "bsp" else "bsp_bcn"
            }
        }

        if (turvalaitetyyppiFi == "Reunamerkki") {
            return when (symboli?.uppercase()) {
                "5" -> "bl_bcn_red"
                "7" -> "bl_bcn_green"
                "9" -> "bc_bcn_north"
                "A" -> "bc_bcn_south"
                "C" -> "bc_bcn_west"
                "E" -> "bc_bcn_east"
                "4" -> "bid_bcn"
                else -> "bl_bcn_red"
            }
        }

        return when (turvalaitetyyppiFi) {
            "Merimajakka", "Sektoriloisto",
            "Suuntaloisto", "Loisto", "Linjaloisto", "Sivuloisto" -> "lmaj_red"
            "Apuloisto" -> "lmin_yellow"
            "Linjamerkki" -> "daymark"
            "Kummeli", "Tutkamerkki", "Muu merkki" -> "daymark"
            "Päivätunnus" -> "daymark"
            "Sumumerkki" -> "fog"
            "AIS-turvalaite" -> "virt"
            "Kiinnitysmerkki" -> "moor_buoy"
            "Merkin pohja" -> "lmaj_red"
            else -> "lmaj_red"
        }
    }
}
