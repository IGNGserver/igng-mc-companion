package net.igng.mcstatus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import net.igng.mcstatus.data.AppSettings
import net.igng.mcstatus.data.ThemeAccent

private val TealLightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2EA),
    onPrimaryContainer = Color(0xFF052F2B),
    secondary = Color(0xFF355C7D),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFF5F7FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEAF0F7),
)

private val TealDarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2B),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFB8F2EA),
    secondary = Color(0xFF89A6C5),
    background = Color(0xFF08131A),
    surface = Color(0xFF08131A),
    surfaceContainer = Color(0xFF10202A),
    surfaceContainerHigh = Color(0xFF17303D),
)

private val BlueLightScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF355C7D),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFF5F7FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEAF0F7),
)

private val BlueDarkScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF89A6C5),
    background = Color(0xFF08131A),
    surface = Color(0xFF08131A),
    surfaceContainer = Color(0xFF10202A),
    surfaceContainerHigh = Color(0xFF17303D),
)

private val OrangeLightScheme = lightColorScheme(
    primary = Color(0xFFEA580C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0CC),
    onPrimaryContainer = Color(0xFF431407),
    secondary = Color(0xFF7C4A2F),
    background = Color(0xFFFFF7F2),
    surface = Color(0xFFFFF7F2),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFFFEDE0),
)

private val OrangeDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB689),
    onPrimary = Color(0xFF4A1D00),
    primaryContainer = Color(0xFF9A3412),
    onPrimaryContainer = Color(0xFFFFE0CC),
    secondary = Color(0xFFE5A77A),
    background = Color(0xFF1A0F0A),
    surface = Color(0xFF1A0F0A),
    surfaceContainer = Color(0xFF2A1710),
    surfaceContainerHigh = Color(0xFF3A2117),
)

private val RoseLightScheme = lightColorScheme(
    primary = Color(0xFFBE185D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E8),
    onPrimaryContainer = Color(0xFF500724),
    secondary = Color(0xFF8A3B5F),
    background = Color(0xFFFFF7FA),
    surface = Color(0xFFFFF7FA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFFFE7F0),
)

private val RoseDarkScheme = darkColorScheme(
    primary = Color(0xFFFF9BC2),
    onPrimary = Color(0xFF5A0B2A),
    primaryContainer = Color(0xFF9D174D),
    onPrimaryContainer = Color(0xFFFFD8E8),
    secondary = Color(0xFFE7A9C2),
    background = Color(0xFF170B11),
    surface = Color(0xFF170B11),
    surfaceContainer = Color(0xFF28111D),
    surfaceContainerHigh = Color(0xFF371A28),
)

@Composable
fun IGNGMcStatusTheme(
    settings: AppSettings,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        settings.useSystemAccent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        settings.accent == ThemeAccent.BLUE -> if (darkTheme) BlueDarkScheme else BlueLightScheme
        settings.accent == ThemeAccent.ORANGE -> if (darkTheme) OrangeDarkScheme else OrangeLightScheme
        settings.accent == ThemeAccent.ROSE -> if (darkTheme) RoseDarkScheme else RoseLightScheme
        else -> if (darkTheme) TealDarkScheme else TealLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
