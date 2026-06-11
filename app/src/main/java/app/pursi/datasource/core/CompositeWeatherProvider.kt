package app.pursi.datasource.core

import app.pursi.weather.ForecastPoint
import app.pursi.weather.LightningStrike
import app.pursi.weather.StationWeatherData
import app.pursi.weather.WaterLevelStation
import app.pursi.weather.WaveStation

class CompositeWeatherProvider(
    private val primary: WeatherProvider,
    private val fallbacks: List<WeatherProvider>
) : WeatherProvider by primary {

    override suspend fun getNearestWeatherStations(
        latitude: Double, longitude: Double, maxStations: Int
    ): List<StationWeatherData> {
        val result = primary.getNearestWeatherStations(latitude, longitude, maxStations)
        if (result.isNotEmpty()) return result
        for (fb in fallbacks) {
            val fbResult = fb.getNearestWeatherStations(latitude, longitude, maxStations)
            if (fbResult.isNotEmpty()) return fbResult
        }
        return emptyList()
    }

    override suspend fun getWaveObservations(
        latitude: Double, longitude: Double
    ): List<WaveStation> {
        val result = primary.getWaveObservations(latitude, longitude)
        if (result.isNotEmpty()) return result
        for (fb in fallbacks) {
            val fbResult = fb.getWaveObservations(latitude, longitude)
            if (fbResult.isNotEmpty()) return fbResult
        }
        return emptyList()
    }

    override suspend fun getForecast(
        latitude: Double, longitude: Double
    ): List<ForecastPoint> {
        val result = primary.getForecast(latitude, longitude)
        if (result.isNotEmpty()) return result
        for (fb in fallbacks) {
            val fbResult = fb.getForecast(latitude, longitude)
            if (fbResult.isNotEmpty()) return fbResult
        }
        return emptyList()
    }

    override suspend fun getLightningData(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double
    ): List<LightningStrike> {
        val result = primary.getLightningData(minLat, minLng, maxLat, maxLng)
        if (result.isNotEmpty()) return result
        for (fb in fallbacks) {
            val fbResult = fb.getLightningData(minLat, minLng, maxLat, maxLng)
            if (fbResult.isNotEmpty()) return fbResult
        }
        return emptyList()
    }

    override suspend fun getWaterLevelData(
        latitude: Double, longitude: Double
    ): List<WaterLevelStation> {
        val result = primary.getWaterLevelData(latitude, longitude)
        if (result.isNotEmpty()) return result
        for (fb in fallbacks) {
            val fbResult = fb.getWaterLevelData(latitude, longitude)
            if (fbResult.isNotEmpty()) return fbResult
        }
        return emptyList()
    }
}
