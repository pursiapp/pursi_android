package app.pursi.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

data class FallbackTileLayer(
    val tileUrl: String,
    val minZoom: Float,
    val minTileSize: Int = 0,
)

class TraficomTileServer(
    private val fallbackLayers: List<FallbackTileLayer>,
    port: Int = 8081,
    private val cacheDir: java.io.File? = null,
) {
    private val semaphore = Semaphore(8)

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply { maxRequests = 16; maxRequestsPerHost = 4 })
        .apply {
            if (cacheDir != null) {
                cache(Cache(java.io.File(cacheDir, "tile-http-cache"), 50L * 1024 * 1024))
            }
        }
        .build()

    private val server = LocalTileServer(port)

    val baseTileUrl: String
        get() = "http://127.0.0.1:${server.actualPort}/{z}/{y}/{x}.png"

    val isRunning: Boolean
        get() = server.isRunning && server.actualPort > 0

    fun startServer(): Boolean {
        val ok = server.start { path -> serve(path) }
        if (ok) {
            Log.d("TraficomProxy", "Tile proxy started on port ${server.actualPort}")
        } else {
            Log.e("TraficomProxy", "Failed to start server")
        }
        return ok
    }

    fun stopServer() {
        server.stop()
    }

    private fun serve(path: String): LocalTileServer.Response? {
        val uri = path.trimStart('/')
        try {
            semaphore.acquire()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return LocalTileServer.Response(503, "text/plain", ByteArray(0))
        }
        try {
            val p = uri.removeSuffix(".png")
            val parts = p.split("/")
            if (parts.size != 3) {
                return LocalTileServer.Response(400, "text/plain", ByteArray(0))
            }
            val z = parts[0].toIntOrNull()
                ?: return LocalTileServer.Response(400, "text/plain", ByteArray(0))
            val y = parts[1].toIntOrNull()
                ?: return LocalTileServer.Response(400, "text/plain", ByteArray(0))
            val x = parts[2].toIntOrNull()
                ?: return LocalTileServer.Response(400, "text/plain", ByteArray(0))

            val minFallbackZoom = (z - 6).coerceAtLeast(4)
            for (zTry in z downTo minFallbackZoom) {
                val scale = 1 shl (z - zTry)
                val xTry = x / scale
                val yTry = y / scale

                for (layer in fallbackLayers) {
                    if (zTry < layer.minZoom) continue
                    val url = layer.tileUrl
                        .replace("{z}", zTry.toString())
                        .replace("{x}", xTry.toString())
                        .replace("{y}", yTry.toString())

                    val tile = fetchTile(url, layer.minTileSize)
                    if (tile != null) {
                        var imgData = if (zTry < z) {
                            upscaleAndCrop(tile, z, zTry, x, y) ?: tile
                        } else {
                            tile
                        }
                        if (layer.minTileSize > 0 && imgData.size >= 26 && checkHasAlpha(imgData)) {
                            val baseLayer = fallbackLayers.last()
                            val baseUrl = baseLayer.tileUrl
                                .replace("{z}", zTry.toString())
                                .replace("{x}", xTry.toString())
                                .replace("{y}", yTry.toString())
                            val baseTile = fetchTile(baseUrl, 0)
                            if (baseTile != null) {
                                compositeTiles(imgData, baseTile)?.let { imgData = it }
                            }
                        }
                        return LocalTileServer.Response(200, "image/png", imgData)
                    }
                }
            }

            return LocalTileServer.Response(404, "text/plain", ByteArray(0))
        } catch (e: Throwable) {
            Log.e("TraficomProxy", "Error: ${e.message}", e)
            return LocalTileServer.Response(500, "text/plain", ByteArray(0))
        } finally {
            semaphore.release()
        }
    }

    private fun fetchTile(url: String, minSize: Int = 0): ByteArray? {
        return try {
            val request = OkHttpRequest.Builder().url(url).build()
            val resp = client.newCall(request).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@use null
                val ct = r.header("Content-Type", "") ?: ""
                if (!ct.startsWith("image/")) return@use null
                val body = r.body?.bytes() ?: return@use null
                if (body.size < minSize) return@use null
                body
            }
        } catch (e: Throwable) {
            Log.e("TraficomProxy", "fetchTile error: ${e.message}")
            null
        }
    }

    private fun upscaleAndCrop(tileData: ByteArray, zReq: Int, zAvail: Int, xReq: Int, yReq: Int): ByteArray? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(tileData, 0, tileData.size) ?: return null
            val srcSize = bitmap.width.coerceAtMost(bitmap.height)
            val scale = 1 shl (zReq - zAvail)
            val subSize = (srcSize / scale).coerceAtLeast(1)

            val offsetX = xReq % scale
            val offsetY = yReq % scale
            val cropped = Bitmap.createBitmap(bitmap, offsetX * subSize, offsetY * subSize, subSize, subSize).also {
                bitmap.recycle()
            }
            val scaled = Bitmap.createScaledBitmap(cropped, srcSize, srcSize, true).also {
                cropped.recycle()
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out).also {
                scaled.recycle()
            }
            out.toByteArray()
        } catch (e: Throwable) {
            Log.e("TraficomProxy", "upscale error: ${e.message}")
            null
        }
    }

    private fun checkHasAlpha(pngData: ByteArray): Boolean {
        if (pngData.size < 26) return false
        if (pngData[0] != 0x89.toByte() || pngData[1] != 0x50.toByte() ||
            pngData[2] != 0x4E.toByte() || pngData[3] != 0x47.toByte()
        ) return false
        val colorType = pngData[25].toInt() and 0xFF
        return colorType == 4 || colorType == 6
    }

    private fun compositeTiles(topData: ByteArray, bottomData: ByteArray): ByteArray? {
        return try {
            val top = BitmapFactory.decodeByteArray(topData, 0, topData.size) ?: return null
            val bottom = BitmapFactory.decodeByteArray(bottomData, 0, bottomData.size)
                ?: run { top.recycle(); return null }

            val bottom2 = if (bottom.width != top.width || bottom.height != top.height) {
                Bitmap.createScaledBitmap(bottom, top.width, top.height, true).also { bottom.recycle() }
            } else {
                bottom
            }

            val pixels = IntArray(top.width * top.height)
            top.getPixels(pixels, 0, top.width, 0, 0, top.width, top.height)
            val bottomPixels = IntArray(bottom2.width * bottom2.height)
            bottom2.getPixels(bottomPixels, 0, bottom2.width, 0, 0, bottom2.width, bottom2.height)

            for (i in pixels.indices) {
                val alpha = pixels[i] shr 24 and 0xFF
                if (alpha == 0) {
                    pixels[i] = bottomPixels[i]
                }
            }

            val result = Bitmap.createBitmap(pixels, top.width, top.height, Bitmap.Config.ARGB_8888).also {
                top.recycle(); bottom2.recycle()
            }
            val out = ByteArrayOutputStream()
            result.compress(Bitmap.CompressFormat.PNG, 100, out).also { result.recycle() }
            out.toByteArray()
        } catch (e: Exception) {
            Log.e("TraficomProxy", "composite error: ${e.message}")
            null
        }
    }
}
