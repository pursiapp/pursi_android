package app.pursi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import app.pursi.analytics.AnalyticsManager
import app.pursi.location.LocationService
import app.pursi.ais.AisRepository
import app.pursi.map.NetworkMonitor
import app.pursi.map.TileStorage
import app.pursi.map.DownloadManager
import app.pursi.ui.navigation.AppNavigation
import app.pursi.weather.WeatherRepository
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var aisRepository: AisRepository
    @Inject lateinit var tileStorage: TileStorage
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var analyticsManager: AnalyticsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Start weather/ais refresh loops at process lifetime (not Activity onStart)
        // so a brief background doesn't restart them from scratch. Loops are
        // suspended (not cancelled) on onStop via pause(); resumed on onStart.
        if (::weatherRepository.isInitialized) {
            weatherRepository.startAutoRefresh()
        }
        if (::aisRepository.isInitialized) {
            aisRepository.startAutoRefresh()
        }
        setContent {
            AppNavigation(
                tileStorage = tileStorage,
                downloadManager = downloadManager,
                networkMonitor = networkMonitor,
                analyticsManager = analyticsManager,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        ensureLocationServiceRunning()
        if (::weatherRepository.isInitialized) {
            weatherRepository.resume()
        }
    }

    override fun onStop() {
        if (::weatherRepository.isInitialized) {
            weatherRepository.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::weatherRepository.isInitialized) {
            weatherRepository.destroy()
        }
        if (::aisRepository.isInitialized) {
            aisRepository.destroy()
        }
        if (::networkMonitor.isInitialized) {
            networkMonitor.cancel()
        }
        super.onDestroy()
    }

    private fun ensureLocationServiceRunning() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
