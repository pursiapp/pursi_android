package app.pursi.ui.viewmodel

data class PaneLayerState(
    val chartMode: PaneChartMode = PaneChartMode.Auto,
    val showLightning: Boolean = false,
    val showWarnings: Boolean = false,
    val showRadar: Boolean = false,
    val radarTimeOffset: Int = 0,
    val radarOpacity: Float = 0.4f,
    val showAis: Boolean = false,
    val showAlgae: Boolean = false,
    val showDepth: Boolean = true,
    val showWindMeter: Boolean = false,
    val showVvNavmarks: Boolean = true,
    val showTurvalaiteviat: Boolean = true,
    val showSectors: Boolean = true,
    val chartOpacity: Float = 1.0f,
    val navmarkSize: NavmarkSize = NavmarkSize.MEDIUM,
    val boatIconSize: BoatIconSize = BoatIconSize.MEDIUM,
    val boatIconColor: String = "#1976D2",
)

enum class PaneChartMode { Auto, VectorOnly, RasterOnly, Custom }
