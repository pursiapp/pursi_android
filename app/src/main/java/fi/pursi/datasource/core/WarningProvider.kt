package fi.pursi.datasource.core

import fi.pursi.weather.MarineWarning

interface WarningProvider {
    val providerId: String
    val displayName: String
    val coverage: BoundingBox
    val supportedLanguages: List<String>
    val priority: Int
        get() = 0

    suspend fun getMarineWarnings(
        language: String,
        latitude: Double,
        longitude: Double
    ): List<MarineWarning>
}
