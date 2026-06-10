package fi.pursi.navigation

import fi.pursi.location.SpeedCalculator

object RouteSimplifier {

    private const val EPSILON_METERS = 10.0

    fun simplify(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        return rdp(points, EPSILON_METERS)
    }

    private fun rdp(points: List<Pair<Double, Double>>, epsilon: Double): List<Pair<Double, Double>> {
        if (points.size < 3) return points

        var dMax = 0.0
        var index = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], first, last)
            if (d > dMax) {
                dMax = d
                index = i
            }
        }

        return if (dMax > epsilon) {
            val left = rdp(points.subList(0, index + 1), epsilon)
            val right = rdp(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(
        point: Pair<Double, Double>,
        lineA: Pair<Double, Double>,
        lineB: Pair<Double, Double>
    ): Double {
        val (x, y) = point
        val (x1, y1) = lineA
        val (x2, y2) = lineB

        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy

        if (lenSq == 0.0) {
            return SpeedCalculator.distanceBetween(y1, x1, y, x)
        }

        val t = ((x - x1) * dx + (y - y1) * dy) / lenSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy

        return SpeedCalculator.distanceBetween(y, x, projY, projX)
    }
}
