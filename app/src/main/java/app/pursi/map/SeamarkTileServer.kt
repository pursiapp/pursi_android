package app.pursi.map

import android.util.Log
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.File

class SeamarkTileServer(
    private val pmtilesFiles: List<File> = emptyList(),
    private val pmtilesUrls: List<String> = emptyList(),
    private val client: OkHttpClient? = null,
    port: Int = 8080,
) {
    private var reader: SeamarkReader? = null
    private val mutex = Any()
    private val server = LocalTileServer(port)

    val actualPort: Int
        get() = server.actualPort

    val isRunning: Boolean
        get() = server.isRunning

    fun startServer(): Boolean {
        synchronized(mutex) {
            reader?.close()
            reader = try {
                val localFiles = pmtilesFiles.filter { it.exists() }
                when {
                    localFiles.isNotEmpty() -> PmtilesReader.multiFile(localFiles)
                    pmtilesUrls.isNotEmpty() -> PmtilesReader.multiRemote(pmtilesUrls, client ?: OkHttpClient())
                    else -> { Log.e("SeamarkServer", "No PMTiles source"); return false }
                }
            } catch (e: Exception) {
                Log.e("SeamarkServer", "Failed to open PMTiles: ${e.message}")
                return false
            }
        }
        val ok = server.start { path -> serve(path) }
        if (ok) {
            Log.d("SeamarkServer", "Tile server started on port ${server.actualPort}")
        } else {
            Log.e("SeamarkServer", "Failed to start tile server")
        }
        return ok
    }

    fun stopServer() {
        server.stop()
        synchronized(mutex) {
            reader?.close()
            reader = null
        }
    }

    private fun serve(path: String): LocalTileServer.Response? {
        val p = path.trimStart('/')
        Log.d("SeamarkServer", "Request GET $p")
        return when {
            p == "tilejson" || p == "" -> handleTileJson()
            p.contains("/") -> handleTile(p)
            else -> LocalTileServer.Response(404, "text/plain", "not found".toByteArray())
        }
    }

    private fun handleTileJson(): LocalTileServer.Response {
        val tileUrl = "http://127.0.0.1:${server.actualPort}/{z}/{x}/{y}"
        val json = """{"tilejson":"2.2.0","tiles":["$tileUrl"],"minzoom":0,"maxzoom":14,"scheme":"xyz","format":"pbf","vector_layers":[{"id":"seamarks","description":"","minzoom":0,"maxzoom":14,"fields":{}}]}"""
        Log.d("SeamarkServer", "Serving TileJSON: $json")
        val body = json.toByteArray()
        return LocalTileServer.Response(200, "application/json", body)
    }

    private fun handleTile(path: String): LocalTileServer.Response {
        val parts = path.removeSuffix(".pbf").split("/")
        if (parts.size != 3) {
            return LocalTileServer.Response(404, "text/plain", "not found".toByteArray())
        }
        val z = parts[0].toIntOrNull() ?: return LocalTileServer.Response(400, "text/plain", "invalid z".toByteArray())
        val x = parts[1].toIntOrNull() ?: return LocalTileServer.Response(400, "text/plain", "invalid x".toByteArray())
        val y = parts[2].toIntOrNull() ?: return LocalTileServer.Response(400, "text/plain", "invalid y".toByteArray())
        val r = synchronized(mutex) { reader }
        if (r == null) {
            return LocalTileServer.Response(500, "text/plain", "reader null".toByteArray())
        }
        val data = try {
            r.readTile(z, x, y)
        } catch (e: Exception) {
            Log.e("SeamarkServer", "Error reading tile $z/$x/$y: ${e.message}", e)
            return LocalTileServer.Response(500, "text/plain", (e.message ?: "error").toByteArray())
        }
        if (data == null) {
            return LocalTileServer.Response(204, "application/vnd.mapbox-vector-tile", ByteArray(0))
        }
        Log.d("SeamarkServer", "Serving tile $z/$x/$y (${data.size} bytes)")
        return LocalTileServer.Response(200, "application/vnd.mapbox-vector-tile", data)
    }
}
