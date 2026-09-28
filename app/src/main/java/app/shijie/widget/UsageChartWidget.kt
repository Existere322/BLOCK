package app.shijie.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.shijie.MainActivity
import app.shijie.domain.UsageChartLayout
import app.shijie.domain.formatDurationMinutes

const val EXTRA_OPEN_STATS = "app.shijie.extra.OPEN_STATS"

class UsageChartWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetChartLoader.load(context)
        provideContent {
            UsageChartWidgetContent(snapshot)
        }
    }
}

class UsageChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = UsageChartWidget()
}

fun requestPinUsageChart(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    val provider = ComponentName(context, UsageChartWidgetReceiver::class.java)
    return manager.requestPinAppWidget(provider, null, null)
}

suspend fun refreshUsageChartWidgets(context: Context) {
    runCatching { UsageChartWidget().updateAll(context) }
}

@androidx.compose.runtime.Composable
private fun UsageChartWidgetContent(snapshot: WidgetChartSnapshot) {
    val context = LocalContext.current
    val size = LocalSize.current
    val palette = snapshot.palette
    val fontScale = context.resources.configuration.fontScale
    val widgetHeight = size.height.value.coerceAtLeast(1f)
    val legendRows = UsageChartLayout.visibleLegendRows(snapshot.legend.size, widgetHeight, fontScale)
    val chartDp = UsageChartLayout.chartHeightDp(widgetHeight, legendRows, fontScale)
    val legendRowDp = UsageChartLayout.legendRowDp(fontScale)
    val density = context.resources.displayMetrics.density
    val imageWidthDp = (
        size.width.value - (UsageChartLayout.OUTER_PADDING_DP + UsageChartLayout.CARD_PADDING_DP) * 2f
        ).coerceAtLeast(48f)
    val widthPx = (imageWidthDp * density).toInt().coerceAtLeast(1)
    val heightPx = (chartDp * density).toInt().coerceAtLeast(1)
    val bitmap = if (snapshot.emptyMessage == null && snapshot.columns.isNotEmpty()) {
        remember(snapshot, widthPx, heightPx) {
            UsageChartBitmap.render(context, snapshot.columns, snapshot.hourly, palette, widthPx, heightPx)
        }
    } else {
        null
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(palette.page)))
            .appWidgetBackground()
            .padding(UsageChartLayout.OUTER_PADDING_DP.dp),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color(palette.white)))
                .cornerRadius(10.dp)
                .padding(UsageChartLayout.CARD_PADDING_DP.dp),
        ) {
            Text(
                "合计 ${snapshot.totalLabel}",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color(palette.ink)),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(UsageChartLayout.SECTION_GAP_DP.dp))
            val message = snapshot.emptyMessage
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "使用时长柱状图",
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(chartDp.dp)
                        .clickable(actionStartActivity(openStatsIntent(context))),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                Text(
                    message ?: WIDGET_EMPTY_MESSAGE,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(chartDp.dp)
                        .clickable(actionStartActivity(openStatsIntent(context))),
                    style = TextStyle(
                        color = ColorProvider(Color(palette.muted)),
                        fontSize = 14.sp,
                    ),
                )
            }
            if (legendRows > 0) {
                Spacer(GlanceModifier.height(UsageChartLayout.SECTION_GAP_DP.dp))
                Legend(snapshot.legend.take(legendRows * 2), palette, legendRowDp)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Legend(
    items: List<app.shijie.domain.ChartSlice>,
    palette: app.shijie.theme.Palette,
    legendRowDp: Float,
) {
    Column {
        items.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Spacer(GlanceModifier.height(UsageChartLayout.LEGEND_ROW_GAP_DP.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(legendRowDp.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { slice ->
                    Row(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(8.dp)
                                .background(ColorProvider(Color(slice.colorArgb)))
                                .cornerRadius(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Column {
                            Text(
                                slice.name,
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(Color(palette.inkSoft)),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                formatDurationMinutes(slice.millis),
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(Color(palette.ink)),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

private fun openStatsIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_OPEN_STATS, true)
    }
}
