package app.pursi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pursi.R

@Composable
fun LayersPanel(
    chartOpacity: Float,
    showRadar: Boolean,
    showAis: Boolean,
    vesselCount: Int,
    showDepth: Boolean,
    showWindMeter: Boolean,
    showAlgae: Boolean,
    onChartOpacityChange: (Float) -> Unit,
    onChartOpacityFinished: () -> Unit,
    onToggleRadar: () -> Unit,
    onToggleAis: () -> Unit,
    onToggleDepth: () -> Unit,
    onToggleWindMeter: () -> Unit,
    onToggleAlgae: () -> Unit,
    bottomInsetPx: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(bottom = bottomInsetPx + 80.dp)
            .width(260.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume click, prevent scrim dismiss */ }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.osm), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.nautical_chart), style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = chartOpacity,
            onValueChange = onChartOpacityChange,
            onValueChangeFinished = onChartOpacityFinished,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.rain_and_lightning), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = showRadar, onCheckedChange = { onToggleRadar() })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ais_ship_traffic), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (showAis) {
                Text("$vesselCount", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Switch(checked = showAis, onCheckedChange = { onToggleAis() })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.depth_data), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = showDepth, onCheckedChange = { onToggleDepth() })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.wind_meter), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = showWindMeter, onCheckedChange = { onToggleWindMeter() })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.water_obs), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = showAlgae, onCheckedChange = { onToggleAlgae() })
        }
    }
}
