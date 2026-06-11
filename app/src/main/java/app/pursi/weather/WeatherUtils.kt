package app.pursi.weather

fun warningColorHex(color: String): Long = when (color) {
    "yellow" -> 0xFFFFB300L; "orange" -> 0xFFFF6F00L; "red" -> 0xFFD50000L; else -> 0xFF9E9E9EL
}

fun weatherEmoji(code: Int?): String = when {
    code == null -> ""; code == 0 -> "☀️"; code in 1..3 -> "🌤️"; code in 4..5 -> "🌫️"
    code in 10..19 -> "🌁"; code in 20..29 -> "🌧️"; code in 30..39 -> "⛈️"
    code in 40..49 -> "🌫️"; code in 50..59 -> "🌦️"; code in 60..69 -> "🌧️"
    code in 70..79 -> "🌨️"; code in 80..89 -> "🌦️"; code in 90..99 -> "⛈️"; else -> "🌈"
}

fun forecastEmoji(fp: ForecastPoint): String {
    if (fp.windSpeedMs != null && fp.windSpeedMs > 10f) return "💨"
    val w = fp.weatherSymbol
    if (w != null) {
        val sym = w.toInt()
        val isNight = sym > 100
        val stripped = if (isNight) sym - 100 else sym
        return when {
            stripped == 1 -> if (isNight) "🌙" else "☀️"
            stripped == 2 -> if (isNight) "🌙" else "🌤️"
            stripped in 3..4 -> "⛅"
            stripped in 6..7 -> "☁️"
            stripped == 9 -> "🌫️"
            stripped in 11..17 -> "🌦️"
            stripped in 21..27 -> "🌦️"
            stripped in 31..39 -> "🌧️"
            stripped in 41..49 -> "🌧️"
            stripped in 51..59 -> "🌨️"
            stripped in 61..67 -> "⛈️"
            stripped in 71..77 -> "⛈️"
            else -> if (fp.precipitationMm != null && fp.precipitationMm > 0f) "🌧️" else if (isNight) "🌙" else "☀️"
        }
    }
    if (fp.precipitationMm != null && fp.precipitationMm > 0) return "🌧️"
    val c = fp.cloudiness ?: return "☀️"
    val isNight = isNightTime(fp.timestamp)
    if (isNight) {
        return when { c < 30 -> "🌙"; c < 70 -> "☁️"; else -> "☁️" }
    }
    return when { c < 20 -> "☀️"; c < 50 -> "🌤️"; c < 80 -> "⛅"; else -> "☁️" }
}

fun sunriseSunset(lat: Double, lon: Double, unixSeconds: Long): Pair<Long, Long> {
    val rad = Math.PI / 180.0
    val latRad = lat * rad
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = unixSeconds * 1000L }
    val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (10.0 - 12.0) / 24.0)
    val eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma) - 0.014615 * Math.cos(2 * gamma) - 0.04089 * Math.sin(2 * gamma))
    val decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma) - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma) - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma)
    val denomSun = Math.cos(latRad) * Math.cos(decl)
    if (kotlin.math.abs(denomSun) < 1e-10) return Pair(0L, 0L)
    val cosH = (-0.833 * rad - Math.sin(latRad) * Math.sin(decl)) / denomSun
    if (cosH < -1 || cosH > 1) return Pair(0L, 0L)
    val ha = Math.acos(cosH) / rad
    val noon = 43200 - (lon * 240).toLong() - (eqTime * 60).toLong()
    val base = (unixSeconds / 86400) * 86400
    return Pair(base + noon - (ha * 240).toLong(), base + noon + (ha * 240).toLong())
}

fun moonPhase(unixSeconds: Long): Float {
    val d = unixSeconds / 86400.0 - 51544.5
    val sunLon = 280.466 + 0.985647 * d
    val moonLon = 218.316 + 13.176396 * d
    var diff = ((moonLon - sunLon) % 360.0).toFloat()
    if (diff < 0) diff += 360f
    return diff / 360f
}

fun moonPhaseEmoji(phase: Float): String = when {
    phase < 0.06f || phase > 0.94f -> "🌑"
    phase < 0.19f -> "🌒"
    phase < 0.31f -> "🌓"
    phase < 0.44f -> "🌔"
    phase < 0.56f -> "🌕"
    phase < 0.69f -> "🌖"
    phase < 0.81f -> "🌗"
    else -> "🌘"
}

