package app.pursi.datasource.fi

import android.util.Log
import app.pursi.data.AppDatabase
import app.pursi.data.model.WfsFeature
import app.pursi.data.wfs.WfsClient
import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.FeatureSource
import app.pursi.datasource.core.JsonProviderLoader
import app.pursi.datasource.core.MarineFeatureProvider
import app.pursi.datasource.core.WfsQueryResult
import javax.inject.Inject

class FinnishMarineFeatureProvider @Inject constructor(
    private val database: AppDatabase,
    private val client: WfsClient,
    loader: JsonProviderLoader
) : MarineFeatureProvider {
    private val config = loader.loadMarineFeatureConfig("fi-vayla-traficom")!!

    override val providerId = config.providerId
    override val displayName = config.displayName
    override val coverage = config.coverage.toBoundingBox()
    override val sources = config.sources.map { it.toFeatureSource() }
    override val priority = config.priority

    private val dao = database.wfsFeatureDao()
    private val tag = "Pursi.FinnishMarine"

    override suspend fun getFeatures(
        source: FeatureSource,
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double
    ): WfsQueryResult {
        val cached = dao.getFeatures(source.featureType, minLat, minLng, maxLat, maxLng)
        if (cached.isNotEmpty()) {
            Log.d(tag, "Cache HIT: ${cached.size} features for ${source.name} ($source.featureType) bbox=[$minLat,$minLng]-[$maxLat,$maxLng]")
            return WfsQueryResult(cached, false, fromCache = true)
        }
        Log.d(tag, "Cache MISS for ${source.name} ($source.featureType) bbox=[$minLat,$minLng]-[$maxLat,$maxLng], fetching from WFS")

        val typeName = source.extraParams["typenames"] ?: return WfsQueryResult(emptyList(), false, fromCache = true)
        val result = client.query(
            baseUrl = source.urlTemplate,
            typeName = typeName,
            minLat = minLat, minLng = minLng,
            maxLat = maxLat, maxLng = maxLng,
            maxFeatures = source.maxFeatures,
            extraParams = source.extraParams,
            bboxSrs4326 = source.bboxSrs4326
        )

        val entities = result.features.map { data ->
            WfsFeature(
                source = source.name,
                featureType = source.featureType,
                geometry = data.geometry,
                properties = data.properties.entries.joinToString("\n") { (k, v) -> "$k=$v" },
                latitude = data.latitude,
                longitude = data.longitude,
                minLat = data.minLat, minLng = data.minLng,
                maxLat = data.maxLat, maxLng = data.maxLng
            )
        }

        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
            if (entities.size > 1000) {
                dao.clearOlderThan(source.name, source.featureType, System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
            }
        }
        return WfsQueryResult(entities, false, fromCache = false)
    }
}
