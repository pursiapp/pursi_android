package app.pursi.datasource.fi

import app.pursi.data.dao.WfsFeatureDao
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class TraficomDepthProviderTest {

    private val provider = TraficomDepthProvider(mock(WfsFeatureDao::class.java))

    @Test
    fun `pointInPolygon returns true for point inside rectangle`() {
        val polygon = listOf(
            Pair(60.0, 24.0),
            Pair(60.0, 25.0),
            Pair(61.0, 25.0),
            Pair(61.0, 24.0)
        )
        assertTrue(provider.pointInPolygon(60.5, 24.5, polygon))
    }

    @Test
    fun `pointInPolygon returns false for point outside rectangle`() {
        val polygon = listOf(
            Pair(60.0, 24.0),
            Pair(60.0, 25.0),
            Pair(61.0, 25.0),
            Pair(61.0, 24.0)
        )
        assertFalse(provider.pointInPolygon(62.0, 24.5, polygon))
    }

    @Test
    fun `pointInPolygon returns true for point at polygon edge`() {
        val polygon = listOf(
            Pair(60.0, 24.0),
            Pair(60.0, 25.0),
            Pair(61.0, 25.0),
            Pair(61.0, 24.0)
        )
        assertTrue(provider.pointInPolygon(60.0, 24.5, polygon))
    }

    @Test
    fun `pointInPolygon handles null polygon`() {
        assertFalse(provider.pointInPolygon(60.5, 24.5, null))
    }

    @Test
    fun `pointInPolygon handles polygon with less than 3 points`() {
        val polygon = listOf(Pair(60.0, 24.0), Pair(61.0, 25.0))
        assertFalse(provider.pointInPolygon(60.5, 24.5, polygon))
    }

    @Test
    fun `pointInPolygon returns true for complex polygon`() {
        val polygon = listOf(
            Pair(60.0, 24.0),
            Pair(60.0, 25.0),
            Pair(61.0, 25.0),
            Pair(61.0, 24.5),
            Pair(60.5, 24.5),
            Pair(60.5, 24.0)
        )
        assertTrue(provider.pointInPolygon(60.2, 24.3, polygon))
        assertFalse(provider.pointInPolygon(60.7, 24.8, polygon))
    }

    @Test
    fun `parsePolygonPoints returns null for Point geometry`() {
        val geo = """{"type":"Point","coordinates":[24.0,60.0]}"""
        val result = provider.parsePolygonPoints(geo)
        assertTrue(result == null || result.isEmpty())
    }

    @Test
    fun `parsePolygonPoints parses Polygon geometry`() {
        val geo = """{"type":"Polygon","coordinates":[[[24.0,60.0],[25.0,60.0],[25.0,61.0],[24.0,61.0],[24.0,60.0]]]}"""
        val result = provider.parsePolygonPoints(geo)
        assertTrue(result != null && result.size >= 4)
    }

    @Test
    fun `parseLineStringPoints parses LineString geometry`() {
        val geo = """{"type":"LineString","coordinates":[[24.0,60.0],[25.0,60.5],[26.0,61.0]]}"""
        val result = provider.parseLineStringPoints(geo)
        assertTrue(result != null && result.size == 3)
    }
}
