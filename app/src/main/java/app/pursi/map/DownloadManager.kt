package app.pursi.map

import android.content.Context
import android.os.StatFs
import app.pursi.data.dao.DownloadJobDao
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class DownloadManager(
    private val context: Context,
    private val dao: DownloadJobDao,
    private val tileStorage: TileStorage,
    private val calculator: RectangleTileCalculator,
    private val downloader: ParallelTileDownloader
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    fun cancel() {
        scope.cancel()
    }

    fun hasCompletedJobs(): Boolean {
        return _progress.value.values.any { it.status == "COMPLETED" }
    }

    fun existingRects(): List<LatLngRect> {
        return _progress.value.values
            .filter { it.status == "COMPLETED" }
            .flatMap { parseRectanglesJson(it.rectanglesJson) }
    }

    suspend fun enqueue(
        name: String,
        rectangles: List<LatLngRect>,
        minZoom: Int,
        maxZoom: Int,
        selectedLayers: List<String>,
        providerIds: List<String>,
        tileSources: List<TileSource>,
        snapshotPath: String? = null
    ): String? {
        val sources = tileSources.filter { it.providerId in providerIds }
        if (sources.isEmpty()) return null

        val availableBytes = availableStorageBytes()
        val avgTileBytes = 25_000L
        val tileCount = calculator.calculateTiles(rectangles, minZoom, maxZoom).size
        val estimatedBytes = tileCount * sources.size * avgTileBytes
        if (estimatedBytes > availableBytes * 0.9) {
            return null
        }

        val jobId = UUID.randomUUID().toString()
        val rectanglesJson = rectangles.joinToString("|") { "${it.minLat},${it.maxLat},${it.minLng},${it.maxLng}" }

        val job = app.pursi.data.model.DownloadJob(
            id = jobId,
            name = name,
            minZoom = minZoom,
            maxZoom = maxZoom,
            selectedLayers = selectedLayers.joinToString(","),
            providerIds = providerIds.joinToString(","),
            status = "RUNNING",
            progressTiles = 0,
            totalTiles = tileCount * sources.size,
            createdAt = System.currentTimeMillis(),
            rectanglesJson = rectanglesJson,
            snapshotPath = snapshotPath
        )
        dao.insert(job)

        _progress.update { it.toMutableMap().apply {
            put(jobId, DownloadProgress(jobId, name, "RUNNING", 0, tileCount * sources.size, rectanglesJson = rectanglesJson, snapshotPath = snapshotPath, providerIds = providerIds.joinToString(","), createdAt = job.createdAt))
        } }

        scope.launch {
            var completed = 0
            for (source in sources) {
                val tiles = calculator.calculateTiles(rectangles, minZoom, maxZoom)
                downloader.downloadTiles(
                    providerId = source.providerId,
                    urlTemplate = source.urlTemplate,
                    extension = source.extension,
                    tiles = tiles
                ) { done, _ ->
                    completed = done
                    val total = tileCount * sources.size
                    _progress.update { it.toMutableMap().apply {
                        put(jobId, DownloadProgress(jobId, name, "RUNNING", completed, total, source.providerId, rectanglesJson = rectanglesJson, snapshotPath = snapshotPath, providerIds = providerIds.joinToString(","), createdAt = job.createdAt))
                    } }
                }
            }
            val updatedJob = dao.getById(jobId)?.copy(
                status = "COMPLETED",
                progressTiles = completed
            ) ?: return@launch
            dao.update(updatedJob)
            _progress.update { it.toMutableMap().apply {
                put(jobId, DownloadProgress(jobId, name, "COMPLETED", completed, completed, rectanglesJson = rectanglesJson, snapshotPath = snapshotPath, providerIds = providerIds.joinToString(","), createdAt = job.createdAt))
            } }
        }
        return jobId
    }

    suspend fun cancel(jobId: String) {
        val job = dao.getById(jobId) ?: return
        if (job.status == "RUNNING" || job.status == "PENDING") {
            dao.update(job.copy(status = "CANCELLED"))
            _progress.update { it.toMutableMap().apply {
                put(jobId, DownloadProgress(jobId, job.name, "CANCELLED", job.progressTiles, job.totalTiles, rectanglesJson = job.rectanglesJson, snapshotPath = job.snapshotPath, providerIds = job.providerIds, createdAt = job.createdAt))
            } }
        }
    }

    suspend fun delete(jobId: String) {
        val job = dao.getById(jobId) ?: return
        dao.delete(job)
        _progress.update { it.toMutableMap().apply { remove(jobId) } }
    }

    fun loadJobs() {
        scope.launch {
            val jobs = dao.getAllSync()
            for (job in jobs) {
                _progress.update { it.toMutableMap().apply {
                    put(job.id, DownloadProgress(job.id, job.name, job.status, job.progressTiles, job.totalTiles,
                        rectanglesJson = job.rectanglesJson, snapshotPath = job.snapshotPath, providerIds = job.providerIds, createdAt = job.createdAt))
                } }
            }
        }
    }

    fun clearAll() {
        tileStorage.deleteAll()
        scope.launch {
            dao.deleteAll()
            _progress.value = emptyMap()
        }
    }

    private fun availableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }
}
