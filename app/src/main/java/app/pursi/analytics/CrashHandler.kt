package app.pursi.analytics

import android.os.Build
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

class CrashHandler(
    private val umamiUrl: String,
    private val websiteId: String,
    private val versionName: String,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            sendCrashSync(e)
        } catch (_: Exception) {
            // must never fail — app is dying
        }
        defaultHandler?.uncaughtException(t, e)
    }

    private fun sendCrashSync(e: Throwable) {
        val stack = e.stackTraceToString()
        val truncated = if (stack.length > MAX_STACK_LENGTH) {
            stack.substring(0, MAX_STACK_LENGTH)
        } else {
            stack
        }

        val data = mapOf(
            "type" to (e::class.qualifiedName ?: "Unknown"),
            "msg" to (e.message?.take(200) ?: ""),
            "stack" to truncated,
            "ver" to versionName,
            "android" to Build.VERSION.SDK_INT.toString(),
            "device" to Build.MODEL,
        )

        val event = AnalyticsEvent(
            type = "event",
            payload = EventPayload(
                website = websiteId,
                hostname = "app.pursi",
                url = "/crash",
                name = "app-crash",
                title = "app-crash",
                language = Locale.getDefault().toLanguageTag(),
                screen = "",
                referrer = "",
                data = data,
                id = "crash-${UUID.randomUUID()}",
            ),
        )

        val jsonBody = json.encodeToString(event)
        val url = URL("$umamiUrl/api/send")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Pursi/$versionName (Android ${Build.VERSION.SDK_INT}; ${Build.MODEL})")
            conn.doOutput = true
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.outputStream.use { stream ->
                OutputStreamWriter(stream).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val MAX_STACK_LENGTH = 500
    }
}
