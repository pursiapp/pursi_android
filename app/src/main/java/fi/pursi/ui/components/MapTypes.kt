package fi.pursi.ui.components

data class WindData(
    val speedMs: Float?,
    val directionDeg: Float?,
    val temperatureC: Float?,
    val pressureHPa: Float?
)

data class RecordingData(
    val isRecording: Boolean,
    val distanceNm: Double,
    val elapsedSec: Int
)

data class VvStatus(
    val downloaded: Boolean,
    val usingNetwork: Boolean,
    val turvalaiteCount: Int,
    val valosektoriCount: Int,
    val navlineCount: Int
)
