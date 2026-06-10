package fi.pursi.datasource.core

import fi.pursi.data.model.WfsFeature

data class WfsQueryResult(
    val features: List<WfsFeature>,
    val truncated: Boolean = false,
    val fromCache: Boolean = true
)

data class FeatureSource(
    val name: String,
    val displayName: String,
    val featureType: String,
    val urlTemplate: String,
    val extraParams: Map<String, String> = emptyMap(),
    val bboxSrs4326: Boolean = true,
    val maxFeatures: Int = 1000
)

interface MarineFeatureProvider {
    val providerId: String
    val displayName: String
    val coverage: BoundingBox
    val sources: List<FeatureSource>
    val priority: Int
        get() = 0

    suspend fun getFeatures(
        source: FeatureSource,
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double
    ): WfsQueryResult
}
