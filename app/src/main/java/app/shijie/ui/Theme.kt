package app.shijie.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1C2428)
private val BlueGray = Color(0xFF2C4552)
private val Amber = Color(0xFFA56B12)
private val Paper = Color(0xFFF6F3EC)
private val CardLight = Color(0xFFFFFCF7)

private val Night = Color(0xFF14181C)
private val NightCard = Color(0xFF222A31)
private val NightRaised = Color(0xFF2C3640)
private val Mist = Color(0xFFF4F7F8)
private val MistSoft = Color(0xFFD5DEE3)
private val AmberGlow = Color(0xFFF0C56A)

private val LightColors = lightColorScheme(
    primary = BlueGray,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E4EC),
    onPrimaryContainer = Color(0xFF102028),
    secondary = Amber,
    onSecondary = Color(0xFF2A1C06),
    secondaryContainer = Color(0xFFF8E6C4),
    onSecondaryContainer = Color(0xFF3A2808),
    tertiary = Color(0xFF5C6B62),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = CardLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E1D6),
    onSurfaceVariant = Color(0xFF3C4A52),
    surfaceContainer = Color(0xFFEFEAE1),
    surfaceContainerHigh = Color(0xFFE7E1D6),
    surfaceContainerHighest = Color(0xFFDDD6C8),
    outline = Color(0xFF7C8B93),
    outlineVariant = Color(0xFFD5D0C6),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD7E4EC),
    onPrimary = Color(0xFF16303A),
    primaryContainer = Color(0xFF3A5160),
    onPrimaryContainer = Color(0xFFE8F2F6),
    secondary = AmberGlow,
    onSecondary = Color(0xFF3A2808),
    secondaryContainer = Color(0xFF5C4316),
    onSecondaryContainer = Color(0xFFFFE7B8),
    tertiary = Color(0xFFC9D5CE),
    onTertiary = Color(0xFF24302A),
    background = Night,
    onBackground = Mist,
    surface = NightCard,
    onSurface = Mist,
    surfaceVariant = NightRaised,
    onSurfaceVariant = MistSoft,
    surfaceContainer = Color(0xFF1C242B),
    surfaceContainerHigh = NightCard,
    surfaceContainerHighest = NightRaised,
    outline = Color(0xFF9AABB4),
    outlineVariant = Color(0xFF3A4650),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val ShijieTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val ShijieShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ShijieTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = ShijieTypography,
        shapes = ShijieShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
            contentColor = scheme.onBackground,
            content = content,
        )
    }
}

val GroupPalette: List<Int> = listOf(
    0xFF2C4552.toInt(),
    0xFF5E7381.toInt(),
    0xFF2F6F6A.toInt(),
    0xFFC48A2A.toInt(),
    0xFF8D6E63.toInt(),
    0xFF3E6B8A.toInt(),
    0xFF6B5B95.toInt(),
    0xFFB06A3B.toInt(),
)
