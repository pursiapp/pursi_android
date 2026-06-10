package fi.pursi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    onPrimary = White,
    primaryContainer = LightBlue,
    secondary = SeaFoam,
    onSecondary = White,
    tertiary = TertiaryLight,
    onTertiary = White,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = NearBlack,
    background = ChartBackground,
    onBackground = NearBlack,
    surface = White,
    onSurface = NearBlack,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = WarningRed,
    onError = White
)

private val DarkColorScheme = darkColorScheme(
    primary = NightModeAccent,
    onPrimary = NightModeBackground,
    primaryContainer = SteelBlue,
    secondary = SeaFoam,
    onSecondary = NightModeBackground,
    tertiary = TertiaryDark,
    onTertiary = NightModeBackground,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = NightModeText,
    background = NightModeBackground,
    onBackground = NightModeText,
    surface = NightModeCard,
    onSurface = NightModeText,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = CoralRed,
    onError = White
)

@Composable
fun PursiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PursiTypography,
        content = content
    )
}
