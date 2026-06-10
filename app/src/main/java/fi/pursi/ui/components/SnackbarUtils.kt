package fi.pursi.ui.components

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun CoroutineScope.showUserError(
    context: Context,
    snackbarHostState: SnackbarHostState?,
    @StringRes messageResId: Int
) {
    val message = context.getString(messageResId)
    if (snackbarHostState != null) {
        launch { snackbarHostState.showSnackbar(message) }
    } else {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
