package fi.pursi.water

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterObservationRepository @Inject constructor(
    private val client: WaterObservationClient
) {
    private val _observations = MutableStateFlow<List<WaterObservation>>(emptyList())
    val observations: StateFlow<List<WaterObservation>> = _observations.asStateFlow()

    private val _algaeObservations = MutableStateFlow<List<WaterObservation>>(emptyList())
    val algaeObservations: StateFlow<List<WaterObservation>> = _algaeObservations.asStateFlow()
    private val _tempObservations = MutableStateFlow<List<WaterObservation>>(emptyList())
    val tempObservations: StateFlow<List<WaterObservation>> = _tempObservations.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        startAutoRefresh()
    }

    fun startAutoRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            while (isActive) {
                try {
                    val algae = client.fetchAlgae()
                    val temps = client.fetchTemperature()
                    val cutoff = System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000L
                    val freshAlgae = algae.filter { it.timestamp == 0L || it.timestamp >= cutoff }
                    val freshTemps = temps.filter { it.timestamp == 0L || it.timestamp >= cutoff }
                    _algaeObservations.value = freshAlgae
                    _tempObservations.value = freshTemps
                    _observations.value = freshAlgae + freshTemps
                    android.util.Log.d("WaterObsRepo", "Refreshed ${freshAlgae.size}/${algae.size} algae + ${freshTemps.size}/${temps.size} temps")
                } catch (e: Exception) {
                    android.util.Log.e("WaterObsRepo", "Refresh failed: ${e.message}")
                }
                delay(30 * 60 * 1000L)
            }
        }
    }
}
