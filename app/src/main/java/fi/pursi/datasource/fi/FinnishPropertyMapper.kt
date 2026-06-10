package fi.pursi.datasource.fi

import fi.pursi.datasource.core.PropertyMapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinnishPropertyMapper @Inject constructor() : PropertyMapper {
    override val providerId = "fi-vayla-traficom"

    override fun mapKey(providerKey: String): String? = when (providerKey) {
        "turvalaitetyyppifi" -> "aton_type"
        "alityyppi" -> "structure_type"
        "navigointilajikoodi" -> "nav_category"
        "symboli" -> "symbol"
        "nimifi" -> "name"
        "nimisv" -> "name_sv"
        "valaistu" -> "lit"
        "loistojen_tiedot" -> "light_character"
        "valosektorien_tiedot" -> "sector_info"
        "vaylan_nimi" -> "fairway_name"
        "toimintatilakoodi" -> "status"
        "aisfi" -> "ais"
        "turvalaitenumero" -> "aton_number"
        "rakennusvuodet" -> "construction_year"
        "omistaja" -> "owner"
        "alkukulma" -> "sector_start"
        "loppukulma" -> "sector_end"
        "vari" -> "colour"
        "vlmlajityyppi" -> "sign_type"
        "rajoitusarvo" -> "restriction_value"
        "lisakilventeksti1" -> "sign_text"
        "loistotunnus" -> "light_id"
        else -> providerKey
    }

    override fun mapValue(key: String, providerValue: String): String? = when (key) {
        "aton_type" -> when (providerValue) {
            "Viitta" -> "beacon"
            "Poiju" -> "buoy"
            "Reunamerkki" -> "lateral_mark"
            "Merimajakka" -> "major_light"
            "Sektoriloisto" -> "sector_light"
            "Suuntaloisto" -> "directional_light"
            "Loisto" -> "light"
            "Linjaloisto" -> "leading_light"
            "Sivuloisto" -> "side_light"
            "Apuloisto" -> "auxiliary_light"
            "Linjamerkki" -> "leading_mark"
            "Paivatunnus" -> "daymark"
            "Sumumerkki" -> "fog_signal"
            "AIS-turvalaite" -> "virtual_aton"
            "Tutkamajakka" -> "radar_beacon"
            "Tutkamerkki" -> "radar_reflector"
            "Huippumerkki" -> "topmark"
            "Kiinnitysmerkki" -> "mooring"
            "Kummeli" -> "special_mark"
            "Muu merkki" -> "special_mark"
            "Merkin pohja" -> "special_mark"
            else -> providerValue
        }
        "structure_type" -> when (providerValue) {
            "KELLUVA" -> "floating"
            "KIINTEA" -> "fixed"
            else -> providerValue
        }
        "colour" -> when (providerValue) {
            "v" -> "green"
            "p" -> "red"
            "vi" -> "white"
            "k" -> "yellow"
            else -> providerValue
        }
        "lit", "ais" -> when (providerValue) {
            "K", "Kyllä" -> "yes"
            "E", "Ei" -> "no"
            else -> providerValue
        }
        "status" -> when (providerValue) {
            "1" -> "active"
            "2" -> "removed"
            else -> providerValue
        }
        else -> providerValue
    }
}
