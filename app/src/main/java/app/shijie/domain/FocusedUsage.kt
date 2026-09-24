package app.shijie.domain

import java.time.Duration

/** Sums focused foreground spans. One package is never counted twice for the same moment. */
object FocusedUsage {
    fun totals(spans: List<ForegroundSpan>): Map<String, Long> {
        val totals = HashMap<String, Long>()
        spans.forEach { span ->
            if (span.packageName.isEmpty() || !span.end.isAfter(span.start)) return@forEach
            val millis = Duration.between(span.start, span.end).toMillis()
            if (millis <= 0L) return@forEach
            totals[span.packageName] = (totals[span.packageName] ?: 0L) + millis
        }
        return totals
    }
}
