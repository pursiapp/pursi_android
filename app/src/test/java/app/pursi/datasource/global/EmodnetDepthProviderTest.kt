package app.pursi.datasource.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmodnetDepthProviderTest {

    private val provider = EmodnetDepthProvider(okhttp3.OkHttpClient())

    @Test
    fun `parseDepthResponse returns correct values for valid JSON`() {
        val json = """{"min":12.3,"max":14.1,"mean":13.0,"stdev":0.4}"""
        val result = provider.parseDepthResponse(json, 59.45, 24.75)
        assertNotNull(result)
        assertEquals(13.0f, result!!.meanDepthM)
        assertEquals(12.3f, result.minDepthM)
        assertEquals(14.1f, result.maxDepthM)
        assertEquals(0.4f, result.stdevM)
        assertEquals(59.45, result.latitude, 0.001)
        assertEquals(24.75, result.longitude, 0.001)
    }

    @Test
    fun `parseDepthResponse handles min only response`() {
        val json = """{"min":8.5,"max":9.2,"mean":8.7}"""
        val result = provider.parseDepthResponse(json, 60.0, 20.0)
        assertNotNull(result)
        assertEquals(8.7f, result!!.meanDepthM)
        assertEquals(8.5f, result.minDepthM)
        assertEquals(9.2f, result.maxDepthM)
    }

    @Test
    fun `parseDepthResponse returns null for empty JSON`() {
        val json = """{}"""
        val result = provider.parseDepthResponse(json, 60.0, 20.0)
        assertNull(result)
    }

    @Test
    fun `parseDepthResponse returns null for invalid JSON`() {
        val result = provider.parseDepthResponse("not json", 60.0, 20.0)
        assertNull(result)
    }

    @Test
    fun `parseDepthResponse handles null values in JSON`() {
        val json = """{"min":null,"max":null,"mean":null}"""
        val result = provider.parseDepthResponse(json, 60.0, 20.0)
        assertNull(result)
    }
}
