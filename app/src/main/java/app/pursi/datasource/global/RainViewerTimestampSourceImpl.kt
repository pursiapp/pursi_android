package app.pursi.datasource.global

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

    init {
        refreshAsync()
    }

    override fun getNearestFrame(targetUnixTime: Long): Pair<Long, String>? {
        if (cachedFrames.isNotEmpty() &&
            System.currentTimeMillis() - lastFetchTime > REFRESH_INTERVAL_MS
        ) {
            refreshAsync()
        }
        return cachedFrames.minByOrNull { kotlin.math.abs(it.first - targetUnixTime) }
    }

    fun refreshAsync() {
        Thread {
            try {
                val frames = fetchFrames()
                if (frames.isNotEmpty()) {
                    cachedFrames = frames
                    lastFetchTime = System.currentTimeMillis()
                }
            } catch (_: Exception) {
                // keep old cache on failure
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
        android.util.Log.d("RainViewer", "Fetched ${sorted.size} frames (past=${past.length()}, now=${if (radar.has("now")) radar.getJSONArray("now").length() else 0})")
        return sorted
    }

    fun clearCache() {
        cachedFrames = emptyList()
        lastFetchTime = 0
        refreshAsync()
    }

    companion object {
        private const val API_URL = "https://api.rainviewer.com/public/weather-maps.json"
        private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    }
}
