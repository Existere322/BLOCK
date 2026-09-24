package app.shijie.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 24px line icons at the same weight as the JavaGem screens. Tint comes from Icon(). */
object CafeIcons {
    val Home: ImageVector = stroke(
        "Home",
        "M4.5,10.8 L12,4.2 L19.5,10.8 V19.5 H15 V13.5 H9 V19.5 H4.5 Z",
    )
    val HomeFilled: ImageVector = fill(
        "HomeFilled",
        "M4.2,10.8 L12,4 L19.8,10.8 V20.2 H4.2 Z M9.2,20.2 V14 H14.8 V20.2 Z",
        evenOdd = true,
    )
    val Groups: ImageVector = stroke(
        "Groups",
        "M4.5,4.5 H9.8 V9.8 H4.5 Z M14.2,4.5 H19.5 V9.8 H14.2 Z M4.5,14.2 H9.8 V19.5 H4.5 Z M14.2,14.2 H19.5 V19.5 H14.2 Z",
    )
    val Stats: ImageVector = stroke(
        "Stats",
        "M6,19.2 V11 M12,19.2 V5.5 M18,19.2 V13.2 M4,19.2 H20",
    )
    val Settings: ImageVector = stroke(
        "Settings",
        "M12,8.3 A3.7,3.7 0 1 1 11.99,8.3 M12,3 V5.6 M12,18.4 V21 M3,12 H5.6 M18.4,12 H21 M5.7,5.7 L7.6,7.6 M16.4,16.4 L18.3,18.3 M18.3,5.7 L16.4,7.6 M7.6,16.4 L5.7,18.3",
    )
    val Back: ImageVector = stroke("Back", "M14.5,5 L8,12 L14.5,19")
    val Chevron: ImageVector = stroke("Chevron", "M9.5,5 L16,12 L9.5,19")
    val Plus: ImageVector = stroke("Plus", "M12,5 V19 M5,12 H19")
    val Go: ImageVector = stroke("Go", "M5,12 H19 M13,6 L19,12 L13,18")

    private fun stroke(name: String, path: String): ImageVector = icon(name, path, filled = false, evenOdd = false)

    private fun fill(name: String, path: String, evenOdd: Boolean): ImageVector = icon(name, path, filled = true, evenOdd = evenOdd)

    private fun icon(name: String, path: String, filled: Boolean, evenOdd: Boolean): ImageVector {
        val nodes = PathParser().parsePathString(path).toNodes()
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = nodes,
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = if (filled) 0f else 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            )
        }.build()
    }
}
