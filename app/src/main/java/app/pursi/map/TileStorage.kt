package app.pursi.map

import android.content.Context
import java.io.File

class TileStorage(private val context: Context) {

    private val storageDir: File
        get() = File(context.filesDir, "chart_tiles").also { it.mkdirs() }

    val storagePath: String
        get() = storageDir.absolutePath

    fun hasTile(providerId: String, z: Int, x: Int, y: Int, ext: String): Boolean {
        return tileFile(providerId, z, x, y, ext).exists()
    }

    fun saveTile(providerId: String, z: Int, x: Int, y: Int, ext: String, data: ByteArray) {
        val file = tileFile(providerId, z, x, y, ext)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(data)
        tmp.renameTo(file)
    }

    fun deleteProvider(providerId: String) {
        File(storageDir, providerId).deleteRecursively()
    }

    fun deleteAll() {
        storageDir.deleteRecursively()
        storageDir.mkdirs()
    }

    fun tileCount(providerId: String): Int {
        return File(storageDir, providerId).walkTopDown().filter { it.isFile }.count()
    }

    fun cacheSizeBytes(providerId: String): Long {
        return File(storageDir, providerId).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun totalTileCount(): Int {
        return storageDir.walkTopDown().filter { it.isFile }.count()
    }

    fun totalCacheSizeBytes(): Long {
        return storageDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun cacheSizeFormatted(providerId: String): String = formatBytes(cacheSizeBytes(providerId))

    fun totalCacheSizeFormatted(): String = formatBytes(totalCacheSizeBytes())

    private fun tileFile(providerId: String, z: Int, x: Int, y: Int, ext: String): File {
        return File(storageDir, "$providerId/$z/$y/$x.$ext")
    }

    fun tileFile(path: String): File = File(storageDir, path)

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    }
}
