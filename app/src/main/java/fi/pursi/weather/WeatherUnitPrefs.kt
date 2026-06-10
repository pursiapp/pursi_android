package fi.pursi.weather

import android.content.SharedPreferences
import fi.pursi.location.SpeedUnit
import java.util.Locale

object WeatherUnitPrefs {
    private const val KEY_WIND_UNIT = "weather_wind_unit"
    private const val KEY_TEMP_UNIT = "weather_temp_unit"
    private const val KEY_PRESSURE_UNIT = "weather_pressure_unit"
    private const val KEY_SPEED_UNIT = "boat_speed_unit"
    private const val KEY_WIND_METER_SIZE = "wind_meter_size"

    enum class WindUnit(val label: String) {
        MS("m/s"), KNOTS("kn"), KMH("km/h"), MPH("mph"), BEAUFORT("Bft")
    }

    enum class TempUnit(val label: String) {
        CELSIUS("°C"), FAHRENHEIT("°F")
    }

    enum class PressureUnit(val label: String) {
        HPA("hPa"), MMHG("mmHg"), INHG("inHg")
    }

    enum class WindMeterSize(val dp: Int) {
        AUTO(0), SMALL(56), MEDIUM(84), LARGE(112)
    }

    private fun localeDefaultWind(): WindUnit {
        val l = Locale.getDefault()
        return when {
            l.language == "en" && l.country == "US" -> WindUnit.MPH
            l.language == "en" -> WindUnit.KNOTS
            else -> WindUnit.MS
        }
    }

    private fun localeDefaultTemp(): TempUnit {
        val l = Locale.getDefault()
        return if (l.language == "en" && l.country == "US") TempUnit.FAHRENHEIT else TempUnit.CELSIUS
    }

    private fun localeDefaultPressure(): PressureUnit {
        val l = Locale.getDefault()
        return if (l.language == "en" && l.country == "US") PressureUnit.INHG else PressureUnit.HPA
    }

    private fun localeDefaultSpeed(): SpeedUnit {
        val l = Locale.getDefault()
        return when {
            l.language == "en" && l.country == "US" -> SpeedUnit.MPH
            else -> SpeedUnit.KNOTS
        }
    }

    private fun localeDefaultWindMeterSize(): WindMeterSize = WindMeterSize.AUTO

    fun windUnit(prefs: SharedPreferences): WindUnit {
        val stored = prefs.getString(KEY_WIND_UNIT, null) ?: return localeDefaultWind()
        return try { WindUnit.valueOf(stored) } catch (_: Exception) { localeDefaultWind() }
    }

    fun setWindUnit(prefs: SharedPreferences, u: WindUnit) {
        prefs.edit().putString(KEY_WIND_UNIT, u.name).apply()
    }

    fun tempUnit(prefs: SharedPreferences): TempUnit {
        val stored = prefs.getString(KEY_TEMP_UNIT, null) ?: return localeDefaultTemp()
        return try { TempUnit.valueOf(stored) } catch (_: Exception) { localeDefaultTemp() }
    }

    fun setTempUnit(prefs: SharedPreferences, u: TempUnit) {
        prefs.edit().putString(KEY_TEMP_UNIT, u.name).apply()
    }

    fun pressureUnit(prefs: SharedPreferences): PressureUnit {
        val stored = prefs.getString(KEY_PRESSURE_UNIT, null) ?: return localeDefaultPressure()
        return try { PressureUnit.valueOf(stored) } catch (_: Exception) { localeDefaultPressure() }
    }

    fun setPressureUnit(prefs: SharedPreferences, u: PressureUnit) {
        prefs.edit().putString(KEY_PRESSURE_UNIT, u.name).apply()
    }

    fun speedUnit(prefs: SharedPreferences): SpeedUnit {
        val stored = prefs.getString(KEY_SPEED_UNIT, null) ?: return localeDefaultSpeed()
        return try { SpeedUnit.valueOf(stored) } catch (_: Exception) { localeDefaultSpeed() }
    }

    fun setSpeedUnit(prefs: SharedPreferences, u: SpeedUnit) {
        prefs.edit().putString(KEY_SPEED_UNIT, u.name).apply()
    }

    fun windMeterSize(prefs: SharedPreferences): WindMeterSize {
        val stored = prefs.getString(KEY_WIND_METER_SIZE, null) ?: return localeDefaultWindMeterSize()
        return try { WindMeterSize.valueOf(stored) } catch (_: Exception) { localeDefaultWindMeterSize() }
    }

    fun setWindMeterSize(prefs: SharedPreferences, s: WindMeterSize) {
        prefs.edit().putString(KEY_WIND_METER_SIZE, s.name).apply()
    }

    fun msToKnots(ms: Float): Float = ms * 1.94384f
    fun msToKmh(ms: Float): Float = ms * 3.6f
    fun msToMph(ms: Float): Float = ms * 2.23694f

    fun convertWind(ms: Float, unit: WindUnit): Float = when (unit) {
        WindUnit.MS -> ms
        WindUnit.KNOTS -> msToKnots(ms)
        WindUnit.KMH -> msToKmh(ms)
        WindUnit.MPH -> msToMph(ms)
        WindUnit.BEAUFORT -> ms
    }

    fun beaufort(ms: Float): Int = when {
        ms < 0.5f -> 0
        ms < 1.6f -> 1
        ms < 3.4f -> 2
        ms < 5.5f -> 3
        ms < 8.0f -> 4
        ms < 10.8f -> 5
        ms < 13.9f -> 6
        ms < 17.2f -> 7
        ms < 20.8f -> 8
        ms < 24.5f -> 9
        ms < 28.5f -> 10
        ms < 32.7f -> 11
        else -> 12
    }

    fun convertTemp(c: Float, unit: TempUnit): Float = when (unit) {
        TempUnit.CELSIUS -> c
        TempUnit.FAHRENHEIT -> c * 9f / 5f + 32f
    }

    fun convertPressure(hPa: Float, unit: PressureUnit): Float = when (unit) {
        PressureUnit.HPA -> hPa
        PressureUnit.MMHG -> hPa * 0.75006f
        PressureUnit.INHG -> hPa * 0.02953f
    }

    fun formatWind(ms: Float, unit: WindUnit): Pair<String, String> {
        val valStr = if (unit == WindUnit.BEAUFORT) {
            beaufort(ms).toString()
        } else {
            "%.1f".format(convertWind(ms, unit))
        }
        return Pair(valStr, unit.label)
    }

    fun formatTemp(c: Float?, unit: TempUnit): Pair<String, String>? {
        if (c == null) return null
        val converted = convertTemp(c, unit)
        return Pair("%.0f".format(converted), unit.label)
    }

    fun formatPressure(hPa: Float?, unit: PressureUnit): Pair<String, String>? {
        if (hPa == null) return null
        val converted = convertPressure(hPa, unit)
        return Pair("%.0f".format(converted), unit.label)
    }

    fun windMeterDp(size: WindMeterSize, smallestScreenWidthDp: Int): Int {
        if (size != WindMeterSize.AUTO) return size.dp
        return when {
            smallestScreenWidthDp < 500 -> WindMeterSize.MEDIUM.dp
            smallestScreenWidthDp < 720 -> WindMeterSize.LARGE.dp
            else -> WindMeterSize.LARGE.dp
        }
    }
}
