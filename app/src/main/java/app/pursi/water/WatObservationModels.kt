package app.pursi.water

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WaterObservationType { ALGAE, TEMPERATURE }

data class WaterObservation(
    val type: WaterObservationType,
    val latitude: Double,
    val longitude: Double,
    val algaeLevel: Int = 0,
    val temperatureC: Double = Double.NaN,
    val timestamp: Long,
    val source: String,
    val yllapito: String = "",
    val siteName: String = "",
    val lakeName: String = ""
) {
    val algaeLevelText: String
        get() = when (algaeLevel) {
            0 -> "Ei levää"
            1 -> "Hieman levää"
            2 -> "Runsaasti levää"
            3 -> "Erittäin runsaasti levää"
            else -> "?"
        }

    val algaeLevelColor: String
        get() = when (algaeLevel) {
            0 -> "#4CAF50"
            1 -> "#FFEB3B"
            2 -> "#FF9800"
            3 -> "#F44336"
            else -> "#9E9E9E"
        }

    val tempColor: String
        get() = when {
            temperatureC.isNaN() -> "#9E9E9E"
            temperatureC < 5 -> "#2196F3"
            temperatureC < 10 -> "#4CAF50"
            temperatureC < 15 -> "#FFEB3B"
            temperatureC < 20 -> "#FF9800"
            else -> "#F44336"
        }

    val tempFormatted: String
        get() = if (temperatureC.isNaN()) "?" else "${"%.1f".format(temperatureC)}°C"

    val tempLabel: String
        get() = if (temperatureC.isNaN()) "" else "${"%.1f".format(temperatureC)}°"

    val dateFormatted: String
        get() = if (timestamp > 0) {
            SimpleDateFormat("d.M.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        } else "?"

    val sourceFormatted: String
        get() = when {
            source.contains("Valtakunnallinen", ignoreCase = true) -> "Valtakunnallinen leväseuranta"
            source.contains("Rotarien", ignoreCase = true) -> "Rotarien sinileväseuranta"
            source.isNotBlank() -> source
            else -> "Kansalaishavainto"
        }

    val observerType: String
        get() = when {
            yllapito.contains("Viranomai", ignoreCase = true) -> "Viranomainen"
            yllapito.contains("Asiantuntija", ignoreCase = true) -> "Asiantuntija"
            source.contains("Valtakunnallinen", ignoreCase = true) -> "Viranomainen"
            source.contains("Rotarien", ignoreCase = true) -> "Järjestö"
            source == "-" || source.isBlank() -> "Kansalainen"
            else -> "Kansalainen"
        }

    val displayName: String
        get() = when {
            siteName.isNotBlank() -> siteName
            lakeName.isNotBlank() -> lakeName
            else -> "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
        }

    val titleLine: String
        get() = when (type) {
            WaterObservationType.TEMPERATURE -> tempFormatted
            WaterObservationType.ALGAE -> algaeLevelText
        }

    val circleColor: String
        get() = when (type) {
            WaterObservationType.TEMPERATURE -> tempColor
            WaterObservationType.ALGAE -> algaeLevelColor
        }
}
