package app.pursi.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pursi.R
import app.pursi.location.SpeedCalculator
import app.pursi.map.DownloadManager
import app.pursi.map.LatLngRect
import app.pursi.map.RectangleTileCalculator
import app.pursi.map.TileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private enum class SelState { IDLE, READY, ADJUST_CORNER, ADJUST_RECT }

@Composable
fun AreaSelectionScreen(
    tileStorage: TileStorage,
    downloadManager: DownloadManager,
    existingRects: List<LatLngRect> = emptyList(),
    initialCamLat: Double = Double.NaN,
    initialCamLon: Double = Double.NaN,
    initialCamZoom: Double = 7.0,
    onBack: () -> Unit,
    onAreaSelected: (LatLngRect, String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    var rectangle by remember { mutableStateOf<LatLngRect?>(null) }
    var state by remember { mutableStateOf(SelState.IDLE) }
    var cornerIndex by remember { mutableStateOf(-1) }
    var snapshoting by remember { mutableStateOf(false) }

    // Long-press tracking (single finger adjustment)
    var touchDownX by remember { mutableStateOf(0f) }
    var touchDownY by remember { mutableStateOf(0f) }
    var latestTouchX by remember { mutableStateOf(0f) }
    var latestTouchY by remember { mutableStateOf(0f) }
    var singlePendingRunnable by remember { mutableStateOf<Runnable?>(null) }

    // Two-finger creation tracking
    var tfX1 by remember { mutableStateOf(0f) }
    var tfY1 by remember { mutableStateOf(0f) }
    var tfX2 by remember { mutableStateOf(0f) }
    var tfY2 by remember { mutableStateOf(0f) }
    var tfPendingRunnable by remember { mutableStateOf<Runnable?>(null) }

    // Animated ring feedback
    var pendingTouchX by remember { mutableStateOf(0f) }
    var pendingTouchY by remember { mutableStateOf(0f) }
    var showRing by remember { mutableStateOf(false) }
    val ringProgress = remember { Animatable(0f) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            onStart()
            onResume()
        }
    }

    // Cancel all pending on dispose
    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Init map
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapRef = map
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.setMinZoomPreference(4.0)
            map.setMaxZoomPreference(18.0)
            map.setStyle(Style.Builder().fromUri("asset://pursi_style_vector.json")) {
                styleReady = true
            }
            if (!initialCamLat.isNaN() && !initialCamLon.isNaN()) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(initialCamLat, initialCamLon))
                    .zoom(initialCamZoom)
                    .build()
            }
        }
    }

    // Touch listener
    DisposableEffect(Unit) {
        var singleRunnable: Runnable? = null
        var twoFingerRunnable: Runnable? = null

        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x; touchDownY = event.y
                    latestTouchX = event.x; latestTouchY = event.y
                    if (state == SelState.READY) {
                        val rect = rectangle
                        if (rect != null) {
                            singleRunnable = Runnable {
                                val map = mapRef ?: return@Runnable
                                val r = rectangle ?: return@Runnable
                                val threshold = 45f * context.resources.displayMetrics.density
                                val corners = listOf(
                                    map.projection.toScreenLocation(LatLng(r.minLat, r.minLng)),
                                    map.projection.toScreenLocation(LatLng(r.minLat, r.maxLng)),
                                    map.projection.toScreenLocation(LatLng(r.maxLat, r.maxLng)),
                                    map.projection.toScreenLocation(LatLng(r.maxLat, r.minLng))
                                )
                                var hit = -1
                                for (i in 0..3) {
                                    val dx = latestTouchX - corners[i].x
                                    val dy = latestTouchY - corners[i].y
                                    if (kotlin.math.sqrt((dx*dx + dy*dy).toDouble()) < threshold) {
                                        hit = i; break
                                    }
                                }
                                if (hit >= 0) {
                                    cornerIndex = hit; state = SelState.ADJUST_CORNER
                                } else {
                                    val pts = listOf(
                                        map.projection.toScreenLocation(LatLng(r.minLat, r.minLng)),
                                        map.projection.toScreenLocation(LatLng(r.minLat, r.maxLng)),
                                        map.projection.toScreenLocation(LatLng(r.maxLat, r.maxLng)),
                                        map.projection.toScreenLocation(LatLng(r.maxLat, r.minLng))
                                    )
                                    val minScrX = pts.minOf { it.x }
                                    val maxScrX = pts.maxOf { it.x }
                                    val minScrY = pts.minOf { it.y }
                                    val maxScrY = pts.maxOf { it.y }
                                    if (latestTouchX in minScrX..maxScrX && latestTouchY in minScrY..maxScrY) {
                                        state = SelState.ADJUST_RECT
                                    }
                                }
                            }
                        }
                    }
                    singlePendingRunnable = singleRunnable
                    singleRunnable?.let { handler.postDelayed(it, 400) }
                    if (state == SelState.READY && singleRunnable != null) {
                        pendingTouchX = event.x; pendingTouchY = event.y
                        showRing = true
                        scope.launch {
                            ringProgress.snapTo(0f)
                            ringProgress.animateTo(1f, animationSpec = tween(400))
                        }
                    }
                    false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        tfX1 = event.getX(0); tfY1 = event.getY(0)
                        tfX2 = event.getX(1); tfY2 = event.getY(1)
                        val r = Runnable {
                            val map = mapRef ?: return@Runnable
                            val p1 = map.projection.fromScreenLocation(PointF(tfX1, tfY1))
                            val p2 = map.projection.fromScreenLocation(PointF(tfX2, tfY2))
                            rectangle = LatLngRect(
                                minLat = minOf(p1.latitude, p2.latitude),
                                maxLat = maxOf(p1.latitude, p2.latitude),
                                minLng = minOf(p1.longitude, p2.longitude),
                                maxLng = maxOf(p1.longitude, p2.longitude)
                            )
                            state = SelState.READY
                        }
                        twoFingerRunnable = r
                        tfPendingRunnable = r
                        handler.postDelayed(r, 400)
                        false
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    latestTouchX = event.x; latestTouchY = event.y

                    // Cancel two-finger if fingers moved too much
                    if (twoFingerRunnable != null && event.pointerCount >= 2) {
                        val tfR = twoFingerRunnable
                        val dx1 = kotlin.math.abs(event.getX(0) - tfX1)
                        val dy1 = kotlin.math.abs(event.getY(0) - tfY1)
                        val dx2 = kotlin.math.abs(event.getX(1) - tfX2)
                        val dy2 = kotlin.math.abs(event.getY(1) - tfY2)
                        if (dx1 > 20f || dy1 > 20f || dx2 > 20f || dy2 > 20f) {
                            tfR?.let { handler.removeCallbacks(it) }
                            twoFingerRunnable = null; tfPendingRunnable = null
                        }
                        false
                    } else if (state == SelState.ADJUST_CORNER) {
                        true.also {
                            mapRef?.let { map ->
                                rectangle?.let { r ->
                                    val ll = map.projection.fromScreenLocation(PointF(event.x, event.y))
                                    rectangle = when (cornerIndex) {
                                        0 -> r.copy(minLat = ll.latitude, minLng = ll.longitude)
                                        1 -> r.copy(minLat = ll.latitude, maxLng = ll.longitude)
                                        2 -> r.copy(maxLat = ll.latitude, maxLng = ll.longitude)
                                        else -> r.copy(maxLat = ll.latitude, minLng = ll.longitude)
                                    }
                                    rectangle?.let { rect ->
                                        rectangle = rect.copy(
                                            minLat = minOf(rect.minLat, rect.maxLat),
                                            maxLat = maxOf(rect.minLat, rect.maxLat)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (state == SelState.ADJUST_RECT) {
                        true.also {
                            mapRef?.let { map ->
                                rectangle?.let { r ->
                                    val dx = event.x - touchDownX
                                    val dy = event.y - touchDownY
                                    if (kotlin.math.abs(dx) > 5f || kotlin.math.abs(dy) > 5f) {
                                        try {
                                            val cur = map.projection.fromScreenLocation(PointF(event.x, event.y))
                                            val prev = map.projection.fromScreenLocation(PointF(event.x - dx, event.y - dy))
                                            val latDelta = cur.latitude - prev.latitude
                                            val lngDelta = cur.longitude - prev.longitude
                                            rectangle = r.copy(
                                                minLat = r.minLat - latDelta, maxLat = r.maxLat - latDelta,
                                                minLng = r.minLng - lngDelta, maxLng = r.maxLng - lngDelta
                                            )
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    } else {
                        // Cancel single touch long-press if moved too much
                        val sr = singleRunnable
                        if (sr != null) {
                            val dist = kotlin.math.sqrt(
                                ((event.x - touchDownX) * (event.x - touchDownX) +
                                 (event.y - touchDownY) * (event.y - touchDownY)).toDouble()
                            )
                            if (dist > 20f) {
                                handler.removeCallbacks(sr)
                                singleRunnable = null; singlePendingRunnable = null
                                showRing = false
                            }
                        }
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    showRing = false
                    if (state == SelState.ADJUST_CORNER || state == SelState.ADJUST_RECT) {
                        state = SelState.READY; cornerIndex = -1
                        return@setOnTouchListener true
                    }
                    singleRunnable?.let { handler.removeCallbacks(it) }
                    singleRunnable = null; singlePendingRunnable = null
                    twoFingerRunnable?.let { handler.removeCallbacks(it) }
                    twoFingerRunnable = null; tfPendingRunnable = null
                    false
                }

                else -> false
            }
        }

        onDispose {
            mapView.setOnTouchListener(null)
        }
    }

    // Render existing rects
    LaunchedEffect(styleReady, existingRects) {
        val map = mapRef ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        map.getStyle { style ->
            try { style.removeLayer("area-existing-fill") } catch (_: Exception) {}
            try { style.removeLayer("area-existing-line") } catch (_: Exception) {}
            try { style.removeSource("area-existing") } catch (_: Exception) {}

            if (existingRects.isEmpty()) return@getStyle
            val features = existingRects.map { rect ->
                val coords = listOf(listOf(
                    Point.fromLngLat(rect.minLng, rect.minLat),
                    Point.fromLngLat(rect.maxLng, rect.minLat),
                    Point.fromLngLat(rect.maxLng, rect.maxLat),
                    Point.fromLngLat(rect.minLng, rect.maxLat),
                    Point.fromLngLat(rect.minLng, rect.minLat)
                ))
                Feature.fromGeometry(Polygon.fromLngLats(coords))
            }
            val src = GeoJsonSource("area-existing")
            src.setGeoJson(FeatureCollection.fromFeatures(features))
            style.addSource(src)
            style.addLayerAbove(FillLayer("area-existing-fill", "area-existing").apply {
                setProperties(PropertyFactory.fillColor("#9E9E9E"), PropertyFactory.fillOpacity(0.12f))
            }, "layer-seamark-bottom")
            style.addLayerAbove(LineLayer("area-existing-line", "area-existing").apply {
                setProperties(PropertyFactory.lineColor("#757575"), PropertyFactory.lineWidth(1.5f),
                    PropertyFactory.lineOpacity(0.5f), PropertyFactory.lineDasharray(arrayOf(3f, 2f)))
            }, "area-existing-fill")
        }
    }

    // Render current rect + corner handles
    LaunchedEffect(styleReady, rectangle) {
        val map = mapRef ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        map.getStyle { style ->
            try { style.removeLayer("area-current-fill") } catch (_: Exception) {}
            try { style.removeLayer("area-current-line") } catch (_: Exception) {}
            try { style.removeLayer("area-corners") } catch (_: Exception) {}
            try { style.removeSource("area-current") } catch (_: Exception) {}
            try { style.removeSource("area-corners") } catch (_: Exception) {}

            val rect = rectangle ?: return@getStyle
            val coords = listOf(listOf(
                Point.fromLngLat(rect.minLng, rect.minLat),
                Point.fromLngLat(rect.maxLng, rect.minLat),
                Point.fromLngLat(rect.maxLng, rect.maxLat),
                Point.fromLngLat(rect.minLng, rect.maxLat),
                Point.fromLngLat(rect.minLng, rect.minLat)
            ))
            val rectSrc = GeoJsonSource("area-current")
            rectSrc.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Polygon.fromLngLats(coords)))))
            style.addSource(rectSrc)
            style.addLayerAbove(FillLayer("area-current-fill", "area-current").apply {
                setProperties(PropertyFactory.fillColor("#2196F3"), PropertyFactory.fillOpacity(0.25f))
            }, "layer-seamark-bottom")
            style.addLayerAbove(LineLayer("area-current-line", "area-current").apply {
                setProperties(PropertyFactory.lineColor("#1976D2"), PropertyFactory.lineWidth(2f), PropertyFactory.lineOpacity(0.8f))
            }, "area-current-fill")

            val cornerPoints = listOf(
                Point.fromLngLat(rect.minLng, rect.minLat),
                Point.fromLngLat(rect.maxLng, rect.minLat),
                Point.fromLngLat(rect.maxLng, rect.maxLat),
                Point.fromLngLat(rect.minLng, rect.maxLat)
            )
            val cornerSrc = GeoJsonSource("area-corners")
            cornerSrc.setGeoJson(FeatureCollection.fromFeatures(cornerPoints.map { Feature.fromGeometry(it) }))
            style.addSource(cornerSrc)
            style.addLayerAbove(CircleLayer("area-corners", "area-corners").apply {
                setProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(3f),
                    PropertyFactory.circleStrokeColor("#1976D2"),
                    PropertyFactory.circleOpacity(0.9f)
                )
            }, "area-current-line")
        }
    }

    // Snapshot + continue
    val takeSnapshotAndContinue: () -> Unit = lambda@ {
        val map = mapRef ?: return@lambda
        val rect = rectangle ?: return@lambda
        if (snapshoting) return@lambda
        snapshoting = true
        map.snapshot { bitmap ->
            scope.launch(Dispatchers.IO) {
                val tempId = UUID.randomUUID().toString()
                val snapDir = File(context.filesDir, "snapshots")
                snapDir.mkdirs()
                val snapFile = File(snapDir, "$tempId.png")
                val cropped = bitmap.centerCrop(240, 160)
                FileOutputStream(snapFile).use { out ->
                    cropped.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                if (cropped !== bitmap) cropped.recycle()
                withContext(Dispatchers.Main) {
                    onAreaSelected(rect, snapFile.absolutePath)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Animated ring
        if (showRing) {
            val xDp = with(density) { pendingTouchX.toDp() - 24.dp }
            val yDp = with(density) { pendingTouchY.toDp() - 24.dp }
            Canvas(
                modifier = Modifier
                    .offset(x = xDp, y = yDp)
                    .size(48.dp)
            ) {
                val radius = size.minDimension / 2
                drawCircle(
                    color = Color(0xFF2196F3).copy(alpha = ringProgress.value * 0.5f),
                    radius = radius,
                    style = Stroke(width = 3f * ringProgress.value)
                )
            }
        }

        // Top bar
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.close))
                }
                if (state == SelState.READY || state == SelState.ADJUST_CORNER || state == SelState.ADJUST_RECT) {
                    SmallFloatingActionButton(onClick = {
                        rectangle = null; state = SelState.IDLE
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.offline_area_cancel_selection))
                    }
                }
            }
        }

        // Bottom card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .widthIn(max = 320.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val hint = when {
                        state == SelState.ADJUST_CORNER -> stringResource(R.string.offline_area_hint_corner)
                        state == SelState.ADJUST_RECT -> stringResource(R.string.offline_area_hint_drag)
                        rectangle != null -> {
                            val r = rectangle!!
                            val midLat = (r.minLat + r.maxLat) / 2.0
                            val midLng = (r.minLng + r.maxLng) / 2.0
                            val wKm = SpeedCalculator.distanceBetween(midLat, r.minLng, midLat, r.maxLng) / 1000.0
                            val hKm = SpeedCalculator.distanceBetween(r.minLat, midLng, r.maxLat, midLng) / 1000.0
                            "${"%.0f".format(wKm)} km × ${"%.0f".format(hKm)} km"
                        }
                        else -> stringResource(R.string.offline_area_hint_two_finger)
                    }
                    Text(hint, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp))
                    Button(
                        onClick = { takeSnapshotAndContinue() },
                        enabled = rectangle != null && !snapshoting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (snapshoting) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.offline_area_continue))
                        }
                    }
                }
            }
        }
    }
}

private fun Bitmap.centerCrop(w: Int, h: Int): Bitmap {
    val srcW = width.toFloat()
    val srcH = height.toFloat()
    val targetRatio = w.toFloat() / h.toFloat()
    val srcRatio = srcW / srcH
    return if (srcRatio > targetRatio) {
        val cropW = (srcH * targetRatio).toInt()
        val x = ((srcW - cropW) / 2f).toInt()
        Bitmap.createScaledBitmap(Bitmap.createBitmap(this, x, 0, cropW, height), w, h, true)
    } else {
        val cropH = (srcW / targetRatio).toInt()
        val y = ((srcH - cropH) / 2f).toInt()
        Bitmap.createScaledBitmap(Bitmap.createBitmap(this, 0, y, width, cropH), w, h, true)
    }
}
