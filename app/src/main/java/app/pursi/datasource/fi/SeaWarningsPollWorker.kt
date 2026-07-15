package app.pursi.datasource.fi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SeaWarningsPollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SeaWarningsPoll"
        private const val WORK_NAME = "seawarnings-poll"
        private const val PREFS_NAME = "seawarnings_worker"
        private const val KEY_KNOWN_IDS = "known_ids"
        private const val KEY_DISTANCE_KM = "distance_km"
        private const val CHANNEL_ID = "pursi_seawarnings"
        private const val DEFAULT_DISTANCE_KM = 50

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val work = PeriodicWorkRequestBuilder<SeaWarningsPollWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
            Log.d(TAG, "Scheduled seawarnings poll every 15 min")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun isEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean("enabled", true)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", enabled).apply()
            if (enabled) schedule(context) else cancel(context)
        }

        fun getDistanceKm(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_DISTANCE_KM, DEFAULT_DISTANCE_KM)
        }

        fun setDistanceKm(context: Context, km: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_DISTANCE_KM, km).apply()
        }
    }

    override suspend fun doWork(): Result {
        if (!isEnabled(applicationContext)) return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val knownIds = prefs.getStringSet(KEY_KNOWN_IDS, mutableSetOf()) ?: mutableSetOf()
        val distanceKm = prefs.getInt(KEY_DISTANCE_KM, DEFAULT_DISTANCE_KM)

        val client = FintrafficSeaWarningsClient(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        )

        return try {
            val warnings = client.fetchAll()
            val now = System.currentTimeMillis()
            val newIds = mutableSetOf<String>()

            for (w in warnings) {
                newIds.add(w.id)

                if (w.id in knownIds) continue
                val start = w.validityStartEpochMs
                val end = w.validityEndEpochMs

                if (start == null || now < start) continue
                if (end != null && now > end) continue

                val distKm = haversine(
                    60.0, 24.0,
                    w.centroidLat, w.centroidLon
                ) / 1000.0
                if (distKm <= distanceKm) {
                    sendNotification(w)
                }
            }

            prefs.edit()
                .putStringSet(KEY_KNOWN_IDS, newIds)
                .apply()

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Poll failed: ${e.message}")
            Result.retry()
        }
    }

    private fun sendNotification(w: SeaWarning) {
        try {
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            ensureChannel(notificationManager)

            val intent = applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
            intent?.putExtra("open_warnings_tab", true)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val content = w.contentFi.take(200)

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("${w.typeFi}: ${w.locationFi}")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            notificationManager.notify(
                (w.id.hashCode() and Int.MAX_VALUE) % 10000 + 1000,
                notification
            )
        } catch (e: Exception) {
            Log.w(TAG, "Notification failed: ${e.message}")
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Merivaroitukset",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Fintrafficin merivaroitukset ja rajoitusalueet"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2.0).pow(2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0).pow(2.0)
        return R * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
    }

    private fun Double.pow(e: Double): Double = Math.pow(this, e)
}
