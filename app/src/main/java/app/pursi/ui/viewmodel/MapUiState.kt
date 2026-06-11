package app.pursi.ui.viewmodel

import app.pursi.water.WaterObservation
import app.pursi.data.model.WfsFeature
import app.pursi.datasource.core.ChartProvider

enum class NavmarkSize(val multiplier: Float) {
    SMALL(0.7f),
    MEDIUM(1.0f),
    LARGE(1.4f)
}

enum class BoatIconSize(val multiplier: Float) {
    SMALL(0.5f),
    MEDIUM(1.0f),
    LARGE(1.5f)
}

data class MapUiState(
    val showLightning: Boolean = false,
    val showWarnings: Boolean = false,
    val showRadar: Boolean = false,
    val radarOpacity: Float = 0.7f,
    val radarTimeOffset: Int = 0,
    val radarEffectiveDelay: Int = 0,
    val chartOpacity: Float = 1.0f,
    val chartProviders: List<ChartProvider> = emptyList(),
    val lookAheadSec: Int = 5,
    val currentTrackId: String? = null,
    val followMode: FollowMode = FollowMode.CENTERED,
    val orientationMode: OrientationMode = OrientationMode.COURSE_UP,
    val showAis: Boolean = false,
    val seamarksDownloaded: Boolean = false,
    val downloadedSeamarkContinents: Set<String> = emptySet(),
    val showDepth: Boolean = true,
    val showWindMeter: Boolean = false,
    val depthFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    val navmarkSize: NavmarkSize = NavmarkSize.MEDIUM,
    val boatIconSize: BoatIconSize = BoatIconSize.MEDIUM,
    val boatIconColor: String = "#F57C00",
    val showAlgae: Boolean = false,
    val waterObservations: List<WaterObservation> = emptyList(),
    val selectedSeamark: SeamarkDetail? = null,
    val selectedAlgaeObservations: List<WaterObservation> = emptyList(),
    val sectorMode: SectorMode = SectorMode.NIGHT,

    val fiState: FinnishMapState? = null
)

data class FinnishMapState(
    val vvDataDownloaded: Boolean = false,
    val vvUsingNetwork: Boolean = false,
    val vvFetchCounter: Int = 0,
    val showVvNavmarks: Boolean = true,
    val showTurvalaiteviat: Boolean = true,
    val turvalaiteFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    val valosektoriFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    val vesiliikennemerkkiFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    val navlineFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    val fairwayFeatures: Map<String, List<WfsFeature>> = emptyMap(),
    val turvalaitevikaFeatures: Map<String, List<WfsFeature>> = emptyMap()
)
