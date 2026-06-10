package fi.pursi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import fi.pursi.R
import fi.pursi.ui.viewmodel.SettingsViewModel
import fi.pursi.ui.viewmodel.NightMode
import fi.pursi.ui.viewmodel.SectorMode
import fi.pursi.data.model.Boat
import fi.pursi.map.DownloadManager
import fi.pursi.map.LatLngRect
import fi.pursi.map.TileSource
import fi.pursi.map.TileSourceBuilder
import fi.pursi.map.RectangleTileCalculator
import fi.pursi.map.TileStorage
import fi.pursi.datasource.core.ChartProvider
import fi.pursi.map.parseRectanglesJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import fi.pursi.ui.viewmodel.BoatIconSize
import fi.pursi.location.SpeedUnit
import fi.pursi.weather.WeatherUnitPrefs


@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    nightMode: NightMode = NightMode.AUTO,
    onNightModeChange: (NightMode) -> Unit = {},
    sectorMode: SectorMode = SectorMode.NIGHT,
    onSectorModeChange: (SectorMode) -> Unit = {},
    courseLinesEnabled: Boolean = true,
    onToggleCourseLines: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = false,
    onToggleKeepScreenOn: (Boolean) -> Unit = {},
    tileStorage: TileStorage,
    downloadManager: DownloadManager,
    offlineMode: Boolean = false,
    onToggleOfflineMode: (Boolean) -> Unit = {},
    isOnline: Boolean = true,
    navmarkSize: fi.pursi.ui.viewmodel.NavmarkSize = fi.pursi.ui.viewmodel.NavmarkSize.MEDIUM,
    onNavmarkSizeChange: (fi.pursi.ui.viewmodel.NavmarkSize) -> Unit = {},
    onSeamarksDataChanged: () -> Unit = {},
    pmtilesDownloader: fi.pursi.map.PmtilesDownloader? = null,
    vvDataDownloader: fi.pursi.map.VvDataDownloader? = null,
    onVvDataChanged: () -> Unit = {},
    debugMode: Boolean = false,
    onToggleDebugMode: (Boolean) -> Unit = {},
    analyticsEnabled: Boolean = true,
    onToggleAnalytics: (Boolean) -> Unit = {},
    onClearWfsCache: () -> Unit = {},
    initialCamLat: Double = Double.NaN,
    initialCamLon: Double = Double.NaN,
    initialCamZoom: Double = 7.0,
    chartProviders: List<ChartProvider> = emptyList(),
    useGoogleLocation: Boolean = false,
    onToggleGoogleLocation: (Boolean) -> Unit = {},
    boatIconSize: BoatIconSize = BoatIconSize.MEDIUM,
    onBoatIconSizeChange: (BoatIconSize) -> Unit = {},
    boatIconColor: String = "#1976D2",
    onBoatIconColorChange: (String) -> Unit = {},
    windUnit: WeatherUnitPrefs.WindUnit = WeatherUnitPrefs.WindUnit.MS,
    onWindUnitChange: (WeatherUnitPrefs.WindUnit) -> Unit = {},
    tempUnit: WeatherUnitPrefs.TempUnit = WeatherUnitPrefs.TempUnit.CELSIUS,
    onTempUnitChange: (WeatherUnitPrefs.TempUnit) -> Unit = {},
    pressureUnit: WeatherUnitPrefs.PressureUnit = WeatherUnitPrefs.PressureUnit.HPA,
    onPressureUnitChange: (WeatherUnitPrefs.PressureUnit) -> Unit = {},
    speedUnit: SpeedUnit = SpeedUnit.KNOTS,
    onSpeedUnitChange: (SpeedUnit) -> Unit = {},
    windMeterSize: WeatherUnitPrefs.WindMeterSize = WeatherUnitPrefs.WindMeterSize.AUTO,
    onWindMeterSizeChange: (WeatherUnitPrefs.WindMeterSize) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenAreaSelector: (() -> Unit)? = null,
    pendingAreaResult: Pair<LatLngRect, String>? = null,
    onConsumeAreaResult: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val boatList by settingsViewModel.allBoats.collectAsStateWithLifecycle(initialValue = emptyList())
    val boatManager = settingsViewModel.boatManager
    val scope = rememberCoroutineScope()
    val calculator = remember { RectangleTileCalculator() }

    val downloadProgress by downloadManager.progress.collectAsStateWithLifecycle(initialValue = emptyMap())
    var showBoatDialog by rememberSaveable { mutableStateOf(false) }
    var showConfirmClear by rememberSaveable { mutableStateOf(false) }
    var tileCountToClear by rememberSaveable { mutableStateOf(0) }
    var editingBoat by rememberSaveable { mutableStateOf<Boat?>(null) }
    var boatToDelete by remember { mutableStateOf<Boat?>(null) }
    var showAreaSelector by rememberSaveable { mutableStateOf(false) }
    var showOfflineDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRect by remember { mutableStateOf<LatLngRect?>(null) }
    var snapshotPath by remember { mutableStateOf<String?>(null) }
    val existingRects = remember(downloadProgress) { downloadManager.existingRects() }

    // Process area selection result from external (tablet) overlay
    LaunchedEffect(pendingAreaResult) {
        val (rect, snap) = pendingAreaResult ?: return@LaunchedEffect
        selectedRect = rect
        snapshotPath = snap
        showOfflineDialog = true
        onConsumeAreaResult()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
        }

        // ── KARTTA JA NÄYTTÖ ──
        item { SectionHeader(stringResource(R.string.settings_section_map)) }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.night_mode), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val options = listOf(NightMode.DAY, NightMode.AUTO, NightMode.NIGHT)
                    options.forEach { opt ->
                        val selected = nightMode == opt
                        val label = when (opt) {
                            NightMode.DAY -> stringResource(R.string.night_mode_day)
                            NightMode.NIGHT -> stringResource(R.string.night_mode_night)
                            NightMode.AUTO -> stringResource(R.string.night_mode_auto)
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onNightModeChange(opt) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.sector_mode), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val sectorOpts = listOf(SectorMode.OFF, SectorMode.NIGHT, SectorMode.ALWAYS)
                    sectorOpts.forEach { opt ->
                        val selected = sectorMode == opt
                        val label = when (opt) {
                            SectorMode.OFF -> stringResource(R.string.sector_mode_off)
                            SectorMode.NIGHT -> stringResource(R.string.sector_mode_night)
                            SectorMode.ALWAYS -> stringResource(R.string.sector_mode_always)
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onSectorModeChange(opt) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                SettingsItem(stringResource(R.string.course_lines), stringResource(R.string.course_lines_desc), courseLinesEnabled, onToggleCourseLines)
                SettingsItem(stringResource(R.string.keep_screen_on), stringResource(R.string.keep_screen_on_desc), keepScreenOn, onToggleKeepScreenOn)
            }
        }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.navmark_size),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    val options = listOf(fi.pursi.ui.viewmodel.NavmarkSize.SMALL, fi.pursi.ui.viewmodel.NavmarkSize.MEDIUM, fi.pursi.ui.viewmodel.NavmarkSize.LARGE)
                    options.forEach { opt ->
                        val selected = navmarkSize == opt
                        val label = when (opt) {
                            fi.pursi.ui.viewmodel.NavmarkSize.SMALL -> stringResource(R.string.navmark_size_small)
                            fi.pursi.ui.viewmodel.NavmarkSize.MEDIUM -> stringResource(R.string.navmark_size_medium)
                            fi.pursi.ui.viewmodel.NavmarkSize.LARGE -> stringResource(R.string.navmark_size_large)
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onNavmarkSizeChange(opt) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.boat_icon_size),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    val sizeOptions = listOf(BoatIconSize.SMALL, BoatIconSize.MEDIUM, BoatIconSize.LARGE)
                    sizeOptions.forEach { opt ->
                        val selected = boatIconSize == opt
                        val label = when (opt) {
                            BoatIconSize.SMALL -> stringResource(R.string.boat_icon_size_small)
                            BoatIconSize.MEDIUM -> stringResource(R.string.boat_icon_size_medium)
                            BoatIconSize.LARGE -> stringResource(R.string.boat_icon_size_large)
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onBoatIconSizeChange(opt) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.boat_icon_color),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val colorOptions = listOf("#1976D2", "#D32F2F", "#388E3C", "#F57C00", "#FBC02D", "#FFFFFF")
                    colorOptions.forEach { hex ->
                        val isSelected = boatIconColor == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(AndroidColor.parseColor(hex)))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { onBoatIconColorChange(hex) }
                        )
                    }
                }
            }
        }

        // ── YKSIKÖT ──
        item { SectionHeader(stringResource(R.string.settings_section_units)) }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.wind_unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val windOpts = listOf(WeatherUnitPrefs.WindUnit.MS, WeatherUnitPrefs.WindUnit.KNOTS, WeatherUnitPrefs.WindUnit.KMH, WeatherUnitPrefs.WindUnit.BEAUFORT)
                    windOpts.forEach { opt ->
                        FilterChip(
                            selected = windUnit == opt,
                            onClick = { onWindUnitChange(opt) },
                            label = { Text(opt.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.temperature_unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val tempOpts = listOf(WeatherUnitPrefs.TempUnit.CELSIUS, WeatherUnitPrefs.TempUnit.FAHRENHEIT)
                    tempOpts.forEach { opt ->
                        FilterChip(
                            selected = tempUnit == opt,
                            onClick = { onTempUnitChange(opt) },
                            label = { Text(opt.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.pressure_unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val pressOpts = listOf(WeatherUnitPrefs.PressureUnit.HPA, WeatherUnitPrefs.PressureUnit.MMHG, WeatherUnitPrefs.PressureUnit.INHG)
                    pressOpts.forEach { opt ->
                        FilterChip(
                            selected = pressureUnit == opt,
                            onClick = { onPressureUnitChange(opt) },
                            label = { Text(opt.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.boat_speed_unit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val speedOpts = listOf(SpeedUnit.KNOTS, SpeedUnit.KMH, SpeedUnit.MPH)
                    speedOpts.forEach { opt ->
                        FilterChip(
                            selected = speedUnit == opt,
                            onClick = { onSpeedUnitChange(opt) },
                            label = { Text(opt.shortLabel, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.wind_meter_size), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    val sizeOpts = listOf(WeatherUnitPrefs.WindMeterSize.AUTO, WeatherUnitPrefs.WindMeterSize.SMALL, WeatherUnitPrefs.WindMeterSize.MEDIUM, WeatherUnitPrefs.WindMeterSize.LARGE)
                    sizeOpts.forEach { opt ->
                        val label = when (opt) {
                            WeatherUnitPrefs.WindMeterSize.AUTO -> stringResource(R.string.wind_meter_size_auto)
                            WeatherUnitPrefs.WindMeterSize.SMALL -> stringResource(R.string.wind_meter_size_small)
                            WeatherUnitPrefs.WindMeterSize.MEDIUM -> stringResource(R.string.wind_meter_size_medium)
                            WeatherUnitPrefs.WindMeterSize.LARGE -> stringResource(R.string.wind_meter_size_large)
                        }
                        FilterChip(
                            selected = windMeterSize == opt,
                            onClick = { onWindMeterSizeChange(opt) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        // ── VENEET ──
        item { SectionHeader(stringResource(R.string.settings_section_boats)) }

        items(boatList, key = { it.id }) { boat ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Anchor, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(boat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (boat.isDefault) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                            }
                        }
                        Text("${"%.1f".format(boat.cruisingSpeedKn)} kn / max ${"%.1f".format(boat.maxSpeedKn)} kn",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        boat.fuelConsumptionLh?.let { fc ->
                            Text("${"%.1f".format(fc)} L/h · ${boat.fuelCapacityL?.let { "${"%.0f".format(it)} L" } ?: "—"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    IconButton(onClick = {
                        if (!boat.isDefault) boatManager?.setDefault(boat)
                    }) {
                        Icon(Icons.Default.Star, contentDescription = stringResource(R.string.default_boat),
                            tint = if (boat.isDefault) Color(0xFFFFB300) else Color.Gray)
                    }
                    IconButton(onClick = { editingBoat = boat; showBoatDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_boat), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    IconButton(onClick = { boatToDelete = boat }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        item {
            TextButton(onClick = { editingBoat = null; showBoatDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_boat))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_boat))
            }
        }

        // ── MERENKULUN DATA ──
        item { SectionHeader(stringResource(R.string.settings_section_marine_data)) }

        // ── Väylävirasto official nav data download ──
        if (vvDataDownloader != null) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.vv_navdata_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.vv_navdata_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))

                        val isDownloaded by vvDataDownloader.isDownloaded.collectAsStateWithLifecycle()
                        val isDownloading by vvDataDownloader.isDownloading.collectAsStateWithLifecycle()
                        val dlProgress by vvDataDownloader.progress.collectAsStateWithLifecycle()
                        val statusText by vvDataDownloader.statusText.collectAsStateWithLifecycle()
                        val lastUpdated by vvDataDownloader.lastUpdated.collectAsStateWithLifecycle()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null,
                                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.vv_navdata_label), style = MaterialTheme.typography.bodyMedium)
                                Text("~12 MB", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                if (isDownloaded) {
                                    Text(stringResource(R.string.vv_navdata_downloaded),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                    if (lastUpdated != null) {
                                        Text("${stringResource(R.string.vv_navdata_updated)} $lastUpdated",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                } else {
                                    Text(stringResource(R.string.vv_navdata_not_downloaded),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                                if (isDownloading) {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = { dlProgress / 100f }, modifier = Modifier.fillMaxWidth())
                                    if (statusText != null) {
                                        Text(statusText ?: "", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else if (isDownloaded) {
                                IconButton(onClick = {
                                    scope.launch {
                                        vvDataDownloader.clear()
                                        onVvDataChanged()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                FilledTonalButton(onClick = {
                                    scope.launch {
                                        vvDataDownloader.download()
                                        onVvDataChanged()
                                    }
                                }) { Text(stringResource(R.string.vv_navdata_download_btn)) }
                            }
                        }
                    }
                }
            }
        }

        // ── Seamarks (OpenSeaMap) continent downloads ──
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.offline_seamarks_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.offline_seamarks_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))

                    val continents = fi.pursi.map.PmtilesDownloader.CONTINENTS
                    val downloaded by pmtilesDownloader?.downloadedContinents?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(emptySet()) }
                    val progressMap by pmtilesDownloader?.continentProgress?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(emptyMap()) }
                    val downloading by pmtilesDownloader?.downloadingContinent?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf<String?>(null) }

                    var showAll by rememberSaveable { mutableStateOf(false) }
                    val visibleContinents = if (showAll) continents else continents.take(1)

                    visibleContinents.forEach { c ->
                        val isDownloaded = c.id in downloaded
                        val dlProgress = progressMap[c.id] ?: 0f
                        val isThisDownloading = downloading == c.id

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Language, contentDescription = null,
                                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(c.nameResId), style = MaterialTheme.typography.bodyMedium)
                                Text("~${c.sizeMb} MB", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                if (isDownloaded) {
                                    val f = pmtilesDownloader?.continentFile(c.id)
                                    val actualMb = f?.let { if (it.exists()) "%.0f".format(it.length() / (1024.0 * 1024.0)) else null }
                                    Text(stringResource(R.string.offline_seamarks_downloaded, actualMb?.toIntOrNull() ?: c.sizeMb),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text(stringResource(R.string.offline_seamarks_not_downloaded),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                                if (isThisDownloading) {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { dlProgress / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(stringResource(R.string.seamarks_downloading, dlProgress.toInt()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                            if (isThisDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else if (isDownloaded) {
                                IconButton(onClick = {
                                    scope.launch {
                                        pmtilesDownloader?.deleteContinent(c.id)
                                        onSeamarksDataChanged()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                FilledTonalButton(onClick = {
                                    scope.launch {
                                        pmtilesDownloader?.downloadContinent(c.id)
                                        onSeamarksDataChanged()
                                    }
                                }) { Text(stringResource(R.string.seamarks_download_btn)) }
                            }
                        }
                        if (isDownloaded && !isThisDownloading) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (continents.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showAll = !showAll }) {
                            Text(if (showAll) stringResource(R.string.offline_seamarks_hide_all)
                                 else stringResource(R.string.offline_seamarks_show_all))
                        }
                    }
                }
            }
        }

        // ── OFFLINE-KARTAT ──
        item { SectionHeader(stringResource(R.string.settings_section_offline)) }

        if (downloadProgress.values.any { it.status == "COMPLETED" }) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.use_offline_charts), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Switch(checked = offlineMode, onCheckedChange = onToggleOfflineMode)
                        }
                        Text(if (offlineMode) stringResource(R.string.showing_cached) else stringResource(R.string.cached_available),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    val cachedTilesLabel = stringResource(R.string.cached_tiles)
                    var totalTiles by remember { mutableStateOf(0) }
                    var cacheStats by remember { mutableStateOf("...") }
                    LaunchedEffect(Unit) {
                        val count = withContext(Dispatchers.IO) { tileStorage.totalTileCount() }
                        totalTiles = count
                        val size = withContext(Dispatchers.IO) { tileStorage.totalCacheSizeFormatted() }
                        cacheStats = "$count $cachedTilesLabel, $size"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff, contentDescription = null,
                            tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isOnline) stringResource(R.string.online) else stringResource(R.string.offline),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(cacheStats,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onOpenAreaSelector?.invoke() ?: run { showAreaSelector = true } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.offline_area_download_btn), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.offline_area_download_btn))
                    }
                }
            }
        }

        items(downloadProgress.values.toList().sortedByDescending { it.status == "RUNNING" }, key = { it.jobId }) { job ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (job.status == "COMPLETED" && job.snapshotPath != null && File(job.snapshotPath).exists()) {
                        val snapBmp = remember(job.snapshotPath) {
                            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 3 }
                            android.graphics.BitmapFactory.decodeFile(job.snapshotPath, opts)
                        }
                        if (snapBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = snapBmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp, 54.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                    } else {
                        Icon(
                            if (job.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (job.status == "COMPLETED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(job.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        when (job.status) {
                            "RUNNING" -> {
                                Text("${job.completedTiles} / ${job.totalTiles} tiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Spacer(Modifier.height(4.dp))
                                val progress = if (job.totalTiles > 0) job.completedTiles.toFloat() / job.totalTiles else 0f
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                            "COMPLETED" -> {
                                val dateStr = remember {
                                    val df = java.text.SimpleDateFormat("d.M.yyyy", java.util.Locale.getDefault())
                                    df.format(java.util.Date(job.createdAt))
                                }
                                Row {
                                    Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${job.totalTiles} tiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            else -> {
                                Text(job.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        if (job.rectanglesJson.isNotBlank()) {
                            val rects = remember(job.rectanglesJson) { parseRectanglesJson(job.rectanglesJson) }
                            if (rects.isNotEmpty()) {
                                val r = rects.first()
                                Text("${r.maxLat.toInt()}°N  ${r.minLng.toInt()}°E", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                        if (job.status == "COMPLETED" && job.providerIds.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                job.providerIds.split(",").forEach { pid ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(when (pid) {
                                            "fi-traficom" -> "Traficom"
                                            "openseamap" -> "OSM"
                                            "openfreemap" -> "OFM"
                                            else -> pid
                                        }, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { downloadManager.delete(job.jobId) }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Clear cache ──
        item {
            Spacer(Modifier.height(8.dp))
            val cachedTilesLabel = stringResource(R.string.cached_tiles)
            var tilesToClear by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                tilesToClear = withContext(Dispatchers.IO) { tileStorage.totalTileCount() }
            }
            OutlinedButton(
                onClick = { tileCountToClear = tilesToClear; if (tilesToClear > 0) showConfirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = tilesToClear > 0
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.clear_cache) + " ($tilesToClear $cachedTilesLabel)")
            }
        }

        // ── MUUT ──
        item { SectionHeader(stringResource(R.string.settings_section_other)) }

        item {
            SettingsCard {
                SettingsItem(stringResource(R.string.debug_mode), stringResource(R.string.debug_mode_desc), debugMode, onToggleDebugMode)
            }
        }

        item {
            SettingsCard {
                SettingsItem(stringResource(R.string.analytics_enabled), stringResource(R.string.analytics_enabled_desc), analyticsEnabled, onToggleAnalytics)
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.location_source), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val options = listOf(false, true)
                        val labels = listOf(stringResource(R.string.location_source_android), stringResource(R.string.location_source_google))
                        options.forEachIndexed { index, opt ->
                            val selected = useGoogleLocation == opt
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleGoogleLocation(opt) },
                                label = { Text(labels[index], style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.location_source_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }

        // ── TIETOA ──
        item { SectionHeader(stringResource(R.string.settings_section_about)) }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_version), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.app_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(stringResource(R.string.license), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    val privacyUrl = context.getString(R.string.privacy_policy_url)
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                    }) {
                        Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.bodyMedium)
                    }
                    val ossUrl = context.getString(R.string.oss_licenses_url)
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ossUrl)))
                    }) {
                        Text(stringResource(R.string.oss_licenses), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.data_sources), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val credits = listOf(
                        stringResource(R.string.traficom_credit),
                        stringResource(R.string.vayla_credit),
                        stringResource(R.string.fmi_credit),
                        stringResource(R.string.osm_credit),
                        stringResource(R.string.openseamap_credit),
                        stringResource(R.string.fintraffic_credit),
                        stringResource(R.string.rainviewer_credit),
                        stringResource(R.string.nominatim_credit),
                        stringResource(R.string.kartverket_credit),
                        stringResource(R.string.metnorway_credit),
                        stringResource(R.string.smhi_credit),
                    )
                    credits.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.data_sources_note), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }

    // ── Boat dialog ──
    if (boatToDelete != null) {
        AlertDialog(
            onDismissRequest = { boatToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_boat_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { boatManager?.deleteBoat(boatToDelete!!) }
                    boatToDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { boatToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (showBoatDialog) {
        BoatDialog(
            boat = editingBoat,
            onDismiss = { showBoatDialog = false },
            onSave = { boat ->
                settingsViewModel.saveBoat(boat); editingBoat = null; showBoatDialog = false
            }
        )
    }
    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text(stringResource(R.string.clear_cache)) },
            text = { Text(stringResource(R.string.clear_cache_confirm, tileCountToClear)) },
            confirmButton = {
                TextButton(onClick = {
                    downloadManager.clearAll()
                    onClearWfsCache()
                    showConfirmClear = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
    // Inline area selector — only used on phone (compact) layout when no external callback
    if (onOpenAreaSelector == null && showAreaSelector) {
        AreaSelectionScreen(
            tileStorage = tileStorage,
            downloadManager = downloadManager,
            existingRects = existingRects,
            initialCamLat = initialCamLat,
            initialCamLon = initialCamLon,
            initialCamZoom = initialCamZoom,
            onBack = { showAreaSelector = false },
            onAreaSelected = { rect, snap ->
                showAreaSelector = false
                selectedRect = rect
                snapshotPath = snap
                showOfflineDialog = true
            }
        )
    }
    val tileSources = remember(chartProviders) {
        TileSourceBuilder.buildFromProviders(
            providers = chartProviders,
            extraSources = listOf(
                TileSource(
                    providerId = "openseamap", displayName = "OpenSeaMap",
                    urlTemplate = "https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png",
                    extension = "png", minZoom = 4, maxZoom = 18,
                    avgTileBytes = 15_000L,
                    coverageName = "Maailmanlaajuinen",
                    description = "Merimerkit (poijut, viitat, majakat)",
                    category = "seamarks",
                    coverage = fi.pursi.datasource.core.BoundingBox.WORLD
                ),
                TileSource(
                    providerId = "openfreemap", displayName = "OpenFreeMap",
                    urlTemplate = "https://tiles.openfreemap.org/planet/{z}/{x}/{y}.pbf",
                    extension = "pbf", minZoom = 0, maxZoom = 14,
                    avgTileBytes = 5_000L,
                    coverageName = "Maailmanlaajuinen",
                    description = "Vektoripohjakartta (maasto, tiet, nimistö)",
                    category = "base",
                    coverage = fi.pursi.datasource.core.BoundingBox.WORLD
                )
            )
        )
    }

    if (showOfflineDialog && selectedRect != null) {
        OfflineAreaDialog(
            rectangles = listOf(selectedRect!!),
            tileSources = tileSources,
            tileStorage = tileStorage,
            downloadManager = downloadManager,
            calculator = calculator,
            snapshotPath = snapshotPath,
            onDismiss = { showOfflineDialog = false; selectedRect = null; snapshotPath = null },
            onComplete = { showOfflineDialog = false; selectedRect = null; snapshotPath = null }
        )
    }
}

@Composable
private fun BoatDialog(boat: Boat?, onDismiss: () -> Unit, onSave: (Boat) -> Unit) {
    var name by remember { mutableStateOf(boat?.name ?: "") }
    var cruiseSpd by remember { mutableStateOf(boat?.cruisingSpeedKn?.toString() ?: "") }
    var maxSpd by remember { mutableStateOf(boat?.maxSpeedKn?.toString() ?: "") }
    var fuelCons by remember { mutableStateOf(boat?.fuelConsumptionLh?.toString() ?: "") }
    var fuelCap by remember { mutableStateOf(boat?.fuelCapacityL?.toString() ?: "") }
    var isDef by remember { mutableStateOf(boat?.isDefault ?: false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().imePadding(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (boat != null) stringResource(R.string.edit_boat) else stringResource(R.string.add_boat),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.boat_name)) }, singleLine = true)
                OutlinedTextField(value = cruiseSpd, onValueChange = { cruiseSpd = it }, label = { Text(stringResource(R.string.cruising_speed)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = maxSpd, onValueChange = { maxSpd = it }, label = { Text(stringResource(R.string.max_speed)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = fuelCons, onValueChange = { fuelCons = it }, label = { Text(stringResource(R.string.fuel_consumption)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(value = fuelCap, onValueChange = { fuelCap = it }, label = { Text(stringResource(R.string.fuel_capacity)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.default_boat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = isDef, onCheckedChange = { isDef = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val c = cruiseSpd.toFloatOrNull() ?: return@Button
                        val m = maxSpd.toFloatOrNull() ?: return@Button
                        onSave(Boat(id = boat?.id ?: 0, name = name, cruisingSpeedKn = c, maxSpeedKn = m,
                            fuelConsumptionLh = fuelCons.toFloatOrNull(), fuelCapacityL = fuelCap.toFloatOrNull(), isDefault = isDef))
                    }) { Text(stringResource(R.string.save_boat)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(8.dp)) { content() }
    }
}

@Composable
private fun SettingsItem(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
