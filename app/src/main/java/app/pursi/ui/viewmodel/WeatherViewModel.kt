package app.pursi.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import app.pursi.water.WaterObservation
import app.pursi.water.WaterObservationRepository
import app.pursi.datasource.core.WeatherProvider
import app.pursi.weather.ForecastPoint
import app.pursi.weather.LightningStrike
import app.pursi.weather.MarineWarning
import app.pursi.location.LocationStateHolder
import app.pursi.weather.StationWeatherData
import app.pursi.weather.WaterLevelStation
import app.pursi.weather.WaveStation
import app.pursi.weather.WeatherCapabilities
import app.pursi.weather.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: WeatherRepository,
    private val locationStateHolder: LocationStateHolder,
    private val waterObservationRepository: WaterObservationRepository
) : ViewModel() {

    private val _algaeTarget = MutableStateFlow<org.maplibre.android.geometry.LatLng?>(null)
    val algaeTarget: StateFlow<org.maplibre.android.geometry.LatLng?> = _algaeTarget.asStateFlow()

    fun setAlgaeTarget(lat: Double, lon: Double) {
        _algaeTarget.value = org.maplibre.android.geometry.LatLng(lat, lon)
    }

    fun clearAlgaeTarget() {
        _algaeTarget.value = null
    }

    companion object {
        private const val PREFS_FAVES = "weather_faves"
    }

    val stations: StateFlow<List<StationWeatherData>> = repository.stations
    val waveStations: StateFlow<List<WaveStation>> = repository.waveStations
    val forecast: StateFlow<List<ForecastPoint>> = repository.forecast
    val warnings: StateFlow<List<MarineWarning>> = repository.warnings
    val lightning: StateFlow<List<LightningStrike>> = repository.lightning
    val waterLevel: StateFlow<List<WaterLevelStation>> = repository.waterLevel
    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing

    val currentLocation = locationStateHolder.currentLocation

    val capabilities: StateFlow<WeatherCapabilities> = repository.activeProvider.map { provider ->
        WeatherCapabilities(
            hasObservations = provider?.supportsObservations ?: false,
            hasWaves = provider?.supportsWaves ?: false,
            hasForecast = provider?.supportsForecast ?: false,
            hasLightning = provider?.supportsLightning ?: false,
            hasWaterLevel = provider?.supportsWaterLevel ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherCapabilities())

    private val _selectedTab = MutableStateFlow(savedStateHandle.get<Int>("tab") ?: 0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _faves = MutableStateFlow(
        context.getSharedPreferences(PREFS_FAVES, Context.MODE_PRIVATE)
            .getStringSet("faves", emptySet()) ?: emptySet()
    )
    val faves: StateFlow<Set<String>> = _faves.asStateFlow()

    val algaeObservations: StateFlow<List<WaterObservation>> = waterObservationRepository.algaeObservations
    val tempObservations: StateFlow<List<WaterObservation>> = waterObservationRepository.tempObservations

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
        savedStateHandle["tab"] = tab
    }

    fun toggleFave(name: String) {
        val current = _faves.value.toMutableSet()
        if (name in current) current.remove(name) else current.add(name)
        _faves.value = current
        context.getSharedPreferences(PREFS_FAVES, Context.MODE_PRIVATE).edit()
            .putStringSet("faves", current).apply()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            repository.refresh(force)
        }
    }
}
