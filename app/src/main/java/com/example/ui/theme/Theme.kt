package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassDarkColorScheme = darkColorScheme(
    primary = PrimaryForestGreen,
    onPrimary = Color.White,
    secondary = DarkSageGreen,
    onSecondary = GlassTextPrimaryDark,
    tertiary = CinematicGold,
    background = GlassBackdropDark,
    onBackground = GlassTextPrimaryDark,
    surface = Color(0xFF1B1D17),
    onSurface = GlassTextPrimaryDark,
    surfaceVariant = Color(0xFF23261F),
    onSurfaceVariant = Color(0xFF8C9384),
    outline = MutedSlate
)

private val GlassLightColorScheme = lightColorScheme(
    primary = PrimaryForestGreen,
    onPrimary = Color.White,
    secondary = LightSageGreen,
    onSecondary = GlassTextPrimaryLight,
    tertiary = CinematicGold,
    background = GlassBackdropLight,
    onBackground = GlassTextPrimaryLight,
    surface = Color.White,
    onSurface = GlassTextPrimaryLight,
    surfaceVariant = SecondaryContainerGlass,
    onSurfaceVariant = Color(0xFF43493E),
    outline = MutedSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Set false by default to showcase the stunning warm-light organic frosted design
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) GlassDarkColorScheme else GlassLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
