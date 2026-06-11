package app.pursi.analytics

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val prefs = appContext.getSharedPreferences("pursi_analytics", Context.MODE_PRIVATE)

    private val versionName: String by lazy {
        try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    val sessionId: String
        get() {
            val now = System.currentTimeMillis()
            val existingId = prefs.getString(KEY_SESSION_ID, null)
            val existingStart = prefs.getLong(KEY_SESSION_START, 0L)
            if (existingId != null && (now - existingStart) < SESSION_DURATION_MS) {
                return existingId
            }
            val newId = UUID.randomUUID().toString()
            prefs.edit()
                .putString(KEY_SESSION_ID, newId)
                .putLong(KEY_SESSION_START, now)
                .apply()
            return newId
        }

    fun sessionData(): Map<String, String> = mapOf(
        "ver" to versionName,
        "android" to Build.VERSION.SDK_INT.toString(),
        "device" to Build.MODEL,
        "lang" to Locale.getDefault().language,
    )

    companion object {
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_START = "session_start"
        private const val SESSION_DURATION_MS = 24L * 60 * 60 * 1000
    }
}
