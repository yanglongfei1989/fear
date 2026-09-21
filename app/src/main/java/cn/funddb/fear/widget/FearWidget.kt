package cn.funddb.fear.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import cn.funddb.fear.data.worker.triggerImmediateRefresh

class FearWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FearWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 当用户首次添加桌面组件时，立即触发一次后台拉取并渲染
        triggerImmediateRefresh(context)
    }
}
