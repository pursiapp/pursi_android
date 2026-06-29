package app.pursi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import app.pursi.R

@Composable
fun RadarTimeSlider(
    showRadar: Boolean,
    showSlider: Boolean,
    radarTimeOffset: Int,
    effectiveDelay: Int = 0,
    onRadarTimeOffsetChange: (Int) -> Unit,
    bottomInsetPx: Dp = 0.dp,
    maxHistoryMinutes: Int = 60,
    modifier: Modifier = Modifier
) {
    if (showRadar && showSlider) {
        val maxHistory = maxHistoryMinutes.coerceAtLeast(5)
        Card(
            modifier = modifier
                .padding(bottom = bottomInsetPx + 8.dp)
                .width(280.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-$maxHistory min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(
                        if (radarTimeOffset == 0) stringResource(R.string.radar_live)
                        else "$radarTimeOffset min ago",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (radarTimeOffset == 0) Color(0xFF4CAF50) else Color(0xFFFF8F00)
                    )
                }
                Slider(
                    value = (maxHistory - radarTimeOffset).toFloat(),
                    onValueChange = { onRadarTimeOffsetChange(maxHistory - it.roundToInt()) },
                    valueRange = 0f..maxHistory.toFloat(),
                    steps = (maxHistory / 5) - 1,
                    modifier = Modifier.fillMaxWidth()
                )
                val cal = Calendar.getInstance()
                cal.add(Calendar.MINUTE, -radarTimeOffset)
                cal.add(Calendar.MINUTE, -(cal.get(Calendar.MINUTE) % 5))
                cal.add(Calendar.MINUTE, -effectiveDelay)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(
                    sdf.format(cal.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
