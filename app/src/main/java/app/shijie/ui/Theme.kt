package app.shijie.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.shijie.R

val CafePage = Color(0xFFF9F9F9)
val CafeWhite = Color(0xFFFFFFFF)
val CafeInk = Color(0xFF242424)
val CafeInkSoft = Color(0xFF313131)
val CafeMuted = Color(0xFF909090)
val CafeMuted2 = Color(0xFFA2A2A2)
val CafeAccent = Color(0xFFC67C4E)
val CafeCream = Color(0xFFF9F2ED)
val CafeTan = Color(0xFFEDD6C8)
val CafeSearch = Color(0xFF2A2A2A)
val CafeTrack = Color(0xFFEDEDED)
val CafeLine = Color(0xFFE3E3E3)
val CafeGreen = Color(0xFF36C07E)

private val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
)

private val CafeColors = lightColorScheme(
    primary = CafeAccent,
    onPrimary = CafeWhite,
    primaryContainer = CafeCream,
    onPrimaryContainer = CafeInk,
    secondary = CafeAccent,
    onSecondary = CafeWhite,
    secondaryContainer = CafeTan,
    onSecondaryContainer = CafeInk,
    tertiary = CafeGreen,
    onTertiary = CafeWhite,
    background = CafePage,
    onBackground = CafeInk,
    surface = CafeWhite,
    onSurface = CafeInk,
    surfaceVariant = CafeTrack,
    onSurfaceVariant = CafeMuted,
    surfaceContainer = CafeWhite,
    surfaceContainerHigh = CafeCream,
    surfaceContainerHighest = CafeTrack,
    outline = CafeLine,
    outlineVariant = CafeLine,
    error = Color(0xFFB3261E),
    onError = CafeWhite,
)

private val CafeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Sora,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sora,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sora,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sora,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontFamily = Sora,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = Sora,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sora,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sora,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = Sora,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = Sora,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = Sora,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontFamily = Sora,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
)

private val CafeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun ShijieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CafeColors,
        typography = CafeTypography,
        shapes = CafeShapes,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CafePage),
        ) {
            content()
        }
    }
}

@Composable
fun CafeSystemBars(darkIconsOnStatus: Boolean, darkIconsOnNavigation: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIconsOnStatus
            isAppearanceLightNavigationBars = darkIconsOnNavigation
        }
    }
}

val GroupPalette: List<Int> = listOf(
    0xFFC67C4E.toInt(),
    0xFF527A73.toInt(),
    0xFF78885A.toInt(),
    0xFFC19A56.toInt(),
    0xFF81708E.toInt(),
    0xFFCA786C.toInt(),
    0xFF5E748D.toInt(),
    0xFF806653.toInt(),
)

val TileShape = RoundedCornerShape(12.dp)
val CardShape = RoundedCornerShape(16.dp)
