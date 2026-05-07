package com.example.agenda

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = LightBlue,
    onPrimary = White,
    secondary = SoftPurple,
    onSecondary = White,
    background = SoftCream,
    onBackground = DarkBlue,
    surface = White,
    onSurface = DarkBlue,
)

private val DarkColors = darkColorScheme(
    primary = LightBlue,
    onPrimary = DarkBlue,
    secondary = SoftPurple,
    onSecondary = DarkBlue,
    background = DarkBlue,
    onBackground = SoftCream,
    surface = DarkBlue,
    onSurface = SoftCream,
)

@Composable
fun AgendaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    )
}
