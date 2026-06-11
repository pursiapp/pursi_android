package app.pursi.datasource.template

import app.pursi.datasource.core.PropertyMapper

/**
 * TEMPLATE: Kopioi tämä tiedosto datasource/<country>/ ja muokkaa.
 *
 * PropertyMapper kääntää maakohtaiset WFS-ominaisuusavaimet IALA-standardiin.
 * IalaFeatureRenderer hoitaa renderöinnin automaattisesti — ei tarvitse omaa.
 */
class TemplatePropertyMapper : PropertyMapper {
    override val providerId = "xx-provider" // Korvaa esim. "no-kartverket"

    override fun mapKey(providerKey: String): String? = when (providerKey) {
        // "navigasjonstype" -> "aton_type"  // Norjalainen -> IALA
        // "konstruksjon" -> "structure_type"
        // "navn" -> "name"
        // "hoyde" -> "height"
        else -> providerKey // Tuntemattomat läpivirtaukseen
    }

    override fun mapValue(key: String, providerValue: String): String? = when (key) {
        // "aton_type" -> when (providerValue) {
        //     "fyr" -> "major_light"
        //     "bøye" -> "buoy"
        //     "merke" -> "beacon"
        //     else -> providerValue
        // }
        // "structure_type" -> when (providerValue) {
        //     "flytende" -> "floating"
        //     "fast" -> "fixed"
        //     else -> providerValue
        // }
        else -> providerValue
    }
}
