package cn.funddb.fear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private val vm: FearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
}
