package app.pursi.datasource.core

data class DepthSample(
    val latitude: Double,
    val longitude: Double,
    val meanDepthM: Float?,
    val minDepthM: Float? = null,
    val maxDepthM: Float? = null,
    val stdevM: Float? = null,
    val source: String
)

interface DepthProvider {
    val providerId: String
    val displayName: String
    val coverage: BoundingBox
    val priority: Int

    suspend fun getDepthAt(lat: Double, lon: Double): DepthSample?
}
