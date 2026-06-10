package fi.pursi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.pursi.R

@Composable
fun CompassRose(
    mapBearing: Float = 0f,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val orientationDesc = stringResource(R.string.change_orientation)
    Canvas(modifier = modifier.size(64.dp).clickable { onClick() }.semantics {
        contentDescription = orientationDesc
        role = Role.Button
    }) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.minDimension / 2 * 0.85f

        // White background with border for visibility
        drawCircle(Color(0xCCFFFFFF), radius, Offset(cx, cy))
        drawCircle(Color(0x88000000), radius, Offset(cx, cy), style = Stroke(width = 1.5f))

        rotate(-mapBearing, pivot = Offset(cx, cy)) {
            for (i in 0 until 360 step 30) {
                val angle = Math.toRadians(i.toDouble())
                val isCardinal = i % 90 == 0
                val isIntercardinal = i % 45 == 0 && !isCardinal
                val innerRadius = if (isCardinal) radius * 0.75f
                else if (isIntercardinal) radius * 0.82f
                else radius * 0.88f

                drawLine(
                    color = if (isCardinal) Color(0xFFD32F2F) else Color(0xFF555555),
                    start = Offset(
                        cx + innerRadius * Math.sin(angle).toFloat(),
                        cy - innerRadius * Math.cos(angle).toFloat()
                    ),
                    end = Offset(
                        cx + radius * Math.sin(angle).toFloat(),
                        cy - radius * Math.cos(angle).toFloat()
                    ),
                    strokeWidth = if (isCardinal) 3f else 1.5f
                )
            }

            // N label (red, bold, larger)
            val nStyle = TextStyle(color = Color(0xFFD32F2F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            val nResult = textMeasurer.measure(text = "N", style = nStyle)
            val nRad = Math.toRadians(0.0)
            val nRadius = radius * 0.62f
            drawText(nResult, topLeft = Offset(
                cx + nRadius * Math.sin(nRad).toFloat() - nResult.size.width / 2,
                cy - nRadius * Math.cos(nRad).toFloat() - nResult.size.height / 2
            ))

            // E, S, W labels (dark, readable)
            val otherStyle = TextStyle(color = Color(0xFF333333), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            val otherLabels = mapOf(90f to "E", 180f to "S", 270f to "W")
            for ((angleDeg, label) in otherLabels) {
                val rad = Math.toRadians(angleDeg.toDouble())
                val lr = radius * 0.62f
                val result = textMeasurer.measure(text = label, style = otherStyle)
                drawText(result, topLeft = Offset(
                    cx + lr * Math.sin(rad).toFloat() - result.size.width / 2,
                    cy - lr * Math.cos(rad).toFloat() - result.size.height / 2
                ))
            }
        }
    }
}
