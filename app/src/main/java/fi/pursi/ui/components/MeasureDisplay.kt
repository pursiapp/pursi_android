package fi.pursi.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fi.pursi.location.SpeedCalculator
import org.maplibre.android.geometry.LatLng

@Composable
fun MeasureDisplay(
    measurePoints: List<LatLng>,
    twoFingerMeasure: Float?,
    measureActive: Boolean,
    onTwoFingerMeasureDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Tap-mode distance
    if (measurePoints.size >= 2) {
        val d = SpeedCalculator.distanceBetween(
            measurePoints[0].latitude, measurePoints[0].longitude,
            measurePoints[1].latitude, measurePoints[1].longitude
        )
        val dNm = d / 1852.0
        Card(
            modifier = Modifier.padding(top = 60.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            Text("${"%.0f".format(d)} m / ${"%.2f".format(dNm)} nm",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }

    // Two-finger measure
    twoFingerMeasure?.let { dist ->
        Card(
            modifier = Modifier.padding(top = 60.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            Text("${"%.0f".format(dist)} m / ${"%.2f".format(dist / 1852.0f)} nm",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        LaunchedEffect(measureActive) {
            if (!measureActive) {
                kotlinx.coroutines.delay(5000)
                onTwoFingerMeasureDismiss()
            }
        }
    }
}
