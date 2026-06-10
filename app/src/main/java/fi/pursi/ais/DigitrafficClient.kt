package fi.pursi.ais

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCATIONS_URL =
    "https://meri.digitraffic.fi/api/ais/v1/locations"
private const val TAG = "DigitrafficClient"

private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Singleton
class DigitrafficClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /** Returns (vessels, rateLimitMessage) */
    suspend fun getVesselsNearby(
        lat: Double,
        lon: Double,
        radiusKm: Int = 50
    ): Pair<List<AisVessel>, String?> {
        return withContext(Dispatchers.IO) {
            val url = "$LOCATIONS_URL?latitude=$lat&longitude=$lon&radius=$radiusKm"
            Log.d(TAG, "GET $url")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                Log.w(TAG, "API $url → $err")
                if (response.code == 429) {
                    return@withContext Pair(emptyList(), err)
                }
                return@withContext Pair(emptyList(), null)
            }
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.w(TAG, "API $url → empty body")
                return@withContext Pair(emptyList(), null)
            }
            val parsed = JSON.decodeFromString<AisLocationsResponse>(body)
            Log.d(TAG, "API returned ${parsed.features.size} features")
            val vessels = parsed.features.mapNotNull { feature ->
                val coords = feature.geometry.coordinates
                if (coords.size < 2) return@mapNotNull null
                val props = feature.properties
                AisVessel(
                    mmsi = feature.mmsi,
                    lon = coords[0],
                    lat = coords[1],
                    sog = if (props.sog >= 0f && props.sog < 102.3f) props.sog else 0f,
                    cog = if (props.cog >= 0f && props.cog < 360f) props.cog else 0f,
                    navStat = props.navStat,
                    heading = if (props.heading in 0..359) props.heading else -1,
                    rot = props.rot,
                    posAcc = props.posAcc,
                    raim = props.raim,
                    timestampExternal = props.timestampExternal
                )
            }
            Pair(vessels, null)
        }
    }

    suspend fun fetchVesselMetadata(mmsi: Int): VesselMetadata? {
        return try {
            withContext(Dispatchers.IO) {
                val url = "https://meri.digitraffic.fi/api/ais/v1/vessels/$mmsi"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val raw = JSON.decodeFromString<AisVesselMetadataResponse>(body)
                VesselMetadata(
                    mmsi = raw.mmsi,
                    name = raw.name,
                    shipType = raw.shipType,
                    destination = raw.destination,
                    draught = raw.draught?.let { it / 10f },
                    callSign = raw.callSign,
                    imo = raw.imo
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
