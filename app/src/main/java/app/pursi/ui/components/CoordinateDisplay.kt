package app.pursi.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.location.Location

@Composable
fun CoordinateDisplay(
    showCoords: Int,
    location: Location,
    onDismiss: () -> Unit = {},
    bottomInsetPx: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(showCoords) {
        if (showCoords > 0) {
            kotlinx.coroutines.delay(5000)
            onDismiss()
        }
    }
    if (showCoords > 0) {
        Card(
            modifier = modifier
                .padding(bottom = bottomInsetPx + 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        ) {
            Text("${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}
