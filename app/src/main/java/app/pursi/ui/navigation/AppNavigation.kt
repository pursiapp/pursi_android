package app.pursi.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.pursi.R
import app.pursi.location.LocationService
import app.pursi.analytics.AnalyticsManager
import app.pursi.ui.theme.PursiTheme
import app.pursi.ui.theme.Dimens
import app.pursi.ui.screens.MapScreen
import app.pursi.ui.screens.RoutesScreen
import app.pursi.ui.screens.RoutesContent
import app.pursi.ui.screens.SettingsScreen
import app.pursi.ui.screens.WeatherScreen
import app.pursi.ui.screens.WeatherContent
import app.pursi.ui.screens.AreaSelectionScreen
import app.pursi.ui.viewmodel.FollowMode
import app.pursi.ui.viewmodel.MapViewModel
import app.pursi.ui.viewmodel.NightMode
import app.pursi.ui.viewmodel.OrientationMode
import app.pursi.ui.viewmodel.RoutesViewModel
import app.pursi.ui.viewmodel.SettingsViewModel
import app.pursi.ui.viewmodel.WeatherViewModel
import org.maplibre.android.geometry.LatLng
import app.pursi.map.LatLngRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.pursi.location.SpeedUnit
import app.pursi.weather.WeatherUnitPrefs

