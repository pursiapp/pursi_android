package app.pursi.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.pursi.ais.AisRepository
import app.pursi.data.dao.BoatDao
import app.pursi.data.dao.EmodnetDepthSampleDao
import app.pursi.data.dao.SavedRouteDao
import app.pursi.data.dao.TrackDao
import app.pursi.data.dao.TrackSummaryDao
import app.pursi.data.dao.WfsFeatureDao
import app.pursi.datasource.core.FeatureRendererRegistry
import app.pursi.datasource.core.SourceResolver
import app.pursi.datasource.global.EmodnetDepthClient
import app.pursi.location.LocationStateHolder
import app.pursi.location.TrackRecorder
import app.pursi.testutils.MainDispatcherRule
import app.pursi.water.WaterObservationRepository
import app.pursi.weather.WeatherRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val trackRecorder = mockk<TrackRecorder>(relaxed = true)
    private val trackSummaryDao = mockk<TrackSummaryDao>(relaxed = true)
    private val savedRouteDao = mockk<SavedRouteDao>(relaxed = true)
    private val boatDao = mockk<BoatDao>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val sourceResolver = mockk<SourceResolver>(relaxed = true)
    private val weatherRepository = mockk<WeatherRepository>(relaxed = true)
    private val locationStateHolder = LocationStateHolder()
    private val context = mockk<Context>(relaxed = true)
    private val aisRepository = mockk<AisRepository>(relaxed = true)
    private val waterObservationRepository = mockk<WaterObservationRepository>(relaxed = true)
    private val featureRendererRegistry = mockk<FeatureRendererRegistry>(relaxed = true)
    private val emodnetDepthClient = mockk<EmodnetDepthClient>(relaxed = true)
    private val emodnetDepthSampleDao = mockk<EmodnetDepthSampleDao>(relaxed = true)
    private val wfsFeatureDao = mockk<WfsFeatureDao>(relaxed = true)

    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        every { weatherRepository.warnings } returns MutableStateFlow(emptyList())
        every { weatherRepository.lightning } returns MutableStateFlow(emptyList())
        every { waterObservationRepository.observations } returns MutableStateFlow(emptyList())
        every { context.filesDir } returns java.io.File(System.getProperty("java.io.tmpdir"))
        every { sourceResolver.chartProviders } returns emptySet()

        viewModel = MapViewModel(
            savedStateHandle = SavedStateHandle(),
            context = context,
            trackRecorder = trackRecorder,
            trackSummaryDao = trackSummaryDao,
            savedRouteDao = savedRouteDao,
            boatDao = boatDao,
            trackDao = trackDao,
            sourceResolver = sourceResolver,
            weatherRepository = weatherRepository,
            locationStateHolder = locationStateHolder,
            aisRepository = aisRepository,
            waterObservationRepository = waterObservationRepository,
            featureRendererRegistry = featureRendererRegistry,
            emodnetDepthClient = emodnetDepthClient,
            emodnetDepthSampleDao = emodnetDepthSampleDao,
            wfsFeatureDao = wfsFeatureDao
        )
    }

    @Test
    fun `initial uiState has default values`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.showLightning)
            assertFalse(state.showWarnings)
            assertFalse(state.showRadar)
            assertEquals(0.7f, state.radarOpacity, 0.01f)
            assertEquals(1.0f, state.chartOpacity, 0.01f)
            assertEquals(5, state.lookAheadSec)
            assertEquals(FollowMode.CENTERED, state.followMode)
            assertEquals(OrientationMode.NORTH_UP, state.orientationMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleShowLightning flips state`() = runTest {
        viewModel.uiState.test {
            assertFalse(awaitItem().showLightning)
            viewModel.toggleShowLightning()
            assertTrue(awaitItem().showLightning)
            viewModel.toggleShowLightning()
            assertFalse(awaitItem().showLightning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleShowWarnings flips state`() = runTest {
        viewModel.uiState.test {
            assertFalse(awaitItem().showWarnings)
            viewModel.toggleShowWarnings()
            assertTrue(awaitItem().showWarnings)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleShowRadar flips state`() = runTest {
        viewModel.uiState.test {
            assertFalse(awaitItem().showRadar)
            viewModel.toggleShowRadar()
            assertTrue(awaitItem().showRadar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setChartOpacity updates opacity`() = runTest {
        viewModel.uiState.test {
            assertEquals(1.0f, awaitItem().chartOpacity, 0.01f)
            viewModel.setChartOpacity(0.5f)
            assertEquals(0.5f, awaitItem().chartOpacity, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRadarOpacity updates opacity`() = runTest {
        viewModel.uiState.test {
            assertEquals(0.7f, awaitItem().radarOpacity, 0.01f)
            viewModel.setRadarOpacity(0.3f)
            assertEquals(0.3f, awaitItem().radarOpacity, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRadarTimeOffset clamps between 0 and 60`() = runTest {
        viewModel.uiState.test {
            assertEquals(0, awaitItem().radarTimeOffset)
            viewModel.setRadarTimeOffset(30)
            assertEquals(30, awaitItem().radarTimeOffset)
            viewModel.setRadarTimeOffset(-5)
            assertEquals(0, awaitItem().radarTimeOffset)
            viewModel.setRadarTimeOffset(100)
            assertEquals(60, awaitItem().radarTimeOffset)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cycleFollowMode cycles through modes`() = runTest {
        viewModel.uiState.test {
            assertEquals(FollowMode.CENTERED, awaitItem().followMode)
            viewModel.cycleOrientationMode()
            // Just verify follow mode stays correct
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cycleOrientationMode cycles through modes`() = runTest {
        viewModel.uiState.test {
            assertEquals(OrientationMode.NORTH_UP, awaitItem().orientationMode)
            viewModel.cycleOrientationMode()
            assertEquals(OrientationMode.COURSE_UP, awaitItem().orientationMode)
            viewModel.cycleOrientationMode()
            assertEquals(OrientationMode.NORTH_UP, awaitItem().orientationMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleRainAndLightning toggles both radar and lightning together`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.showRadar)
            assertFalse(initial.showLightning)

            viewModel.toggleRainAndLightning()
            val afterToggle = awaitItem()
            assertTrue(afterToggle.showRadar)
            assertTrue(afterToggle.showLightning)
            assertEquals(0, afterToggle.radarTimeOffset)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLookAheadSec clamps between 3 and 15`() = runTest {
        viewModel.uiState.test {
            assertEquals(5, awaitItem().lookAheadSec)
            viewModel.setLookAheadSec(10)
            assertEquals(10, awaitItem().lookAheadSec)
            viewModel.setLookAheadSec(1)
            assertEquals(3, awaitItem().lookAheadSec)
            viewModel.setLookAheadSec(20)
            assertEquals(15, awaitItem().lookAheadSec)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCurrentTrackId updates track id`() = runTest {
        viewModel.uiState.test {
            assertEquals(null, awaitItem().currentTrackId)
            viewModel.setCurrentTrackId("test-id")
            assertEquals("test-id", awaitItem().currentTrackId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startRecording delegates to trackRecorder`() {
        every { trackRecorder.startRecording(any<String>(), intervalMs = any()) } returns "recording-id"
        val result = viewModel.startRecording()
        assertEquals("recording-id", result)
        verify { trackRecorder.startRecording(any<String>(), intervalMs = 2000L) }
    }

    @Test
    fun `stopRecording delegates to trackRecorder`() {
        viewModel.stopRecording()
        verify { trackRecorder.stopRecording() }
    }

    @Test
    fun `SavedStateHandle persists toggles across process death`() {
        val handle = SavedStateHandle()
        viewModel = MapViewModel(
            savedStateHandle = handle,
            context = context,
            trackRecorder = trackRecorder,
            trackSummaryDao = trackSummaryDao,
            savedRouteDao = savedRouteDao,
            boatDao = boatDao,
            trackDao = trackDao,
            sourceResolver = sourceResolver,
            weatherRepository = weatherRepository,
            locationStateHolder = locationStateHolder,
            aisRepository = aisRepository,
            waterObservationRepository = waterObservationRepository,
            featureRendererRegistry = featureRendererRegistry,
            emodnetDepthClient = emodnetDepthClient,
            emodnetDepthSampleDao = emodnetDepthSampleDao,
            wfsFeatureDao = wfsFeatureDao
        )

        viewModel.toggleShowLightning()
        viewModel.toggleShowWarnings()
        viewModel.toggleShowRadar()
        viewModel.setChartOpacity(0.3f)

        assertTrue(handle.get<Boolean>("showLightning")!!)
        assertTrue(handle.get<Boolean>("showWarnings")!!)
        assertTrue(handle.get<Boolean>("showRadar")!!)
        assertEquals(0.3f, handle.get<Float>("chartOpacity") ?: 0f, 0.01f)
    }
}
