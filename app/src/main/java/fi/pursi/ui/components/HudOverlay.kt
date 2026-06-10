package fi.pursi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.pursi.location.SpeedUnit

@Composable
fun HudOverlay(
    latitude: Double,
    longitude: Double,
    speedMps: Float,
    heading: Float,
    mapBearing: Float = 0f,
    windSpeedMs: Float?,
    windDirectionDeg: Float?,
    unit: SpeedUnit,
    trackCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedIndicator(speedMps = speedMps, unit = unit)
            CompassRose(mapBearing = mapBearing)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = formatCoord(latitude, true),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = formatCoord(longitude, false),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )

        if (windSpeedMs != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Wind: ${"%.0f".format(windSpeedMs * 1.94384f)} kn" +
                        if (windDirectionDeg != null) " @ ${windDirectionDeg.toInt()}°" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (trackCount > 0) {
            Text(
                text = "Track: $trackCount pts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

private fun formatCoord(value: Double, isLat: Boolean): String {
    val deg = value.toInt()
    val min = (Math.abs(value - deg) * 60).toInt()
    val sec = ((Math.abs(value - deg) * 60 - min) * 60).toInt()
    val dir = if (isLat) if (value >= 0) "N" else "S" else if (value >= 0) "E" else "W"
    return "${Math.abs(deg)}°${min}'${sec}\"$dir"
}
