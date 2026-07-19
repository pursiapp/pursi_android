package app.pursi.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.pursi.R
import app.pursi.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.util.Locale

enum class OnboardingStep {
    DISCLAIMER,
    LOCATION_PERMISSION,
    OFFLINE_PREP,
    TIPS
}

private enum class OfflinePhase {
    IDLE,
    DETECTING,
    DOWNLOADING,
    SUCCESS,
    FAILED
}

@Composable
fun OnboardingOverlay(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    downloadManager: app.pursi.map.DownloadManager? = null,
    pmtilesDownloader: app.pursi.map.PmtilesDownloader? = null,
    vvDataDownloader: app.pursi.map.VvDataDownloader? = null,
    currentLatLng: LatLng? = null,
    onChooseCustom: (() -> Unit)? = null
) {
    var step by remember { mutableStateOf(OnboardingStep.DISCLAIMER) }

    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            try {
                val intent = Intent(context, LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) { }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(enabled = false) { }
    ) {
        when (step) {
            OnboardingStep.DISCLAIMER -> {
                DisclaimerContent(
                    onAccept = { step = OnboardingStep.LOCATION_PERMISSION }
                )
            }
            OnboardingStep.LOCATION_PERMISSION -> {
                LocationPermissionContent(
                    onAllow = {
                        val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
                        step = OnboardingStep.OFFLINE_PREP
                    },
                    onDeny = { step = OnboardingStep.OFFLINE_PREP }
                )
            }
            OnboardingStep.OFFLINE_PREP -> {
                OfflinePrepContent(
                    downloadManager = downloadManager,
                    pmtilesDownloader = pmtilesDownloader,
                    vvDataDownloader = vvDataDownloader,
                    currentLatLng = currentLatLng,
                    onChooseCustom = onChooseCustom,
                    onSkip = { step = OnboardingStep.TIPS },
                    onComplete = onComplete
                )
            }
            OnboardingStep.TIPS -> {
                TipsContent(onComplete = onComplete)
            }
        }
    }
}

@Composable
private fun DisclaimerContent(
    onAccept: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(20.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_disclaimer_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_disclaimer_accept),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LocationPermissionContent(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(20.dp)
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_permission_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_permission_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permission_allow),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDeny,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permission_deny),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun OfflinePrepContent(
    downloadManager: app.pursi.map.DownloadManager?,
    pmtilesDownloader: app.pursi.map.PmtilesDownloader?,
    vvDataDownloader: app.pursi.map.VvDataDownloader?,
    currentLatLng: LatLng?,
    onChooseCustom: (() -> Unit)?,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(OfflinePhase.DETECTING) }
    var profile by remember { mutableStateOf(OnboardingProfile.EUROPE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pmtilesContinentProgress by pmtilesDownloader?.continentProgress?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    LaunchedEffect(Unit) {
        profile = detectOnboardingProfile(
            localeCountry = Locale.getDefault().country,
            lastKnownLatLng = currentLatLng
        )
        phase = OfflinePhase.IDLE
    }

    val pmtilesMb = 80
    val vvMb = 12
    val totalMb = pmtilesMb + if (profile == OnboardingProfile.FINLAND) vvMb else 0

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(20.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.onboarding_offline_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_offline_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            when (phase) {
                OfflinePhase.DETECTING -> {
                    Text(
                        text = stringResource(R.string.onboarding_offline_detecting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OfflinePhase.IDLE -> {
                    Text(
                        text = stringResource(R.string.onboarding_offline_recommendation),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        ContentRow(
                            nameRes = R.string.onboarding_offline_content_seamarks,
                            sizeMb = pmtilesMb
                        )
                        if (profile == OnboardingProfile.FINLAND) {
                            ContentRow(
                                nameRes = R.string.onboarding_offline_content_vv,
                                sizeMb = vvMb
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_offline_total_fmt, totalMb),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.onboarding_offline_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    val failedNetworkMsg = stringResource(R.string.onboarding_offline_failed_network)
                    if (pmtilesDownloader != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    phase = OfflinePhase.DOWNLOADING
                                    errorMessage = null

                                    val fastTasks = mutableListOf<kotlinx.coroutines.Deferred<Boolean>>()
                                    fastTasks += async { pmtilesDownloader.downloadContinent("europe") }
                                    if (profile == OnboardingProfile.FINLAND) {
                                        fastTasks += async { vvDataDownloader?.download() != false }
                                    }

                                    val fastResults = fastTasks.awaitAll()
                                    if (fastResults.all { it }) {
                                        phase = OfflinePhase.SUCCESS
                                        kotlinx.coroutines.delay(1500L)
                                        onComplete()
                                    } else {
                                        phase = OfflinePhase.FAILED
                                        errorMessage = failedNetworkMsg
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_offline_download_now_fmt, totalMb),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.onboarding_offline_skip),
                            fontSize = 14.sp
                        )
                    }
                }
                OfflinePhase.DOWNLOADING -> {
                    val pmtilesPct = pmtilesContinentProgress["europe"] ?: 0f
                    val pmtilesDone = pmtilesPct >= 100f
                    val displayPct = if (pmtilesDone) 100 else pmtilesPct.toInt().coerceIn(0, 99)

                    Text(
                        text = if (!pmtilesDone)
                            stringResource(R.string.onboarding_offline_progress_status_pmtiles, displayPct)
                        else
                            stringResource(R.string.onboarding_offline_progress_status_vv),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pmtilesPct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.onboarding_offline_skip),
                            fontSize = 14.sp
                        )
                    }
                }
                OfflinePhase.SUCCESS -> {
                    Text(
                        text = stringResource(R.string.onboarding_offline_done),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onComplete,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_offline_start_using), fontSize = 14.sp)
                        }
                        if (onChooseCustom != null) {
                            Button(
                                onClick = { onChooseCustom?.invoke() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.onboarding_offline_open_settings), fontSize = 14.sp)
                            }
                        }
                    }
                }
                OfflinePhase.FAILED -> {
                    Text(
                        text = errorMessage ?: stringResource(R.string.onboarding_offline_failed_network),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { phase = OfflinePhase.IDLE },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_offline_retry))
                        }
                        TextButton(onClick = onSkip) {
                            Text(stringResource(R.string.onboarding_offline_skip))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentRow(nameRes: Int, sizeMb: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u2022",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(nameRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "~${sizeMb} MB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TipsContent(
    onComplete: () -> Unit
) {
    val tips = listOf(
        stringResource(R.string.onboarding_tip_center),
        stringResource(R.string.onboarding_tip_compass),
        stringResource(R.string.onboarding_tip_layers),
        stringResource(R.string.onboarding_tip_nav),
        stringResource(R.string.onboarding_tip_longpress)
    )

    val pagerState = rememberPagerState(pageCount = { tips.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 48.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                FeatureTipCard(text = tips[page])
            }

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(tips.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (pagerState.currentPage == tips.lastIndex) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_close),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = "Seuraava",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
