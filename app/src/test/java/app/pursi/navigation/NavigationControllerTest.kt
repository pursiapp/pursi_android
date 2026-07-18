package app.pursi.navigation

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class NavigationControllerTest {

    // Route: 3 waypoints roughly Helsinki → Porvoo area (60°N, 24–25°E)
    // A→B is ~28.3 km (~15.3 nm), bearing ~78° (ENE)
    // B→C is ~35 km (~19 nm), bearing ~79° (ENE)
    private val A = LatLng(60.0, 24.0)
    private val B = LatLng(60.05, 24.5)
    private val C = LatLng(60.10, 25.0)
    private val route = listOf(A, B, C)

    // ── findCurrentWaypointIndex — edge cases ──────────────────────

    @Test
    fun `empty waypoints returns -1`() {
        val result = NavigationController.findCurrentWaypointIndex(
            LatLng(60.0, 24.5), 0f, emptyList(), 0
        )
        assertEquals(-1, result)
    }

    @Test
    fun `single waypoint returns 0 when far`() {
        val result = NavigationController.findCurrentWaypointIndex(
            LatLng(60.01, 24.01), 0f, listOf(A), 0
        )
        assertEquals(0, result)
    }

    @Test
    fun `single waypoint returns 1 when at it`() {
        val result = NavigationController.findCurrentWaypointIndex(
            A, 0f, listOf(A), 0
        )
        assertEquals(1, result)
    }

    // ── findCurrentWaypointIndex — rule 1 (arrival radius 100 m) ───

    @Test
    fun `within 100m of waypoint B at index 1 advances to index 2`() {
        val boat = LatLng(60.05015, 24.5001) // ~15 m from B
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 1
        )
        assertEquals(2, result)
    }

    @Test
    fun `exactly at waypoint B at index 1 advances to index 2`() {
        val result = NavigationController.findCurrentWaypointIndex(
            B, 0f, route, 1
        )
        assertEquals(2, result)
    }

    @Test
    fun `last waypoint within 100m completes`() {
        val boat = LatLng(60.10005, 25.00005) // ~7 m from C
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 2
        )
        assertEquals(3, result)
    }

    @Test
    fun `last waypoint far away does not complete`() {
        val boat = LatLng(60.11, 25.02) // ~2 km from C
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 2
        )
        assertEquals(2, result)
    }

    // ── findCurrentWaypointIndex — rule 2 (along-track) ────────────

    @Test
    fun `midpoint of A-B does not advance (index 0 has no prev)`() {
        // At index 0 with no previous waypoint, only arrival radius applies
        val boat = LatLng(60.025, 24.25)
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 0
        )
        assertEquals(0, result)
    }

    @Test
    fun `past B on course line advances via along-track`() {
        val boat = LatLng(60.07, 24.7) // beyond B toward C
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 1
        )
        assertEquals(2, result)
    }

    @Test
    fun `past B off to the north advances via along-track`() {
        val boat = LatLng(60.07, 24.52)
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 1
        )
        assertEquals(2, result)
    }

    @Test
    fun `approaching B from behind does not advance`() {
        val boat = LatLng(60.03, 24.35)
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 1
        )
        assertEquals(1, result)
    }

    @Test
    fun `side-pass south of B advances via along-track`() {
        // Boat at (60.00, 24.55) is 22 km SE of A with along-track projection
        // past B's perpendicular
        val boat = LatLng(60.00, 24.55)
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 1
        )
        assertEquals(2, result)
    }

    @Test
    fun `far behind B does not advance`() {
        val boat = LatLng(59.98, 24.3)
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 1
        )
        assertEquals(1, result)
    }

    // ── findCurrentWaypointIndex — rule 3 (bearing + 500m) ─────────

    @Test
    fun `sharp corner-cut within 500m advances via bearing check`() {
        // Boat near B but not tracked along A→B, within 500m of B
        // and past the perpendicular
        val boat = LatLng(60.06, 24.51) // ~1.1 km ENE of B, well within
        val result = NavigationController.findCurrentWaypointIndex(
            boat, 0f, route, 1
        )
        assertEquals(2, result)
    }

    // ── crossTrackError ────────────────────────────────────────────

    @Test
    fun `crossTrackError xte near zero for midpoint of A-B`() {
        val boat = LatLng(60.025, 24.25)
        val result = NavigationController.crossTrackError(boat, A, B)
        assertTrue("XTE should be near zero, was ${result.xteNm} nm", result.xteNm < 0.02)
    }

    @Test
    fun `crossTrackError boat right of track has positive xte`() {
        // Boat south of the A→B line: segment bearing ~78°, boatBearing > 78°
        // → sin(positive) > 0 → xte > 0 → RIGHT_OF_TRACK
        val boat = LatLng(60.02, 24.25)
        val result = NavigationController.crossTrackError(boat, A, B)
        assertEquals(XteDirection.RIGHT_OF_TRACK, result.direction)
        assertTrue(result.xteNm > 0)
    }

    @Test
    fun `crossTrackError boat left of track has positive xte`() {
        // Boat north of the A→B line: segment bearing ~78°, boatBearing < 78°
        // → sin(negative) < 0 → xte < 0 → LEFT_OF_TRACK
        val boat = LatLng(60.03, 24.25)
        val result = NavigationController.crossTrackError(boat, A, B)
        assertEquals(XteDirection.LEFT_OF_TRACK, result.direction)
        assertTrue(result.xteNm > 0)
    }

    @Test
    fun `crossTrackError progress between 0 and 1`() {
        val boat = LatLng(60.025, 24.25)
        val result = NavigationController.crossTrackError(boat, A, B)
        assertTrue("Progress 0-1, was ${result.progress}",
            result.progress >= 0.0 && result.progress <= 1.0)
        assertTrue("Progress near 0.5, was ${result.progress}",
            result.progress > 0.3 && result.progress < 0.7)
    }

    // ── distance helpers ───────────────────────────────────────────

    @Test
    fun `distanceMeters A-B correct`() {
        val result = NavigationController.distanceMeters(A, B)
        // ~28.3 km — accept ±3 km
        assertTrue("A→B ~28 km, was $result", result > 25_000.0 && result < 32_000.0)
    }

    @Test
    fun `distanceNm A-B correct`() {
        val result = NavigationController.distanceNm(A, B)
        // ~15.3 nm — accept ±3 nm
        assertTrue("A→B ~15 nm, was $result", result > 12.0 && result < 18.0)
    }

    @Test
    fun `bearingTo A-B is ENE`() {
        val result = NavigationController.bearingTo(A, B)
        assertTrue("bearing ~78°, was $result", result in 60.0..90.0)
    }

    // ── relativeBearing ────────────────────────────────────────────

    @Test
    fun `relativeBearing zero when heading directly at target`() {
        val heading = NavigationController.bearingTo(B, C).toFloat()
        val rel = NavigationController.relativeBearing(B, heading, C)
        assertTrue("rel ≈ 0, was $rel", abs(rel) < 1.0)
    }

    @Test
    fun `relativeBearing 180 when heading away from target`() {
        // Boat at C heading in the B→C direction (away from B)
        val heading = NavigationController.bearingTo(B, C).toFloat()
        val rel = NavigationController.relativeBearing(C, heading, B)
        assertTrue("rel ≈ 180, was $rel", abs(abs(rel) - 180.0) < 2.0)
    }

    // ── alongTrackSignedMeters ─────────────────────────────────────

    @Test
    fun `alongTrack at midpoint equals half segment`() {
        val boat = LatLng(60.025, 24.25)
        val at = NavigationController.alongTrackSignedMeters(A, B, boat)
        val segLen = NavigationController.distanceMeters(A, B)
        val ratio = at / segLen
        assertTrue("ratio ~0.5, was $ratio", ratio > 0.4 && ratio < 0.6)
    }

    @Test
    fun `alongTrack past B exceeds segment length`() {
        val boat = LatLng(60.07, 24.7)
        val at = NavigationController.alongTrackSignedMeters(A, B, boat)
        val segLen = NavigationController.distanceMeters(A, B)
        assertTrue("alongTrack=$at > segLen=$segLen", at > segLen)
    }
}
