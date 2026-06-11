package app.pursi.datasource.fi

import app.pursi.ais.AisVessel
import app.pursi.ais.DigitrafficClient
import app.pursi.ais.VesselMetadata
import app.pursi.datasource.core.AisProvider
import app.pursi.datasource.core.BoundingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class DigitrafficAisProvider @Inject constructor(
    private val client: DigitrafficClient
) : AisProvider {
    override val providerId = "fi-digitraffic"
    override val displayName = "Digitraffic (Fintraffic)"
    override val coverage = BoundingBox(58.0, 67.0, 19.0, 30.0)
    override val priority = 1

    override suspend fun getVesselsNearby(lat: Double, lon: Double, radiusKm: Int): List<AisVessel> {
        val (vessels, rateLimit) = client.getVesselsNearby(lat, lon, radiusKm)
        _rateLimited.value = rateLimit
        return vessels
    }

    override suspend fun fetchVesselMetadata(mmsi: Int): VesselMetadata? =
        client.fetchVesselMetadata(mmsi)

    private val _rateLimited = MutableStateFlow<String?>(null)
    override val rateLimited: StateFlow<String?> = _rateLimited.asStateFlow()
}
