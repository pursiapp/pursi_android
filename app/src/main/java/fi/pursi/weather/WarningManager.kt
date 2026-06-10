package fi.pursi.weather

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import fi.pursi.MainActivity
import fi.pursi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WarningManager(private val context: Context) {

    private val _activeWarnings = MutableStateFlow<List<LightningStrike>>(emptyList())
    val activeWarnings: StateFlow<List<LightningStrike>> = _activeWarnings.asStateFlow()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun processStrikes(strikes: List<LightningStrike>) {
        val previous = _activeWarnings.value
        _activeWarnings.value = strikes

        if (strikes.isNotEmpty() && previous.isEmpty()) {
            showLightningNotification(strikes.size)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_warning_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_warning_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showLightningNotification(count: Int) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_warning_title))
            .setContentText(context.getString(R.string.notification_warning_text, count))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(1002, notification)
    }

    fun clear() {
        _activeWarnings.value = emptyList()
    }

    companion object {
        private const val CHANNEL_ID = "pursi_lightning"
    }
}
