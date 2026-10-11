package com.MAD.n152.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EditorialColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentContainer,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF765B00),
    onSecondary = Color.White,
    secondaryContainer = ReaderAccentSoft,
    onSecondaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftSurface,
    onSurfaceVariant = MutedInk,
    outline = Outline,
    outlineVariant = Color(0xFFDED3AD),
    error = Error,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Ink,
    inverseOnSurface = ReaderPaper,
    inversePrimary = ReaderAccent
)

@Composable
fun N152Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EditorialColorScheme,
        typography = Typography,
        content = content
    )
}
