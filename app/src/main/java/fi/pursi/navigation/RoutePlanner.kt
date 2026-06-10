package fi.pursi.navigation

import fi.pursi.data.model.Waypoint
import fi.pursi.location.SpeedCalculator
import kotlin.math.roundToInt

data class Route(
    val waypoints: List<Waypoint>,
    val totalDistanceNm: Double = 0.0,
    val estimatedTimeHours: Double = 0.0
)

object RoutePlanner {

    fun calculateRoute(waypoints: List<Waypoint>): Route {
        if (waypoints.size < 2) return Route(waypoints)

        var totalDistanceNm = 0.0
        for (i in 0 until waypoints.size - 1) {
            val wp1 = waypoints[i]
            val wp2 = waypoints[i + 1]
            totalDistanceNm += SpeedCalculator.distanceNm(
                wp1.latitude, wp1.longitude,
                wp2.latitude, wp2.longitude
            )
        }
        return Route(
            waypoints = waypoints,
            totalDistanceNm = totalDistanceNm,
            estimatedTimeHours = 0.0
        )
    }

    fun estimateTime(route: Route, speedKnots: Float): Route {
        if (speedKnots <= 0) return route
        val hours = route.totalDistanceNm / speedKnots
        return route.copy(estimatedTimeHours = hours)
    }

    fun formatTimeEstimate(hours: Double): String {
        val totalMinutes = (hours * 60).roundToInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 -> "${h}h ${m}min"
            else -> "${m}min"
        }
    }

    fun formatDistanceNm(distanceNm: Double): String {
        return "%.1f".format(distanceNm)
    }
}
