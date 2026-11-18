package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = CyanBright,
    onPrimary = Color(0xFF002430),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = OnCyanContainerDark,
    secondary = IndigoBright,
    onSecondary = Color(0xFF14114A),
    secondaryContainer = IndigoContainerDark,
    onSecondaryContainer = OnIndigoContainerDark,
    tertiary = TealBright,
    background = MidnightBackgroundDark,
    onBackground = MidnightTextPrimaryDark,
    surface = MidnightSurfaceDark,
    onSurface = MidnightTextPrimaryDark,
    surfaceVariant = MidnightSurfaceVariantDark,
    onSurfaceVariant = MidnightTextSecondaryDark,
    outline = MidnightOutlineDark,
    error = StatusError,
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = OnCyanContainerLight,
    secondary = IndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainerLight,
    onSecondaryContainer = OnIndigoContainerLight,
    tertiary = TealTertiary,
    background = SlateBackgroundLight,
    onBackground = SlateTextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = SlateTextPrimaryLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateTextSecondaryLight,
    outline = SlateOutlineLight,
    error = StatusError,
    errorContainer = StatusErrorContainerLight,
    onErrorContainer = Color(0xFF991B1B),
  )

val ConnectMeShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

enum class ThemePreference {
  SYSTEM,
  LIGHT,
  DARK,
}

@Composable
fun ConnectMeTheme(
  themePreference: ThemePreference = ThemePreference.SYSTEM,
  content: @Composable () -> Unit,
) {
  val useDark =
    when (themePreference) {
      ThemePreference.SYSTEM -> isSystemInDarkTheme()
      ThemePreference.LIGHT -> false
      ThemePreference.DARK -> true
    }

  MaterialTheme(
    colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
    typography = ConnectMeTypography,
    shapes = ConnectMeShapes,
    content = content,
  )
}
