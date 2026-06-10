package fi.pursi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fi.pursi.R
import fi.pursi.data.model.SavedRoute
import fi.pursi.data.model.TrackSummary
import fi.pursi.location.SpeedCalculator
import fi.pursi.map.PoiCategory
import fi.pursi.ui.viewmodel.RoutesViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RoutesScreen(
    routesViewModel: RoutesViewModel,
    onNavigateToMap: ((Double, Double) -> Unit)? = null,
    onViewRoute: ((String, Double, Double) -> Unit)? = null,
    onViewTrack: ((String, Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    RoutesContent(
        routesViewModel = routesViewModel,
        onNavigateToMap = onNavigateToMap,
        onViewRoute = onViewRoute,
        onViewTrack = onViewTrack,
        modifier = modifier
    )
}

@Composable
fun RoutesContent(
    routesViewModel: RoutesViewModel,
    onNavigateToMap: ((Double, Double) -> Unit)? = null,
    onViewRoute: ((String, Double, Double) -> Unit)? = null,
    onViewTrack: ((String, Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val recordings by routesViewModel.recordings.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedRoutes by routesViewModel.savedRoutes.collectAsStateWithLifecycle(initialValue = emptyList())

    val searchQ by routesViewModel.searchQ.collectAsStateWithLifecycle()
    val searchRes by routesViewModel.searchRes.collectAsStateWithLifecycle()
    val searching by routesViewModel.searching.collectAsStateWithLifecycle()
    val poiResults by routesViewModel.poiResults.collectAsStateWithLifecycle()
    val selectedPoiCategory by routesViewModel.selectedPoiCategory.collectAsStateWithLifecycle()
    val poiSearching by routesViewModel.poiSearching.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var renameTarget by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleteConfirmRoute by rememberSaveable { mutableStateOf<SavedRoute?>(null) }
    var deleteConfirmRecording by rememberSaveable { mutableStateOf<TrackSummary?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = searchQ, onValueChange = { routesViewModel.setSearchQ(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.routes_search_hint), maxLines = 1) },
            trailingIcon = { if (searchQ.isNotEmpty()) IconButton(onClick = { routesViewModel.clearSearch() }) { Icon(Icons.Default.Close, stringResource(R.string.close)) } },
            leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.search)) }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                if (searchQ.length >= 2) { routesViewModel.search() }
            }))
        Spacer(Modifier.height(4.dp))

        if (searchRes.isNotEmpty()) {
            val myLoc by routesViewModel.currentLocation.collectAsStateWithLifecycle()
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(searchRes.size) { i ->
                    val r = searchRes[i]
                    val distM = myLoc?.let {
                        SpeedCalculator.distanceBetween(it.latitude, it.longitude, r.latitude, r.longitude)
                    }
                    val distStr = distM?.let {
                        if (it < 1000) "${"%.0f".format(it)} m"
                        else "${"%.1f".format(it / 1000)} km"
                    }
                    Card(Modifier.fillMaxWidth().clickable { onNavigateToMap?.invoke(r.latitude, r.longitude) }.semantics { role = Role.Button },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, stringResource(R.string.view_on_map), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name.take(60), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("${r.type} · ${"%.3f".format(r.latitude)}, ${"%.3f".format(r.longitude)}${distStr?.let { " · $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())

        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.poi_categories), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.height(2.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(PoiCategory.entries) { cat ->
                val isSelected = selectedPoiCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        routesViewModel.selectPoiCategory(if (isSelected) null else cat)
                    },
                    leadingIcon = {
                        Icon(cat.icon, stringResource(cat.displayNameRes),
                            modifier = Modifier.size(16.dp))
                    },
                    label = { Text(stringResource(cat.displayNameRes), style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = if (isSelected) ({ Icon(Icons.Default.Check, "✓", modifier = Modifier.size(16.dp)) }) else null
                )
            }
        }

        if (poiResults.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val myLoc by routesViewModel.currentLocation.collectAsStateWithLifecycle()
            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                items(poiResults.size) { i ->
                    val r = poiResults[i]
                    val distM = myLoc?.let {
                        SpeedCalculator.distanceBetween(it.latitude, it.longitude, r.latitude, r.longitude)
                    }
                    val distStr = distM?.let {
                        if (it < 1000) "${"%.0f".format(it)} m"
                        else "${"%.1f".format(it / 1000)} km"
                    }
                    Card(Modifier.fillMaxWidth().clickable {
                        onNavigateToMap?.invoke(r.latitude, r.longitude)
                    }.semantics { role = Role.Button },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(r.category.icon, stringResource(r.category.displayNameRes),
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.displayName.take(50), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium, maxLines = 1)
                                val extras = buildList {
                                    if (r.tags.containsKey("website")) add(stringResource(R.string.poi_has_website))
                                    if (r.hasFee) add(stringResource(R.string.poi_has_fee))
                                    if (r.hasPower) add(stringResource(R.string.poi_has_power))
                                    if (r.hasSanitary) add(stringResource(R.string.poi_has_sanitary))
                                }
                                val desc = "${"%.3f".format(r.latitude)}, ${"%.3f".format(r.longitude)}${distStr?.let { " · $it" } ?: ""}" +
                                    extras.take(2).joinToString(" · ", " · ")
                                Text(desc, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
        }
        if (poiSearching) LinearProgressIndicator(Modifier.fillMaxWidth())

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.tab_saved_routes)) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.tab_recordings)) })
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            when (selectedTab) {
                0 -> {
                    item {
                        Text(stringResource(R.string.saved_routes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.routes_hint_planning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(6.dp))
                    }
                    if (savedRoutes.isEmpty()) {
                        item {
                            Text(stringResource(R.string.routes_no_data),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    items(savedRoutes, key = { it.id }) { sr ->
                        SavedRouteCard(sr, onClick = {
                            routesViewModel.getWaypointsForRoute(sr.id) { wps ->
                                if (wps.isNotEmpty()) {
                                    val lat = wps.map { it.latitude }.average()
                                    val lon = wps.map { it.longitude }.average()
                                    onViewRoute?.invoke(sr.id, lat, lon)
                                }
                            }
                        }, onRename = { renameTarget = Pair(sr.id, sr.name); renameText = sr.name },
                            onDelete = { deleteConfirmRoute = sr },
                            onExport = { routesViewModel.exportGpx(sr) })
                        Spacer(Modifier.height(6.dp))
                    }
                }
                1 -> {
                    item {
                        Text(stringResource(R.string.recordings), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(R.string.routes_hint_recording),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(6.dp))
                    }
                    if (recordings.isEmpty()) {
                        item {
                            Text(stringResource(R.string.routes_no_data),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    items(recordings, key = { it.id }) { tr ->
                        RecordingCard(tr,
                            onClick = {
                                routesViewModel.getTrackCenter(tr.id) { lat, lon, _ ->
                                    onViewTrack?.invoke(tr.id, lat, lon)
                                }
                            },
                            onRename = { renameTarget = Pair(tr.id, tr.name); renameText = tr.name },
                            onDelete = { deleteConfirmRecording = tr },
                            onSaveAsRoute = { routesViewModel.saveRecordingAsRoute(tr.name, tr.id, tr.distanceNm, tr.boatId) },
                            onExport = { routesViewModel.exportGpxTrack(tr) })
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    // rename dialog
    if (renameTarget != null) {
        Dialog(onDismissRequest = { renameTarget = null }) {
            Card(Modifier.fillMaxWidth().imePadding(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.rename_route), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.route_name)) }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val target = renameTarget ?: return@Button
                            routesViewModel.renameItem(target.first, renameText)
                            renameTarget = null
                        }) { Text(stringResource(R.string.save_boat)) }
                    }
                }
            }
        }
    }

    // delete route confirm
    deleteConfirmRoute?.let { route ->
        AlertDialog(
            onDismissRequest = { deleteConfirmRoute = null },
            title = { Text(stringResource(R.string.delete_confirm)) },
            text = { Text("\"${route.name}\"") },
            confirmButton = {
                TextButton(onClick = {
                    routesViewModel.deleteRoute(route)
                    deleteConfirmRoute = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmRoute = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // delete recording confirm
    deleteConfirmRecording?.let { recording ->
        AlertDialog(
            onDismissRequest = { deleteConfirmRecording = null },
            title = { Text(stringResource(R.string.delete_confirm)) },
            text = { Text("\"${recording.name}\"") },
            confirmButton = {
                TextButton(onClick = {
                    routesViewModel.deleteRecording(recording)
                    deleteConfirmRecording = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmRecording = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SavedRouteCard(sr: SavedRoute, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
    val df = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { role = Role.Button },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sr.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("${"%.1f".format(sr.totalDistanceNm)} nm · ${sr.waypointCount} ${stringResource(R.string.stops)} · ${df.format(Date(sr.createdAt))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, stringResource(R.string.rename), tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onExport) { Icon(Icons.Default.Share, stringResource(R.string.export_gpx), tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RecordingCard(tr: TrackSummary, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onSaveAsRoute: () -> Unit, onExport: () -> Unit) {
    val df = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { role = Role.Button },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val dur = tr.endTime?.let { (it - tr.startTime) / 1000 } ?: 0L
                val durStr = if (dur > 3600) "${dur / 3600}h ${(dur % 3600) / 60}min" else "${dur / 60}min"
                Text("${"%.1f".format(tr.distanceNm)} nm · $durStr · ${tr.pointCount} ${stringResource(R.string.pts)} · ${df.format(Date(tr.startTime))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            IconButton(onClick = onSaveAsRoute) { Icon(Icons.Default.Save, stringResource(R.string.save_as_route), tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, stringResource(R.string.rename), tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onExport) { Icon(Icons.Default.Share, stringResource(R.string.export_gpx), tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
        }
    }
}
