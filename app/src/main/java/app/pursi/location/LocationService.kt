package app.pursi.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import app.pursi.MainActivity
import app.pursi.R
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service() {

    @Inject lateinit var locationStateHolder: LocationStateHolder

    private var locationManager: LocationManager? = null

    private val androidGpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            locationStateHolder.updateLocation(location)
            adaptInterval(location)
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Provider disabled: $provider")
        }
    }

    private val androidNetworkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            locationStateHolder.updateLocation(location)
            adaptInterval(location)
        }

        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Provider disabled: $provider")
        }
    }

    private var intervalMs: Long = HIGH_ACCURACY_INTERVAL_MS
    private var isLowPowerMode = false
    private var lowPowerStartTime = 0L
    private var lastIntervalChangeMs = 0L

    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopLocationUpdates()
        locationStateHolder.clear()
        super.onDestroy()
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        stopLocationUpdates()
        startAndroidLocationUpdates()
    }

    private fun startAndroidLocationUpdates() {
        val mgr = locationManager ?: return
        if (!mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "GPS provider not enabled, trying network provider")
        }
        try {
            mgr.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalMs,
                MIN_DISTANCE_METERS,
                androidGpsListener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Android location permission denied", e)
            stopSelf()
            return
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "GPS provider not available", e)
        }

        try {
            mgr.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                intervalMs,
                MIN_DISTANCE_METERS,
                androidNetworkListener,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun stopLocationUpdates() {
        val mgr = locationManager ?: return
        mgr.removeUpdates(androidGpsListener)
        mgr.removeUpdates(androidNetworkListener)
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Pursi location tracking"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_location_title))
            .setContentText(getString(R.string.notification_location_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun updateLocationRequest(newIntervalMs: Long) {
        intervalMs = newIntervalMs
        stopLocationUpdates()
        startLocationUpdates()
    }

    private fun adaptInterval(location: Location) {
        val now = System.currentTimeMillis()
        if (now - lastIntervalChangeMs < 5000L) return

        val screenOn = powerManager.isInteractive

        // Näyttö pois: harva 30 s päivitys, ei reagoi nopeuteen
        if (!screenOn) {
            if (intervalMs != BACKGROUND_INTERVAL_MS) {
                Log.d(TAG, "Screen off: switching to ${BACKGROUND_INTERVAL_MS}ms")
                lastIntervalChangeMs = now
                scheduleIntervalChange(BACKGROUND_INTERVAL_MS)
            }
            return
        }

        val speed = if (location.hasSpeed()) location.speed else 0f

        if (isLowPowerMode) {
            if (speed >= SPEED_THRESHOLD_HIGH) {
                isLowPowerMode = false
                lowPowerStartTime = 0L
                lastIntervalChangeMs = now
                scheduleIntervalChange(HIGH_ACCURACY_INTERVAL_MS)
            }
        } else {
            if (speed < SPEED_THRESHOLD_LOW) {
                if (lowPowerStartTime == 0L) {
                    lowPowerStartTime = now
                } else if (now - lowPowerStartTime >= LOW_POWER_DELAY_MS) {
                    isLowPowerMode = true
                    lowPowerStartTime = 0L
                    lastIntervalChangeMs = now
                    scheduleIntervalChange(LOW_POWER_INTERVAL_MS)
                }
            } else {
                lowPowerStartTime = 0L
            }
        }
    }

    private fun scheduleIntervalChange(intervalMs: Long) {
        Handler(Looper.getMainLooper()).post {
            updateLocationRequest(intervalMs)
        }
    }

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "pursi_location"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_DISTANCE_METERS = 5f
        private const val HIGH_ACCURACY_INTERVAL_MS = 1000L
        private const val LOW_POWER_INTERVAL_MS = 3000L
        private const val SPEED_THRESHOLD_LOW = 0.3f
        private const val SPEED_THRESHOLD_HIGH = 0.8f
        private const val LOW_POWER_DELAY_MS = 15000L
        private const val BACKGROUND_INTERVAL_MS = 30_000L
    }
}
