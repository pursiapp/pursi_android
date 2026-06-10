package fi.pursi.map.overlays

import org.maplibre.android.maps.Style

object OverlayUtils {
    fun safeRemoveLayer(style: Style, id: String) {
        try { style.removeLayer(id) } catch (_: Exception) {}
    }

    fun safeRemoveSource(style: Style, id: String) {
        try { style.removeSource(id) } catch (_: Exception) {}
    }

    fun safeRemoveLayers(style: Style, vararg ids: String) {
        for (id in ids) safeRemoveLayer(style, id)
    }

    fun safeRemoveSources(style: Style, vararg ids: String) {
        for (id in ids) safeRemoveSource(style, id)
    }
}
