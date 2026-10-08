package cn.funddb.fear.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.funddb.fear.data.worker.AlarmScheduler
import cn.funddb.fear.data.worker.triggerImmediateRefresh

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 点桌面组件进入（或冷启动）都顺手触发一次后台刷新，并续约闹钟链
        triggerImmediateRefresh(this)
        AlarmScheduler.scheduleNext(this)
        setContent {
            val vm: FearViewModel = viewModel()
            val state by vm.state.collectAsState()
            MaterialTheme(
                colorScheme = if (state.isDark) {
                    darkColorScheme(
                        primary = Color(0xFF3FA7F5),
                        surface = Color(0xFF101418),
                        background = Color(0xFF101418),
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF1890FF),
                        surface = Color(0xFFF4F6F9),
                        background = Color(0xFFF4F6F9),
                    )
                },
            ) {
                HomeScreen(vm = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask 复用实例时（如点桌面组件），同样顺手刷新一次
        triggerImmediateRefresh(this)
        AlarmScheduler.scheduleNext(this)
    }
}
