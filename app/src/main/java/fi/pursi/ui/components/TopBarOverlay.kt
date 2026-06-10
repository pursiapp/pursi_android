package fi.pursi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.pursi.location.SpeedUnit
import fi.pursi.weather.MarineWarning
import fi.pursi.weather.WeatherRepository
import fi.pursi.R

@Composable
fun TopBarOverlay(
    smoothedSpeed: Float,
    speedUnit: SpeedUnit,
    visibleWarnings: List<MarineWarning>,
    lightningMode: WeatherRepository.LightningMode,
    recordingData: RecordingData,
    mapBearing: Float,
    orientationLabel: String?,
    onSpeedUnitClick: () -> Unit,
    onCompassClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Top-left: speed indicator + warnings + lightning + recording info
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 0.dp, start = 8.dp)
        ) {
            val speedUnitLabel = stringResource(R.string.toggle_speed_unit) + " (" + speedUnit.shortLabel + ")"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.clickable { onSpeedUnitClick() }.semantics {
                        contentDescription = speedUnitLabel
                        role = Role.Button
                    }
                ) {
                    SpeedIndicator(speedMps = smoothedSpeed, unit = speedUnit)
                }
                if (visibleWarnings.isNotEmpty()) {
                    WarningBadge(visibleWarnings)
                }
                if (lightningMode == WeatherRepository.LightningMode.FAST) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFCC0000)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "⚡ LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (recordingData.isRecording) {
                val hours = recordingData.elapsedSec / 3600
                val mins = (recordingData.elapsedSec % 3600) / 60
                val secs = recordingData.elapsedSec % 60
                val timeStr = if (hours > 0) "${hours}h ${"%02d".format(mins)}m" else "${mins}m ${"%02d".format(secs)}s"
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "${"%.1f".format(recordingData.distanceNm)} nm · $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Top-right: compass rose + orientation label
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompassRose(mapBearing = mapBearing, onClick = onCompassClick)
            orientationLabel?.let { label ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun WarningBadge(warnings: List<MarineWarning>) {
    val icon: String
    val bgColor: Color
    when {
        warnings.any { it.eventCode.contains("thunder", ignoreCase = true) || it.eventCode.contains("lightning", ignoreCase = true) } -> {
            icon = "⛈"; bgColor = Color(0xFFFF6F00)
        }
        warnings.any { it.color == "red" || it.color == "orange" } -> {
            icon = "🌪"; bgColor = Color(0xFFD50000)
        }
        else -> {
            icon = "⚠"; bgColor = Color(0xFFFFB300)
        }
    }
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(32.dp)
            .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, style = MaterialTheme.typography.titleSmall)
    }
}
