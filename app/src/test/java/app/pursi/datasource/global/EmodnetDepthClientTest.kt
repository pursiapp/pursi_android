package app.pursi.datasource.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmodnetDepthClientTest {

    private val client = EmodnetDepthClient(okhttp3.OkHttpClient())

    @Test
    fun `gridKeysForBbox returns quantized grid keys`() {
        val keys = client.gridKeysForBbox(59.44, 24.68, 59.48, 24.72)
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.all { it.startsWith("lat=") })
        assertTrue(keys.all { it.contains("_lng=") })
    }

    @Test
    fun `gridKeysForBbox keys roundtrip through parseGridKey`() {
        val parsed = client.parseGridKey("lat=59.4400_lng=24.6800")
        assertNotNull(parsed)
    }

    @Test
    fun `parseGridKey returns correct values`() {
        val pair = client.parseGridKey("lat=59.4500_lng=24.7500")
        assertEquals(59.45, pair!!.first, 0.0001)
        assertEquals(24.75, pair.second, 0.0001)
    }

    @Test
    fun `parseGridKey returns null for invalid key`() {
        assertNull(client.parseGridKey("invalid"))
        assertNull(client.parseGridKey(""))
    }

    @Test
    fun `parseRestDepth returns DepthResult for negative avg`() {
        val json = """{"avg":-12.5,"elementarySurfaces":1}"""
        val result = client.parseRestDepth(json)
        assertNotNull(result)
        assertEquals(12.5f, result!!.avg, 0.01f)
    }

    @Test
    fun `parseRestDepth returns DepthResult for positive avg`() {
        val json = """{"avg":57.15,"elementarySurfaces":1}"""
        val result = client.parseRestDepth(json)
        assertNotNull(result)
        assertEquals(57.15f, result!!.avg, 0.01f)
    }

    @Test
    fun `parseRestDepth uses smoothed when avg is zero`() {
        val json = """{"avg":0.0,"smoothed":-4.59}"""
        val result = client.parseRestDepth(json)
        assertNotNull(result)
        assertEquals(4.59f, result!!.avg, 0.01f)
    }

    @Test
    fun `parseRestDepth returns null for empty body`() {
        assertNull(client.parseRestDepth(""))
    }

    @Test
    fun `parseRestDepth returns null for NaN avg`() {
        val json = """{}"""
        assertNull(client.parseRestDepth(json))
    }

    @Test
    fun `parseRestDepth uses min and max from response`() {
        val json = """{"min":12.3,"max":14.1,"avg":13.0,"stdev":0.4}"""
        val result = client.parseRestDepth(json)
        assertNotNull(result)
        assertEquals(13.0f, result!!.avg, 0.01f)
        assertEquals(12.3f, result.min, 0.01f)
        assertEquals(14.1f, result.max, 0.01f)
    }

    @Test
    fun `parseRestDepth handles real EMODNET response with all fields`() {
        val json = """{"avg":-269.78,"elementarySurfaces":1,"interpolationType":true,"smoothed":-269.78748}"""
        val result = client.parseRestDepth(json)
        assertNotNull(result)
        assertEquals(269.78f, result!!.avg, 0.01f)
        assertEquals(0f, result.min, 0.01f)
        assertEquals(0f, result.max, 0.01f)
    }

    @Test
    fun `parseRestDepth handles full response with min and max`() {
        val json = """{"min":-31.3,"max":-31.35,"avg":-31.23,"stdev":0.022}"""
        val result = client.parseRestDepth(json)
        assertNotNull(result)
        assertEquals(31.23f, result!!.avg, 0.01f)
        assertEquals(31.3f, result.min, 0.01f)
        assertEquals(31.35f, result.max, 0.01f)
    }

    @Test
    fun `parseRestDepth handles positive avg without min max`() {
        val json = """{"avg":5.2,"elementarySurfaces":1,"interpolationType":false,"smoothed":5.35,"smoothedOffset":0.15,"reference":{}}"""
        val result = client.parseRestDepth(json)
        assertNotNull(result)
        assertEquals(5.2f, result!!.avg, 0.01f)
    }
}
