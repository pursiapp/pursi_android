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
    val chartOpacity: Float = 0.0f,
    val navmarkSize: NavmarkSize = NavmarkSize.MEDIUM,
    val boatIconSize: BoatIconSize = BoatIconSize.MEDIUM,
    val boatIconColor: String = "#1976D2",
    val orientationMode: OrientationMode = OrientationMode.COURSE_UP,
) {
    fun toJson(): String = org.json.JSONObject().apply {
        put("chartMode", chartMode.name)
        put("showLightning", showLightning)
        put("showWarnings", showWarnings)
        put("showRadar", showRadar)
        put("radarTimeOffset", radarTimeOffset)
        put("radarOpacity", radarOpacity.toDouble())
        put("showAis", showAis)
        put("showAlgae", showAlgae)
        put("showDepth", showDepth)
        put("showWindMeter", showWindMeter)
        put("showVvNavmarks", showVvNavmarks)
        put("showTurvalaiteviat", showTurvalaiteviat)
        put("showSectors", showSectors)
        put("chartOpacity", chartOpacity.toDouble())
        put("navmarkSize", navmarkSize.name)
        put("boatIconSize", boatIconSize.name)
        put("boatIconColor", boatIconColor)
        put("orientationMode", orientationMode.name)
    }.toString()

    companion object {
        fun fromJson(json: String?): PaneLayerState {
            if (json == null) return PaneLayerState()
            return try {
                val obj = org.json.JSONObject(json)
                PaneLayerState(
                    chartMode = tryOr { PaneChartMode.valueOf(obj.optString("chartMode", PaneChartMode.Auto.name)) } ?: PaneChartMode.Auto,
                    showLightning = obj.optBoolean("showLightning", false),
                    showWarnings = obj.optBoolean("showWarnings", false),
                    showRadar = obj.optBoolean("showRadar", false),
                    radarTimeOffset = obj.optInt("radarTimeOffset", 0),
                    radarOpacity = obj.optDouble("radarOpacity", 0.4).toFloat(),
                    showAis = obj.optBoolean("showAis", false),
                    showAlgae = obj.optBoolean("showAlgae", false),
                    showDepth = obj.optBoolean("showDepth", true),
                    showWindMeter = obj.optBoolean("showWindMeter", false),
                    showVvNavmarks = obj.optBoolean("showVvNavmarks", true),
                    showTurvalaiteviat = obj.optBoolean("showTurvalaiteviat", true),
                    showSectors = obj.optBoolean("showSectors", true),
                    chartOpacity = obj.optDouble("chartOpacity", 0.0).toFloat(),
                    navmarkSize = tryOr { NavmarkSize.valueOf(obj.optString("navmarkSize", NavmarkSize.MEDIUM.name)) } ?: NavmarkSize.MEDIUM,
                    boatIconSize = tryOr { BoatIconSize.valueOf(obj.optString("boatIconSize", BoatIconSize.MEDIUM.name)) } ?: BoatIconSize.MEDIUM,
                    boatIconColor = obj.optString("boatIconColor", "#1976D2"),
                    orientationMode = tryOr { OrientationMode.valueOf(obj.optString("orientationMode", OrientationMode.COURSE_UP.name)) } ?: OrientationMode.COURSE_UP,
                )
            } catch (_: Exception) {
                PaneLayerState()
            }
        }

        fun fromMapUiState(state: MapUiState): PaneLayerState = PaneLayerState(
            chartMode = PaneChartMode.Auto,
            showLightning = state.showLightning,
            showWarnings = state.showWarnings,
            showRadar = state.showRadar,
            radarTimeOffset = state.radarTimeOffset,
            radarOpacity = state.radarOpacity,
            showAis = state.showAis,
            showAlgae = state.showAlgae,
            showDepth = state.showDepth,
            showWindMeter = state.showWindMeter,
            showVvNavmarks = state.fiState?.showVvNavmarks ?: true,
            showTurvalaiteviat = state.fiState?.showTurvalaiteviat ?: true,
            showSectors = true,
            chartOpacity = state.chartOpacity,
            navmarkSize = state.navmarkSize,
            boatIconSize = state.boatIconSize,
            boatIconColor = state.boatIconColor,
            orientationMode = state.orientationMode,
        )
    }
}

enum class PaneChartMode { Auto, VectorOnly, RasterOnly, Custom }

private inline fun <reified T> tryOr(body: () -> T): T? = try { body() } catch (_: Exception) { null }
