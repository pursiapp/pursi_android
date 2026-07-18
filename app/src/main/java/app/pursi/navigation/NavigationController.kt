package app.pursi.navigation

import org.maplibre.android.geometry.LatLng
import app.pursi.location.SpeedCalculator
import kotlin.math.*

object NavigationController {

    private const val ARRIVAL_RADIUS_METERS = 100.0
    private const val PERPENDICULAR_RADIUS_METERS = 500.0

    fun bearingTo(from: LatLng, to: LatLng): Double {
        return SpeedCalculator.bearingBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude
        ).toDouble()
    }

    fun distanceNm(from: LatLng, to: LatLng): Double {
        return SpeedCalculator.distanceNm(
            from.latitude, from.longitude,
            to.latitude, to.longitude
        )
    }

    fun distanceMeters(from: LatLng, to: LatLng): Double {
        return SpeedCalculator.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude
        )
    }

    fun relativeBearing(from: LatLng, headingDeg: Float, to: LatLng): Double {
        val bearing = bearingTo(from, to)
        var rel = bearing - headingDeg
        if (rel > 180.0) rel -= 360.0
        if (rel < -180.0) rel += 360.0
        return rel
    }

    fun crossTrackError(boatPos: LatLng, wpA: LatLng, wpB: LatLng): CteResult {
        val d = distanceMeters(wpA, wpB)
        if (d < 1.0) return CteResult(0.0, XteDirection.ON_TRACK)

        val segmentBearing = Math.toRadians(bearingTo(wpA, wpB))
        val boatBearing = Math.toRadians(bearingTo(wpA, boatPos))
        val boatDist = distanceMeters(wpA, boatPos)

        val alongTrack = boatDist * cos(boatBearing - segmentBearing)
        val xte = boatDist * sin(boatBearing - segmentBearing)

        val direction = when {
            abs(xte) < 1.0 -> XteDirection.ON_TRACK
            xte > 0 -> XteDirection.RIGHT_OF_TRACK
            else -> XteDirection.LEFT_OF_TRACK
        }

        val progress = if (d > 0) (alongTrack / d).coerceIn(0.0, 1.0) else 0.0
        return CteResult(abs(xte) / 1852.0, direction, progress)
    }

    fun alongTrackSignedMeters(wpA: LatLng, wpB: LatLng, boat: LatLng): Double {
        val segLen = distanceMeters(wpA, wpB)
        if (segLen < 1.0) return 0.0
        val segmentBearing = Math.toRadians(bearingTo(wpA, wpB))
        val boatBearing = Math.toRadians(bearingTo(wpA, boat))
        val boatDist = distanceMeters(wpA, boat)
        return boatDist * cos(boatBearing - segmentBearing)
    }

    fun findCurrentWaypointIndex(
        boatPos: LatLng,
        boatHeading: Float,
        waypoints: List<LatLng>,
        currentIndex: Int
    ): Int {
        if (waypoints.isEmpty()) return -1
        if (currentIndex >= waypoints.size - 1) {
            val dist = distanceMeters(boatPos, waypoints.last())
            return if (dist < ARRIVAL_RADIUS_METERS) waypoints.size else currentIndex
        }

        val currentWp = waypoints[currentIndex]
        val distToCurrent = distanceMeters(boatPos, currentWp)

        // Rule 1: within arrival radius
        if (distToCurrent < ARRIVAL_RADIUS_METERS) {
            return currentIndex + 1
        }

        val nextWp = waypoints[currentIndex + 1]

        // Rule 2: great-circle along-track past segment
        // Projects boat position onto the previous-to-current segment using
        // proper great-circle math.  If the projected distance exceeds the
        // segment length, the boat has passed the perpendicular through
        // the current waypoint — even when sailing off the line.
        if (currentIndex > 0) {
            val prevWp = waypoints[currentIndex - 1]
            val alongTrack = alongTrackSignedMeters(prevWp, currentWp, boatPos)
            val segLen = distanceMeters(prevWp, currentWp)
            if (alongTrack > segLen) {
                return currentIndex + 1
            }
        }

        // Rule 3: past-the-perpendicular bearing check (within 500m)
        // Catches sharp corner-cuts where the boat passes the waypoint
        // without having travelled the full segment distance from the
        // previous waypoint.
        val bearingToBoat = bearingTo(currentWp, boatPos)
        val bearingToNext = bearingTo(currentWp, nextWp)
        var diff = abs(bearingToBoat - bearingToNext)
        if (diff > 180.0) diff = 360.0 - diff
        if (diff <= 90.0 && distToCurrent < PERPENDICULAR_RADIUS_METERS) {
            return currentIndex + 1
        }

        return currentIndex
    }
}

data class CteResult(
    val xteNm: Double,
    val direction: XteDirection,
    val progress: Double = 0.0
)

enum class XteDirection { ON_TRACK, LEFT_OF_TRACK, RIGHT_OF_TRACK }
