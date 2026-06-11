package app.pursi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pursi.R

@Composable
fun RouteSaveDialog(
    showDialog: Boolean,
    routeName: String,
    onRouteNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.imePadding(),
            title = { Text(stringResource(R.string.save_route)) },
            text = {
                OutlinedTextField(value = routeName, onValueChange = onRouteNameChange,
                    label = { Text(stringResource(R.string.route_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = onSave) { Text(stringResource(R.string.save_route)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
