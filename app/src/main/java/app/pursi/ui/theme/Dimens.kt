package app.pursi.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Dimens {
    fun screenHorizontalPadding(isCompact: Boolean): Dp = if (isCompact) 16.dp else 24.dp

    fun contentMaxWidth(isCompact: Boolean): Dp = if (isCompact) Dp.Unspecified else 680.dp

    fun touchTargetSize(isCompact: Boolean): Dp = if (isCompact) 48.dp else 56.dp

    fun mapOverlayPanelWidth(isCompact: Boolean): Dp = when {
        isCompact -> 260.dp
        else -> 380.dp
    }

    val sidePanelWidth = 380.dp
    val navigationRailWidth = 80.dp
    val paddingSmall = 4.dp
    val paddingMedium = 8.dp
    val paddingLarge = 16.dp
    val paddingXLarge = 24.dp
    val cornerRadius = 8.dp
    val hudCornerRadius = 12.dp
    val iconSize = 24.dp
    val iconSizeSmall = 20.dp
    val hudIconSize = 32.dp
    val buttonMinSize = 48.dp
    val indicatorWidth = 80.dp
    val speedIndicatorSize = 96.dp
    val compassRoseSize = 64.dp
    val fabButtonSize = 40.dp
    val panelCardElevation = 4.dp
}
