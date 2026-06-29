package app.pursi.weather

import android.content.Context
import android.content.SharedPreferences
import app.pursi.datasource.core.SourceResolver
import app.pursi.location.LocationStateHolder
import app.pursi.map.NetworkMonitor
import app.pursi.testutils.MainDispatcherRule
import android.content.res.Configuration
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WeatherRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val sourceResolver = mockk<SourceResolver>(relaxed = true)
    private val locationStateHolder = LocationStateHolder()
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)

    private lateinit var repository: WeatherRepository

    @Before
    fun setup() {
        val resources = mockk<android.content.res.Resources>(relaxed = true)
        val config = Configuration().apply { locale = java.util.Locale("fi") }
        every { resources.configuration } returns config
        every { context.resources } returns resources
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getLong(any(), any()) } returns 0L

        repository = WeatherRepository(context, sourceResolver, locationStateHolder, networkMonitor)
    }

    @Test
    fun `hasWindWarning returns false when no warnings`() {
        assertFalse(repository.hasWindWarning())
    }

    @Test
    fun `hasStormWarning returns false when no warnings`() {
        assertFalse(repository.hasStormWarning())
    }

    @Test
    fun `hasThunderstormWarning returns false when no lightning or warnings`() {
        assertFalse(repository.hasThunderstormWarning())
    }

    @Test
    fun `highestWarningSeverity returns MINOR when no warnings`() {
        assertTrue(repository.highestWarningSeverity() == WarningSeverity.MINOR)
    }

    @Test
    fun `activeWarningSummary returns empty string when no warnings`() {
        assertTrue(repository.activeWarningSummary().isEmpty())
    }

    /**
     * S2: Cold start should populate StateFlows from cache so the user sees
     * last-known data while the first refresh runs (no blank-then-pop).
     */
    @Test
    fun `loads cached forecast on init`() = runTest {
        val cached = listOf(
            ForecastPoint(timestamp = 1_700_000_000L, referenceTime = 1_700_000_000L, temperatureC = 18f, windSpeedMs = 5f)
        )
        val cachedJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ForecastPoint.serializer()), cached
        )
        // Reset and re-setup so the loadCachedForecast() init block sees the cached value
        every { prefs.getString("cached_forecast", null) } returns cachedJson
        val freshRepo = WeatherRepository(context, sourceResolver, locationStateHolder, networkMonitor)
        val loaded = freshRepo.forecast.value
        assertNotNull(loaded)
        assertEquals(1, loaded.size)
        assertEquals(18f, loaded[0].temperatureC)
    }
}
