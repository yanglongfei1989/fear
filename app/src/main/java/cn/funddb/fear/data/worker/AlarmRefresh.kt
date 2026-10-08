package cn.funddb.fear.data.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 精确闹钟链：MIUI 会冻结 WorkManager 周期任务，但精确闹钟通常能准时唤醒。
 * 策略：每次触发后自续约下一次；与 WorkManager 双通道并存（刷新幂等，通知有去重）。
 */
object AlarmScheduler {
    const val INTERVAL_MS = 60 * 60 * 1000L
    private const val REQ_CODE = 1001
    const val PREF_NEXT_ALARM_AT = "next_alarm_at"

    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "cn.funddb.fear.action.HOURLY_ALARM"
        }
        return PendingIntent.getBroadcast(
            context,
            REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 续约下一次（整点对齐+1h，若已过期则顺延）。调用处：启动/开机/点组件/每次触发后。 */
    fun scheduleNext(context: Context) {
        if (!canSchedule(context)) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        var next = ((now / INTERVAL_MS) + 1) * INTERVAL_MS
        if (next - now < 5 * 60 * 1000L) next += INTERVAL_MS
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context))
        context.getSharedPreferences("fear_prefs", Context.MODE_PRIVATE)
            .edit().putLong(PREF_NEXT_ALARM_AT, next).apply()
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 先续约，保证链条不断；再执行刷新（失败靠下一次整点+WorkManager兜底）
                AlarmScheduler.scheduleNext(context)
                markAttempt(context)
                doRefreshWork(context)
            } finally {
                pending.finish()
            }
        }
    }
}
