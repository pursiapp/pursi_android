package fi.pursi.datasource.fi

import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.core.WarningProvider
import fi.pursi.weather.FmiClient
import fi.pursi.weather.MarineWarning
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
