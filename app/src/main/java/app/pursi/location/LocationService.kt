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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import app.pursi.MainActivity
import app.pursi.R
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service() {

    @Inject lateinit var locationStateHolder: LocationStateHolder

    private var useGoogleLocation: Boolean = false

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private val googleCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                locationStateHolder.updateLocation(location)
                adaptInterval(location)
            }
        }
    }

    private var locationRequest: LocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 1000L
    )
        .setMinUpdateIntervalMillis(500L)
        .setMaxUpdateDelayMillis(2000L)
        .build()

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

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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
        useGoogleLocation = getSharedPreferences("pursi_map", Context.MODE_PRIVATE)
            .getBoolean("use_google_location", false)
        if (useGoogleLocation) {
            startGoogleLocationUpdates()
        } else {
            startAndroidLocationUpdates()
        }
    }

    private fun startGoogleLocationUpdates() {
        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                googleCallback,
                Looper.getMainLooper()
            ) ?: run {
                Log.w(TAG, "Google Play Services not available, falling back to LocationManager")
                useGoogleLocation = false
                getSharedPreferences("pursi_map", Context.MODE_PRIVATE).edit()
                    .putBoolean("use_google_location", false).apply()
                startAndroidLocationUpdates()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Google location permission denied", e)
            stopSelf()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Google Play Services not available", e)
            useGoogleLocation = false
            getSharedPreferences("pursi_map", Context.MODE_PRIVATE).edit()
                .putBoolean("use_google_location", false).apply()
            startAndroidLocationUpdates()
        }
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
        fusedLocationClient?.removeLocationUpdates(googleCallback)
        val mgr = locationManager
        if (mgr != null) {
            mgr.removeUpdates(androidGpsListener)
            mgr.removeUpdates(androidNetworkListener)
        }
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

    fun updateLocationRequest(
        newIntervalMs: Long,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY
    ) {
        intervalMs = newIntervalMs
        locationRequest = LocationRequest.Builder(priority, newIntervalMs)
            .setMinUpdateIntervalMillis(newIntervalMs / 2)
            .setMaxUpdateDelayMillis(newIntervalMs * 2)
            .build()
        stopLocationUpdates()
        startLocationUpdates()
    }

    private fun adaptInterval(location: Location) {
        val speed = if (location.hasSpeed()) location.speed else 0f
        val now = System.currentTimeMillis()

        if (now - lastIntervalChangeMs < 5000L) return

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
                    scheduleIntervalChange(
                        LOW_POWER_INTERVAL_MS,
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    )
                }
            } else {
                lowPowerStartTime = 0L
            }
        }
    }

    private fun scheduleIntervalChange(intervalMs: Long, priority: Int = Priority.PRIORITY_HIGH_ACCURACY) {
        Handler(Looper.getMainLooper()).post {
            updateLocationRequest(intervalMs, priority)
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
    }
}
