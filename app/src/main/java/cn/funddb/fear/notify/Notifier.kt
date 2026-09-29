package cn.funddb.fear.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.funddb.fear.R
import cn.funddb.fear.data.model.FearLatest
import cn.funddb.fear.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 指数提醒通知：阈值穿越告警 + 每日交易日早报。
 * 阈值默认 90/10（与网页订阅一致），存 fear_prefs，可在设置页修改。
 */
object Notifier {

    const val CHANNEL_ID = "fear_alerts"
    private const val PREFS = "fear_prefs"
    private const val KEY_HIGH = "th_high"
    private const val KEY_LOW = "th_low"
    private const val KEY_SIDE = "alert_side"
    private const val KEY_MORNING_DATE = "morning_date"

    const val DEFAULT_HIGH = 90f
    const val DEFAULT_LOW = 10f

    fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun highThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_HIGH, DEFAULT_HIGH)

    fun lowThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_LOW, DEFAULT_LOW)

    fun saveThresholds(context: Context, high: Float, low: Float) {
        prefs(context).edit().putFloat(KEY_HIGH, high).putFloat(KEY_LOW, low).apply()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun channel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "指数提醒", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun push(context: Context, id: Int, title: String, text: String) {
        if (!hasNotificationPermission(context)) return
        channel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fear)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    /** 阈值穿越告警：只在“进入”新区间的第一次推，区间内不再重复。 */
    fun evaluateAlerts(context: Context, latest: FearLatest?) {
        val v = latest?.point?.fear ?: return
        if (v.isNaN()) return
        val p = prefs(context)
        val high = p.getFloat(KEY_HIGH, DEFAULT_HIGH)
        val low = p.getFloat(KEY_LOW, DEFAULT_LOW)
        val side = p.getString(KEY_SIDE, "none")
        val label = latest.emotion.label
        when {
            v >= high && side != "high" -> {
                push(context, 1001, "恐惧贪婪进入极度贪婪区", "当前 ${fmt(v)} · $label，注意过热风险（仅供参考）")
                p.edit().putString(KEY_SIDE, "high").apply()
            }
            v <= low && side != "low" -> {
                push(context, 1002, "恐惧贪婪进入极度恐惧区", "当前 ${fmt(v)} · $label，市场情绪极度悲观（仅供参考）")
                p.edit().putString(KEY_SIDE, "low").apply()
            }
            v < high && v > low && side != "none" -> {
                p.edit().putString(KEY_SIDE, "none").apply()
            }
        }
    }

    /** 每日早报：交易日 9 点后首次调度推一次（用日期标记防重复）。 */
    fun evaluateMorning(context: Context, latest: FearLatest?) {
        val v = latest?.point?.fear ?: return
        if (v.isNaN()) return
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return
        if (cal.get(Calendar.HOUR_OF_DAY) < 9) return
        val p = prefs(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (p.getString(KEY_MORNING_DATE, "") == today) return
        val deriv = latest.derivative
        val dText = when {
            deriv == null -> ""
            deriv > 0 -> "，较昨日 +${fmt(deriv)}"
            deriv < 0 -> "，较昨日 ${fmt(deriv)}"
            else -> "，与昨日持平"
        }
        val dateStr = latest.currentTime ?: latest.point.date
        push(
            context, 1003, "今日恐惧贪婪 ${fmt0(v)} · ${latest.emotion.label}",
            "数据日期 $dateStr$dText",
        )
        p.edit().putString(KEY_MORNING_DATE, today).apply()
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun fmt0(v: Double) = String.format(Locale.US, "%.0f", v)
}
