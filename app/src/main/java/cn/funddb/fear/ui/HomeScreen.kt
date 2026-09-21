package cn.funddb.fear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import cn.funddb.fear.data.model.Emotion
import cn.funddb.fear.data.model.FearLatest
import cn.funddb.fear.data.model.FearPoint
import cn.funddb.fear.data.model.Symbol
import cn.funddb.fear.data.repo.FearRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Application
import androidx.lifecycle.AndroidViewModel

enum class Range(val label: String, val days: Int) {
    M3("近3月", 66),
    M6("半年", 132),
    Y1("1年", 252),
    ALL("全部", Int.MAX_VALUE),
}

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val latest: FearLatest? = null,
    val history: List<FearPoint> = emptyList(),
    val symbol: Symbol = Symbol.SHANGHAI,
    val range: Range = Range.Y1,
    val error: String? = null,
)

class FearViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FearRepository(app)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(loading = s.history.isEmpty(), error = null)
            try {
                val latest = repo.latest(s.symbol)
                val history = repo.history(s.symbol)
                _state.value = s.copy(loading = false, refreshing = false, latest = latest, history = history)
            } catch (e: Exception) {
                _state.value = s.copy(loading = false, refreshing = false, error = "加载失败：${e.message}")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
            try {
                repo.refreshAll()
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(refreshing = false, error = "刷新失败：${e.message}")
            }
        }
    }

    fun selectSymbol(symbol: Symbol) {
        if (symbol == _state.value.symbol) return
        _state.value = HomeUiState(symbol = symbol, range = _state.value.range)
        load()
    }

    fun selectRange(range: Range) {
        _state.value = _state.value.copy(range = range)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: FearViewModel) {
    val state by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("恐惧贪婪指数") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
            if (state.refreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
                // 标的切换
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Symbol.values().forEach { sym ->
                        FilterChip(
                            selected = state.symbol == sym,
                            onClick = { vm.selectSymbol(sym) },
                            label = { Text(sym.label) },
                        )
                    }
                }
                if (state.symbol == Symbol.HS300) {
                    Text(
                        "沪深300为官方独立口径（直连）；降级时暂与上证同源",
                        fontSize = 11.sp,
                        color = Color.Gray,
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (state.loading) {
                    CircularProgressIndicator()
                } else {
                    LatestCard(state.latest)
                    Spacer(Modifier.height(12.dp))
                    HistoryCard(state) { vm.selectRange(it) }
                }

                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Button(onClick = { vm.load() }) { Text("重试") }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "数据每日更新（交易日）；桌面组件约每小时刷新一次。\n数据仅供参考，不构成投资建议。",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

@Composable
private fun LatestCard(latest: FearLatest?) {
    val v = latest?.point?.fear
    val emotion = Emotion.of(v)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2230)),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Gauge(value = v)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (v == null) "--" else String.format(Locale.US, "%.1f", v),
                fontSize = 44.sp,
                color = emotionColor(emotion),
            )
            AssistChip(onClick = {}, label = { Text(emotion.label) })
            Spacer(Modifier.height(4.dp))
            val d = latest?.derivative
            Text(
                text = "较上个交易日 " + when {
                    d == null -> "—"
                    d > 0 -> "▲ +${String.format(Locale.US, "%.1f", d)}"
                    d < 0 -> "▼ ${String.format(Locale.US, "%.1f", d)}"
                    else -> "持平"
                },
                fontSize = 13.sp,
                color = Color(0xFF9AA4B2),
            )
            latest?.let {
                Text(
                    text = "${it.point.date} · ${it.source.label}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
                Text(
                    text = "更新 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(it.fetchedAtMillis)),
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(state: HomeUiState, onRange: (Range) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2230)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("历史走势", fontSize = 15.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Range.values().forEach { r ->
                    FilterChip(
                        selected = state.range == r,
                        onClick = { onRange(r) },
                        label = { Text(r.label, fontSize = 12.sp) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val points = state.history
                .filter { it.fear != null }
                .takeLast(if (state.range.days == Int.MAX_VALUE) Int.MAX_VALUE else state.range.days)
            if (points.size >= 2) {
                FearChart(points = points, modifier = Modifier.fillMaxWidth().height(220.dp))
                Text(
                    "${points.first().date} ~ ${points.last().date}（${points.size}个交易日）",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            } else {
                Text("暂无历史数据", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}

/** 半圆仪表盘。 */
@Composable
private fun Gauge(value: Double?) {
    Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width
        val cx = w / 2f
        val cy = size.height * 0.92f
        val r = minOf(w / 2f * 0.92f, size.height * 0.88f)
        // 底弧
        drawArc(
            color = Color(0xFF2A3442),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = 22f, cap = StrokeCap.Round),
        )
        if (value != null) {
            val sweep = (value.coerceIn(0.0, 100.0) / 100 * 180).toFloat()
            drawArc(
                color = emotionColor(Emotion.of(value)),
                startAngle = 180f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = 22f, cap = StrokeCap.Round),
            )
        }
    }
}

/** 恐惧贪婪历史折线 + 25/45/55/75 情绪分界线。 */
@Composable
private fun FearChart(points: List<FearPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val vals = points.map { it.fear ?: 50.0 }
        val min = 0f
        val max = 100f
        val left = 8f
        val right = size.width - 8f
        val top = 8f
        val bottom = size.height - 20f
        fun x(i: Int) = left + (right - left) * i / (vals.size - 1)
        fun y(v: Double) = (bottom - (top + (bottom - top) * ((v - min) / (max - min)))).toFloat()

        // 分界线
        listOf(25.0 to Color(0x553FA7F5), 45.0 to Color(0x339AA4B2), 55.0 to Color(0x339AA4B2), 75.0 to Color(0x55FF5A5A))
            .forEach { (t, c) ->
                drawLine(c, Offset(left, y(t).toFloat()), Offset(right, y(t).toFloat()), strokeWidth = 1.5f)
            }

        // 折线
        val path = Path()
        vals.forEachIndexed { i, v ->
            val px = x(i)
            val py = y(v).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color(0xFF3FA7F5), style = Stroke(width = 4f, cap = StrokeCap.Round))

        // 最新点
        drawCircle(Color.White, radius = 7f, center = Offset(x(vals.size - 1), y(vals.last()).toFloat()))
        drawCircle(Color(0xFF3FA7F5), radius = 4.5f, center = Offset(x(vals.size - 1), y(vals.last()).toFloat()))
    }
}

private fun emotionColor(e: Emotion): Color = when (e) {
    Emotion.EXTREME_FEAR -> Color(0xFF3FA7F5)
    Emotion.FEAR -> Color(0xFF5AC8FA)
    Emotion.NEUTRAL -> Color(0xFF9AA4B2)
    Emotion.GREED -> Color(0xFFFF9F43)
    Emotion.EXTREME_GREED -> Color(0xFFFF5A5A)
    Emotion.UNKNOWN -> Color(0xFF9AA4B2)
}
