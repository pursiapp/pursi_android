package fi.pursi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.pursi.location.SpeedUnit

@Composable
fun SpeedIndicator(
    speedMps: Float,
    unit: SpeedUnit,
    modifier: Modifier = Modifier
) {
    val speedKn = speedMps * 1.94384f
    val animatedSpeed by animateFloatAsState(targetValue = speedKn, label = "speed")

    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val strokeWidth = 6f
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - radius * 2) / 2,
                (size.height - radius * 2) / 2
            )

            val arcSweep = (animatedSpeed / 30f).coerceIn(0f, 1f) * 270f

            drawArc(
                color = Color(0xFF37474F),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            val arcColor = when {
                animatedSpeed < 5f -> Color(0xFF4CAF50)
                animatedSpeed < 15f -> Color(0xFFFFA000)
                else -> Color(0xFFFF5722)
            }
            drawArc(
                color = arcColor,
                startAngle = 135f,
                sweepAngle = arcSweep,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            val displayValue = when (unit) {
                SpeedUnit.KNOTS -> animatedSpeed
                SpeedUnit.KMH -> animatedSpeed * 1.852f
                SpeedUnit.MPH -> animatedSpeed * 1.15078f
            }
            Text(
                text = "%.1f".format(displayValue),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = unit.shortLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
