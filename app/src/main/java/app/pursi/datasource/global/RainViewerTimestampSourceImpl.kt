package app.pursi.datasource.global

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RainViewerTimestampSourceImpl @Inject constructor(
    private val client: OkHttpClient
) : RainViewerTimestampSource {

    @Volatile
    private var cachedFrames: List<Pair<Long, String>> = emptyList()
    @Volatile
    private var lastFetchTime: Long = 0

    private var firstFetch = CompletableDeferred<Unit>()

    init {
        refreshAsync()
    }

    override fun getNearestFrame(targetUnixTime: Long): Pair<Long, String>? {
        // Trigger refresh on stale-non-empty OR on permanently-empty (recovery).
        val nowMs = System.currentTimeMillis()
        if (cachedFrames.isEmpty() || nowMs - lastFetchTime > REFRESH_INTERVAL_MS) {
            refreshAsync()
        }
        return cachedFrames.minByOrNull { kotlin.math.abs(it.first - targetUnixTime) }
    }

    /**
     * Awaits the first successful fetch with a short timeout. Lets callers (e.g.
     * PursiMapView's 1s retry loop) make progress after a failed init instead
     * of spinning on null forever.
     */
    suspend fun awaitFirstFrames(timeoutMs: Long = 3000L) {
        if (cachedFrames.isNotEmpty()) return
        withTimeoutOrNull(timeoutMs) {
            firstFetch.await()
            Unit
        }
    }

    fun refreshAsync() {
        Thread {
            try {
                val frames = fetchFrames()
                if (frames.isNotEmpty()) {
                    cachedFrames = frames
                    lastFetchTime = System.currentTimeMillis()
                    firstFetch.complete(Unit)
                } else {
                    firstFetch.complete(Unit)
                }
            } catch (_: Exception) {
                firstFetch.complete(Unit)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun fetchFrames(): List<Pair<Long, String>> {
        val request = Request.Builder()
            .url(API_URL)
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        val root = JSONObject(body)
        val radar = root.getJSONObject("radar")
        val frames = mutableListOf<Pair<Long, String>>()

        val past = radar.getJSONArray("past")
        for (i in 0 until past.length()) {
            val obj = past.getJSONObject(i)
            frames.add(obj.getLong("time") to obj.getString("path"))
        }

        if (radar.has("now")) {
            val now = radar.getJSONArray("now")
            for (i in 0 until now.length()) {
                val obj = now.getJSONObject(i)
                frames.add(obj.getLong("time") to obj.getString("path"))
            }
        }

        val sorted = frames.sortedBy { it.first }.distinctBy { it.first }
        return sorted
    }

    /**
     * Synchronous variant of [refreshAsync] for tests and callers that want a
     * deterministic fetch. Returns true on success, false on failure.
     */
    fun refreshNow(): Boolean {
        return try {
            val frames = fetchFrames()
            if (frames.isNotEmpty()) {
                cachedFrames = frames
                lastFetchTime = System.currentTimeMillis()
            }
            firstFetch.complete(Unit)
            true
        } catch (e: Exception) {
            firstFetch.complete(Unit)
            false
        }
    }

    override fun clearCache() {
        cachedFrames = emptyList()
        lastFetchTime = 0
        // Reset the firstFetch so awaitFirstFrames can wait for the new fetch
        synchronized(this) {
            firstFetch = CompletableDeferred()
        }
        refreshAsync()
    }

    companion object {
        private const val API_URL = "https://api.rainviewer.com/public/weather-maps.json"
        private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    }
}
