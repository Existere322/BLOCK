package app.shijie.theme

import app.shijie.domain.AppTheme

data class Palette(
    val page: Int,
    val white: Int,
    val ink: Int,
    val inkSoft: Int,
    val muted: Int,
    val muted2: Int,
    val accent: Int,
    val cream: Int,
    val tan: Int,
    val search: Int,
    val track: Int,
    val line: Int,
    val green: Int,
    val groups: List<Int>,
)

object AppPalettes {
    val ocean = Palette(
        page = 0xFFF6F8FC.toInt(),
        white = 0xFFFFFFFF.toInt(),
        ink = 0xFF202833.toInt(),
        inkSoft = 0xFF2F3A4C.toInt(),
        muted = 0xFF75808F.toInt(),
        muted2 = 0xFFAFBBCB.toInt(),
        accent = 0xFF1468E8.toInt(),
        cream = 0xFFEAF2FF.toInt(),
        tan = 0xFFD6E6FF.toInt(),
        search = 0xFF263448.toInt(),
        track = 0xFFF0F2F6.toInt(),
        line = 0xFFE4E9F1.toInt(),
        green = 0xFF2B9E88.toInt(),
        groups = listOf(
            0xFF1468E8.toInt(),
            0xFF9137D7.toInt(),
            0xFFF18A27.toInt(),
            0xFF249A87.toInt(),
            0xFF5E73D7.toInt(),
            0xFFE36D79.toInt(),
            0xFF5C829D.toInt(),
            0xFF9671A6.toInt(),
        ),
    )

    val coffee = Palette(
        page = 0xFFF6F3EC.toInt(),
        white = 0xFFFFFCF7.toInt(),
        ink = 0xFF1C2428.toInt(),
        inkSoft = 0xFF12110F.toInt(),
        muted = 0xFF3C4A52.toInt(),
        muted2 = 0xFFD5DEE3.toInt(),
        accent = 0xFFA56B12.toInt(),
        cream = 0xFFF8E6C4.toInt(),
        tan = 0xFFE7E1D6.toInt(),
        search = 0xFF1A2830.toInt(),
        track = 0xFFE7E1D6.toInt(),
        line = 0xFFD5D0C6.toInt(),
        green = 0xFF2F6F6A.toInt(),
        groups = listOf(
            0xFF2C4552.toInt(),
            0xFF5E7381.toInt(),
            0xFF2F6F6A.toInt(),
            0xFFC48A2A.toInt(),
            0xFF8D6E63.toInt(),
            0xFF3E6B8A.toInt(),
            0xFF6B5B95.toInt(),
            0xFFB06A3B.toInt(),
        ),
    )

    fun of(theme: AppTheme): Palette = if (theme == AppTheme.PREVIOUS) coffee else ocean
}
