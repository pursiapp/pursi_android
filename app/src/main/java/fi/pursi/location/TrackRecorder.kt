package fi.pursi.location

import android.location.Location
import android.util.Log
import fi.pursi.data.dao.TrackDao
import fi.pursi.data.model.TrackPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class TrackRecorder(private val trackDao: TrackDao) {

    private var recordingScope: CoroutineScope? = null
    private var trackId: String = UUID.randomUUID().toString()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedPoints = MutableStateFlow(0)
    val recordedPoints: StateFlow<Int> = _recordedPoints.asStateFlow()

    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentTrackId.asStateFlow()

    private var recordingIntervalMs = 2000L
    private var minDistanceMeters = 15f
    private var maxIntervalMs = 30000L
    private val lastRecordedTime = AtomicLong(0L)
    private var lastRecordedLat = 0.0
    private var lastRecordedLon = 0.0
    private var adaptiveIntervalMs = 2000L
    private var consecutiveSkips = 0

    fun startRecording(
        trackId: String = UUID.randomUUID().toString(),
        intervalMs: Long = 2000L
    ): String {
        synchronized(this) {
            if (_isRecording.value) return this.trackId
            _isRecording.value = true
            recordingIntervalMs = intervalMs
            this.trackId = trackId
            _currentTrackId.value = trackId
        }
        recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        lastRecordedTime.set(0L)
        adaptiveIntervalMs = intervalMs
        consecutiveSkips = 0
        lastRecordedLat = 0.0
        lastRecordedLon = 0.0
        return trackId
    }

    fun stopRecording() {
        synchronized(this) {
            _isRecording.value = false
        }
        recordingScope?.cancel()
        recordingScope = null
    }

    fun cancel() {
        stopRecording()
    }

    fun getTrackPointsFlow(trackId: String): Flow<List<TrackPoint>> {
        return trackDao.getTrackPoints(trackId)
    }

    suspend fun computeTrackStats(): TrackStats {
        val points = trackDao.getTrackPoints(trackId).firstOrNull() ?: return TrackStats(0.0, null)
        var distanceNm = 0.0
        var maxSpeedKn: Float? = null
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            distanceNm += SpeedCalculator.distanceNm(
                p1.latitude, p1.longitude,
                p2.latitude, p2.longitude
            )
        }
        for (p in points) {
            p.speedOverGround?.let { speed ->
                val kn = speed / 0.514f
                if (maxSpeedKn == null || kn > maxSpeedKn) maxSpeedKn = kn
            }
        }
        return TrackStats(distanceNm, maxSpeedKn)
    }

    fun onLocationUpdate(location: Location) {
        val currentTid: String
        synchronized(this) {
            if (!_isRecording.value) return
            currentTid = trackId
        }
        val now = System.currentTimeMillis()
        val lastTime = lastRecordedTime.get()
        if (now - lastTime < adaptiveIntervalMs) return
        if (!lastRecordedTime.compareAndSet(lastTime, now)) return
        val theScope = recordingScope ?: return
        if (!theScope.isActive) return

        val dist = SpeedCalculator.distanceBetween(
            lastRecordedLat, lastRecordedLon,
            location.latitude, location.longitude
        )
        if (dist < minDistanceMeters) {
            consecutiveSkips++
            adaptiveIntervalMs = minOf(
                recordingIntervalMs * (consecutiveSkips + 1),
                maxIntervalMs
            )
            return
        }

        adaptiveIntervalMs = recordingIntervalMs
        consecutiveSkips = 0
        lastRecordedLat = location.latitude
        lastRecordedLon = location.longitude

        theScope.launch {
            try {
                val point = TrackPoint(
                    trackId = currentTid,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    speedOverGround = if (location.hasSpeed()) location.speed else null,
                    courseOverGround = if (location.hasBearing()) location.bearing else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    timestamp = location.time
                )
                trackDao.insert(point)
                _recordedPoints.value = trackDao.getPointCount(trackId)
            } catch (e: Exception) {
                Log.e("TrackRecorder", "Failed to insert track point", e)
            }
        }
    }

    suspend fun getTrackCount(): Int {
        return trackDao.getPointCount(trackId)
    }

    suspend fun getTrackIds(): List<String> {
        return trackDao.getAllTrackIds().firstOrNull() ?: emptyList()
    }

    suspend fun deleteTrack(id: String) {
        trackDao.deleteTrack(id)
    }
}

data class TrackStats(val distanceNm: Double, val maxSpeedKn: Float?)
