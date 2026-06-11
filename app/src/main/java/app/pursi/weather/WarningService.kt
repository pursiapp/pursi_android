package app.pursi.weather

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import app.pursi.MainActivity
import app.pursi.R
import app.pursi.datasource.core.CompositeWeatherProvider
import app.pursi.datasource.core.SourceResolver
import app.pursi.datasource.core.WeatherProvider
import app.pursi.location.LocationStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WarningService : Service() {

    @Inject lateinit var sourceResolver: SourceResolver
    @Inject lateinit var locationStateHolder: LocationStateHolder

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private lateinit var warningManager: WarningManager

    override fun onCreate() {
        super.onCreate()
        warningManager = WarningManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startPolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                try {
                    val location = locationStateHolder.currentLocation.firstOrNull()
                    if (location != null) {
                        val allProviders = sourceResolver.allWeatherProvidersFor(
                            location.latitude, location.longitude
                        )
                        val primaryProvider = allProviders.firstOrNull()
                        val provider = if (primaryProvider != null && allProviders.size > 1) {
                            CompositeWeatherProvider(primaryProvider, allProviders.drop(1))
                        } else {
                            primaryProvider
                        }
                        val strikes = provider?.getLightningData(
                            location.latitude - 1.0, location.longitude - 1.0,
                            location.latitude + 1.0, location.longitude + 1.0
                        ) ?: emptyList()
                        if (strikes.isNotEmpty()) {
                            warningManager.processStrikes(strikes)
                        }
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w(TAG, "Lightning poll failed", e) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_warning_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_warning_channel_desc)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_warning_title))
            .setContentText(getString(R.string.notification_warning_text, 0))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        private const val TAG = "WarningService"
        private const val CHANNEL_ID = "pursi_warnings"
        private const val NOTIFICATION_ID = 1003
        private const val POLL_INTERVAL_MS = 5 * 60 * 1000L
    }
}
