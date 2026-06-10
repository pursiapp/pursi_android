package fi.pursi.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fi.pursi.water.WaterObservation
import fi.pursi.water.WaterObservationType
import fi.pursi.ui.viewmodel.MapViewModel
import fi.pursi.R
import fi.pursi.ui.viewmodel.WeatherViewModel
import fi.pursi.weather.ForecastPoint
import fi.pursi.weather.MarineWarning
import fi.pursi.weather.StationWeatherData
import fi.pursi.weather.WaterLevelStation
import fi.pursi.weather.WaveStation
import fi.pursi.weather.degreesToCompass
import fi.pursi.weather.estimateUV
import fi.pursi.weather.forecastEmoji
import fi.pursi.weather.moonPhase
import fi.pursi.weather.moonPhaseEmoji
import fi.pursi.weather.moonriseMoonset
import fi.pursi.weather.sunriseSunset
import fi.pursi.weather.warningColorHex
import fi.pursi.weather.weatherEmoji
import fi.pursi.weather.windArrow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun uvLabelRes(uv: Float): Int = when {
    uv < 3f -> R.string.uv_low; uv < 6f -> R.string.uv_moderate
    uv < 8f -> R.string.uv_high; uv < 11f -> R.string.uv_very_high
    else -> R.string.uv_extreme
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
    onAlgaeClick: (Double, Double) -> Unit = { _, _ -> }
) {
    val weatherViewModel: WeatherViewModel = hiltViewModel()
    val mapViewModel: MapViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        weatherViewModel.algaeTarget.collect { target ->
            if (target != null) {
                mapViewModel.setSearchTarget(target.latitude, target.longitude)
                onAlgaeClick(target.latitude, target.longitude)
                weatherViewModel.clearAlgaeTarget()
            }
        }
    }

    WeatherContent(weatherViewModel = weatherViewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherContent(
    weatherViewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val location by weatherViewModel.currentLocation.collectAsStateWithLifecycle()

    val stations by weatherViewModel.stations.collectAsStateWithLifecycle()
    val waves by weatherViewModel.waveStations.collectAsStateWithLifecycle()
    val waterLevel by weatherViewModel.waterLevel.collectAsStateWithLifecycle()
    val forecast by weatherViewModel.forecast.collectAsStateWithLifecycle()
    val warnings by weatherViewModel.warnings.collectAsStateWithLifecycle()
    val isRefreshing by weatherViewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedTab by weatherViewModel.selectedTab.collectAsStateWithLifecycle()
    val faves by weatherViewModel.faves.collectAsStateWithLifecycle()
    val algaeObs by weatherViewModel.algaeObservations.collectAsStateWithLifecycle()
    val tempObs by weatherViewModel.tempObservations.collectAsStateWithLifecycle()

    val tabs = listOf(
        stringResource(R.string.conditions_tab),
        stringResource(R.string.forecast_tab),
        stringResource(R.string.bulletin_tab)
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { weatherViewModel.refresh(force = true) },
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        Text(stringResource(R.string.weather_title),
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp).semantics { heading() })

        ScrollableTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { weatherViewModel.setSelectedTab(i) },
                    text = { Text(title) })
            }
        }

        when (selectedTab) {
             0 -> ConditionsTab(
                stations = stations,
                waves = waves,
                waterLevel = waterLevel,
                faves = faves,
                onToggleFave = { name -> weatherViewModel.toggleFave(name) },
                refLat = location?.latitude ?: 60.0,
                refLon = location?.longitude ?: 24.0,
                algaeObs = algaeObs,
                tempObs = tempObs,
                onAlgaeClick = { lat, lon -> weatherViewModel.setAlgaeTarget(lat, lon) }
            )
            1 -> ForecastTab(forecast, location?.latitude ?: 60.0, location?.longitude ?: 24.0)
            2 -> WarningsTab(warnings)
            }
        }
    }
}

// ── Conditions Tab ─────────────────────────────────────────────────────

