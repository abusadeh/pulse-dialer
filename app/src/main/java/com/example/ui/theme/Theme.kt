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

val AccentColors = listOf(
    Color(0xFF0284C7) to Color(0xFF38BDF8), // Blue (Light to Dark)
    Color(0xFF059669) to Color(0xFF10B981), // Emerald
    Color(0xFF7C3AED) to Color(0xFFA78BFA), // Violet
    Color(0xFFDC2626) to Color(0xFFF87171), // Crimson
    Color(0xFFD97706) to Color(0xFFFBBF24)  // Amber
)

val AccentNames = listOf(
    "Pulse Blue",
    "Emerald Green",
    "Electric Violet",
    "Crimson Red",
    "Sunset Amber"
)

@Composable
fun PulseDialerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentIndex: Int = 0,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val (accentLight, accentDark) = AccentColors.getOrElse(accentIndex) { AccentColors[0] }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = accentDark,
            onPrimary = Color(0xFF0F172A),
            primaryContainer = accentDark.copy(alpha = 0.25f),
            onPrimaryContainer = Color.White,
            secondary = PulseSecondaryDark,
            onSecondary = Color(0xFF0F172A),
            background = PulseBackgroundDark,
            onBackground = Color(0xFFF8FAFC),
            surface = PulseSurfaceDark,
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = PulseSurfaceVariantDark,
            onSurfaceVariant = Color(0xFFCBD5E1),
            error = CallRed,
            onError = Color.White
        )
        else -> lightColorScheme(
            primary = accentLight,
            onPrimary = Color.White,
            primaryContainer = accentLight.copy(alpha = 0.15f),
            onPrimaryContainer = accentLight,
            secondary = PulseSecondaryLight,
            onSecondary = Color.White,
            background = PulseBackgroundLight,
            onBackground = Color(0xFF0F172A),
            surface = PulseSurfaceLight,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = PulseSurfaceVariantLight,
            onSurfaceVariant = Color(0xFF475569),
            error = CallRedLight,
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
