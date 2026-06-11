package app.pursi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pursi.weather.WeatherUnitPrefs
import app.pursi.weather.WeatherUnitPrefs.WindUnit
import app.pursi.weather.WeatherUnitPrefs.TempUnit
import app.pursi.weather.WeatherUnitPrefs.PressureUnit

private enum class DisplayMode { WIND, TEMPERATURE, PRESSURE }

@Composable
fun WindMeter(
    windSpeedMs: Float?,
    windDirectionDeg: Float?,
    temperatureC: Float? = null,
    pressureHPa: Float? = null,
    mapBearing: Float = 0f,
    windUnit: WindUnit = WindUnit.MS,
    tempUnit: TempUnit = TempUnit.CELSIUS,
    pressureUnit: PressureUnit = PressureUnit.HPA,
    sizeDp: Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val hasData = windSpeedMs != null && windDirectionDeg != null
    val degText = if (hasData) "%.0f\u00b0".format(windDirectionDeg) else ""

    val arrowColor = when {
        windSpeedMs == null -> Color(0xFF333333)
        windSpeedMs >= 14f -> Color(0xFFD50000)
        windSpeedMs >= 11f -> Color(0xFFFFB300)
        else -> Color(0xFF333333)
    }

    val textMeasurer = rememberTextMeasurer()

    var displayMode by remember { mutableStateOf(DisplayMode.WIND) }

    LaunchedEffect(displayMode) {
        if (displayMode != DisplayMode.WIND) {
            kotlinx.coroutines.delay(5000L)
            displayMode = DisplayMode.WIND
        }
    }

    val (bottomValue, bottomUnit, bottomColor) = when (displayMode) {
        DisplayMode.WIND -> {
            if (hasData) {
                val (v, u) = WeatherUnitPrefs.formatWind(windSpeedMs!!, windUnit)
                Triple(v, u, arrowColor)
            } else {
                Triple("--", windUnit.label, arrowColor)
            }
        }
        DisplayMode.TEMPERATURE -> {
            val formatted = WeatherUnitPrefs.formatTemp(temperatureC, tempUnit)
            if (formatted != null) Triple(formatted.first, formatted.second, Color(0xFFE53935))
            else Triple("--", "", Color(0xFFE53935))
        }
        DisplayMode.PRESSURE -> {
            val formatted = WeatherUnitPrefs.formatPressure(pressureHPa, pressureUnit)
            if (formatted != null) Triple(formatted.first, formatted.second, Color(0xFF43A047))
            else Triple("--", "", Color(0xFF43A047))
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .clickable {
                displayMode = when (displayMode) {
                    DisplayMode.WIND ->
                        if (temperatureC != null) DisplayMode.TEMPERATURE
                        else if (pressureHPa != null) DisplayMode.PRESSURE
                        else DisplayMode.WIND
                    DisplayMode.TEMPERATURE ->
                        if (pressureHPa != null) DisplayMode.PRESSURE
                        else DisplayMode.WIND
                    DisplayMode.PRESSURE -> DisplayMode.WIND
                }
            }
            .semantics {
                contentDescription = "Wind meter"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val ringRadius = size.minDimension / 2f * 0.78f

            rotate(-mapBearing, pivot = Offset(cx, cy)) {
                // Ring shadow (dark offset)
                drawCircle(
                    color = Color(0x4D000000),
                    radius = ringRadius,
                    center = Offset(cx + 1.5f, cy + 1.5f),
                    style = Stroke(width = 1.5f)
                )
                // Main ring
                drawCircle(
                    color = Color(0xCCFFFFFF),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
                // North notch (triangle at top of ring)
                val notchPath = Path().apply {
                    moveTo(cx, cy - ringRadius - 6f)
                    lineTo(cx - 5f, cy - ringRadius + 3f)
                    lineTo(cx + 5f, cy - ringRadius + 3f)
                    close()
                }
                drawPath(notchPath, color = Color(0xCCFFFFFF), style = Fill)
                // N label above notch
                val nStyle = TextStyle(color = Color(0xCCFFFFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                val nResult = textMeasurer.measure(text = "N", style = nStyle)
                val nY = cy - ringRadius - 10f
                drawText(nResult, topLeft = Offset(cx - nResult.size.width / 2f, nY - nResult.size.height / 2f))

                if (hasData) {
                    val dir = windDirectionDeg ?: return@rotate
                    val arrowRotation = dir + 180f
                    val arrowLen = ringRadius + 4f

                    rotate(degrees = arrowRotation, pivot = Offset(cx, cy)) {
                        val shaftWidth = 3f
                        val headWidth = 8f
                        val headLen = 12f

                        val shaft = Path().apply {
                            moveTo(cx - shaftWidth / 2f, cy + arrowLen * 0.6f)
                            lineTo(cx + shaftWidth / 2f, cy + arrowLen * 0.6f)
                            lineTo(cx + shaftWidth / 2f, cy - arrowLen * 0.45f)
                            lineTo(cx - shaftWidth / 2f, cy - arrowLen * 0.45f)
                            close()
                        }
                        val head = Path().apply {
                            moveTo(cx, cy - arrowLen * 0.95f)
                            lineTo(cx - headWidth / 2f, cy - arrowLen * 0.45f)
                            lineTo(cx + headWidth / 2f, cy - arrowLen * 0.45f)
                            close()
                        }

                        // Arrow shadow (light offset)
                        translate(left = 1.5f, top = 2.5f) {
                            drawPath(shaft, color = Color(0x4DFFFFFF), style = Fill)
                            drawPath(head, color = Color(0x4DFFFFFF), style = Fill)
                        }

                        drawPath(shaft, color = arrowColor, style = Fill)
                        drawPath(head, color = arrowColor, style = Fill)
                    }
                }
            }

            // Center dot (not rotated)
            if (hasData) {
                drawCircle(
                    color = arrowColor,
                    radius = 4f,
                    center = Offset(cx, cy),
                    style = Fill
                )
            }
        }

        // Top: degree text (always present, not rotated)
        Text(
            text = degText,
            color = Color(0xFF333333),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
        )

        // Bottom: cyclable value + unit
        if (hasData || displayMode != DisplayMode.WIND) {
            Text(
                text = bottomValue,
                color = bottomColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            )
            Text(
                text = bottomUnit,
                color = bottomColor.copy(alpha = 0.8f),
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
            )
        }
    }
}
