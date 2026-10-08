package cn.funddb.fear

import android.app.Application
import cn.funddb.fear.data.worker.AlarmScheduler
import cn.funddb.fear.data.worker.scheduleHourly
import cn.funddb.fear.data.worker.triggerImmediateRefresh

class FearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleHourly(this)
        AlarmScheduler.scheduleNext(this)
        triggerImmediateRefresh(this)
    }
}
