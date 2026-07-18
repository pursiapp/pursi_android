package app.pursi.map

import android.content.Context
import okhttp3.OkHttpClient
import java.io.File

object TileServerManager {
    private var seamarkServer: SeamarkTileServer? = null
    private var traficomServer: TraficomTileServer? = null
    private var seamarkRefCount = 0
    private var traficomRefCount = 0

    @Synchronized
    fun acquireSeamarkServer(
        pmtilesFiles: List<File>,
        pmtilesUrls: List<String>,
        client: OkHttpClient?,
        context: Context,
    ): SeamarkTileServer {
        seamarkRefCount++
        seamarkServer?.stopServer()
        val server = SeamarkTileServer(
            pmtilesFiles = pmtilesFiles,
            pmtilesUrls = pmtilesUrls,
            client = client,
            port = 8080
        )
        server.startServer()
        seamarkServer = server
        return server
    }

    @Synchronized
    fun releaseSeamarkServer() {
        seamarkRefCount--
        if (seamarkRefCount <= 0) {
            seamarkServer?.stopServer()
            seamarkServer = null
            seamarkRefCount = 0
        }
    }

    @Synchronized
    fun acquireTraficomServer(
        fallbackLayers: List<FallbackTileLayer>,
        cacheDir: File,
    ): TraficomTileServer {
        traficomRefCount++
        if (traficomServer?.isRunning == true) return traficomServer!!
        traficomServer?.stopServer()
        val server = TraficomTileServer(fallbackLayers, cacheDir = cacheDir)
        server.startServer()
        traficomServer = server
        return server
    }

    @Synchronized
    fun releaseTraficomServer() {
        traficomRefCount--
        if (traficomRefCount <= 0) {
            traficomServer?.stopServer()
            traficomServer = null
            traficomRefCount = 0
        }
    }

    @Synchronized
    fun resetAll() {
        seamarkServer?.stopServer()
        seamarkServer = null
        seamarkRefCount = 0
        traficomServer?.stopServer()
        traficomServer = null
        traficomRefCount = 0
    }
}
