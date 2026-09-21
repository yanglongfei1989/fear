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
import cn.funddb.fear.data.model.Symbol
import cn.funddb.fear.data.repo.FearRepository
import cn.funddb.fear.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FearWidget : GlanceAppWidget() {
    // 默认 SingleColumn，不覆写 sizeMode 以保证最大兼容。

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = FearRepository(context)
        val latest = runCatching { repo.latest(Symbol.SHANGHAI) }.getOrNull()
        val value = latest?.point?.fear
        val emotion = Emotion.of(value)
        val time = latest?.let {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.fetchedAtMillis))
        } ?: "--"
        val deriv = latest?.derivative
        val derivText = when {
            deriv == null -> ""
            deriv > 0 -> "▲ +${fmt(deriv)}"
            deriv < 0 -> "▼ ${fmt(deriv)}"
            else -> "— 0.0"
        }

        provideContent {
            GlanceTheme {
                Box(
                    modifier = androidx.glance.GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF141A20)))
                        .padding(12.dp)
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
                                text = if (value == null) "--" else fmt(value),
                                style = TextStyle(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(emotionColor(emotion)),
                                ),
                            )
                            Spacer(modifier = androidx.glance.GlanceModifier.defaultWeight())
                            Text(
                                text = emotion.label,
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(emotionColor(emotion)),
                                ),
                            )
                        }
                        Spacer(modifier = androidx.glance.GlanceModifier.height(2.dp))
                        Text(
                            text = "恐惧贪婪指数 $derivText",
                            style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF9AA4B2))),
                        )
                        Text(
                            text = "更新 $time · 点击查看曲线",
                            style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color(0xFF6B7684))),
                        )
                    }
                }
            }
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun emotionColor(e: Emotion): Color = when (e) {
        Emotion.EXTREME_FEAR -> Color(0xFF3FA7F5)
        Emotion.FEAR -> Color(0xFF5AC8FA)
        Emotion.NEUTRAL -> Color(0xFF9AA4B2)
        Emotion.GREED -> Color(0xFFFF9F43)
        Emotion.EXTREME_GREED -> Color(0xFFFF5A5A)
        Emotion.UNKNOWN -> Color(0xFF9AA4B2)
    }
}

class FearWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FearWidget()
}
