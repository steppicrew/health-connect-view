package de.steppicrew.healthconnectview.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import de.steppicrew.healthconnectview.settings.ThemeChoice

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF6FC),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    surfaceVariant = Color(0xFFDAE4E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CD9E0),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF6FF6FC),
    secondary = Color(0xFFB0CCCC),
    surfaceVariant = Color(0xFF3F4949),
)

@Composable
fun HealthConnectViewTheme(
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    // The system bars take their icon tint from the app's own theme, not the system's.
    //
    // From targetSdk 35 the bars are transparent and the legacy statusBarColor /
    // navigationBarColor attributes are ignored, so whatever this app paints shows through
    // them and only the icon tint is left to control. Without this the tint follows the
    // *system's* night mode while the palette follows the *app's* setting, and the two
    // disagree whenever they differ: choosing Light while the system is dark drew white
    // back/home/recents glyphs onto a white surface.
    //
    // It belongs in the theme rather than the activity because `theme` is collected as state
    // and recomposes on change; an onCreate-only call would leave the bars stale until the
    // next restart. SideEffect rather than LaunchedEffect: this is a cheap write to the
    // window that must land on every committed composition, not a cancellable coroutine.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
