package app.pursi.datasource.fi

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.WarningProvider
import app.pursi.weather.FmiClient
import app.pursi.weather.MarineWarning
import javax.inject.Inject

class FinnishWarningProvider @Inject constructor(
    private val client: FmiClient
) : WarningProvider {
    override val providerId = "fi-fmi-warnings"
    override val displayName = "Ilmatieteen laitos"
    override val coverage = BoundingBox(58.5, 70.5, 19.0, 32.0)
    override val supportedLanguages = listOf("fi", "sv", "en")
    override val priority = 1

    override suspend fun getMarineWarnings(
        language: String, latitude: Double, longitude: Double
    ): List<MarineWarning> {
        val fmiLang = when (language) {
            "sv" -> "sv"
            "en" -> "en"
            else -> "fi"
        }
        return client.getMarineWarnings(fmiLang, latitude, longitude)
    }
}
