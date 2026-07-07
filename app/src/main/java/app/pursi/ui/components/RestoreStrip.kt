package app.pursi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pursi.ui.viewmodel.SplitOrientation

@Composable
fun RestoreStrip(
    orientation: SplitOrientation,
    paneACollapsed: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVertical = orientation == SplitOrientation.Vertical
    val icon = if (paneACollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft

    Box(
        modifier = modifier
            .then(
                if (isVertical) Modifier.fillMaxHeight().width(32.dp)
                else Modifier.fillMaxWidth().height(32.dp)
            )
            .clickable(onClick = onRestore),
        contentAlignment = Alignment.Center
    ) {
        SmallFloatingActionButton(
            onClick = onRestore,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(icon, contentDescription = "Restore split")
        }
    }
}
