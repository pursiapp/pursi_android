package fi.pursi.water

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterObservationClient @Inject constructor(
    private val client: OkHttpClient
) {
    private val queryApi = "https://www.jarviwiki.fi/w/api.php"

    suspend fun fetchAlgae(daysBack: Int = 14): List<WaterObservation> =
        fetch("alg", "|?Koordinaatit|?Päivämäärä|?Seuranta|?Ylläpito", daysBack) { null }

    suspend fun fetchTemperature(daysBack: Int = 14): List<WaterObservation> =
        fetch("temp", "|?Koordinaatit|?Päivämäärä|?Pintaveden_lämpötila|?Seuranta|?Ylläpito", daysBack) { printouts ->
            val tempArr = printouts.optJSONArray("Pintaveden lämpötila")
            val result = if (tempArr != null && tempArr.length() > 0) {
                val obj = tempArr.optJSONObject(0)
                if (obj != null) {
                    val num = obj.optDouble("value", Double.NaN)
                    if (!num.isNaN()) num
                    else obj.optString("value")?.replace(",",".")?.toDoubleOrNull() ?: Double.NaN
                } else Double.NaN
            } else Double.NaN
            if (result.isNaN()) {
                android.util.Log.d("WaterObsClient", "temp NaN: arr=${tempArr != null} len=${tempArr?.length()} obj=${if (tempArr != null && tempArr.length() > 0) tempArr.opt(0) else "null"}")
            }
            result
        }

    private suspend fun fetch(
        obsCode: String,
        printoutFields: String,
        daysBack: Int,
        extraValue: (JSONObject) -> Double?
    ): List<WaterObservation> = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysBack)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

        val query = ("[[Havainto::+]][[ObsCode::$obsCode]][[Päivämäärä::>$dateStr]]" +
            printoutFields +
            "|sort=Päivämäärä|order=descending|limit=200")

        val url = "$queryApi?action=ask" +
            "&query=" + java.net.URLEncoder.encode(query, "UTF-8") +
            "&format=json&origin=*"

        val request = Request.Builder().url(url).get()
            .header("User-Agent", "Pursi/1.0").build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()

        parseObservations(body, obsCode, extraValue)
    }

    private fun parseObservations(
        json: String,
        obsCode: String,
        extraValue: (JSONObject) -> Double?
    ): List<WaterObservation> {
        val results = mutableListOf<WaterObservation>()
        try {
            val root = JSONObject(json)
            if (root.has("error")) return results
            val res = root.optJSONObject("query")?.optJSONObject("results") ?: return results
            val keys = res.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("S Arkisto:") || key.startsWith("S_Arkisto:")) continue
                val printouts = res.optJSONObject(key)?.optJSONObject("printouts") ?: continue

                val lakeName = key.substringBefore(" (").substringBefore("/")
                val siteName = key.substringAfter("/", "").substringBefore("# ").trim()
                    .replaceFirst("^\\d+\\.?\\s*".toRegex(), "")

                val coords = printouts.optJSONArray("Koordinaatit")
                if (coords == null || coords.length() == 0) continue
                val coordObj = coords.optJSONObject(0) ?: continue
                val lat = coordObj.optDouble("lat", Double.NaN)
                val lon = coordObj.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val dates = printouts.optJSONArray("Päivämäärä")
                val timestamp = if (dates != null && dates.length() > 0) {
                    dates.optJSONObject(0)?.optLong("timestamp", 0L) ?: 0L
                } else 0L

                val seurantaArr = printouts.optJSONArray("Seuranta")
                val source = if (seurantaArr != null && seurantaArr.length() > 0) {
                    seurantaArr.optString(0, "")
                } else ""

                val yllapitoArr = printouts.optJSONArray("Ylläpito")
                val yllapito = if (yllapitoArr != null && yllapitoArr.length() > 0) {
                    yllapitoArr.optString(0, "")
                } else ""

                val extra = extraValue(printouts)

                if (obsCode == "temp" && extra != null && !extra.isNaN()) {
                    android.util.Log.d("WaterObsClient", "temp=${extra} at $siteName")
                }

                results.add(
                    when (obsCode) {
                        "alg" -> WaterObservation(
                            type = WaterObservationType.ALGAE,
                            latitude = lat, longitude = lon,
                            timestamp = timestamp * 1000L,
                            source = source, yllapito = yllapito,
                            siteName = siteName, lakeName = lakeName
                        )
                        "temp" -> WaterObservation(
                            type = WaterObservationType.TEMPERATURE,
                            latitude = lat, longitude = lon,
                            temperatureC = extra ?: Double.NaN,
                            timestamp = timestamp * 1000L,
                            source = source, yllapito = yllapito,
                            siteName = siteName, lakeName = lakeName
                        )
                        else -> continue
                    }
                )
            }
            android.util.Log.d("WaterObsClient", "Parsed ${results.size} $obsCode observations")
        } catch (e: Exception) {
            android.util.Log.e("WaterObsClient", "Parse error ($obsCode): ${e.message}", e)
        }
        return results
    }
}
