package app.pursi.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pursi.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@SuppressLint("SetJavaScriptEnabled")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CyanobacteriaReportDialog(
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                androidx.compose.material3.TopAppBar(
                    title = { Text(stringResource(R.string.algae_report_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                )
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                setBackgroundColor(Color.TRANSPARENT)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mixedContentMode =
                                    android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                webChromeClient = WebChromeClient()

                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedSslError(
                                        view: WebView,
                                        handler: SslErrorHandler,
                                        error: SslError
                                    ) {
                                        handler.cancel()
                                        isLoading = false
                                        android.util.Log.e("AlgaeReport",
                                            "SSL error: ${error.primaryError}")
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        super.onPageFinished(view, url)
                                        isLoading = false

                                        view.postDelayed({
                                            injectLocation(view, latitude, longitude)
                                        }, 1000)

                                        if (view.title?.contains("Havaintolähetti", ignoreCase = true) == true) {
                                            view.postDelayed({
                                                injectLocation(view, latitude, longitude)
                                            }, 500)
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        errorCode: Int,
                                        description: String,
                                        failingUrl: String
                                    ) {
                                        isLoading = false
                                        android.util.Log.e("AlgaeReport",
                                            "WebView error $errorCode: $description")
                                    }
                                }

                                loadUrl("https://www.jarviwiki.fi/havaintolahetti/")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun injectLocation(view: WebView, latitude: Double, longitude: Double) {
    val js = """
(function() {
    function tryInject(retries) {
        if (typeof ol === 'undefined' || typeof jQuery === 'undefined' || typeof map === 'undefined') {
            if (retries > 0) {
                setTimeout(function() { tryInject(retries - 1); }, 800);
            }
            return;
        }
        var coord = ol.proj.transform(
            [$longitude, $latitude], 'EPSG:4326', 'EPSG:3857'
        );
        map.getView().setCenter(coord);
        map.getView().setZoom(16);
        jQuery('.jwLon').val($longitude);
        jQuery('.jwLat').val($latitude);
        jQuery('.jwCoords').val('$latitude, $longitude');
        jQuery('#jwAppPage_2 .jwAppFwd').removeClass('ui-disabled');
        if (typeof memo !== 'undefined') {
            memo.chosenCoords = [$longitude, $latitude];
            memo.mapMovedByHand = true;
        }
    }
    tryInject(25);
})();
""".trimIndent()

    view.evaluateJavascript(js, null)
}
