package fi.pursi

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dagger.hilt.android.HiltAndroidApp
import fi.pursi.analytics.AnalyticsManager
import fi.pursi.analytics.CrashHandler
import fi.pursi.map.TileStorage
import fi.pursi.map.ParallelTileDownloader
import javax.inject.Inject

@HiltAndroidApp
class PursiApplication : Application() {

    @Inject lateinit var tileStorage: TileStorage
    @Inject lateinit var parallelTileDownloader: ParallelTileDownloader

    @Inject lateinit var analyticsManager: AnalyticsManager

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(
                umamiUrl = BuildConfig.UMAMI_URL,
                websiteId = BuildConfig.UMAMI_WEBSITE_ID,
                versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
                } catch (_: Exception) { "unknown" },
                defaultHandler = Thread.getDefaultUncaughtExceptionHandler(),
            ),
        )

        registerActivityLifecycleCallbacks(AppLifecycleTracker(analyticsManager))
    }
}

private class AppLifecycleTracker(
    private val analyticsManager: AnalyticsManager,
) : Application.ActivityLifecycleCallbacks {
    private var startedCount = 0

    override fun onActivityStarted(activity: Activity) {
        if (startedCount == 0) {
            analyticsManager.onAppForeground()
        }
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
        if (startedCount == 0) {
            analyticsManager.onAppBackground()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
