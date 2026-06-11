package app.pursi.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import app.pursi.R

enum class PoiCategory(
    val id: String,
    val icon: ImageVector,
    val displayNameRes: Int,
    val overpassQuery: String
) {
    FUEL("fuel", Icons.Default.LocalGasStation, R.string.poi_fuel,
        """[out:json];(node["waterway"="fuel"](around:50000,{lat},{lon});way["waterway"="fuel"](around:50000,{lat},{lon}););out center;"""),

    SANITARY("sanitary", Icons.Default.CleaningServices, R.string.poi_sanitary,
        """[out:json];(node["sanitary_dump_station"="yes"](around:50000,{lat},{lon});way["sanitary_dump_station"="yes"](around:50000,{lat},{lon}););out center;"""),

    GUEST_HARBOUR("guest_harbour", Icons.Default.DirectionsBoat, R.string.poi_guest_harbour,
        """[out:json];(node["leisure"="marina"]["fee"="yes"](around:50000,{lat},{lon});way["leisure"="marina"]["fee"="yes"](around:50000,{lat},{lon});node["seamark:harbour:category"="marina"]["fee"="yes"](around:50000,{lat},{lon});way["seamark:harbour:category"="marina"]["fee"="yes"](around:50000,{lat},{lon}););out center;"""),

    ELECTRICITY("electricity", Icons.Default.Bolt, R.string.poi_electricity,
        """[out:json];(node["power_supply"="yes"]["leisure"="marina"](around:50000,{lat},{lon});way["power_supply"="yes"]["leisure"="marina"](around:50000,{lat},{lon}););out center;"""),

    BOATYARD("boatyard", Icons.Default.Build, R.string.poi_boatyard,
        """[out:json];(node["waterway"="boatyard"](around:50000,{lat},{lon});way["waterway"="boatyard"](around:50000,{lat},{lon});node["seamark:small_craft_facility:category"="boatyard"](around:50000,{lat},{lon});way["seamark:small_craft_facility:category"="boatyard"](around:50000,{lat},{lon}););out center;"""),

    SLIPWAY("slipway", Icons.Default.ArrowUpward, R.string.poi_slipway,
        """[out:json];(node["seamark:small_craft_facility:category"="slipway"](around:50000,{lat},{lon});way["seamark:small_craft_facility:category"="slipway"](around:50000,{lat},{lon});node["seamark:facility"="slipway"](around:50000,{lat},{lon});way["seamark:facility"="slipway"](around:50000,{lat},{lon}););out center;"""),

    WRECK("wreck", Icons.Default.Explore, R.string.poi_wreck,
        """[out:json];(node["seamark:type"="wreck"](around:50000,{lat},{lon});way["seamark:type"="wreck"](around:50000,{lat},{lon}););out center;"""),

    WILDERNESS_HUT("hut", Icons.Default.HolidayVillage, R.string.poi_hut,
        """[out:json];(node["tourism"="wilderness_hut"](around:50000,{lat},{lon}););out center;"""),

    BOAT_SHOP("boat_shop", Icons.Default.ShoppingCart, R.string.poi_boat_shop,
        """[out:json];(node["shop"="boat"](around:50000,{lat},{lon});node["shop"="marine"](around:50000,{lat},{lon});way["shop"="boat"](around:50000,{lat},{lon});way["shop"="marine"](around:50000,{lat},{lon}););out center;"""),

    DRINKING_WATER("drinking_water", Icons.Default.Water, R.string.poi_drinking_water,
        """[out:json];(node["drinking_water"="yes"](around:50000,{lat},{lon}););out center;""");
}

data class PoiResult(
    val osmId: Long,
    val osmType: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: PoiCategory,
    val tags: Map<String, String> = emptyMap()
) {
    val hasWebsite: Boolean get() = tags.containsKey("website")
    val hasFee: Boolean get() = tags.get("fee") == "yes"
    val hasPower: Boolean get() = tags.get("power_supply") == "yes"
    val hasSanitary: Boolean get() = tags.get("sanitary_dump_station") == "yes"
    val displayName: String get() = name.ifBlank {
        listOfNotNull(
            tags["seamark:name"],
            tags["addr:street"],
            "${latitude}${"%,.4f".format(latitude)}, ${"%,.4f".format(longitude)}"
        ).firstOrNull { it.isNotBlank() } ?: "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
    }
}