@Composable
private fun ConditionsTab(
    stations: List<StationWeatherData>, waves: List<WaveStation>,
    waterLevel: List<WaterLevelStation>,
    faves: Set<String>, onToggleFave: (String) -> Unit,
    refLat: Double = 60.0, refLon: Double = 24.0,
    algaeObs: List<WaterObservation> = emptyList(),
    tempObs: List<WaterObservation> = emptyList(),
    onAlgaeClick: (Double, Double) -> Unit = { _, _ -> }
) {
    if (stations.isEmpty() && waves.isEmpty()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.loading),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }
    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        items(stations.filter { it.station.stationName in faves }) { s ->
            StationCard(s, true, onToggleFave, refLat, refLon); Spacer(Modifier.height(8.dp))
        }
        items(stations.filter { it.station.stationName !in faves }) { s ->
            StationCard(s, false, onToggleFave, refLat, refLon); Spacer(Modifier.height(8.dp))
        }
        // Water level — from mareograph stations
        if (waterLevel.isNotEmpty()) {
            item {
                Text(stringResource(R.string.water_level_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
            }
            items(waterLevel) { wl -> WaterLevelCard(wl); Spacer(Modifier.height(8.dp)) }
        }
        if (waves.isNotEmpty()) {
            item { Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.wave_buoys),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(waves) { w -> WaveCard(w); Spacer(Modifier.height(8.dp)) }
        }
        if (tempObs.isNotEmpty()) {
            val nearestTemps = tempObs.sortedBy { o ->
                val dlat = o.latitude - refLat
                val dlon = o.longitude - refLon
                dlat * dlat + dlon * dlon
            }.take(5)
            item { Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.water_surface_temp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(nearestTemps) { o ->
                WaterObsCard(o, refLat, refLon, onAlgaeClick)
                Spacer(Modifier.height(8.dp))
            }
        }
        if (algaeObs.isNotEmpty()) {
            val nearest = algaeObs.sortedBy { o ->
                val dlat = o.latitude - refLat
                val dlon = o.longitude - refLon
                dlat * dlat + dlon * dlon
            }.take(5)
            item { Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.algae_bloom),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(nearest) { o -> WaterObsCard(o, refLat, refLon, onAlgaeClick); Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun StationCard(d: StationWeatherData, fave: Boolean, onFave: (String) -> Unit, refLat: Double = 60.0, refLon: Double = 24.0) {
    val s = d.station
    val uv = estimateUV(refLat, refLon, System.currentTimeMillis() / 1000L)
    Card(modifier = Modifier.fillMaxWidth().clickable { onFave(s.stationName) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.stationName, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                s.distanceKm?.let { d ->
                    Text(if (d < 1f) "${(d * 1000).toInt()} m" else "${"%.1f".format(d)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Spacer(Modifier.weight(1f))
                Text(s.timestamp, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.width(4.dp))
                Text(if (fave) "★" else "☆",
                    color = if (fave) Color(0xFFFFB300) else Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Param(stringResource(R.string.wind), s.windSpeedMs?.let { "${"%.1f".format(it)} m/s" },
                    if (s.windDirectionDeg != null) "${windArrow(s.windDirectionDeg + 180f)} ${degreesToCompass(s.windDirectionDeg)} ${s.windDirectionDeg.toInt()}°" else null,
                    s.weatherCode?.let { weatherEmoji(it) })
                Param(stringResource(R.string.gusts), s.windGustMs?.let { "${"%.1f".format(it)} m/s" })
                Param(stringResource(R.string.temp), s.temperatureC?.let { "${"%.1f".format(it)}°C" })
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.SpaceEvenly) {
                Param(stringResource(R.string.pressure), s.pressureHPa?.let { "${"%.0f".format(it)} hPa" })
                Param(stringResource(R.string.humidity), s.humidityPercent?.let { "${"%.0f".format(it)}%" })
                Param(stringResource(R.string.visibility), s.visibilityM?.let {
                    if (it < 1000) "${it.toInt()} m" else "${"%.1f".format(it / 1000)} km"
                })
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.SpaceEvenly) {
                Param("UV", "${"%.0f".format(uv)} (${stringResource(uvLabelRes(uv))})")
            }
            if (d.history.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Sparkline(d.history.map { it.windSpeedMs }, Color(0xFF1976D2),
                    "${stringResource(R.string.wind)} (m/s)")
                if (d.history.any { it.temperatureC != null }) {
                    Spacer(Modifier.height(4.dp))
                    Sparkline(d.history.map { it.temperatureC }, Color(0xFFD32F2F),
                        "${stringResource(R.string.temp)} (°C)")
                }
            }
        }
    }
}

@Composable
private fun Param(label: String, value: String?, secondary: String? = null, symbol: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (symbol != null) Text(symbol, style = MaterialTheme.typography.bodyMedium)
            Text(value ?: "--", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        if (secondary != null) Text(secondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Sparkline(values: List<Float?>, color: Color, label: String) {
    val pts = values.mapIndexedNotNull { i, v -> if (v != null) Offset(i.toFloat(), v) else null }
    if (pts.size < 2) return
    val minV = pts.minOf { it.y }; val maxV = pts.maxOf { it.y }
    val range = (maxV - minV).coerceAtLeast(0.1f)
    val gridStep = when { maxV <= 2 -> 0.5f; maxV <= 5 -> 1f; maxV <= 10 -> 2f; maxV <= 20 -> 5f; else -> 10f }
    val gridStart = (minV / gridStep).toInt() * gridStep
    val gridEnd = ((maxV / gridStep) + 1).toInt() * gridStep

    Text(label, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        val w = size.width / (values.size - 1).coerceAtLeast(1); val gh = size.height
        val paint = android.graphics.Paint().apply {
            textSize = 10.sp.toPx(); this.color = android.graphics.Color.argb(120, 128, 128, 128)
        }
        val gNorm = gridStep / range * gh; val sNorm = ((gridStart - minV) / range * gh)
        for (i in 0..((gridEnd - gridStart) / gridStep).toInt()) {
            val y = gh - (sNorm + i * gNorm)
            if (y < 0f || y > gh) continue
            drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), 1f)
            drawContext.canvas.nativeCanvas.drawText("${"%.1f".format(gridStart + i * gridStep)}", 4f, y - 4f, paint)
        }
        val path = Path()
        pts.forEachIndexed { i, p ->
            val x = i * w; val y = gh - ((p.y - minV) / range * gh)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(2f))
    }
}

// ── Wave Card ──────────────────────────────────────────────────────────

@Composable
private fun WaveCard(w: WaveStation) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(w.stationName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Param(stringResource(R.string.wave_height), w.waveHeightM?.let { "${"%.1f".format(it)} m" })
                Param(stringResource(R.string.wave_direction), w.waveDirectionDeg?.let { "${degreesToCompass(it)} ${it.toInt()}°" })
                Param(stringResource(R.string.water_temp), w.waterTemperatureC?.let { "${"%.1f".format(it)}°C" })
                Param(stringResource(R.string.wave_period), w.wavePeriodS?.let { "${"%.1f".format(it)} s" })
            }
        }
    }
}

// ── Water Level Card ──────────────────────────────────────────────────

@Composable
private fun WaterLevelCard(wl: WaterLevelStation) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(wl.stationName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                wl.distanceKm?.let { d ->
                    Text(if (d < 1f) "${(d * 1000).toInt()} m" else "${"%.1f".format(d)} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Spacer(Modifier.weight(1f))
                Text(wl.timestamp, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val level = wl.waterLevelM ?: 0f
                    val prefix = if (level >= 0f) "+" else ""
                    Text("$prefix${"%.0f".format(level)} cm (MW)",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Forecast Tab ───────────────────────────────────────────────────────

@Composable
fun CelestialBlock(icon: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Text(time, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun ForecastTab(forecast: List<ForecastPoint>, lat: Double, lon: Double) {
    if (forecast.isEmpty()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.loading_forecast),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }
    // Sunrise/sunset and moonrise/moonset
    val now = System.currentTimeMillis() / 1000L
    val (sunrise, sunset) = sunriseSunset(lat, lon, now)
    val (moonrise, moonset) = moonriseMoonset(lat, lon, now)
    val moonPh = moonPhase(now)
    val timeFmt = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
    val moonPhaseLabelRes: Int = when {
        moonPh < 0.06f || moonPh > 0.94f -> R.string.moon_new
        moonPh < 0.19f -> R.string.moon_waxing_crescent
        moonPh < 0.31f -> R.string.moon_first_quarter
        moonPh < 0.44f -> R.string.moon_waxing_gibbous
        moonPh < 0.56f -> R.string.moon_full
        moonPh < 0.69f -> R.string.moon_waning_gibbous
        moonPh < 0.81f -> R.string.moon_last_quarter
        else -> R.string.moon_waning_crescent
    }

    // Hourly for first 24h, then every 3h for rest
    val sampled = forecast.mapIndexedNotNull { i, fp ->
        when {
            i < 24 -> fp
            i % 3 == 0 -> fp
            else -> null
        }
    }
    // Build a flat list with day header items interleaved
    data class ForecastItem(val isHeader: Boolean, val header: String = "", val point: ForecastPoint? = null)
    val dayFormat = SimpleDateFormat("EEEE d.M.", Locale.getDefault())
    val todayFormat = SimpleDateFormat("'Today' d.M.", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var prevDay = -1
    val items = mutableListOf<ForecastItem>()
    for (fp in sampled) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = fp.timestamp * 1000L }
        val day = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val year = cal.get(java.util.Calendar.YEAR)
        if (day != prevDay) {
            prevDay = day
            val nowCal = java.util.Calendar.getInstance()
            val header = if (day == nowCal.get(java.util.Calendar.DAY_OF_YEAR) && year == nowCal.get(java.util.Calendar.YEAR))
                todayFormat.format(Date(fp.timestamp * 1000L))
            else
                dayFormat.format(Date(fp.timestamp * 1000L))
            items.add(ForecastItem(isHeader = true, header = header))
        }
        items.add(ForecastItem(isHeader = false, point = fp))
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CelestialBlock("☀️↑", if (sunrise > 0) timeFmt.format(Date(sunrise * 1000L)) else "—")
                CelestialBlock("☀️↓", if (sunset > 0) timeFmt.format(Date(sunset * 1000L)) else "—")
                CelestialBlock(moonPhaseEmoji(moonPh) + "↑", if (moonrise > 0) timeFmt.format(Date(moonrise * 1000L)) else "—")
                CelestialBlock(moonPhaseEmoji(moonPh) + "↓", if (moonset > 0) timeFmt.format(Date(moonset * 1000L)) else "—")
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(stringResource(moonPhaseLabelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        item { Text(stringResource(R.string.hour_forecast),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(items) { item ->
            if (item.isHeader) {
                Text(item.header, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            } else {
                val fp = item.point ?: return@items
                Card(Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                        Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                        Text(timeFormat.format(Date(fp.timestamp * 1000L)),
                            modifier = Modifier.width(44.dp), fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall)
                        Text(forecastEmoji(fp), style = MaterialTheme.typography.titleMedium)
                        Param(stringResource(R.string.temp), fp.temperatureC?.let { "${"%.0f".format(it)}°C" })
                        fp.windSpeedMs?.let { ws ->
                            val dirText = fp.windDirectionDeg?.let { "${windArrow(it + 180f)} ${degreesToCompass(it)}" }
                            Param(stringResource(R.string.wind), "${"%.1f".format(ws)} m/s", dirText)
                        }
                        fp.precipitationMm?.let { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.precipitation_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(if (p > 0f) "${"%.1f".format(p)} mm" else "0.0",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (p > 0f) FontWeight.Bold else FontWeight.Normal,
                                    color = if (p > 0f) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Warnings Tab ───────────────────────────────────────────────────────

@Composable
private fun WarningsTab(warnings: List<MarineWarning>) {
    if (warnings.isEmpty()) {
        Text(stringResource(R.string.no_warnings), Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        return
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        items(warnings) { w ->
            val warnColor = Color(warningColorHex(w.color))
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = warnColor.copy(alpha = 0.1f))) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(warnColor, RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(w.event, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(w.areaDesc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(4.dp))
                    Text(w.headline, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(w.description, style = MaterialTheme.typography.bodySmall)
                    if (w.windSpeedMs != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val dir = w.windDirectionDeg?.let { "${windArrow(it + 180f)} ${degreesToCompass(it)}" } ?: ""
                        Text("${stringResource(R.string.wind)}: ${"%.0f".format(w.windSpeedMs)} m/s $dir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WaterObsCard(
    obs: WaterObservation,
    refLat: Double,
    refLon: Double,
    onAlgaeClick: (Double, Double) -> Unit
) {
    val distKm = kotlin.math.sqrt(
        (obs.latitude - refLat) * (obs.latitude - refLat) * 111.0 * 111.0 +
        (obs.longitude - refLon) * (obs.longitude - refLon) * 111.0 * kotlin.math.cos(Math.toRadians(refLat)) * 111.0 * kotlin.math.cos(Math.toRadians(refLat))
    )
    val distStr = when {
        distKm < 1.0 -> "${"%.0f".format(distKm * 1000)} m"
        distKm < 10.0 -> "${"%.1f".format(distKm)} km"
        else -> "${"%.0f".format(distKm)} km"
    }
    val locationStr = "${"%.4f".format(obs.latitude)}, ${"%.4f".format(obs.longitude)}"

    val levelColor = when (obs.type) {
        WaterObservationType.TEMPERATURE -> when {
            obs.temperatureC.isNaN() -> Color.Gray
            obs.temperatureC < 5 -> Color(0xFF2196F3)
            obs.temperatureC < 10 -> Color(0xFF4CAF50)
            obs.temperatureC < 15 -> Color(0xFFFDD835)
            obs.temperatureC < 20 -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        }
        WaterObservationType.ALGAE -> when (obs.algaeLevel) {
            0 -> Color(0xFF4CAF50)
            1 -> Color(0xFFFDD835)
            2 -> Color(0xFFFF9800)
            3 -> Color(0xFFF44336)
            else -> Color.Gray
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onAlgaeClick(obs.latitude, obs.longitude) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(levelColor, RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (obs.displayName.isNotBlank()) {
                    Text(obs.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Text(obs.titleLine, style = MaterialTheme.typography.bodySmall)
                Text("${obs.observerType} · ${obs.dateFormatted} · $distStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(locationStr, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
