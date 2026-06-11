package app.pursi.weather

import kotlinx.serialization.Serializable

@Serializable
data class WeatherObservation(
    val stationName: String,
    val stationId: String = "",
    val stationLatitude: Double,
    val stationLongitude: Double,
    val timestamp: String,
    val distanceKm: Float? = null,
    val windSpeedMs: Float? = null,
    val windDirectionDeg: Float? = null,
    val windGustMs: Float? = null,
    val temperatureC: Float? = null,
    val pressureHPa: Float? = null,
    val humidityPercent: Float? = null,
    val visibilityM: Float? = null,
    val waveHeightM: Float? = null,
    val seaLevelM: Float? = null,
    val weatherCode: Int? = null
) {
    val hasAnyData: Boolean
        get() = windSpeedMs != null || windGustMs != null || temperatureC != null ||
                pressureHPa != null || humidityPercent != null || visibilityM != null ||
                weatherCode != null
}

@kotlinx.serialization.Serializable
data class MarineWarning(
    val event: String,
    val eventCode: String,
    val severity: String,
    val color: String,
    val description: String,
    val headline: String,
    val areaDesc: String,
    val onset: String,
    val expires: String,
    val windSpeedMs: Float? = null,
    val windDirectionDeg: Float? = null,
    val centroidLat: Double? = null,
    val centroidLon: Double? = null,
    val polygonCoords: String = ""
)

data class StationWeatherData(val station: WeatherObservation, val history: List<WeatherObservation> = emptyList())

data class WaveStation(val stationName: String, val latitude: Double, val longitude: Double, val timestamp: String,
    val waveHeightM: Float? = null, val waveDirectionDeg: Float? = null, val waterTemperatureC: Float? = null, val wavePeriodS: Float? = null)

data class WaterLevelStation(
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val waterLevelM: Float? = null,
    val distanceKm: Float? = null
)

data class ForecastPoint(val timestamp: Long, val temperatureC: Float? = null, val windSpeedMs: Float? = null,
    val windDirectionDeg: Float? = null,
    val pressureHPa: Float? = null,
    val weatherSymbol: Float? = null,
    val cloudiness: Float? = null, val precipitationMm: Float? = null)

@Serializable data class WeatherWarning(val id: String, val severity: WarningSeverity, val warningType: String, val area: String, val description: String, val startTime: String, val endTime: String, val issuedTime: String)
@Serializable enum class WarningSeverity { MINOR, MODERATE, SEVERE, VERY_SEVERE }
@Serializable data class LightningStrike(val latitude: Double, val longitude: Double, val timestamp: String, val epochTimestamp: Long = 0L, val peakCurrentKA: Float? = null, val isCloudFlash: Boolean = false)

data class WeatherCapabilities(
    val hasObservations: Boolean = false,
    val hasWaves: Boolean = false,
    val hasForecast: Boolean = false,
    val hasLightning: Boolean = false,
    val hasWaterLevel: Boolean = false
)
