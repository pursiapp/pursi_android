package fi.pursi.datasource.fi

import android.util.Log
import fi.pursi.data.AppDatabase
import fi.pursi.data.model.WfsFeature
import fi.pursi.data.wfs.WfsClient
import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.core.FeatureSource
import fi.pursi.datasource.core.JsonProviderLoader
import fi.pursi.datasource.core.MarineFeatureProvider
import fi.pursi.datasource.core.WfsQueryResult
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
