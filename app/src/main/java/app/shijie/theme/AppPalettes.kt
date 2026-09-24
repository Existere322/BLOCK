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

    /**
     * Nam Design Coffee Shop color guide (Figma styleguide → Colors):
     * 01 #C67C4E, 02 #EDD6C8, 03 #313131, 04 #E3E3E3, 05 #F9F2ED.
     * Cards stay white, matching the swatch plates in that guide.
     * [muted] is 03 laid over 05 so captions stay readable.
     * [search] is 03 darkened so chips and buttons still show on a 03 header.
     */
    val coffee = Palette(
        page = 0xFFF9F2ED.toInt(),
        white = 0xFFFFFFFF.toInt(),
        ink = 0xFF313131.toInt(),
        inkSoft = 0xFF313131.toInt(),
        muted = 0xFF6B6764.toInt(),
        muted2 = 0xFFE3E3E3.toInt(),
        accent = 0xFFC67C4E.toInt(),
        cream = 0xFFEDD6C8.toInt(),
        tan = 0xFFEDD6C8.toInt(),
        search = 0xFF242424.toInt(),
        track = 0xFFE3E3E3.toInt(),
        line = 0xFFE3E3E3.toInt(),
        green = 0xFFC67C4E.toInt(),
        groups = listOf(
            0xFFC67C4E.toInt(),
            0xFF313131.toInt(),
            0xFF8F5432.toInt(),
            0xFFD4A07A.toInt(),
            0xFF5C4033.toInt(),
            0xFFA67C52.toInt(),
            0xFF6E4B3A.toInt(),
            0xFFEDD6C8.toInt(),
        ),
    )

    fun of(theme: AppTheme): Palette = if (theme == AppTheme.PREVIOUS) coffee else ocean
}
