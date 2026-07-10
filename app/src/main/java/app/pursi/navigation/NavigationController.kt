package app.pursi.navigation

import org.maplibre.android.geometry.LatLng
import app.pursi.location.SpeedCalculator
import kotlin.math.*

object NavigationController {

    private const val ARRIVAL_RADIUS_METERS = 30.0
    private const val ALONG_TRACK_THRESHOLD = 0.95
    private val earthRadius = 6371000.0

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
        val lat1 = Math.toRadians(wpA.latitude)
        val latB = Math.toRadians(boatPos.latitude)
        val lngB = Math.toRadians(boatPos.longitude)
        val lng1 = Math.toRadians(wpA.longitude)

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

        if (distToCurrent < ARRIVAL_RADIUS_METERS) {
            return currentIndex + 1
        }

        val prevWp = if (currentIndex > 0) waypoints[currentIndex - 1] else boatPos
        val nextWp = waypoints[currentIndex + 1]

        val alongTrack = alongTrackRatio(prevWp, currentWp, boatPos)
        if (alongTrack > ALONG_TRACK_THRESHOLD) {
            return currentIndex + 1
        }

        if (currentIndex < waypoints.size - 1) {
            val distToNext = distanceMeters(boatPos, nextWp)
            val prevDist = distToCurrent
            if (prevDist > 50.0) {
                val dDist = prevDist - distToCurrent
                if (dDist < -1.0 && distToNext < prevDist * 0.8) {
                    return currentIndex + 1
                }
            }
        }

        return currentIndex
    }

    private fun alongTrackRatio(wpA: LatLng, wpB: LatLng, boat: LatLng): Double {
        val segLen = distanceMeters(wpA, wpB)
        if (segLen < 1.0) return 0.0

        val ax = wpA.longitude
        val ay = wpA.latitude
        val bx = wpB.longitude
        val by = wpB.latitude
        val cx = boat.longitude
        val cy = boat.latitude

        val dx = bx - ax
        val dy = by - ay
        val dot = (cx - ax) * dx + (cy - ay) * dy
        val ratio = dot / (dx * dx + dy * dy)
        return ratio.coerceIn(0.0, 1.0)
    }
}

data class CteResult(
    val xteNm: Double,
    val direction: XteDirection,
    val progress: Double = 0.0
)

enum class XteDirection { ON_TRACK, LEFT_OF_TRACK, RIGHT_OF_TRACK }
