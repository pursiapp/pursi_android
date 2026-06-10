package fi.pursi.datasource.global

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RainViewerTimestampSourceImplTest {

    private fun mockOkHttp(json: String): OkHttpClient {
        return object : OkHttpClient() {
            override fun newCall(request: Request): Call {
                return object : Call {
                    override fun request(): Request = request
                    override fun execute(): Response {
                        val body = ResponseBody.create(null, json)
                        return Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(body)
                            .build()
                    }
                    override fun enqueue(responseCallback: okhttp3.Callback) {
                        responseCallback.onResponse(this, execute())
                    }
                    override fun clone(): Call = this
                    override fun cancel() {}
                    override fun isCanceled(): Boolean = false
                    override fun isExecuted(): Boolean = false
                    override fun timeout(): okio.Timeout = okio.Timeout.NONE
                }
            }
        }
    }

    private fun mockFailingClient(): OkHttpClient {
        return object : OkHttpClient() {
            override fun newCall(request: Request): Call {
                return object : Call {
                    override fun request(): Request = request
                    override fun execute(): Response {
                        throw java.io.IOException("Network error")
                    }
                    override fun enqueue(responseCallback: okhttp3.Callback) {
                        responseCallback.onFailure(this, java.io.IOException("Network error"))
                    }
                    override fun clone(): Call = this
                    override fun cancel() {}
                    override fun isCanceled(): Boolean = false
                    override fun isExecuted(): Boolean = false
                    override fun timeout(): okio.Timeout = okio.Timeout.NONE
                }
            }
        }
    }

    @Test
    fun `returns nearest frame from API response`() {
        val client = mockOkHttp(
            """{"radar":{"past":[{"time":1000000,"path":"/v2/radar/1000000"},{"time":1000600,"path":"/v2/radar/1000600"}]}}"""
        )
        val source = RainViewerTimestampSourceImpl(client)
        val frame = source.getNearestFrame(1000010L)
        assertEquals(1000000L, frame?.first)
        assertEquals("/v2/radar/1000000", frame?.second)
    }

    @Test
    fun `returns null when API fails and cache empty`() {
        val client = mockFailingClient()
        val source = RainViewerTimestampSourceImpl(client)
        val frame = source.getNearestFrame(1000000L)
        assertNull(frame)
    }

    @Test
    fun `uses cache within refresh window`() {
        val client = mockOkHttp(
            """{"radar":{"past":[{"time":1000000,"path":"/v2/radar/1000000"}]}}"""
        )
        val source = RainViewerTimestampSourceImpl(client)

        val first = source.getNearestFrame(1000000L)
        assertEquals(1000000L, first?.first)
        assertEquals("/v2/radar/1000000", first?.second)

        val second = source.getNearestFrame(1000000L)
        assertEquals(1000000L, second?.first)
        assertEquals("/v2/radar/1000000", second?.second)
    }

    @Test
    fun `returns null when API returns empty past array`() {
        val client = mockOkHttp("""{"radar":{"past":[]}}""")
        val source = RainViewerTimestampSourceImpl(client)
        assertNull(source.getNearestFrame(1000000L))
    }

    @Test
    fun `selects closest frame when multiple available`() {
        val client = mockOkHttp(
            """{"radar":{"past":[{"time":1000000,"path":"/v2/radar/1000000"},{"time":1000600,"path":"/v2/radar/1000600"},{"time":1001200,"path":"/v2/radar/1001200"}]}}"""
        )
        val source = RainViewerTimestampSourceImpl(client)
        val frame = source.getNearestFrame(1000700L)
        assertEquals(1000600L, frame?.first)
        assertEquals("/v2/radar/1000600", frame?.second)
    }

    @Test
    fun `selects exact frame when perfect match exists`() {
        val client = mockOkHttp(
            """{"radar":{"past":[{"time":1000000,"path":"/v2/radar/1000000"},{"time":1000600,"path":"/v2/radar/1000600"}]}}"""
        )
        val source = RainViewerTimestampSourceImpl(client)
        val frame = source.getNearestFrame(1000600L)
        assertEquals(1000600L, frame?.first)
        assertEquals("/v2/radar/1000600", frame?.second)
    }

    @Test
    fun `clearCache forces refetch on next call`() {
        val loadClient = mockOkHttp(
            """{"radar":{"past":[{"time":1000000,"path":"/v2/radar/1000000"}]}}"""
        )
        val source = RainViewerTimestampSourceImpl(loadClient)
        assertEquals(1000000L, source.getNearestFrame(1000000L)?.first)

        source.clearCache()

        val failClient = mockFailingClient()
        val source2 = RainViewerTimestampSourceImpl(failClient)
        assertNull(source2.getNearestFrame(1000000L))
    }
}
