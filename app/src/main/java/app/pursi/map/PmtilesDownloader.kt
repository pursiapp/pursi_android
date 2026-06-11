package app.pursi.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import app.pursi.R

data class SeamarkContinent(
    val id: String,
    val nameResId: Int,
    val sizeMb: Int
)

class PmtilesDownloader(
    private val context: Context,
    private val client: OkHttpClient
) {
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    private val _etag = MutableStateFlow<String?>(null)
    val etag: StateFlow<String?> = _etag.asStateFlow()

    private val _downloadedContinents = MutableStateFlow<Set<String>>(emptySet())
    val downloadedContinents: StateFlow<Set<String>> = _downloadedContinents.asStateFlow()

    private val _continentProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val continentProgress: StateFlow<Map<String, Float>> = _continentProgress.asStateFlow()

    private val _downloadingContinent = MutableStateFlow<String?>(null)
    val downloadingContinent: StateFlow<String?> = _downloadingContinent.asStateFlow()

    val pmtilesFile: File
        get() = File(context.filesDir, "seamarks.pmtiles")

    private val metaFile: File
        get() = File(context.filesDir, "seamarks.pmtiles.meta")

    companion object {
        private const val TAG = "PmtilesDownloader"
        const val RELEASES_BASE =
            "https://github.com/pursiapp/OpenSeaMap-vector/releases/latest/download"
        const val DEFAULT_SEAMARKS_URL =
            "$RELEASES_BASE/seamarks.pmtiles"

        val CONTINENTS = listOf(
            SeamarkContinent("europe", R.string.continent_europe, 80),
            SeamarkContinent("north-america", R.string.continent_north_america, 40),
            SeamarkContinent("asia", R.string.continent_asia, 50),
            SeamarkContinent("south-america", R.string.continent_south_america, 15),
            SeamarkContinent("africa", R.string.continent_africa, 20),
            SeamarkContinent("australia-oceania", R.string.continent_australia_oceania, 10),
            SeamarkContinent("central-america", R.string.continent_central_america, 5),
            SeamarkContinent("antarctica", R.string.continent_antarctica, 1)
        )

        fun continentUrl(continentId: String): String =
            "$RELEASES_BASE/seamarks_$continentId.pmtiles"
    }

    init {
        _isDownloaded.value = pmtilesFile.exists()
        _etag.value = loadMeta()["etag"]
        _downloadedContinents.value = refreshDownloadedContinents()
    }

    fun continentFile(continentId: String): File =
        File(context.filesDir, "seamarks_$continentId.pmtiles")

    fun isContinentDownloaded(continentId: String): Boolean =
        continentFile(continentId).exists()

    fun refreshDownloadedContinents(): Set<String> =
        CONTINENTS.map { it.id }.filter { isContinentDownloaded(it) }.toSet()

    fun continentMetaFile(continentId: String): File =
        File(context.filesDir, "seamarks_$continentId.pmtiles.meta")

    suspend fun isUpdateAvailable(url: String = DEFAULT_SEAMARKS_URL): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                val remoteEtag = response.header("ETag") ?: response.header("etag") ?: return@withContext false
                val localEtag = _etag.value
                remoteEtag != localEtag
            }
        } catch (e: Exception) { Log.w(TAG, "ETag check failed", e); false }
    }

    suspend fun download(url: String = DEFAULT_SEAMARKS_URL): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val total = body.contentLength()
                val tmpFile = File(pmtilesFile.parentFile, "${pmtilesFile.name}.tmp")
                tmpFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (total > 0) {
                                _progress.value = (downloaded.toFloat() / total * 100f)
                            }
                        }
                    }
                }
                tmpFile.renameTo(pmtilesFile)
                val eTag = response.header("ETag")
                _etag.value = eTag
                saveMeta("etag", eTag ?: "")
                _progress.value = 100f
                _isDownloaded.value = true
                true
            }
        } catch (_: Exception) { false }
    }

    suspend fun downloadContinent(continentId: String): Boolean = withContext(Dispatchers.IO) {
        val url = continentUrl(continentId)
        val file = continentFile(continentId)
        _downloadingContinent.value = continentId
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val total = body.contentLength()
                val tmpFile = File(context.filesDir, "${file.name}.tmp")
                tmpFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (total > 0) {
                                val pct = downloaded.toFloat() / total * 100f
                                _continentProgress.value =
                                    _continentProgress.value + (continentId to pct)
                            }
                        }
                    }
                }
                tmpFile.renameTo(file)
                val eTag = response.header("ETag")
                saveContinentMeta(continentId, eTag ?: "")
                _continentProgress.value =
                    _continentProgress.value + (continentId to 100f)
                _downloadedContinents.value = refreshDownloadedContinents()
                _downloadingContinent.value = null
                true
            }
        } catch (_: Exception) {
            _downloadingContinent.value = null
            _continentProgress.value =
                _continentProgress.value + (continentId to -1f)
            false
        }
    }

    fun deleteContinent(continentId: String) {
        continentFile(continentId).delete()
        continentMetaFile(continentId).delete()
        _downloadedContinents.value = refreshDownloadedContinents()
        _continentProgress.value = _continentProgress.value - continentId
    }

    fun delete() {
        pmtilesFile.delete()
        metaFile.delete()
        _isDownloaded.value = false
        _etag.value = null
        _progress.value = 0f
    }

    private fun loadMeta(): Map<String, String> {
        if (!metaFile.exists()) return emptyMap()
        return try {
            metaFile.readLines().associate { line ->
                val eq = line.indexOf('=')
                if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else "" to ""
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveMeta(key: String, value: String) {
        try {
            val meta = loadMeta().toMutableMap()
            meta[key] = value
            metaFile.writeText(meta.entries.joinToString("\n") { "${it.key}=${it.value}" })
        } catch (_: Exception) { }
    }

    private fun saveContinentMeta(continentId: String, etag: String) {
        try {
            val mf = continentMetaFile(continentId)
            mf.writeText("etag=$etag")
        } catch (_: Exception) { }
    }
}