@Composable
fun AppNavigation(
    tileStorage: app.pursi.map.TileStorage,
    downloadManager: app.pursi.map.DownloadManager,
    networkMonitor: app.pursi.map.NetworkMonitor,
    analyticsManager: AnalyticsManager? = null,
) {
    val context = LocalContext.current

    val mapViewModel: MapViewModel = hiltViewModel()
    val isNightMode by mapViewModel.effectiveIsNightMode.collectAsStateWithLifecycle()
    val nightMode by mapViewModel.nightMode.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val weatherViewModel: WeatherViewModel = hiltViewModel()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            try {
                val intent = Intent(context, app.pursi.location.LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pursi_map", Context.MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_shown", false)) {
            val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val allGranted = permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                try {
                    val intent = Intent(context, app.pursi.location.LocationService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (_: Exception) { }
            } else {
                locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    val mapPrefs = context.getSharedPreferences("pursi_map", Context.MODE_PRIVATE)
    var courseLinesEnabled by remember { mutableStateOf(mapPrefs.getBoolean("course_lines_enabled", true)) }
    var keepScreenOn by remember { mutableStateOf(mapPrefs.getBoolean("keep_screen_on", false)) }
    var debugMode by remember { mutableStateOf(mapPrefs.getBoolean("debug_mode", false)) }
    var analyticsEnabled by remember { mutableStateOf(analyticsManager?.enabled ?: true) }
    var useGoogleLocation by remember { mutableStateOf(mapPrefs.getBoolean("use_google_location", false)) }
    var windUnit by remember { mutableStateOf(WeatherUnitPrefs.windUnit(mapPrefs)) }
    var tempUnit by remember { mutableStateOf(WeatherUnitPrefs.tempUnit(mapPrefs)) }
    var pressureUnit by remember { mutableStateOf(WeatherUnitPrefs.pressureUnit(mapPrefs)) }
    var boatSpeedUnit by remember { mutableStateOf(WeatherUnitPrefs.speedUnit(mapPrefs)) }
    var windMeterSize by remember { mutableStateOf(WeatherUnitPrefs.windMeterSize(mapPrefs)) }

    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()
    var offlineMode by rememberSaveable { mutableStateOf(false) }
    var savedCamLat by rememberSaveable { mutableStateOf(mapPrefs.getFloat("cam_lat", Float.NaN).toDouble()) }
    var savedCamLon by rememberSaveable { mutableStateOf(mapPrefs.getFloat("cam_lon", Float.NaN).toDouble()) }
    var savedCamZoom by rememberSaveable { mutableStateOf(mapPrefs.getFloat("cam_zoom", 7.0f).toDouble()) }

    LaunchedEffect(isOnline) {
        if (!isOnline && downloadManager.hasCompletedJobs()) {
            offlineMode = true
        }
    }

    LaunchedEffect(Unit) {
        downloadManager.loadJobs()
    }

    val onCameraMoved: (Double, Double, Double) -> Unit = { lat, lon, zoom ->
        savedCamLat = lat; savedCamLon = lon; savedCamZoom = zoom
        mapPrefs.edit().putFloat("cam_lat", lat.toFloat())
            .putFloat("cam_lon", lon.toFloat())
            .putFloat("cam_zoom", zoom.toFloat()).apply()
    }

    PursiTheme(darkTheme = isNightMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 600.dp
                val okHttp = remember { okhttp3.OkHttpClient() }
                val pmtilesDownloader = remember {
                    app.pursi.map.PmtilesDownloader(context, okHttp)
                }
                val vvDataDownloader = remember {
                    app.pursi.map.VvDataDownloader(context, okHttp)
                }
                val composeScope = rememberCoroutineScope()
                val shared = SharedState(
                    courseLinesEnabled = courseLinesEnabled,
                    keepScreenOn = keepScreenOn,
                    isOnline = isOnline,
                    offlineMode = offlineMode,
                    savedCamLat = savedCamLat,
                    savedCamLon = savedCamLon,
                    savedCamZoom = savedCamZoom,
                    nightMode = nightMode,
                    isNightMode = isNightMode,
                    mapViewModel = mapViewModel,
                    settingsViewModel = settingsViewModel,
                    weatherViewModel = weatherViewModel,
                    tileStorage = tileStorage,
                    downloadManager = downloadManager,
                    pmtilesDownloader = pmtilesDownloader,
                    vvDataDownloader = vvDataDownloader,
                    onCameraMoved = onCameraMoved,
                    onToggleOfflineMode = { offlineMode = it },
                    mapPrefs = mapPrefs,
                    onToggleCourseLines = { value -> mapPrefs.edit().putBoolean("course_lines_enabled", value).apply(); courseLinesEnabled = value },
                    onToggleKeepScreenOn = { value -> mapPrefs.edit().putBoolean("keep_screen_on", value).apply(); keepScreenOn = value },
                    debugMode = debugMode,
                    onToggleDebugMode = { value -> mapPrefs.edit().putBoolean("debug_mode", value).apply(); debugMode = value },
                    analyticsEnabled = analyticsEnabled,
                    onToggleAnalytics = { value -> analyticsManager?.setEnabled(value); analyticsEnabled = value },
                    onClearWfsCache = { composeScope.launch(Dispatchers.IO) { app.pursi.data.AppDatabase.getInstance(context).wfsFeatureDao().clearAll() } },
                    useGoogleLocation = useGoogleLocation,
                    onToggleGoogleLocation = { value ->
                        mapPrefs.edit().putBoolean("use_google_location", value).apply()
                        useGoogleLocation = value
                        ContextCompat.startForegroundService(context, Intent(context, LocationService::class.java))
                    },
                    analyticsManager = analyticsManager,
                    windUnit = windUnit,
                    onWindUnitChange = { WeatherUnitPrefs.setWindUnit(mapPrefs, it); windUnit = it },
                    tempUnit = tempUnit,
                    onTempUnitChange = { WeatherUnitPrefs.setTempUnit(mapPrefs, it); tempUnit = it },
                    pressureUnit = pressureUnit,
                    onPressureUnitChange = { WeatherUnitPrefs.setPressureUnit(mapPrefs, it); pressureUnit = it },
                    boatSpeedUnit = boatSpeedUnit,
                    onSpeedUnitChange = { WeatherUnitPrefs.setSpeedUnit(mapPrefs, it); boatSpeedUnit = it },
                    windMeterSize = windMeterSize,
                    onWindMeterSizeChange = { WeatherUnitPrefs.setWindMeterSize(mapPrefs, it); windMeterSize = it },
                )

                val isPortrait = maxHeight > maxWidth
                if (isCompact) {
                    CompactLayout(shared = shared)
                } else {
                    ExpandedLayout(shared = shared, isPortrait = isPortrait)
                }
            }
        }
    }
}

private data class SharedState(
    val courseLinesEnabled: Boolean,
    val keepScreenOn: Boolean,
    val isOnline: Boolean,
    val offlineMode: Boolean,
    val savedCamLat: Double,
    val savedCamLon: Double,
    val savedCamZoom: Double,
    val nightMode: NightMode,
    val isNightMode: Boolean,
    val mapViewModel: MapViewModel,
    val settingsViewModel: SettingsViewModel,
    val weatherViewModel: WeatherViewModel,
    val tileStorage: app.pursi.map.TileStorage,
    val downloadManager: app.pursi.map.DownloadManager,
    val pmtilesDownloader: app.pursi.map.PmtilesDownloader,
    val vvDataDownloader: app.pursi.map.VvDataDownloader,
    val onCameraMoved: (Double, Double, Double) -> Unit,
    val onToggleOfflineMode: (Boolean) -> Unit,
    val mapPrefs: android.content.SharedPreferences,
    val onToggleCourseLines: (Boolean) -> Unit,
    val onToggleKeepScreenOn: (Boolean) -> Unit,
    val debugMode: Boolean,
    val onToggleDebugMode: (Boolean) -> Unit,
    val analyticsEnabled: Boolean,
    val onToggleAnalytics: (Boolean) -> Unit,
    val onClearWfsCache: () -> Unit,
    val useGoogleLocation: Boolean,
    val onToggleGoogleLocation: (Boolean) -> Unit,
    val analyticsManager: AnalyticsManager?,
    val windUnit: WeatherUnitPrefs.WindUnit,
    val onWindUnitChange: (WeatherUnitPrefs.WindUnit) -> Unit,
    val tempUnit: WeatherUnitPrefs.TempUnit,
    val onTempUnitChange: (WeatherUnitPrefs.TempUnit) -> Unit,
    val pressureUnit: WeatherUnitPrefs.PressureUnit,
    val onPressureUnitChange: (WeatherUnitPrefs.PressureUnit) -> Unit,
    val boatSpeedUnit: SpeedUnit,
    val onSpeedUnitChange: (SpeedUnit) -> Unit,
    val windMeterSize: WeatherUnitPrefs.WindMeterSize,
    val onWindMeterSizeChange: (WeatherUnitPrefs.WindMeterSize) -> Unit,
)

@Composable
private fun CompactLayout(shared: SharedState) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                        label = { Text(stringResource(item.labelRes)) },
                        selected = currentDestination?.route?.let { route ->
                            item.routePattern.let { route.startsWith(it) }
                        } == true,
                        onClick = {
                            shared.analyticsManager?.trackTab(item.name)
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Map(),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable<Routes.Map> { backStackEntry ->
                val route = backStackEntry.toRoute<Routes.Map>()
                val centerTarget = if (route.searchLat != null && route.searchLon != null &&
                    !route.searchLat.isNaN() && !route.searchLon.isNaN()
                ) {
                    LatLng(route.searchLat, route.searchLon)
                } else null
                val mapViewingRouteId by shared.mapViewModel.viewingRouteId.collectAsStateWithLifecycle()
                val mapViewingTrackId by shared.mapViewModel.viewingTrackId.collectAsStateWithLifecycle()
                val uiState by shared.mapViewModel.uiState.collectAsStateWithLifecycle()
                val tileSources = remember(uiState.chartProviders) {
                    app.pursi.map.TileSourceBuilder.buildFromProviders(uiState.chartProviders)
                }
                MapScreen(
                    mapViewModel = shared.mapViewModel,
                    courseLinesEnabled = shared.courseLinesEnabled,
                    bottomInsetPx = innerPadding.calculateBottomPadding(),
                    offlineMode = shared.offlineMode,
                    tilesDirPath = shared.tileStorage.storagePath,
                    centerTarget = centerTarget,
                    initialCamLat = shared.savedCamLat,
                    initialCamLon = shared.savedCamLon,
                    initialCamZoom = shared.savedCamZoom,
                    onCameraMoved = shared.onCameraMoved,
                    viewingRouteId = mapViewingRouteId,
                    onClearViewingRoute = { shared.mapViewModel.setViewingRouteId(null) },
                    viewingTrackId = mapViewingTrackId,
                    onClearViewingTrack = { shared.mapViewModel.setViewingTrackId(null) },
                    onSearchPoi = {
                        navController.navigate(Routes.RouteList) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    showDebug = shared.debugMode,
                    isNightMode = shared.isNightMode,
                    windUnit = shared.windUnit,
                    tempUnit = shared.tempUnit,
                    pressureUnit = shared.pressureUnit,
                    windMeterSize = shared.windMeterSize,
                    snackbarHostState = snackbarHostState,
                    downloadManager = shared.downloadManager,
                    pmtilesDownloader = shared.pmtilesDownloader,
                    vvDataDownloader = shared.vvDataDownloader,
                    tileSources = tileSources,
                    onChooseCustom = {
                        navController.navigate(Routes.Settings) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }
                )
            }
            composable<Routes.RouteList> {
                val routesVm: RoutesViewModel = hiltViewModel()
                RoutesScreen(
                    routesViewModel = routesVm,
                    onNavigateToMap = { lat, lon ->
                        shared.mapViewModel.setFollowMode(FollowMode.OFF)
                        shared.mapViewModel.setSearchTarget(lat, lon)
                        navController.navigate(Routes.Map()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    onViewRoute = { routeId, lat, lon ->
                        shared.mapViewModel.setFollowMode(FollowMode.OFF)
                        shared.mapViewModel.setSearchTarget(lat, lon)
                        shared.mapViewModel.setViewingRouteId(routeId)
                        navController.navigate(Routes.Map()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    onViewTrack = { trackId, lat, lon ->
                        shared.mapViewModel.setFollowMode(FollowMode.OFF)
                        shared.mapViewModel.setSearchTarget(lat, lon)
                        shared.mapViewModel.setViewingTrackId(trackId)
                        navController.navigate(Routes.Map()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }
                )
            }
            composable<Routes.Weather> {
                WeatherScreen(
                    onAlgaeClick = { lat, lon ->
                        shared.mapViewModel.setSearchTarget(lat, lon)
                        navController.navigate(Routes.Map()) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable<Routes.Settings> {
                val uiState by shared.mapViewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    nightMode = shared.nightMode,
                    onNightModeChange = { shared.mapViewModel.setNightMode(it) },
                    sectorMode = uiState.sectorMode,
                    onSectorModeChange = { shared.mapViewModel.setSectorMode(it) },
                    courseLinesEnabled = shared.courseLinesEnabled,
                    onToggleCourseLines = shared.onToggleCourseLines,
                    keepScreenOn = shared.keepScreenOn,
                    onToggleKeepScreenOn = shared.onToggleKeepScreenOn,
                    tileStorage = shared.tileStorage,
                    downloadManager = shared.downloadManager,
                    offlineMode = shared.offlineMode,
                    onToggleOfflineMode = shared.onToggleOfflineMode,
                    isOnline = shared.isOnline,
                    navmarkSize = uiState.navmarkSize,
                    onNavmarkSizeChange = { shared.mapViewModel.setNavmarkSize(it) },
                    onSeamarksDataChanged = { shared.mapViewModel.refreshSeamarksStatus() },
                    pmtilesDownloader = shared.pmtilesDownloader,
                    vvDataDownloader = shared.vvDataDownloader,
                    onVvDataChanged = { shared.mapViewModel.refreshVvDataStatus() },
                    debugMode = shared.debugMode,
                    onToggleDebugMode = shared.onToggleDebugMode,
                    analyticsEnabled = shared.analyticsEnabled,
                    onToggleAnalytics = shared.onToggleAnalytics,
                    onClearWfsCache = shared.onClearWfsCache,
                    initialCamLat = shared.savedCamLat,
                    initialCamLon = shared.savedCamLon,
                    initialCamZoom = shared.savedCamZoom,
                    chartProviders = uiState.chartProviders,
                    useGoogleLocation = shared.useGoogleLocation,
                    onToggleGoogleLocation = shared.onToggleGoogleLocation,
                    boatIconSize = uiState.boatIconSize,
                    onBoatIconSizeChange = { shared.mapViewModel.setBoatIconSize(it) },
                    boatIconColor = uiState.boatIconColor,
                    onBoatIconColorChange = { shared.mapViewModel.setBoatIconColor(it) },
                    windUnit = shared.windUnit,
                    onWindUnitChange = shared.onWindUnitChange,
                    tempUnit = shared.tempUnit,
                    onTempUnitChange = shared.onTempUnitChange,
                    pressureUnit = shared.pressureUnit,
                    onPressureUnitChange = shared.onPressureUnitChange,
                    speedUnit = shared.boatSpeedUnit,
                    onSpeedUnitChange = shared.onSpeedUnitChange,
                    windMeterSize = shared.windMeterSize,
                    onWindMeterSizeChange = shared.onWindMeterSizeChange
                )
            }
        }
    }
}

@Composable
private fun ExpandedLayout(shared: SharedState, isPortrait: Boolean) {
    var expandedSelectedItem by rememberSaveable { mutableStateOf(BottomNavItem.Map.name) }
    var panelCenterTarget by remember { mutableStateOf<LatLng?>(null) }
    var panelViewingRouteId by remember { mutableStateOf<String?>(null) }
    var panelViewingTrackId by remember { mutableStateOf<String?>(null) }
    val routesViewModel: RoutesViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    // Area selector — rendered as full-screen overlay outside the side panel
    var showAreaSelector by rememberSaveable { mutableStateOf(false) }
    var pendingAreaResult by remember { mutableStateOf<Pair<LatLngRect, String>?>(null) }
    val downloadProgress by shared.downloadManager.progress.collectAsStateWithLifecycle(initialValue = emptyMap())
    val existingRects = remember(downloadProgress) { shared.downloadManager.existingRects() }

    Scaffold(
        bottomBar = {},
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val showPanel = expandedSelectedItem != BottomNavItem.Map.name
        BackHandler(enabled = showPanel) {
            expandedSelectedItem = BottomNavItem.Map.name
            panelCenterTarget = null
            panelViewingRouteId = null
            panelViewingTrackId = null
        }
        val panelContent = @Composable {
            ExpandedPanelContent(
                expandedSelectedItem = expandedSelectedItem,
                shared = shared,
                routesViewModel = routesViewModel,
                panelCenterTarget = panelCenterTarget,
                panelViewingRouteId = panelViewingRouteId,
                panelViewingTrackId = panelViewingTrackId,
                onPanelCenterTargetChanged = { panelCenterTarget = it },
                onPanelViewingRouteIdChanged = { panelViewingRouteId = it },
                onPanelViewingTrackIdChanged = { panelViewingTrackId = it },
                onOpenAreaSelector = { showAreaSelector = true },
                pendingAreaResult = pendingAreaResult,
                onConsumeAreaResult = { pendingAreaResult = null }
            )
        }
        val expandedUiState by shared.mapViewModel.uiState.collectAsStateWithLifecycle()
        val expandedTileSources = remember(expandedUiState.chartProviders) {
            app.pursi.map.TileSourceBuilder.buildFromProviders(expandedUiState.chartProviders)
        }
        val mapContent = @Composable {
            MapScreen(
                mapViewModel = shared.mapViewModel,
                courseLinesEnabled = shared.courseLinesEnabled,
                bottomInsetPx = 0.dp,
                offlineMode = shared.offlineMode,
                tilesDirPath = shared.tileStorage.storagePath,
                centerTarget = panelCenterTarget,
                initialCamLat = shared.savedCamLat,
                initialCamLon = shared.savedCamLon,
                initialCamZoom = shared.savedCamZoom,
                onCameraMoved = shared.onCameraMoved,
                viewingRouteId = panelViewingRouteId,
                onClearViewingRoute = { panelViewingRouteId = null },
                viewingTrackId = panelViewingTrackId,
                onClearViewingTrack = { panelViewingTrackId = null },
                onSearchPoi = { expandedSelectedItem = BottomNavItem.RouteList.name },
                showDebug = shared.debugMode,
                isNightMode = shared.isNightMode,
                windUnit = shared.windUnit,
                tempUnit = shared.tempUnit,
                pressureUnit = shared.pressureUnit,
                windMeterSize = shared.windMeterSize,
                snackbarHostState = snackbarHostState,
                downloadManager = shared.downloadManager,
                pmtilesDownloader = shared.pmtilesDownloader,
                vvDataDownloader = shared.vvDataDownloader,
                tileSources = expandedTileSources,
                onChooseCustom = { showAreaSelector = true }
            )
        }

        val onNavSelected: (BottomNavItem) -> Unit = { item ->
            shared.analyticsManager?.trackTab(item.name)
            expandedSelectedItem = item.name
            if (item == BottomNavItem.Map) {
                panelCenterTarget = null
                panelViewingRouteId = null
                panelViewingTrackId = null
            }
        }

        if (isPortrait) {
            PortraitExpandedContent(
                innerPadding = innerPadding,
                expandedSelectedItem = expandedSelectedItem,
                onNavSelected = onNavSelected,
                showPanel = showPanel,
                panelContent = panelContent,
                mapContent = mapContent
            )
        } else {
            LandscapeExpandedContent(
                innerPadding = innerPadding,
                expandedSelectedItem = expandedSelectedItem,
                onNavSelected = onNavSelected,
                showPanel = showPanel,
                panelContent = panelContent,
                mapContent = mapContent
            )
        }

        // Full-screen area selector overlay (escapes the 380dp side panel)
        if (showAreaSelector) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AreaSelectionScreen(
                    tileStorage = shared.tileStorage,
                    downloadManager = shared.downloadManager,
                    existingRects = existingRects,
                    initialCamLat = shared.savedCamLat,
                    initialCamLon = shared.savedCamLon,
                    initialCamZoom = shared.savedCamZoom,
                    onBack = { showAreaSelector = false },
                    onAreaSelected = { rect, snap ->
                        pendingAreaResult = Pair(rect, snap)
                        showAreaSelector = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExpandedPanelContent(
    expandedSelectedItem: String,
    shared: SharedState,
    routesViewModel: RoutesViewModel,
    panelCenterTarget: LatLng?,
    panelViewingRouteId: String?,
    panelViewingTrackId: String?,
    onPanelCenterTargetChanged: (LatLng?) -> Unit,
    onPanelViewingRouteIdChanged: (String?) -> Unit,
    onPanelViewingTrackIdChanged: (String?) -> Unit,
    onOpenAreaSelector: (() -> Unit)? = null,
    pendingAreaResult: Pair<LatLngRect, String>? = null,
    onConsumeAreaResult: () -> Unit = {}
) {
    when (BottomNavItem.entries.find { it.name == expandedSelectedItem }) {
        BottomNavItem.Weather -> WeatherContent(weatherViewModel = shared.weatherViewModel)
        BottomNavItem.RouteList -> RoutesContent(
            routesViewModel = routesViewModel,
            onNavigateToMap = { lat, lon ->
                onPanelViewingRouteIdChanged(null)
                onPanelViewingTrackIdChanged(null)
                onPanelCenterTargetChanged(LatLng(lat, lon))
                shared.mapViewModel.setFollowMode(FollowMode.OFF)
            },
            onViewRoute = { routeId, lat, lon ->
                onPanelViewingTrackIdChanged(null)
                onPanelViewingRouteIdChanged(routeId)
                onPanelCenterTargetChanged(LatLng(lat, lon))
                shared.mapViewModel.setFollowMode(FollowMode.OFF)
            },
            onViewTrack = { trackId, lat, lon ->
                onPanelViewingRouteIdChanged(null)
                onPanelViewingTrackIdChanged(trackId)
                onPanelCenterTargetChanged(LatLng(lat, lon))
                shared.mapViewModel.setFollowMode(FollowMode.OFF)
            }
        )
        BottomNavItem.Settings -> {
            val uiState by shared.mapViewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                settingsViewModel = shared.settingsViewModel,
                nightMode = shared.nightMode,
                onNightModeChange = { shared.mapViewModel.setNightMode(it) },
                sectorMode = uiState.sectorMode,
                onSectorModeChange = { shared.mapViewModel.setSectorMode(it) },
                courseLinesEnabled = shared.courseLinesEnabled,
                onToggleCourseLines = shared.onToggleCourseLines,
                keepScreenOn = shared.keepScreenOn,
                onToggleKeepScreenOn = shared.onToggleKeepScreenOn,
                tileStorage = shared.tileStorage,
                downloadManager = shared.downloadManager,
                offlineMode = shared.offlineMode,
                onToggleOfflineMode = shared.onToggleOfflineMode,
                isOnline = shared.isOnline,
                navmarkSize = uiState.navmarkSize,
                onNavmarkSizeChange = { shared.mapViewModel.setNavmarkSize(it) },
                onSeamarksDataChanged = { shared.mapViewModel.refreshSeamarksStatus() },
                pmtilesDownloader = shared.pmtilesDownloader,
                debugMode = shared.debugMode,
                onToggleDebugMode = shared.onToggleDebugMode,
                analyticsEnabled = shared.analyticsEnabled,
                onToggleAnalytics = shared.onToggleAnalytics,
                onClearWfsCache = shared.onClearWfsCache,
                vvDataDownloader = shared.vvDataDownloader,
                onVvDataChanged = { shared.mapViewModel.refreshVvDataStatus() },
                initialCamLat = shared.savedCamLat,
                initialCamLon = shared.savedCamLon,
                initialCamZoom = shared.savedCamZoom,
                chartProviders = uiState.chartProviders,
                useGoogleLocation = shared.useGoogleLocation,
                onToggleGoogleLocation = shared.onToggleGoogleLocation,
                boatIconSize = uiState.boatIconSize,
                onBoatIconSizeChange = { shared.mapViewModel.setBoatIconSize(it) },
                boatIconColor = uiState.boatIconColor,
                onBoatIconColorChange = { shared.mapViewModel.setBoatIconColor(it) },
                windUnit = shared.windUnit,
                onWindUnitChange = shared.onWindUnitChange,
                tempUnit = shared.tempUnit,
                onTempUnitChange = shared.onTempUnitChange,
                pressureUnit = shared.pressureUnit,
                onPressureUnitChange = shared.onPressureUnitChange,
                speedUnit = shared.boatSpeedUnit,
                onSpeedUnitChange = shared.onSpeedUnitChange,
                windMeterSize = shared.windMeterSize,
                onWindMeterSizeChange = shared.onWindMeterSizeChange,
                onOpenAreaSelector = onOpenAreaSelector,
                pendingAreaResult = pendingAreaResult,
                onConsumeAreaResult = onConsumeAreaResult
            )
        }
        else -> {}
    }
}

@Composable
private fun PortraitExpandedContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    expandedSelectedItem: String,
    onNavSelected: (BottomNavItem) -> Unit,
    showPanel: Boolean,
    panelContent: @Composable () -> Unit,
    mapContent: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomNavItem.entries.forEach { item ->
                    val isSelected = expandedSelectedItem == item.name
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { onNavSelected(item) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(item.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedVisibility(
                visible = showPanel,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it }
            ) {
                Surface(
                    modifier = Modifier
                        .width(Dimens.sidePanelWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    panelContent()
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                mapContent()
            }
        }
    }
}

@Composable
private fun LandscapeExpandedContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    expandedSelectedItem: String,
    onNavSelected: (BottomNavItem) -> Unit,
    showPanel: Boolean,
    panelContent: @Composable () -> Unit,
    mapContent: @Composable () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        NavigationRail {
            BottomNavItem.entries.forEach { item ->
                NavigationRailItem(
                    icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                    label = { Text(stringResource(item.labelRes)) },
                    selected = expandedSelectedItem == item.name,
                    onClick = { onNavSelected(item) }
                )
            }
        }

        AnimatedVisibility(
            visible = showPanel,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it }
        ) {
            Surface(
                modifier = Modifier
                    .width(Dimens.sidePanelWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface
            ) {
                panelContent()
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            mapContent()
        }
    }
}
