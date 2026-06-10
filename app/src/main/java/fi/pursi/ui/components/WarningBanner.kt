package fi.pursi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.pursi.R
import fi.pursi.navigation.AlertSeverity
import fi.pursi.navigation.SafetyAlert

@Composable
fun WarningBanner(
    alert: SafetyAlert?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = alert != null,
        enter = slideInVertically(),
        exit = slideOutVertically(),
        modifier = modifier
    ) {
        val bgColor = when (alert?.severity) {
            AlertSeverity.CRITICAL -> Color(0xFFD32F2F)
            AlertSeverity.WARNING -> Color(0xFFFFA000)
            AlertSeverity.INFO -> Color(0xFF1976D2)
            null -> Color.Transparent
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = alert?.message ?: stringResource(R.string.warning_label),
                tint = Color.White,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = alert?.message ?: "",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = Color.White
                )
            }
        }
    }
}
