package app.pursi.map

import android.content.ComponentCallbacks2
import org.maplibre.android.maps.MapView

object MapViewRegistry {
    private val views = mutableSetOf<MapView>()

    fun register(view: MapView) {
        views.add(view)
    }

    fun unregister(view: MapView) {
        views.remove(view)
    }

    fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                views.forEach {
                    it.onLowMemory()
                    SpriteCacheRegistry.recycleAll()
                    it.queueEvent { }
                }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                views.forEach {
                    it.onLowMemory()
                    SpriteCacheRegistry.recycleByLabel("seamark-sprite")
                    SpriteCacheRegistry.recycleByLabel("ofm-sprite")
                }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                views.forEach { it.onLowMemory() }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                views.forEach { it.onPause() }
            }
        }
    }

    val activeCount: Int get() = views.size
}
