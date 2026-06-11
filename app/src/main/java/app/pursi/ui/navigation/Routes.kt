package app.pursi.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import app.pursi.R
import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    data class Map(
        val searchLat: Double? = null,
        val searchLon: Double? = null,
        val viewingRouteId: String? = null,
        val viewingTrackId: String? = null
    )

    @Serializable data object Weather
    @Serializable data object RouteList
    @Serializable data object Settings
}

enum class BottomNavItem(
    val route: kotlin.Any,
    val routePattern: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Map(Routes.Map(), "app.pursi.ui.navigation.Routes.Map", R.string.map, Icons.Default.DirectionsBoat),
    Weather(Routes.Weather, "app.pursi.ui.navigation.Routes.Weather", R.string.weather, Icons.Default.Cloud),
    RouteList(Routes.RouteList, "app.pursi.ui.navigation.Routes.RouteList", R.string.routes, Icons.Default.Route),
    Settings(Routes.Settings, "app.pursi.ui.navigation.Routes.Settings", R.string.settings, Icons.Default.Settings)
}
