package app.pursi.datasource.core

import app.pursi.ais.AisVessel
import app.pursi.ais.VesselMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AisProvider {
    val providerId: String
    val displayName: String
    val coverage: BoundingBox
    val priority: Int
        get() = 0

    suspend fun getVesselsNearby(
        lat: Double, lon: Double, radiusKm: Int = 50
    ): List<AisVessel>

    suspend fun fetchVesselMetadata(mmsi: Int): VesselMetadata?

    val rateLimited: StateFlow<String?>
        get() = _emptyRateLimited

    companion object {
        private val _emptyRateLimited = MutableStateFlow<String?>(null)
    }
}
