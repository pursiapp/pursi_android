package app.pursi.map

import org.maplibre.android.maps.MapView

object MapViewRegistry {
    private val views = mutableSetOf<MapView>()

    fun register(view: MapView) {
        views.add(view)
    }

    fun unregister(view: MapView) {
        views.remove(view)
    }

    fun onLowMemory() {
        views.forEach { it.onLowMemory() }
    }

    val activeCount: Int get() = views.size
}
