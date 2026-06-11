package app.pursi.map

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

class RemotePmtilesChannel(
    private val url: String,
    client: OkHttpClient? = null
) : FileChannel() {
    private val chClient: OkHttpClient = client ?: OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Resolve final CDN URL once to avoid repeated redirects
    private var resolvedUrl: String = url

    // Limit concurrent HTTP range requests
    private val concurrencyLimit = java.util.concurrent.Semaphore(3)
    private var _size: Long = -1
    private var _closed = false
    private var _resolved = false

    override fun size(): Long {
        if (_size < 0) {
            val req = Request.Builder().url(url).head().build()
            chClient.newCall(req).execute().use { resp ->
                val len = resp.body?.contentLength() ?: -1L
                val cl = resp.header("Content-Length")
                _size = when {
                    len > 0 -> len
                    cl != null -> cl.toLongOrNull() ?: throw IOException("Unknown file size (cl=$cl)")
                    else -> throw IOException("Unknown file size (len=$len, cl=$cl, code=${resp.code})")
                }
            }
            Log.d("SeamarkServer", "RemotePmtiles size: ${_size}")
        }
        return _size
    }

    override fun read(dst: ByteBuffer, position: Long): Int {
        if (_closed) throw IOException("Channel closed")
        val sz = size()
        val end = minOf(position + dst.remaining(), sz) - 1
        if (position >= sz) return -1
        if (end < position) return -1
        val range = "bytes=$position-$end"
        concurrencyLimit.acquire()
        try {
            val req = Request.Builder().url(url).header("Range", range).build()
            val resp = chClient.newCall(req).execute()
            val body = resp.body ?: throw IOException("No body (range=$range)")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} (range=$range)")
            val data = body.bytes()
            dst.put(data)
            return data.size
        } catch (e: Exception) {
            Log.e("SeamarkServer", "RemotePmtiles read error: ${e.message} range=$range")
            throw e
        } finally {
            concurrencyLimit.release()
        }
    }

    override fun read(dst: ByteBuffer) = throw IOException("Use read(dst, pos)")

    override fun write(src: ByteBuffer) = throw IOException("Read-only")

    override fun write(src: ByteBuffer, position: Long) = throw IOException("Read-only")

    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int) =
        throw IOException("Read-only")

    override fun truncate(size: Long) = throw IOException("Read-only")

    override fun force(metaData: Boolean) = Unit

    override fun position(): Long = throw IOException("Not supported")

    override fun position(newPosition: Long): FileChannel = throw IOException("Not supported")

    override fun implCloseChannel() { _closed = true }

    override fun transferTo(position: Long, count: Long, target: java.nio.channels.WritableByteChannel): Long =
        throw IOException("Not supported")

    override fun transferFrom(src: java.nio.channels.ReadableByteChannel, position: Long, count: Long): Long =
        throw IOException("Not supported")

    override fun map(mode: FileChannel.MapMode, position: Long, size: Long): java.nio.MappedByteBuffer =
        throw IOException("Not supported")

    override fun lock(position: Long, size: Long, shared: Boolean) =
        throw IOException("Not supported")

    override fun tryLock(position: Long, size: Long, shared: Boolean) =
        throw IOException("Not supported")

    override fun read(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long =
        throw IOException("Use read(dst, pos)")
}
