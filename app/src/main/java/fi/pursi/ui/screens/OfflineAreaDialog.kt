package fi.pursi.ui.screens

import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fi.pursi.R
import fi.pursi.map.DownloadManager
import fi.pursi.map.LatLngRect
import fi.pursi.map.RectangleTileCalculator
import fi.pursi.map.TileSource
import fi.pursi.map.TileStorage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineAreaDialog(
    rectangles: List<LatLngRect>,
    tileSources: List<TileSource>,
    tileStorage: TileStorage,
    downloadManager: DownloadManager,
    calculator: RectangleTileCalculator,
    snapshotPath: String? = null,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val defaultName = remember {
        "Alue ${SimpleDateFormat("d.M.yyyy HH:mm", Locale.getDefault()).format(Date())}"
    }
    var name by remember { mutableStateOf(defaultName) }
    var minZoom by remember { mutableFloatStateOf(8f) }
    var maxZoom by remember { mutableFloatStateOf(14f) }
    var downloading by remember { mutableStateOf(false) }

    // Filter sources to those whose coverage overlaps the selected rectangles
    val relevantSources = remember(tileSources, rectangles) {
        tileSources.filter { s ->
            rectangles.any { rect ->
                s.overlaps(rect)
            }
        }
    }

    // Deduplicate by providerId — show one row per unique source
    val uniqueSources = remember(relevantSources) {
        relevantSources.distinctBy { it.providerId }
    }

    val enabledSources = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(uniqueSources) {
        uniqueSources.forEach { s -> enabledSources.putIfAbsent(s.providerId, true) }
    }

    val estimate by remember {
        derivedStateOf {
            var tiles = 0
            var bytes = 0L
            for (s in relevantSources) {
                if (enabledSources[s.providerId] == true) {
                    val e = calculator.estimate(rectangles, minZoom.toInt(), maxZoom.toInt(), s.avgTileBytes)
                    tiles += e.tileCount; bytes += e.sizeBytes
                }
            }
            Pair(tiles, bytes)
        }
    }

    val availableBytes = try { StatFs(context.filesDir.absolutePath).availableBytes } catch (_: Exception) { Long.MAX_VALUE }

    // Group by category
    val chartsSources = uniqueSources.filter { it.category == "chart" }
    val baseSources = uniqueSources.filter { it.category == "base" }
    val seamarkSources = uniqueSources.filter { it.category == "seamarks" }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().imePadding(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.offline_area_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.offline_area_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.offline_area_zoom) + ": ${minZoom.toInt()} – ${maxZoom.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${minZoom.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                    Slider(value = minZoom, onValueChange = { minZoom = it.coerceAtMost(maxZoom - 1f) }, valueRange = 4f..14f, steps = 9, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${maxZoom.toInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                    Slider(value = maxZoom, onValueChange = { maxZoom = it.coerceAtLeast(minZoom + 1f) }, valueRange = 4f..14f, steps = 9, modifier = Modifier.weight(1f))
                }

                HorizontalDivider()

                Text(stringResource(R.string.offline_area_layers), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)

                // Merikartat (Nautical charts)
                if (chartsSources.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.offline_source_category_charts),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    chartsSources.forEach { s ->
                        SourceRow(s = s, checked = enabledSources[s.providerId] == true) {
                            enabledSources[s.providerId] = it
                        }
                    }
                }

                // Pohjakartat (Base maps)
                if (baseSources.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.offline_source_category_base),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    baseSources.forEach { s ->
                        SourceRow(s = s, checked = enabledSources[s.providerId] == true) {
                            enabledSources[s.providerId] = it
                        }
                    }
                }

                // Merimerkit (Seamarks)
                if (seamarkSources.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.offline_source_category_seamarks),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    seamarkSources.forEach { s ->
                        SourceRow(s = s, checked = enabledSources[s.providerId] == true) {
                            enabledSources[s.providerId] = it
                        }
                    }
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${estimate.first} ${stringResource(R.string.offline_area_tiles)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(formatBytes(estimate.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Text(if (estimate.second < availableBytes) stringResource(R.string.offline_area_space_ok) else stringResource(R.string.offline_area_space_nok),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (estimate.second < availableBytes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        downloading = true
                        scope.launch {
                            val enabledItems = relevantSources.filter { enabledSources[it.providerId] == true }
                            val layers = enabledItems.map { it.providerId }
                            val providers = enabledItems.map { it.providerId }
                            downloadManager.enqueue(
                                name = name.ifBlank { defaultName },
                                rectangles = rectangles,
                                minZoom = minZoom.toInt(),
                                maxZoom = maxZoom.toInt(),
                                selectedLayers = layers,
                                providerIds = providers,
                                tileSources = tileSources,
                                snapshotPath = snapshotPath
                            )
                            onComplete()
                        }
                    }, enabled = !downloading && estimate.first > 0 && estimate.second < availableBytes) {
                        if (downloading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(R.string.download))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: TileSource, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, onCheckedChange = onToggle)
        }
        if (s.description.isNotBlank()) {
            Text(
                s.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (s.coverageName.isNotBlank()) {
            Text(
                stringResource(R.string.offline_source_coverage, s.coverageName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

private fun TileSource.overlaps(rect: LatLngRect): Boolean {
    val cov = coverage ?: return true
    return !(cov.maxLat < rect.minLat || cov.minLat > rect.maxLat ||
             cov.maxLng < rect.minLng || cov.minLng > rect.maxLng)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
}
