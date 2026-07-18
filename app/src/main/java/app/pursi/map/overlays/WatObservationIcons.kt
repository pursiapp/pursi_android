package app.pursi.map.overlays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import app.pursi.map.SpriteCacheRegistry
import org.maplibre.android.maps.Style

object WatObservationIcons {

    private const val ICON_SIZE = 40

    fun ensureIconsAdded(style: Style) {
        val colors = listOf("#4CAF50", "#FFEB3B", "#FF9800", "#F44336")
        for ((level, color) in colors.withIndex()) {
            val name = "algae-$level"
            if (style.getImage(name) == null) {
                val bmp = createAlgaeIcon(color)
                SpriteCacheRegistry.track(bmp, "wat-obs-icon")
                style.addImage(name, bmp)
            }
        }
    }

    private fun createAlgaeIcon(color: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint().apply {
            this.color = Color.parseColor(color)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val strokePaint = Paint().apply {
            this.color = 0xFF000000.toInt()
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val path = Path().apply {
            moveTo(20f, 3f)
            lineTo(36f, 34f)
            lineTo(4f, 34f)
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        return bitmap
    }
}
