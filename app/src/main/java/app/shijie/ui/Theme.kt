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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.shijie.domain.AppTheme
import app.shijie.theme.AppPalettes
import app.shijie.theme.Palette
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

private data class ShijiePalette(
    val page: Color,
    val white: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val muted2: Color,
    val accent: Color,
    val cream: Color,
    val tan: Color,
    val search: Color,
    val track: Color,
    val line: Color,
    val green: Color,
    val groups: List<Int>,
)

private fun Palette.asCompose() = ShijiePalette(
    page = Color(page),
    white = Color(white),
    ink = Color(ink),
    inkSoft = Color(inkSoft),
    muted = Color(muted),
    muted2 = Color(muted2),
    accent = Color(accent),
    cream = Color(cream),
    tan = Color(tan),
    search = Color(search),
    track = Color(track),
    line = Color(line),
    green = Color(green),
    groups = groups,
)

private val currentPalette = AppPalettes.ocean.asCompose()
private val previousPalette = AppPalettes.coffee.asCompose()

private var activePalette by mutableStateOf(currentPalette)

fun selectPalette(theme: AppTheme) {
    activePalette = if (theme == AppTheme.PREVIOUS) previousPalette else currentPalette
}

val CafePage: Color get() = activePalette.page
val CafeWhite: Color get() = activePalette.white
val CafeInk: Color get() = activePalette.ink
val CafeInkSoft: Color get() = activePalette.inkSoft
val CafeMuted: Color get() = activePalette.muted
val CafeMuted2: Color get() = activePalette.muted2
val CafeAccent: Color get() = activePalette.accent
val CafeCream: Color get() = activePalette.cream
val CafeTan: Color get() = activePalette.tan
val CafeSearch: Color get() = activePalette.search
val CafeTrack: Color get() = activePalette.track
val CafeLine: Color get() = activePalette.line
val CafeGreen: Color get() = activePalette.green

private val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
)

private fun paletteScheme(palette: ShijiePalette) = lightColorScheme(
    primary = palette.accent,
    onPrimary = palette.white,
    primaryContainer = palette.cream,
    onPrimaryContainer = palette.ink,
    secondary = palette.accent,
    onSecondary = palette.white,
    secondaryContainer = palette.tan,
    onSecondaryContainer = palette.ink,
    tertiary = palette.green,
    onTertiary = palette.white,
    background = palette.page,
    onBackground = palette.ink,
    surface = palette.white,
    onSurface = palette.ink,
    surfaceVariant = palette.track,
    onSurfaceVariant = palette.muted,
    surfaceContainer = palette.white,
    surfaceContainerHigh = palette.cream,
    surfaceContainerHighest = palette.track,
    outline = palette.line,
    outlineVariant = palette.line,
    error = Color(0xFFB3261E),
    onError = palette.white,
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
    val palette = activePalette
    MaterialTheme(
        colorScheme = paletteScheme(palette),
        typography = CafeTypography,
        shapes = CafeShapes,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.page),
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

val GroupPalette: List<Int> get() = activePalette.groups

val TileShape = RoundedCornerShape(12.dp)
val CardShape = RoundedCornerShape(16.dp)
