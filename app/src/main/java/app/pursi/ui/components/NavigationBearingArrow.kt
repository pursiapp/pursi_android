package app.pursi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pursi.ui.viewmodel.NavigationState

@Composable
fun NavigationBearingArrow(
    modifier: Modifier = Modifier,
    navState: NavigationState
) {
    if (!navState.isActive || navState.waypoints.isEmpty()) return

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC212121))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            drawArrow(navState.relativeBearingDeg.toFloat())
        }

        Text(
            "%.1f nm".format(navState.distanceToWpNm),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFF6F00),
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(2.dp))

        Text(
            "WP ${navState.currentIndex + 1} / ${navState.waypoints.size}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

private fun DrawScope.drawArrow(relativeBearingDeg: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.width * 0.35f
    val angleRad = Math.toRadians(relativeBearingDeg.toDouble()).toFloat()

    val tipX = cx + radius * kotlin.math.sin(angleRad)
    val tipY = cy - radius * kotlin.math.cos(angleRad)

    val arrowPath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(
            cx - radius * 0.5f * kotlin.math.sin(angleRad + 0.4f),
            cy + radius * 0.5f * kotlin.math.cos(angleRad + 0.4f)
        )
        lineTo(
            cx - radius * 0.5f * kotlin.math.sin(angleRad - 0.4f),
            cy + radius * 0.5f * kotlin.math.cos(angleRad - 0.4f)
        )
        close()
    }

    drawPath(arrowPath, Color(0xFFFF6F00))

    drawCircle(
        Color.White,
        radius = 2f,
        center = Offset(cx, cy)
    )
}
