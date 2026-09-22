package app.shijie.domain

fun formatMinute(minute: Int): String {
    val normalized = if (minute == 1440) 0 else minute
    return "%02d:%02d".format(normalized.coerceIn(0, 1439) / 60, normalized.coerceIn(0, 1439) % 60)
}

fun formatWindow(window: BlockWindow): String {
    val start = formatMinute(window.startMinute)
    val end = if (window.endMinute == 1440) "24:00" else formatMinute(window.endMinute)
    return if (window.startMinute < window.endMinute) {
        "$start–$end"
    } else {
        "$start–次日$end"
    }
}

fun formatDuration(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}小时${minutes}分"
        hours > 0 -> "${hours}小时"
        minutes > 0 && seconds > 0 -> "${minutes}分${seconds}秒"
        minutes > 0 -> "${minutes}分"
        else -> "${seconds}秒"
    }
}

fun iconGlyph(iconKey: String): String = when (iconKey) {
    "book" -> "读"
    "work" -> "事"
    "game" -> "玩"
    "moon" -> "夜"
    "music" -> "乐"
    "run" -> "动"
    "coffee" -> "茶"
    "star" -> "星"
    else -> "组"
}

val GROUP_ICON_KEYS = listOf("book", "work", "game", "moon", "music", "run", "coffee", "star")
