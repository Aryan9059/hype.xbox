package com.xbox.hype.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val XboxGreen = Color(0xFF107C10)
private val XboxGreenBright = Color(0xFF8CFF2E)

private val DarkColorScheme = darkColorScheme(
    primary = XboxGreen,
    onPrimary = Color.White,
    secondary = XboxGreenBright,
    onSecondary = Color.Black,
    tertiary = XboxGreenBright,
    background = Color.Black,
    onBackground = Color(0xFFD9D9D9),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFD9D9D9),
)

private val LightColorScheme = lightColorScheme(
    primary = XboxGreen,
    secondary = XboxGreenBright,
    tertiary = XboxGreenBright,
)

@Composable
fun HypexboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}