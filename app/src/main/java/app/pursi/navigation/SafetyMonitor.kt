package app.pursi.navigation

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SafetyAlert(
    val type: AlertType,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AlertType {
    SHALLOW_WATER,
    RESTRICTED_AREA,
    MOB_ACTIVATED,
    ANCHOR_DRIFT,
    COLLISION_RISK,
    WEATHER_WARNING,
    SPEED_EXCEEDED,
    NAVIGATIONAL_WARNING
}

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

class SafetyMonitor {

    private val _alerts = MutableStateFlow<List<SafetyAlert>>(emptyList())
    val alerts: StateFlow<List<SafetyAlert>> = _alerts.asStateFlow()

    @Volatile private var mobPosition: Location? = null
    @Volatile private var mobTimestamp: Long = 0L

    @Volatile private var anchorPosition: Location? = null
    @Volatile private var anchorRadiusMeters: Float = 50f
    @Volatile private var anchorActive: Boolean = false

    fun activateMob(location: Location) {
        mobPosition = location
        mobTimestamp = System.currentTimeMillis()
        addAlert(
            SafetyAlert(
                type = AlertType.MOB_ACTIVATED,
                message = "MOB at ${location.latitude}, ${location.longitude}",
                severity = AlertSeverity.CRITICAL
            )
        )
    }

    fun getMobReturnInfo(): MobReturnInfo? {
        val pos = mobPosition ?: return null
        return MobReturnInfo(
            latitude = pos.latitude,
            longitude = pos.longitude,
            timestamp = mobTimestamp
        )
    }

    fun clearMob() {
        mobPosition = null
    }

    fun setAnchor(position: Location, radiusMeters: Float = 50f) {
        anchorPosition = position
        anchorRadiusMeters = radiusMeters
        anchorActive = true
    }

    fun disableAnchor() {
        anchorActive = false
        anchorPosition = null
    }

    fun checkForAlerts(currentLocation: Location) {
        checkAnchorDrift(currentLocation)
    }

    private fun checkAnchorDrift(location: Location) {
        val anchor = anchorPosition ?: return
        if (!anchorActive) return
        val distance = location.distanceTo(anchor)
        if (distance > anchorRadiusMeters) {
            addAlert(
                SafetyAlert(
                    type = AlertType.ANCHOR_DRIFT,
                    message = "Drift: ${distance.toInt()}m from anchor",
                    severity = AlertSeverity.WARNING
                )
            )
        }
    }

    fun dismissAlert(alert: SafetyAlert) {
        _alerts.value = _alerts.value - alert
    }

    fun clearAllAlerts() {
        _alerts.value = emptyList()
    }

    private fun addAlert(alert: SafetyAlert) {
        _alerts.value = _alerts.value + alert
    }
}

data class MobReturnInfo(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
