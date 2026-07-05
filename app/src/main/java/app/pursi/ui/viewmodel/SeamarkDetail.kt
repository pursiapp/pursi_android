package app.pursi.ui.viewmodel

enum class SeamarkSource { VV, OSM }

data class SeamarkDetail(
    val source: SeamarkSource,
    val id: Long = 0L,
    val name: String = "",
    val subtitle: String? = null,
    val typeLabel: String? = null,
    val statusLabel: String? = null,
    val structureLabel: String? = null,
    val hasLight: Boolean = false,
    val lightCharacteristic: String? = null,
    val description: String? = null,
    val sectorInfo: String? = null,
    val extraInfo: List<String> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    // VV-specific detail fields, populated by IalaFeatureRenderer.handleClick for
    // navigation_aid / light / daymark feature types and consumed by the popup UI.
    val turvalaitenumero: String = "",
    val kaytossa: Boolean = true,
    val alityyppi: String? = null,
    val rakennusvuodet: String? = null,
    val omistaja: String? = null,
    val vaylanNimi: String? = null,
    val mmsi: Long? = null,
    val aiskaytossa: Boolean = false
)
