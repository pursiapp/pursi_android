package fi.pursi.map

import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.util.zip.GZIPInputStream

interface SeamarkReader : Closeable {
    fun readTile(z: Int, x: Int, y: Int): ByteArray?
}

class PmtilesReader private constructor(
    private val wrapped: ch.poole.geo.pmtiles.Reader,
    private val tileCompression: Int
) : java.io.Closeable, SeamarkReader {

    // LRU tile cache: max 500 tiles (~several MB)
    private val tileCache = object : java.util.LinkedHashMap<String, ByteArray>(500, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > 500
    }

    companion object {
        operator fun invoke(file: File): PmtilesReader {
            val r = ch.poole.geo.pmtiles.Reader(file)
            return PmtilesReader(r, r.tileCompression.toInt())
        }
        operator fun invoke(url: String, client: OkHttpClient): PmtilesReader {
            val channel = RemotePmtilesChannel(url, client)
            val r = ch.poole.geo.pmtiles.Reader(channel)
            return PmtilesReader(r, r.tileCompression.toInt())
        }
        operator fun invoke(channel: java.nio.channels.FileChannel): PmtilesReader {
            val r = ch.poole.geo.pmtiles.Reader(channel)
            return PmtilesReader(r, r.tileCompression.toInt())
        }
        fun multiFile(files: List<File>): SeamarkReader? {
            if (files.isEmpty()) return null
            if (files.size == 1) return invoke(files.first())
            val readers = files.mapNotNull { f ->
                try { invoke(f) } catch (_: Exception) { null }
            }
            if (readers.isEmpty()) return null
            return MultiPmtilesReader(readers)
        }
        fun multiRemote(urls: List<String>, client: OkHttpClient): SeamarkReader? {
            if (urls.isEmpty()) return null
            if (urls.size == 1) return invoke(urls.first(), client)
            val readers = urls.mapNotNull { url ->
                try { invoke(url, client) } catch (_: Exception) { null }
            }
            if (readers.isEmpty()) return null
            return MultiPmtilesReader(readers)
        }
        private fun isGzip(data: ByteArray): Boolean =
            data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()
    }

    val minZoom: Int get() = wrapped.minZoom.toInt()
    val maxZoom: Int get() = wrapped.maxZoom.toInt()

    override fun readTile(z: Int, x: Int, y: Int): ByteArray? {
        val key = "$z/$x/$y"
        tileCache[key]?.let { return it }
        return try {
            val raw = wrapped.getTile(z, x, y) ?: return null
            val data = if (tileCompression == 0 && !isGzip(raw)) raw else decompress(raw)
            tileCache[key] = data
            data
        } catch (_: Exception) { null }
    }

    private fun decompress(data: ByteArray): ByteArray {
        if (isGzip(data)) {
            return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        }
        return data
    }

    override fun close() {
        wrapped.close()
    }
}

class MultiPmtilesReader(private val readers: List<PmtilesReader>) : java.io.Closeable, SeamarkReader {
    private val tileCache = object : java.util.LinkedHashMap<String, ByteArray>(500, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > 500
    }

    val minZoom: Int get() = readers.firstOrNull()?.minZoom ?: 0
    val maxZoom: Int get() = readers.firstOrNull()?.maxZoom ?: 14

    override fun readTile(z: Int, x: Int, y: Int): ByteArray? {
        val key = "$z/$x/$y"
        tileCache[key]?.let { return it }
        for (r in readers) {
            val data = r.readTile(z, x, y)
            if (data != null) {
                tileCache[key] = data
                return data
            }
        }
        return null
    }

    override fun close() {
        for (r in readers) {
            try { r.close() } catch (_: Exception) { }
        }
    }
}
