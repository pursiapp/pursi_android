package app.pursi.analytics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import app.pursi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor(
    private val sessionManager: SessionManager,
    @ApplicationContext private val appContext: Context,
) {
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pursi_analytics", Context.MODE_PRIVATE)

    private val queue = ConcurrentLinkedDeque<AnalyticsEvent>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var flushJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val deviceLanguage: String = Locale.getDefault().toLanguageTag()

    private val deviceScreen: String by lazy {
        try {
            val dm = android.util.DisplayMetrics()
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            if (wm != null) {
                val display = wm.defaultDisplay
                display.getRealMetrics(dm)
                "${dm.widthPixels}x${dm.heightPixels}"
            } else ""
        } catch (_: Exception) { "" }
    }

    private val userAgent: String by lazy {
        "Pursi/$UMAMI_VERSION_NAME (Android ${android.os.Build.VERSION.SDK_INT}; ${android.os.Build.MODEL})"
    }

    private val umamiUrl: String = UMAMI_URL

    val enabled: Boolean
        get() = prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, value).apply()
    }

    private fun build(name: String, url: String, data: Map<String, String>? = null): AnalyticsEvent {
        return AnalyticsEvent(
            type = "event",
            payload = EventPayload(
                website = UMAMI_WEBSITE_ID,
                hostname = "app.pursi",
                url = url,
                name = name,
                title = name,
                language = deviceLanguage,
                screen = deviceScreen,
                referrer = "",
                data = data,
                id = sessionManager.sessionId,
            ),
        )
    }

    fun track(name: String, url: String, data: Map<String, String>? = null) {
        if (!enabled) {
            Log.d(TAG, "track($name) skipped")
            return
        }
        val event = build(name, url, data)
        queue.add(event)
        Log.d(TAG, "track($name) queued, size=${queue.size}")
        if (queue.size >= BATCH_THRESHOLD) {
            flush()
        }
        if (flushJob == null) {
            startFlushTimer()
        }
    }

    fun trackTab(tab: String) {
        track("tab-view", "/$tab")
    }

    fun onAppForeground() {
        Log.d(TAG, "onAppForeground")
        val sid = sessionManager.sessionId
        val prevSid = prefs.getString(KEY_KNOWN_SESSION, null)
        val now = System.currentTimeMillis()
        val lastSession = prefs.getLong(KEY_LAST_SESSION_TIME, 0L)
        val idle = now - lastSession
        val sessionChanged = sid != prevSid

        if (sessionChanged || idle > SESSION_TIMEOUT_MS || lastSession == 0L) {
            track("session-start", "/app", sessionManager.sessionData())
            prefs.edit()
                .putLong(KEY_LAST_SESSION_TIME, now)
                .putString(KEY_KNOWN_SESSION, sid)
                .apply()
        }
    }

    fun onAppBackground() {
        Log.d(TAG, "onAppBackground")
        flushSync()
    }

    private fun httpSend(event: AnalyticsEvent) {
        val body = json.encodeToString(event)
        val url = URL("$umamiUrl/api/send")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", userAgent)
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.outputStream.use { stream ->
                OutputStreamWriter(stream).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = if (conn.errorStream != null) conn.errorStream.bufferedReader().readText() else ""
                Log.w(TAG, "httpSend: HTTP $code $err")
                throw RuntimeException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun flush() {
        if (!enabled) return
        val events = mutableListOf<AnalyticsEvent>()
        while (true) {
            val event = queue.pollFirst() ?: break
            events.add(event)
        }
        if (events.isEmpty()) return
        Log.d(TAG, "flush: sending ${events.size} events")

        scope.launch {
            val failed = mutableListOf<AnalyticsEvent>()
            for (event in events) {
                try {
                    httpSend(event)
                } catch (e: Exception) {
                    Log.w(TAG, "flush: event failed", e)
                    failed.add(event)
                }
            }
            if (failed.isEmpty()) {
                Log.d(TAG, "flush: OK")
            } else {
                Log.w(TAG, "flush: ${failed.size}/${events.size} failed, re-queuing")
                failed.forEach { queue.addFirst(it) }
            }
        }
    }

    fun flushSync() {
        if (!enabled) return
        val events = mutableListOf<AnalyticsEvent>()
        while (true) {
            val event = queue.pollFirst() ?: break
            events.add(event)
        }
        if (events.isEmpty()) return
        Log.d(TAG, "flushSync: sending ${events.size} events")

        scope.launch {
            var ok = true
            for (event in events) {
                try {
                    httpSend(event)
                } catch (e: Exception) {
                    Log.w(TAG, "flushSync: event failed", e)
                    ok = false
                }
            }
            if (ok) Log.d(TAG, "flushSync: OK")
        }
    }

    private fun startFlushTimer() {
        flushJob = scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    companion object {
        private const val TAG = "PursiAnalytics"
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        private const val KEY_LAST_SESSION_TIME = "last_session_time"
        private const val KEY_KNOWN_SESSION = "known_session_id"
        private val UMAMI_WEBSITE_ID: String = BuildConfig.UMAMI_WEBSITE_ID
        private val UMAMI_URL: String = BuildConfig.UMAMI_URL
        private val UMAMI_VERSION_NAME: String = BuildConfig.VERSION_NAME
        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val BATCH_THRESHOLD = 20
        private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