fun moonriseMoonset(lat: Double, lon: Double, unixSeconds: Long): Pair<Long, Long> {
    val rad = Math.PI / 180.0
    val d = unixSeconds / 86400.0 - 51544.5
    val latRad = lat * rad

    val L = 218.316 + 13.176396 * d
    val M = 134.963 + 13.064993 * d
    val F = 93.272 + 13.229350 * d
    val D = 297.850 + 12.190749 * d
    val Om = 125.044 - 0.052954 * d

    val eLon = (L + 6.289 * Math.sin(M * rad) + 1.274 * Math.sin((2 * D - M) * rad)
        + 0.658 * Math.sin(2 * D * rad) + 0.214 * Math.sin(2 * M * rad)
        - 0.114 * Math.sin(2 * F * rad)) * rad

    val eLat = (5.128 * Math.sin(F * rad) + 0.280 * Math.sin((M + F) * rad)
        + 0.277 * Math.sin((M - F) * rad) + 0.173 * Math.sin((2 * D - F) * rad)) * rad

    val eps = (23.4393 - 0.00000036 * d) * rad

    val sinLat = Math.sin(eLat)
    val cosLat = Math.cos(eLat)
    val sinLon = Math.sin(eLon)
    val cosLon = Math.cos(eLon)

    val dec = Math.asin(sinLat * Math.cos(eps) + cosLat * Math.sin(eps) * sinLon)
    val ra = Math.atan2(cosLat * Math.sin(eps) * sinLon - sinLat * Math.cos(eps), cosLon * cosLat)

    val altitude = (-0.5667 - 0.25 + 0.95) * rad
    val denomMoon = Math.cos(latRad) * Math.cos(dec)
    if (kotlin.math.abs(denomMoon) < 1e-10) return Pair(0L, 0L)
    val cosHA = (Math.sin(altitude) - Math.sin(latRad) * Math.sin(dec)) / denomMoon
    if (cosHA < -1 || cosHA > 1) return Pair(0L, 0L)

    val ha = Math.acos(cosHA) * 180.0 / Math.PI
    val lst0 = 100.46 + 0.985647 * d + lon
    val raDeg = ra * 180.0 / Math.PI

    var transit = (raDeg - lst0) / 360.0 * 86400.0
    if (transit < 0) transit += 86400
    if (transit >= 86400) transit -= 86400

    val rise = transit - ha * 240.0
    val set = transit + ha * 240.0

    val base = (unixSeconds / 86400) * 86400
    return Pair((base + rise).toLong(), (base + set).toLong())
}

fun isNightTime(unixSeconds: Long): Boolean {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = unixSeconds * 1000L }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    return hour < 6 || hour >= 22
}

fun estimateUV(lat: Double, lon: Double, unixSeconds: Long, cloudiness: Float? = null): Float {
    val rad = Math.PI / 180.0
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = unixSeconds * 1000L }
    val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val hourOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60.0
    val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1)
    val decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma) - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma) - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma)
    val lonCorr = (hourOfDay - 12.0) * 15.0 * rad
    val latRad = lat * rad
    val sinElev = Math.sin(latRad) * Math.sin(decl) + Math.cos(latRad) * Math.cos(decl) * Math.cos(lonCorr)
    if (sinElev <= 0) return 0f
    val elev = Math.asin(sinElev)
    val baseUV = (Math.sin(elev) * 11.0).toFloat().coerceIn(0f, 11f)
    val cloudFactor = if (cloudiness != null) (1.0 - cloudiness / 100.0 * 0.6).toFloat() else 1f
    return (baseUV * cloudFactor).coerceAtLeast(0f)
}

fun degreesToCompass(deg: Float): String {
    if (deg.isNaN()) return ""
    val dirs = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return dirs[(((deg + 11.25f) / 22.5f).toInt() % 16 + 16) % 16]
}

fun windArrow(deg: Float): String = when {
    deg < 22.5f || deg >= 337.5f -> "↑"; deg < 67.5f -> "↗"; deg < 112.5f -> "→"
    deg < 157.5f -> "↘"; deg < 202.5f -> "↓"; deg < 247.5f -> "↙"
    deg < 292.5f -> "←"; else -> "↖"
}
