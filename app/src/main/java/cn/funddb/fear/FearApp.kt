package cn.funddb.fear

import android.app.Application
import cn.funddb.fear.data.worker.scheduleHourly

class FearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleHourly(this)
    }
}
