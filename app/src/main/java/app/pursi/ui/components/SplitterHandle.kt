package app.pursi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.pursi.ui.viewmodel.SplitOrientation
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SplitterHandle(
    orientation: SplitOrientation,
    splitFraction: Float,
    parentSizePx: Float,
    onFractionChange: (Float) -> Unit,
    onFractionCommit: (Float) -> Unit,
    onSwapPanes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVertical = orientation == SplitOrientation.Vertical
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }

    val labelAlpha by animateFloatAsState(
        targetValue = if (showLabel) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "labelAlpha"
    )

    LaunchedEffect(showLabel) {
        if (showLabel) {
            delay(1200L)
            showLabel = false
        }
    }

    val hitAreaThickness = 12.dp
    val grabDotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    // Wrap values in rememberUpdatedState so gesture lambdas always see the latest values
    val currentSplitFraction by rememberUpdatedState(splitFraction)
    val currentOnFractionChange by rememberUpdatedState(onFractionChange)
    val currentOnFractionCommit by rememberUpdatedState(onFractionCommit)
    val currentOnSwapPanes by rememberUpdatedState(onSwapPanes)

    Box(
        modifier = modifier
            .then(
                if (isVertical) Modifier.fillMaxHeight().width(hitAreaThickness)
                else Modifier.fillMaxWidth().height(hitAreaThickness)
            )
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .pointerInput(parentSizePx) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        showLabel = true
                    },
                    onDragEnd = {
                        isDragging = false
                        val final = when {
                            currentSplitFraction < 0.1f -> 0f
                            currentSplitFraction > 0.9f -> 1f
                            else -> currentSplitFraction
                        }
                        currentOnFractionCommit(final)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (parentSizePx > 0f) {
                            val delta = if (isVertical) {
                                dragAmount.x / parentSizePx
                            } else {
                                dragAmount.y / parentSizePx
                            }
                            currentOnFractionChange(currentSplitFraction + delta)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val newFraction = when {
                            currentSplitFraction < 0.4f -> 0.5f
                            currentSplitFraction < 0.6f -> 0.7f
                            else -> 0.3f
                        }
                        currentOnFractionChange(newFraction)
                        currentOnFractionCommit(newFraction)
                    },
                    onLongPress = { currentOnSwapPanes() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isVertical) Modifier.width(1.dp).fillMaxHeight(0.6f)
                    else Modifier.height(1.dp).fillMaxWidth(0.6f)
                )
                .background(
                    if (isDragging) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline
                )
        )

        if (isVertical) {
            Column(
                modifier = Modifier.offset(y = with(density) { 2.dp }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(3) {
                    Box(Modifier.size(3.dp).background(grabDotColor, CircleShape))
                }
            }
        } else {
            Row(
                modifier = Modifier.offset(x = with(density) { 2.dp }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(3) {
                    Box(Modifier.size(3.dp).background(grabDotColor, CircleShape))
                }
            }
        }

        if (labelAlpha > 0f) {
            val pct1 = (currentSplitFraction * 100).roundToInt()
            val pct2 = 100 - pct1
            val labelOffset = if (isVertical) {
                Modifier.offset(x = with(density) { 20.dp })
            } else {
                Modifier.offset(y = with(density) { 20.dp })
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(labelOffset)
                    .alpha(labelAlpha),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "$pct1% / $pct2%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
