package app.pursi.ui.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import app.pursi.data.dao.SavedRouteDao
import app.pursi.data.dao.TrackDao
import app.pursi.data.dao.TrackSummaryDao
import app.pursi.data.model.RouteWaypoint
import app.pursi.data.model.SavedRoute
import app.pursi.data.model.TrackPoint
import app.pursi.data.model.TrackSummary
import app.pursi.location.LocationStateHolder
import app.pursi.location.SpeedCalculator
import app.pursi.map.GeocodingClient
import app.pursi.map.OverpassClient
import app.pursi.map.PoiCategory
import app.pursi.map.PoiResult
import app.pursi.map.SearchResult
import app.pursi.navigation.RouteSimplifier
import app.pursi.DEFAULT_LAT
import app.pursi.DEFAULT_LON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RoutesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val trackSummaryDao: TrackSummaryDao,
    val savedRouteDao: SavedRouteDao,
    private val trackDao: TrackDao,
    private val geocodingClient: GeocodingClient,
    private val overpassClient: OverpassClient,
    private val locationStateHolder: LocationStateHolder
) : ViewModel() {

    val recordings = trackSummaryDao.getAllRecorded()
    val savedRoutes = savedRouteDao.getAll()

    val currentLocation = locationStateHolder.currentLocation

    private val _searchQ = MutableStateFlow("")
    val searchQ: StateFlow<String> = _searchQ.asStateFlow()

    private val _searchRes = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchRes: StateFlow<List<SearchResult>> = _searchRes.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    fun setSearchQ(q: String) {
        _searchQ.value = q
        if (q.isEmpty()) {
            _searchRes.value = emptyList()
        }
    }

    fun clearSearch() {
        _searchQ.value = ""
        _searchRes.value = emptyList()
    }

    fun search() {
        if (_searchQ.value.length < 2) return
        _searching.value = true
        viewModelScope.launch {
            try {
                val results = geocodingClient.search(_searchQ.value)
                val loc = locationStateHolder.currentLocation.value
                _searchRes.value = if (loc != null) {
                    results.sortedBy {
                        SpeedCalculator.distanceBetween(
                            loc.latitude, loc.longitude, it.latitude, it.longitude
                        )
                    }
                } else results
            } finally {
                _searching.value = false
            }
        }
    }

    private val _poiResults = MutableStateFlow<List<PoiResult>>(emptyList())
    val poiResults: StateFlow<List<PoiResult>> = _poiResults.asStateFlow()

    private val _selectedPoiCategory = MutableStateFlow<PoiCategory?>(null)
    val selectedPoiCategory: StateFlow<PoiCategory?> = _selectedPoiCategory.asStateFlow()

    private val _poiSearching = MutableStateFlow(false)
    val poiSearching: StateFlow<Boolean> = _poiSearching.asStateFlow()

    fun selectPoiCategory(category: PoiCategory?) {
        _selectedPoiCategory.value = category
        if (category == null) {
            _poiResults.value = emptyList()
            return
        }
        _poiSearching.value = true
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("pursi_map", Context.MODE_PRIVATE)
                val camLat = prefs.getFloat("cam_lat", Float.NaN).toDouble()
                val camLon = prefs.getFloat("cam_lon", Float.NaN).toDouble()
                val lat = if (camLat.isNaN() || camLon.isNaN()) {
                    locationStateHolder.currentLocation.value?.latitude ?: DEFAULT_LAT
                } else camLat
                val lon = if (camLat.isNaN() || camLon.isNaN()) {
                    locationStateHolder.currentLocation.value?.longitude ?: DEFAULT_LON
                } else camLon
                val results = overpassClient.query(category, lat, lon)
                val loc = locationStateHolder.currentLocation.value
                _poiResults.value = if (loc != null) {
                    results.sortedBy {
                        SpeedCalculator.distanceBetween(
                            loc.latitude, loc.longitude, it.latitude, it.longitude
                        )
                    }
                } else results
            } finally {
                _poiSearching.value = false
            }
        }
    }

    fun deleteRoute(route: SavedRoute) {
        viewModelScope.launch {
            savedRouteDao.deleteWaypoints(route.id)
            savedRouteDao.delete(route)
        }
    }

    fun getWaypointsForRoute(routeId: String, callback: (List<app.pursi.data.model.RouteWaypoint>) -> Unit) {
        viewModelScope.launch {
            callback(savedRouteDao.getWaypointsSync(routeId))
        }
    }

    fun getTrackCenter(trackId: String, callback: (Double, Double, Boolean) -> Unit) {
        viewModelScope.launch {
            val pts = trackDao.getTrackPointsSync(trackId)
            if (pts.isNotEmpty()) {
                val lat = pts.map { it.latitude }.average()
                val lon = pts.map { it.longitude }.average()
                callback(lat, lon, true)
            } else {
                callback(DEFAULT_LAT, DEFAULT_LON, false)
            }
        }
    }

    fun saveRecordingAsRoute(name: String, trackId: String, distanceNm: Double, boatId: Long?) {
        viewModelScope.launch {
            val points = trackDao.getTrackPoints(trackId).firstOrNull()
            if (points == null || points.isEmpty()) return@launch
            val simplified = RouteSimplifier.simplify(points.map { Pair(it.latitude, it.longitude) })
            val waypoints = simplified.mapIndexed { i, (lat, lon) ->
                RouteWaypoint(
                    routeId = "", name = "WP ${i + 1}",
                    latitude = lat, longitude = lon, order = i
                )
            }
            if (waypoints.isNotEmpty()) {
                val savedRoute = SavedRoute(
                    name = name, sourceTrackId = trackId, waypointCount = waypoints.size,
                    totalDistanceNm = distanceNm, boatId = boatId
                )
                savedRouteDao.insert(savedRoute)
                val wps = waypoints.map { it.copy(routeId = savedRoute.id) }
                savedRouteDao.insertWaypoints(wps)
            }
        }
    }

    fun renameItem(id: String, name: String) {
        viewModelScope.launch {
            val route = savedRouteDao.getById(id)
            if (route != null) {
                savedRouteDao.update(route.copy(name = name))
            } else {
                val recording = trackSummaryDao.getById(id)
                if (recording != null) {
                    trackSummaryDao.update(recording.copy(name = name))
                }
            }
        }
    }

    fun deleteRecording(recording: app.pursi.data.model.TrackSummary) {
        viewModelScope.launch {
            trackDao.deleteTrack(recording.id)
            trackSummaryDao.delete(recording)
        }
    }

    fun exportGpx(route: SavedRoute) {
        viewModelScope.launch {
            try {
                val xml = buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    appendLine("<gpx version=\"1.1\" creator=\"Pursi\">")
                    appendLine("  <rte>")
                    appendLine("    <name>${route.name.escapeXml()}</name>")
                    val waypoints = savedRouteDao.getWaypointsSync(route.id)
                    waypoints.forEach { wp ->
                        appendLine("    <rtept lat=\"${wp.latitude}\" lon=\"${wp.longitude}\">")
                        appendLine("      <name>${wp.name.escapeXml()}</name>")
                        appendLine("    </rtept>")
                    }
                    appendLine("  </rte>")
                    appendLine("</gpx>")
                }
                val file = File(context.cacheDir, "exports/route_${route.id}.gpx")
                withContext(Dispatchers.IO) {
                    file.parentFile?.mkdirs()
                    file.writeText(xml)
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(app.pursi.R.string.export_gpx_title)))
            } catch (_: Exception) {
                _errorMessage.tryEmit(context.getString(app.pursi.R.string.gpx_export_error))
            }
        }
    }

    fun exportGpxTrack(track: TrackSummary) {
        viewModelScope.launch {
            try {
                val points = trackDao.getTrackPoints(track.id).firstOrNull() ?: return@launch
                val xml = buildString {
                    appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    appendLine("<gpx version=\"1.1\" creator=\"Pursi\">")
                    appendLine("  <trk>")
                    appendLine("    <name>${track.name.escapeXml()}</name>")
                    appendLine("    <trkseg>")
                    points.forEach { pt ->
                        append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">")
                        pt.altitude?.let { append("<ele>$it</ele>") }
                        pt.timestamp.let { t ->
                            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            append("<time>${fmt.format(java.util.Date(t))}</time>")
                        }
                        appendLine("</trkpt>")
                    }
                    appendLine("    </trkseg>")
                    appendLine("  </trk>")
                    appendLine("</gpx>")
                }
                val file = File(context.cacheDir, "exports/track_${track.id}.gpx")
                withContext(Dispatchers.IO) {
                    file.parentFile?.mkdirs()
                    file.writeText(xml)
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(app.pursi.R.string.export_gpx_title)))
            } catch (_: Exception) {
                _errorMessage.tryEmit(context.getString(app.pursi.R.string.gpx_export_error))
            }
        }
    }

    companion object {
        private fun String.escapeXml(): String = this
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
