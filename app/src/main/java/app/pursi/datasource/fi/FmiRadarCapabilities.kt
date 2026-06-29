package app.pursi.datasource.fi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers FMI radar WMS time dimension via GetCapabilities so [FmiRadarProvider]
 * can request history frames relative to the actual latest-published time
 * instead of device-clock arithmetic (which produced both stale "old rain"
 * and "empty future" responses depending on FMI publication lag).
 */
@Singleton
class FmiRadarCapabilities @Inject constructor(
    private val client: OkHttpClient
) {
    private val mutex = Mutex()

    @Volatile
    private var cachedEpochSec: Long = 0L

    @Volatile
    private var lastFetchMs: Long = 0L

    /**
     * Returns the latest available radar frame epoch-seconds, or null if the
     * GetCapabilities fetch fails. Caches for [CACHE_MS]; refresh on null-tile
     * retry by passing `force = true`.
     */
    suspend fun latestAvailableEpochSec(force: Boolean = false): Long? {
        val nowMs = System.currentTimeMillis()
        if (!force && cachedEpochSec > 0L && nowMs - lastFetchMs < CACHE_MS) {
            return cachedEpochSec
        }
        return mutex.withLock {
            val nowMs2 = System.currentTimeMillis()
            if (!force && cachedEpochSec > 0L && nowMs2 - lastFetchMs < CACHE_MS) {
                return@withLock cachedEpochSec
            }
            val fetched = fetch()
            if (fetched != null) {
                cachedEpochSec = fetched
                lastFetchMs = System.currentTimeMillis()
            }
            fetched
        }
    }

    fun clearCache() {
        cachedEpochSec = 0L
        lastFetchMs = 0L
    }

    private suspend fun fetch(): Long? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://openwms.fmi.fi/geoserver/wms?SERVICE=WMS&REQUEST=GetCapabilities&LAYERS=$LAYER")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parseLatest(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses the first <Dimension name="time" ...>.../<latestIso>/PT5M</Dimension>
     * and returns the ISO end-of-range timestamp as epoch seconds. FMI may emit
     * several <Dimension> tags; the one whose `default` matches `current` is the
     * authoritative one for radar, but parsing the first works in practice.
     */
    private fun parseLatest(xml: String): Long? {
        val re = Regex("""<Dimension\s+name="time"[^>]*>[^/]*/([^/]*)/PT5M""")
        val m = re.find(xml) ?: return null
        val iso = m.groupValues[1].trim()
        return parseIsoMs(iso)?.let { it / 1000L }
    }

    private fun parseIsoMs(iso: String): Long? = try {
        val pattern = if (iso.contains(".")) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
        val sdf = SimpleDateFormat(pattern, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(iso)?.time
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val LAYER = "Radar:suomi_rr_eureffin"
        private const val CACHE_MS = 5L * 60L * 1000L
    }
}
