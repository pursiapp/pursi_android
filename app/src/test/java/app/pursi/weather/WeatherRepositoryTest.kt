package app.pursi.weather

import android.content.Context
import android.content.SharedPreferences
import app.pursi.datasource.core.SourceResolver
import app.pursi.location.LocationStateHolder
import app.pursi.testutils.MainDispatcherRule
import android.content.res.Configuration
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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

        repository = WeatherRepository(context, sourceResolver, locationStateHolder)
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
}
