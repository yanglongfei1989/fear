package cn.funddb.fear.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.funddb.fear.data.repo.FearRepository
import cn.funddb.fear.widget.FearWidget
import java.util.concurrent.TimeUnit

const val HOURLY_WORK_NAME = "fear-hourly-refresh"
const val IMMEDIATE_WORK_NAME = "fear-immediate-refresh"

/** 每小时拉取一次并刷新全部桌面组件。Doze 下允许 ±10min 漂移（系统行为）。 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            FearRepository(applicationContext).refresh()
            FearWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

/** 注册每小时周期任务。 */
fun scheduleHourly(context: Context) {
    val req = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        HOURLY_WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        req,
    )
}

/** 触发一次即刻后台刷新并更新组件。 */
fun triggerImmediateRefresh(context: Context) {
    val req = OneTimeWorkRequestBuilder<RefreshWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        IMMEDIATE_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        req,
    )
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleHourly(context)
            triggerImmediateRefresh(context)
        }
    }
}
