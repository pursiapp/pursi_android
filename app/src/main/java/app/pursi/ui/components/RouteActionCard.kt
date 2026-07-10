package app.pursi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pursi.data.model.Boat
import app.pursi.navigation.RoutePlanner
import app.pursi.location.SpeedCalculator
import app.pursi.ui.viewmodel.NavigationState
import org.maplibre.android.geometry.LatLng
import app.pursi.R

@Composable
fun RouteActionCard(
    modifier: Modifier = Modifier,
    waypoints: List<LatLng>,
    defaultBoat: Boat?,
    isPlanningMode: Boolean,
    label: String? = null,
    showReportButton: Boolean = false,
    onReportObservation: () -> Unit = {},
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    navigationState: NavigationState = NavigationState(),
    onStartNavigate: () -> Unit = {},
    onStopNavigate: () -> Unit = {},
    onNavigateWaypoint: (Int) -> Unit = {}
) {
    val distNm = if (waypoints.size >= 2) {
        var d = 0.0
        for (i in 0 until waypoints.size - 1) {
            d += SpeedCalculator.distanceNm(
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude
            )
        }
        d
    } else 0.0

    val eta = defaultBoat?.let { distNm / it.cruisingSpeedKn }

    Card(
        modifier = modifier
            .widthIn(max = 340.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (label != null) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${"%.1f".format(distNm)} nm · ${waypoints.size} ${stringResource(R.string.waypoints)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            "${stringResource(R.string.route_pts, waypoints.size)} · ${"%.1f".format(distNm)} nm",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    eta?.let { e ->
                        Text(
                            RoutePlanner.formatTimeEstimate(e),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (navigationState.isActive) {
                    IconButton(onClick = onStopNavigate, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.stop_navigation), modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Save, stringResource(R.string.save_route), modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (isPlanningMode) {
                    IconButton(onClick = onUndo, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Undo, stringResource(R.string.undo), modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, stringResource(R.string.clear_route), modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                    }
                    if (waypoints.size >= 1) {
                        IconButton(onClick = onStartNavigate, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Navigation, stringResource(R.string.navigate), modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Save, stringResource(R.string.save_route), modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.close), modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (showReportButton) {
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = onReportObservation,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(
                        "+ " + stringResource(R.string.report_observation),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
