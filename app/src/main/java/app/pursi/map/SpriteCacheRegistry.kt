package app.pursi.map

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

object SpriteCacheRegistry {

    private val tracked = ConcurrentHashMap<Bitmap, String>()

    fun track(bitmap: Bitmap, label: String) {
        tracked[bitmap] = label
    }

    fun untrack(bitmap: Bitmap) {
        tracked.remove(bitmap)
    }

    fun recycleByLabel(labelPrefix: String): Int {
        val toRecycle = tracked.filterValues { it.startsWith(labelPrefix) }.keys.toList()
        var recycled = 0
        for (bmp in toRecycle) {
            tracked.remove(bmp)
            if (!bmp.isRecycled) {
                bmp.recycle()
                recycled++
            }
        }
        return recycled
    }

    fun recycleAll(): Int {
        val toRecycle = tracked.keys.toList()
        var recycled = 0
        for (bmp in toRecycle) {
            tracked.remove(bmp)
            if (!bmp.isRecycled) {
                bmp.recycle()
                recycled++
            }
        }
        return recycled
    }

    val trackedCount: Int get() = tracked.size
}
