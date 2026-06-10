package fi.pursi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fi.pursi.R
import fi.pursi.ais.AisVessel
import fi.pursi.ais.VesselMetadata

@Composable
fun VesselInfoPopup(
    selectedVesselMmsi: Int?,
    vessels: List<AisVessel>,
    aisMetadata: Map<Int, VesselMetadata>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedVessel = selectedVesselMmsi?.let { mmsi ->
        vessels.find { it.mmsi == mmsi }
    }
    if (selectedVesselMmsi != null && selectedVessel != null) {
        val v = selectedVessel
        val meta = aisMetadata[v.mmsi]
        val displayName = meta?.name ?: v.name
        val displayType = meta?.shipType ?: v.shipType
        val displayDest = meta?.destination ?: v.destination

        Card(
            modifier = modifier
                .padding(top = 100.dp)
                .widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayName ?: "MMSI ${v.mmsi}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            Text("MMSI ${v.mmsi}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            if (displayName != null && meta?.callSign != null) {
                                Text("  ·  ${meta.callSign}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    VesselInfoChip(stringResource(R.string.vessel_speed), "${"%.1f".format(v.sog)} kn")
                    VesselInfoChip(stringResource(R.string.vessel_course), "${"%.0f".format(v.cog)}°")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val navLabel = when (v.navStat) {
                        0 -> stringResource(R.string.vessel_status_underway)
                        1 -> stringResource(R.string.vessel_status_anchored)
                        5 -> stringResource(R.string.vessel_status_moored)
                        8 -> stringResource(R.string.vessel_status_sailing)
                        else -> "${stringResource(R.string.vessel_status)} ${v.navStat}"
                    }
                    VesselInfoChip(stringResource(R.string.vessel_status), navLabel)
                    displayDest?.let { VesselInfoChip(stringResource(R.string.vessel_dest), it) }
                }
                if (displayType != null || meta?.draught != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        displayType?.let { VesselInfoChip(stringResource(R.string.vessel_type), vesselTypeName(it)) }
                        meta?.draught?.let { VesselInfoChip(stringResource(R.string.vessel_draft), "${"%.1f".format(it)} m") }
                    }
                }
                if (v.timestampExternal > 0) {
                    val ageMs = System.currentTimeMillis() - v.timestampExternal
                    val ageText = when {
                        ageMs < 60_000 -> "${ageMs / 1000}s ago"
                        ageMs < 3_600_000 -> "${ageMs / 60_000}m ${(ageMs % 60_000) / 1000}s ago"
                        else -> "${ageMs / 3_600_000}h ${(ageMs % 3_600_000) / 60_000}m ago"
                    }
                    Text(ageText, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun VesselInfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun vesselTypeName(code: Int): String = when (code) {
    30 -> stringResource(R.string.vessel_type_fishing)
    35 -> stringResource(R.string.vessel_type_military)
    36 -> stringResource(R.string.vessel_type_sailing)
    37 -> stringResource(R.string.vessel_type_pleasure)
    in 40..49 -> stringResource(R.string.vessel_type_hsc)
    50 -> stringResource(R.string.vessel_type_pilot)
    51 -> stringResource(R.string.vessel_type_sar)
    52 -> stringResource(R.string.vessel_type_tug)
    55 -> stringResource(R.string.vessel_type_military)
    in 60..69 -> stringResource(R.string.vessel_type_passenger)
    in 70..79 -> stringResource(R.string.vessel_type_cargo)
    in 80..89 -> stringResource(R.string.vessel_type_tanker)
    else -> stringResource(R.string.vessel_type_other, code)
}
