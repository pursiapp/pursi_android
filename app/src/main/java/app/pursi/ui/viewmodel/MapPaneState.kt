package app.pursi.ui.viewmodel

data class MapPaneState(
    val centerTrigger: Int = 0,
    val zoomToBoatTrigger: Int = 0,
    val zoomToBoatLevel: Float = 7f,
    val followMode: FollowMode = FollowMode.CENTERED,
    val orientationMode: OrientationMode = OrientationMode.NORTH_UP,
    val initialCamLat: Double = Double.NaN,
    val initialCamLon: Double = Double.NaN,
    val initialCamZoom: Double = 7.0,
    val paneLayerState: PaneLayerState = PaneLayerState(),
)
