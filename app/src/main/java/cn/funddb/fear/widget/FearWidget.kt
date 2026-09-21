package cn.funddb.fear.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cn.funddb.fear.data.model.Emotion
import cn.funddb.fear.data.repo.FearRepository
import cn.funddb.fear.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FearWidget : GlanceAppWidget() {
    // 默认 SingleColumn，不覆写 sizeMode 以保证最大兼容。

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = FearRepository(context)
        val latest = runCatching { repo.latest() }.getOrNull()
        val value = latest?.point?.fear
        val emotion = latest?.emotion ?: Emotion.of(value)
        val time = latest?.let {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.fetchedAtMillis))
        } ?: "--"
        val deriv = latest?.derivative
        val derivText = when {
            deriv == null -> ""
            deriv > 0 -> "▲+${fmt(deriv)}"
            deriv < 0 -> "▼${fmt(deriv)}"
            else -> "持平"
        }

        provideContent {
            GlanceTheme {
                Box(
                    modifier = androidx.glance.GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF141A20)))
                        .cornerRadius(18.dp)
                        .padding(10.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Column(
                        modifier = androidx.glance.GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = androidx.glance.GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (value == null || value.isNaN()) "--" else fmt0(value),
                                style = TextStyle(
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(emotionColor(emotion)),
                                ),
                            )
                            Spacer(modifier = androidx.glance.GlanceModifier.defaultWeight())
                            Text(
                                text = emotion.label + (if (derivText.isNotEmpty()) " $derivText" else ""),
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(emotionColor(emotion)),
                                ),
                            )
                        }
                        Spacer(modifier = androidx.glance.GlanceModifier.height(1.dp))
                        Text(
                            text = "恐惧贪婪 $time",
                            style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color(0xFF6B7684))),
                        )
                    }
                }
            }
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun fmt0(v: Double): String = String.format(Locale.US, "%.0f", v)

    private fun emotionColor(e: Emotion): Color = when (e) {
        Emotion.EXTREME_FEAR -> Color(0xFF0B6ECE)
        Emotion.FEAR -> Color(0xFF1890FF)
        Emotion.NEUTRAL -> Color(0xFF9AA4B2)
        Emotion.GREED -> Color(0xFFFF7A45)
        Emotion.EXTREME_GREED -> Color(0xFFF5222D)
        Emotion.UNKNOWN -> Color(0xFF9AA4B2)
    }
}

class FearWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FearWidget()
}
