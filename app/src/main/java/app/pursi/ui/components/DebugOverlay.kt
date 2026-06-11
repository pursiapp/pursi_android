package app.pursi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pursi.ui.viewmodel.FollowMode
import app.pursi.ui.viewmodel.OrientationMode

@Composable
fun DebugOverlay(
    showDebug: Boolean,
    currentZoom: Double,
    followMode: FollowMode,
    orientationMode: OrientationMode,
    showDepth: Boolean,
    vvStatus: VvStatus,
    isMockLocation: Boolean,
    mockLocationPending: Boolean,
    onMockGpsToggle: () -> Unit,
    showRadar: Boolean = false,
    radarProviderId: String = "-",
    radarTimeOffset: Int = 0,
    showRadarSlider: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (showDebug) {
        Card(
            modifier = modifier
                .padding(start = 8.dp, top = 120.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("Zoom: ${"%.1f".format(currentZoom)}", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold)
                Text("Follow: $followMode", style = MaterialTheme.typography.bodySmall)
                Text("Orient: $orientationMode", style = MaterialTheme.typography.bodySmall)
                Text("Depth: ${if (showDepth) "ON" else "OFF"}", style = MaterialTheme.typography.bodySmall)
                Text("Radar: ${if (showRadar) "ON" else "OFF"}  prov=$radarProviderId  off=$radarTimeOffset  slider=$showRadarSlider", style = MaterialTheme.typography.bodySmall)
                Text("VV: ${if (vvStatus.downloaded) "OFFLINE" else if (vvStatus.usingNetwork) "NETWORK" else "CACHE"} | T:${vvStatus.turvalaiteCount} L:${vvStatus.valosektoriCount} N:${vvStatus.navlineCount}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onMockGpsToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            mockLocationPending -> MaterialTheme.colorScheme.error
                            isMockLocation -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        when {
                            mockLocationPending -> "Tap map to set GPS"
                            isMockLocation -> "Mock-GPS active"
                            else -> "Mock-GPS"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
