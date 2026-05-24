package com.example.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = CNCYellow,
    secondary = CNCCyan,
    tertiary = CNCYellowLight,
    background = CNCDarkCarbon,
    surface = CNCSteelGray,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = CNCTextPrimary,
    onSurface = CNCTextPrimary,
    error = CNCAberration
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFFE65100), // Industrial Orange
    secondary = Color(0xFF00ACC1), // Deep Cyan
    tertiary = Color(0xFFF57C00),
    background = Color(0xFFECEFF1),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF263238),
    onSurface = Color(0xFF263238)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to Dark Theme which fits the CNC aesthetic perfectly
  dynamicColor: Boolean = false, // Set dynamic color to false to preserve the customized industrial theme
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
