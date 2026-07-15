package app.pursi.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pursi.ui.viewmodel.FollowMode
import app.pursi.weather.WeatherUnitPrefs
import app.pursi.R

@Composable
fun MapControls(
    currentZoom: Double,
    recordingData: RecordingData,
    showLayersPanel: Boolean,
    showRadarSlider: Boolean,
    followMode: FollowMode,
    windData: WindData?,
    mapBearing: Float,
    windUnit: WeatherUnitPrefs.WindUnit,
    tempUnit: WeatherUnitPrefs.TempUnit,
    pressureUnit: WeatherUnitPrefs.PressureUnit,
    windMeterSize: WeatherUnitPrefs.WindMeterSize,
    showWindMeter: Boolean,
    showRadar: Boolean,
    splitMode: Boolean = false,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecordToggle: () -> Unit,
    onLayersToggle: () -> Unit,
    onCenterLocation: () -> Unit,
    onRadarSliderToggle: () -> Unit,
    onSplitToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Right side: Zoom +/- buttons (hidden in split mode — per-pane controls in P3)
        if (!splitMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onZoomIn,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.zoom_in))
                }
                SmallFloatingActionButton(
                    onClick = onZoomOut,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.zoom_out))
                }
            }
        }

        // Bottom-left: Wind meter + Radar toggle
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Bottom)
        ) {
            if (showWindMeter && windData?.speedMs != null && windData.directionDeg != null) {
                WindMeter(
                    windSpeedMs = windData.speedMs,
                    windDirectionDeg = windData.directionDeg,
                    temperatureC = windData.temperatureC,
                    pressureHPa = windData.pressureHPa,
                    mapBearing = mapBearing,
                    windUnit = windUnit,
                    tempUnit = tempUnit,
                    pressureUnit = pressureUnit,
                    sizeDp = WeatherUnitPrefs.windMeterDp(windMeterSize, LocalContext.current.resources.configuration.smallestScreenWidthDp).dp
                )
            }
            if (showRadar) {
                SmallFloatingActionButton(
                    onClick = onRadarSliderToggle,
                    containerColor = if (showRadarSlider)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Thunderstorm,
                        contentDescription = stringResource(R.string.radar_history),
                        tint = if (showRadarSlider) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Bottom-right buttons: Record + Layers + My Location
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val pulse by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.4f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
            )
            SmallFloatingActionButton(
                onClick = onRecordToggle,
                containerColor = if (recordingData.isRecording)
                    MaterialTheme.colorScheme.error.copy(alpha = pulse)
                else
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (recordingData.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (recordingData.isRecording) stringResource(R.string.stop_recording) else stringResource(R.string.start_recording),
                    tint = if (recordingData.isRecording) MaterialTheme.colorScheme.onError
                           else MaterialTheme.colorScheme.error
                )
            }
            if (!splitMode) {
                SmallFloatingActionButton(
                    onClick = onLayersToggle,
                    containerColor = if (showLayersPanel)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.layers))
                }
            }
            if (!splitMode) {
                SmallFloatingActionButton(
                    onClick = onCenterLocation,
                    containerColor = if (followMode != FollowMode.OFF)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.center_on_position),
                        tint = if (followMode != FollowMode.OFF) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            SmallFloatingActionButton(
                onClick = onSplitToggle,
                containerColor = if (splitMode)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.ViewColumn,
                    contentDescription = stringResource(R.string.split_view),
                    tint = if (splitMode) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun PaneControls(
    paneBearing: Float,
    orientationLabel: String? = null,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCenterLocation: () -> Unit,
    onCompassClick: () -> Unit,
    onLayersToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompassRose(
                mapBearing = paneBearing,
                onClick = onCompassClick,
                modifier = Modifier.size(40.dp)
            )
            orientationLabel?.let { label ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = onZoomIn,
                modifier = Modifier.size(32.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.zoom_in))
            }
            SmallFloatingActionButton(
                onClick = onZoomOut,
                modifier = Modifier.size(32.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.zoom_out))
            }
            SmallFloatingActionButton(
                onClick = onLayersToggle,
                modifier = Modifier.size(32.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.layers))
            }
            SmallFloatingActionButton(
                onClick = onCenterLocation,
                modifier = Modifier.size(32.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.center_on_position),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
