package app.pursi.ais

import kotlinx.serialization.Serializable

data class AisVessel(
    val mmsi: Int,
    val lat: Double,
    val lon: Double,
    val sog: Float,
    val cog: Float,
    val navStat: Int,
    val heading: Int,
    val rot: Float,
    val posAcc: Boolean,
    val raim: Boolean,
    val timestampExternal: Long,
    var name: String? = null,
    var shipType: Int? = null,
    var destination: String? = null,
    var draught: Float? = null,
    var callSign: String? = null,
    var imo: Int? = null
) {
    val shipTypeLabel: String
        get() = shipType?.let { shipTypeName(it) } ?: "Unknown"
}

fun shipTypeName(code: Int): String = when (code) {
    20, 21, 22, 23, 24, 25, 26, 27, 28, 29 -> "Wing in ground"
    30 -> "Fishing"
    31 -> "Towing"
    32 -> "Towing (long)"
    33 -> "Dredging"
    34 -> "Diving"
    35 -> "Military"
    36 -> "Sailing"
    37 -> "Pleasure craft"
    40, 41, 42, 43, 44, 45, 46, 47, 48, 49 -> "High speed craft"
    50 -> "Pilot"
    51 -> "SAR"
    52 -> "Tug"
    53 -> "Port tender"
    54 -> "Anti-pollution"
    55 -> "Law enforcement"
    58 -> "Medical transport"
    60, 61, 62, 63, 64, 65, 66, 67, 68, 69 -> "Passenger"
    70, 71, 72, 73, 74, 75, 76, 77, 78, 79 -> "Cargo"
    80, 81, 82, 83, 84, 85, 86, 87, 88, 89 -> "Tanker"
    90, 91, 92, 93, 94, 95, 96, 97, 98, 99 -> "Other"
    else -> "Type $code"
}

data class VesselMetadata(
    val mmsi: Int,
    val name: String?,
    val shipType: Int?,
    val destination: String?,
    val draught: Float?,
    val callSign: String?,
    val imo: Int?
)

@Serializable
data class AisLocationsResponse(
    val type: String,
    val dataUpdatedTime: String = "",
    val features: List<AisVesselFeature> = emptyList()
)

@Serializable
data class AisVesselFeature(
    val mmsi: Int,
    val geometry: AisGeoPoint,
    val properties: AisVesselProperties = AisVesselProperties()
)

@Serializable
data class AisGeoPoint(
    val coordinates: List<Double>
)

@Serializable
data class AisVesselProperties(
    val sog: Float = 0f,
    val cog: Float = 360f,
    val navStat: Int = 0,
    val rot: Float = 0f,
    val posAcc: Boolean = false,
    val raim: Boolean = false,
    val heading: Int = 511,
    val timestamp: Int = 0,
    val timestampExternal: Long = 0L
)

@Serializable
data class AisVesselMetadataResponse(
    val mmsi: Int,
    val name: String? = null,
    val shipType: Int? = null,
    val destination: String? = null,
    val draught: Int? = null,
    val callSign: String? = null,
    val imo: Int? = null,
    val posType: Int? = null,
    val timestamp: Long = 0L
)
