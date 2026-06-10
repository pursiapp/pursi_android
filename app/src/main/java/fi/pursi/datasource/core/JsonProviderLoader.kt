package fi.pursi.datasource.core

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonProviderLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadChartConfigs(): List<JsonChartConfig> {
        val configs = mutableListOf<JsonChartConfig>()
        try {
            val files = context.assets.list("providers/charts") ?: return emptyList()
            for (file in files) {
                if (file.endsWith(".json")) {
                    val config = loadChartConfig(file.removeSuffix(".json"))
                    if (config != null) configs.add(config)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chart configs", e)
        }
        return configs
    }

    fun loadChartConfig(providerId: String): JsonChartConfig? = try {
        val raw = context.assets.open("providers/charts/$providerId.json")
            .bufferedReader().use { it.readText() }
        json.decodeFromString<JsonChartConfig>(raw)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load chart config: $providerId", e)
        null
    }

    fun loadMarineFeatureConfig(providerId: String): JsonMarineFeatureConfig? = try {
        val raw = context.assets.open("providers/marine_features/$providerId.json")
            .bufferedReader().use { it.readText() }
        json.decodeFromString<JsonMarineFeatureConfig>(raw)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load marine feature config: $providerId", e)
        null
    }

    fun loadAllConfigs(): List<Any> {
        val chartConfigs: List<Any> = loadChartConfigs()
        val marineConfig = loadMarineFeatureConfig("fi-vayla-traficom")
        return if (marineConfig != null) chartConfigs + marineConfig else chartConfigs
    }

    companion object {
        private const val TAG = "JsonProviderLoader"
    }
}

@Serializable
data class JsonBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
) {
    fun toBoundingBox() = BoundingBox(minLat, maxLat, minLng, maxLng)
    fun isValid() = minLat < maxLat && minLng < maxLng
}

@Serializable
data class JsonChartLayer(
    val id: String,
    val layerId: String,
    val name: String,
    val tileUrl: String,
    val minZoom: Float,
    val maxZoom: Float = Float.MAX_VALUE,
    val subdir: String
)

@Serializable
data class JsonChartConfig(
    val providerId: String,
    val displayName: String,
    val attribution: String,
    val coverage: JsonBoundingBox,
    val priority: Int = 0,
    val layers: List<JsonChartLayer>,
    val supportsOfflineCache: Boolean = true,
    val needsTileServer: Boolean = false
)

@Serializable
data class JsonFeatureSource(
    val name: String,
    val displayName: String,
    val featureType: String,
    val urlTemplate: String,
    val extraParams: Map<String, String> = emptyMap(),
    val bboxSrs4326: Boolean = true,
    val maxFeatures: Int = 1000
) {
    fun toFeatureSource() = FeatureSource(
        name = name,
        displayName = displayName,
        featureType = featureType,
        urlTemplate = urlTemplate,
        extraParams = extraParams,
        bboxSrs4326 = bboxSrs4326,
        maxFeatures = maxFeatures
    )
}

@Serializable
data class JsonMarineFeatureConfig(
    val providerId: String,
    val displayName: String,
    val coverage: JsonBoundingBox,
    val priority: Int = 0,
    val sources: List<JsonFeatureSource>
)
