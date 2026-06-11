package app.pursi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pursi.R
import app.pursi.ui.viewmodel.SeamarkDetail
import app.pursi.ui.viewmodel.SeamarkSource

@Composable
fun SeamarkInfoCard(
    data: SeamarkDetail,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 360.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        data.name.ifBlank {
                            when (data.source) {
                                SeamarkSource.VV -> "Turvalaite #${data.turvalaitenumero}"
                                SeamarkSource.OSM -> "Merimerkki"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (data.subtitle != null) {
                        Text(data.subtitle, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            if (data.typeLabel != null || data.statusLabel != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    data.typeLabel?.let { InfoChip(stringResource(R.string.navmark_type), it) }
                    data.statusLabel?.let { InfoChip(stringResource(R.string.navmark_status), it) }
                }
            }

            if (data.structureLabel != null || data.hasLight) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    data.structureLabel?.let { InfoChip(stringResource(R.string.navmark_structure), it) }
                    if (data.hasLight) InfoChip(stringResource(R.string.navmark_light), stringResource(R.string.seamark_yes))
                }
            }

            data.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            }

            data.lightCharacteristic?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            }

            data.sectorInfo?.let {
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.navmark_sector_fmt, it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            }

            if (data.extraInfo.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(data.extraInfo.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
