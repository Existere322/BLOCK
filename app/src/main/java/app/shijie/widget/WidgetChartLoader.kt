package app.shijie.widget

import android.content.Context
import app.shijie.ShijieApp
import app.shijie.domain.ChartColumn
import app.shijie.domain.ChartSlice
import app.shijie.domain.UsageCategories
import app.shijie.domain.formatDurationMinutes
import app.shijie.system.Permissions
import app.shijie.theme.AppPalettes
import app.shijie.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val WIDGET_EMPTY_MESSAGE = "还没有可显示的使用记录。允许使用情况访问后，这里会按系统统计校正。"

internal data class WidgetChartSnapshot(
    val hourly: Boolean,
    val totalLabel: String,
    val columns: List<ChartColumn>,
    val legend: List<ChartSlice>,
    val palette: Palette,
    val emptyMessage: String?,
)

/** Same buckets and grouping as the stats page, without the per-app ranking. */
internal object WidgetChartLoader {
    suspend fun load(context: Context): WidgetChartSnapshot {
        val graph = (context.applicationContext as ShijieApp).graph
        val palette = withContext(Dispatchers.IO) { AppPalettes.of(graph.meta.theme()) }
        if (!Permissions.usageGranted(context)) {
            return empty(palette)
        }
        return try {
            withContext(Dispatchers.IO) {
                val open = graph.engine.currentSession()
                val stored = graph.groups.snapshot()
                val membership = HashMap<String, MutableList<Pair<String, Int>>>()
                stored.forEach { group ->
                    group.packages.forEach { pkg ->
                        val list = membership.getOrPut(pkg) { mutableListOf() }
                        if (list.none { it.first == group.group.name }) {
                            list += group.group.name to group.group.colorArgb
                        }
                    }
                }
                val order = stored.map { it.group.name to it.group.colorArgb }
                val hours = graph.usage.hourlyByPackage(open)
                val labels = List(hours.size) { hour -> "%02d".format(hour) }
                val (columns, legend) = UsageCategories.stack(labels, hours, membership, order)
                val total = columns.sumOf { it.uniqueMillis }
                if (columns.isEmpty() || total <= 0L) {
                    empty(palette)
                } else {
                    WidgetChartSnapshot(
                        hourly = true,
                        totalLabel = formatDurationMinutes(total),
                        columns = columns,
                        legend = legend,
                        palette = palette,
                        emptyMessage = null,
                    )
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            empty(palette)
        }
    }

    private fun empty(palette: Palette) = WidgetChartSnapshot(
        hourly = true,
        totalLabel = formatDurationMinutes(0),
        columns = emptyList(),
        legend = emptyList(),
        palette = palette,
        emptyMessage = WIDGET_EMPTY_MESSAGE,
    )
}
