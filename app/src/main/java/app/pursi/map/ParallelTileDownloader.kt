package app.pursi.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ParallelTileDownloader(
    private val client: OkHttpClient,
    private val tileStorage: TileStorage
) {
    var maxRetries = 3

    suspend fun downloadTiles(
        providerId: String,
        urlTemplate: String,
        extension: String,
        tiles: Set<TileCoordinate>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val pending = tiles.filter { !tileStorage.hasTile(providerId, it.z, it.x, it.y, extension) }
        val total = pending.size
        var completed = 0

        val chunks = pending.chunked(6)
        for (chunk in chunks) {
            chunk.map { tile ->
                async {
                    downloadSingle(providerId, urlTemplate, extension, tile)
                }
            }.awaitAll()
            completed += chunk.size
            onProgress(completed, total)
        }
        completed
    }

    private suspend fun downloadSingle(
        providerId: String,
        urlTemplate: String,
        extension: String,
        tile: TileCoordinate
    ) {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                val url = urlTemplate
                    .replace("{z}", tile.z.toString())
                    .replace("{x}", tile.x.toString())
                    .replace("{y}", tile.y.toString())
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body ?: return
                        tileStorage.saveTile(providerId, tile.z, tile.x, tile.y, extension, body.bytes())
                        return
                    }
                    if (response.code == 404) return
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay((attempt + 1) * 200L)
                }
            } catch (_: Exception) {
                return
            }
        }
    }
}
