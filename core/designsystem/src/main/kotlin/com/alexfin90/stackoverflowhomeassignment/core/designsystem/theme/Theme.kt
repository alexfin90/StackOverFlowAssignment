package com.alexfin90.stackoverflowhomeassignment.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo10,
    primaryContainer = Indigo20,
    onPrimaryContainer = Indigo90,
    secondary = Teal80,
    onSecondary = Teal10,
    secondaryContainer = Teal20,
    onSecondaryContainer = Teal90,
    tertiary = Sky80,
    onTertiary = Sky10,
    tertiaryContainer = Sky20,
    onTertiaryContainer = Sky90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Slate10,
    onBackground = Slate90,
    surface = Slate10,
    onSurface = Slate90,
    surfaceVariant = Slate30,
    onSurfaceVariant = Slate80,
    outline = Slate60,
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Sky40,
    onTertiary = Color.White,
    tertiaryContainer = Sky90,
    onTertiaryContainer = Sky10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Slate99,
    onBackground = Slate10,
    surface = Color.White,
    onSurface = Slate10,
    surfaceVariant = Slate95,
    onSurfaceVariant = Slate40,
    outline = Slate50,
)

/** Light and dark Material 3 theme; follows the system setting. */
@Composable
fun StackOverFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
