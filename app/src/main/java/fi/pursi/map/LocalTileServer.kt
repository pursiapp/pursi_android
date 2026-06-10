package fi.pursi.map

import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class LocalTileServer(private val port: Int = 0) {

    data class Response(
        val status: Int,
        val contentType: String,
        val body: ByteArray,
    )

    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    private var serverThread: Thread? = null

    val actualPort: Int
        get() = serverSocket?.localPort ?: 0

    val isRunning: Boolean
        get() = running

    fun start(handler: (path: String) -> Response?): Boolean {
        if (running) return true
        return try {
            serverSocket = ServerSocket(port).also { it.reuseAddress = true }
            running = true
            serverThread = thread(false, "LocalTileServer") {
                while (running) {
                    try {
                        val client = serverSocket!!.accept()
                        handleClient(client, handler)
                    } catch (_: SocketException) {
                        break
                    } catch (e: Exception) {
                        android.util.Log.e("LocalTileServer", "Accept error", e)
                    }
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("LocalTileServer", "Failed to start", e)
            false
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(client: Socket, handler: (String) -> Response?) {
        thread(true, "LocalTileServer-conn") {
            try {
                client.use { sock ->
                    val input = sock.getInputStream().bufferedReader()
                    val requestLine = input.readLine() ?: return@use
                    val parts = requestLine.split(" ")
                    if (parts.size < 2 || parts[0] != "GET") return@use

                    val rawPath = parts[1].substringBefore("?").substringBefore("#")
                    while (input.readLine().isNotEmpty()) {}

                    val response = handler(rawPath)
                    if (response != null) {
                        sendResponse(sock.getOutputStream(), response)
                    } else {
                        sendNotFound(sock.getOutputStream())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun sendResponse(output: OutputStream, response: Response) {
        val statusText = when (response.status) {
            200 -> "OK"
            204 -> "No Content"
            206 -> "Partial Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "Unknown"
        }
        val headers = buildString {
            append("HTTP/1.1 ${response.status} $statusText\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(headers.toByteArray())
        output.write(response.body)
        output.flush()
    }

    private fun sendNotFound(output: OutputStream) {
        val body = "not found".toByteArray()
        sendResponse(output, Response(404, "text/plain", body))
    }

    companion object {
        private var threadId = 0

        private fun thread(daemon: Boolean, name: String, block: () -> Unit): Thread {
            return Thread(null, block, "$name-${++threadId}").apply {
                isDaemon = daemon
                start()
            }
        }
    }
}
