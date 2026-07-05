package app.pursi.data.wfs

import android.util.Log
import app.pursi.data.AppDatabase
import app.pursi.data.model.WfsFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Deprecated("Use FeatureSource from datasource.core instead")
data class WfsSource(
    val name: String,
    val displayName: String,
    val baseUrl: String,
    val typeName: String,
    val featureType: String,
    val extraParams: Map<String, String> = emptyMap(),
    val bboxSrs4326: Boolean = true,
    val maxFeatures: Int = 1000
)

@Deprecated("Use FinnishMarineFeatureProvider.sources from datasource.fi instead")
val WFS_SOURCES = listOf(
    WfsSource(
        name = "vayla_lights",
        displayName = "Lights & Beacons",
        baseUrl = "https://avoinapi.vaylapilvi.fi/vaylatiedot/ows",
        typeName = "vesivaylatiedot:loistot_uusi",
        featureType = "light"
    ),
    WfsSource(
        name = "vayla_navlines",
        displayName = "Navigation Lines",
        baseUrl = "https://avoinapi.vaylapilvi.fi/vaylatiedot/ows",
        typeName = "vesivaylatiedot:navigointilinjat_uusi",
        featureType = "navigation_line"
    ),
    WfsSource(
        name = "vayla_daymarks",
        displayName = "Daymarks",
        baseUrl = "https://avoinapi.vaylapilvi.fi/vaylatiedot/ows",
        typeName = "vesivaylatiedot:paivatunnukset_uusi",
        featureType = "daymark"
    ),
    WfsSource(
        name = "vayla_restrictions",
        displayName = "Restriction Areas",
        baseUrl = "https://avoinapi.vaylapilvi.fi/vaylatiedot/ows",
        typeName = "vesivaylatiedot:rajoitusalue_a_uusi",
        featureType = "restricted_area"
    ),
    WfsSource(
        name = "vayla_fairways_1",
        displayName = "Main Fairways (VL1)",
        baseUrl = "https://avoinapi.vaylapilvi.fi/vaylatiedot/ows",
        typeName = "vesivaylatiedot:vesivaylat_vl1",
        featureType = "fairway"
    ),
    WfsSource(
        name = "vayla_fairways_2",
        displayName = "Secondary Fairways (VL2)",
        baseUrl = "https://avoinapi.vaylapilvi.fi/vaylatiedot/ows",
        typeName = "vesivaylatiedot:vesivaylat_vl2",
        featureType = "fairway"
    ),
    WfsSource(
        name = "traficom_soundings",
        displayName = "Depth soundings",
        baseUrl = "https://julkinen.traficom.fi/inspirepalvelu/rajoitettu/wfs",
        typeName = "rajoitettu:Sounding_P",
        featureType = "depth_sounding",
        extraParams = mapOf("srsName" to "EPSG:4326"),
        bboxSrs4326 = false,
        maxFeatures = 5000
    ),
    WfsSource(
        name = "traficom_contours",
        displayName = "Depth contours",
        baseUrl = "https://julkinen.traficom.fi/inspirepalvelu/rajoitettu/wfs",
        typeName = "rajoitettu:DepthContour_L",
        featureType = "depth_contour",
        extraParams = mapOf("srsName" to "EPSG:4326"),
        bboxSrs4326 = false,
        maxFeatures = 5000
    )
)

class WfsRepository @javax.inject.Inject constructor(
    private val database: AppDatabase,
    private val client: WfsClient
) {

    private val dao = database.wfsFeatureDao()
    private val tag = "Pursi.WFSRepo"

    suspend fun getFeatures(
        source: WfsSource,
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double,
        forceRefresh: Boolean = false
    ): List<WfsFeature> = withContext(Dispatchers.IO) {
        val cached = if (!forceRefresh) {
            dao.getFeatures(source.featureType, minLat, minLng, maxLat, maxLng)
        } else {
            emptyList()
        }

        if (cached.isNotEmpty()) {
            Log.d(tag, "Cache HIT: ${cached.size} features for ${source.name}")
            return@withContext cached
        }
        Log.d(tag, "Cache MISS for ${source.name}, fetching from WFS")

        val raw = client.query(
            baseUrl = source.baseUrl,
            typeName = source.typeName,
            minLat = minLat, minLng = minLng,
            maxLat = maxLat, maxLng = maxLng,
            maxFeatures = source.maxFeatures,
            extraParams = source.extraParams,
            bboxSrs4326 = source.bboxSrs4326
        )

        val entities = raw.features.map { data ->
            WfsFeature(
                source = source.name,
                featureType = source.featureType,
                geometry = data.geometry,
                properties = data.properties.entries.joinToString("\n") { (k, v) -> "$k=$v" },
                latitude = data.latitude,
                longitude = data.longitude,
                minLat = data.minLat,
                minLng = data.minLng,
                maxLat = data.maxLat,
                maxLng = data.maxLng
            )
        }

        if (entities.isNotEmpty()) {
            dao.clearOutside(source.name, source.featureType, minLat, minLng, maxLat, maxLng)
            dao.insertAll(entities)
        }

        entities
    }

    suspend fun getCachedFeatures(
        source: WfsSource,
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double
    ): List<WfsFeature> {
        return dao.getFeatures(source.featureType, minLat, minLng, maxLat, maxLng)
    }

    suspend fun clearSource(source: WfsSource) {
        dao.clear(source.name, source.featureType)
    }

    fun getProperty(props: String, key: String): String? {
        return props.lines().firstOrNull { it.startsWith("$key=") }?.substringAfter("$key=")
    }
}
