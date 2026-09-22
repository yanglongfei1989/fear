package cn.funddb.fear.ui

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.funddb.fear.data.model.Emotion
import cn.funddb.fear.data.model.FearLatest
import cn.funddb.fear.data.model.FearPoint
import cn.funddb.fear.data.model.PastRing
import cn.funddb.fear.data.repo.FearRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Bg = Color(0xFF0E1116)
private val CardBg = Color(0xFF161C24)
private val Track = Color(0xFF232B36)
private val Muted = Color(0xFF9AA4B2)
private val FearBlue = Color(0xFF1890FF)
private val GreedRed = Color(0xFFF5222D)
private val MidPurple = Color(0xFF7B5CFF)

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
    val rings: List<PastRing> = emptyList(),
    val range: Range = Range.Y1,
    val error: String? = null,
    val batteryIgnored: Boolean = true,
    val workerStatus: String = "",
)

class FearViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FearRepository(app)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    val isXiaomi: Boolean
        get() = cn.funddb.fear.util.BatteryOptimizer.isXiaomi

    fun requestIgnoreBattery() {
        cn.funddb.fear.util.BatteryOptimizer.requestIgnoreBatteryOptimizations(getApplication())
    }

    fun openAutoStart() {
        cn.funddb.fear.util.BatteryOptimizer.openAutoStartSettings(getApplication())
    }

    private fun batteryIgnoredNow(): Boolean =
        cn.funddb.fear.util.BatteryOptimizer.isIgnoringBatteryOptimizations(getApplication())

    private fun workerStatusNow(): String {
        return try {
            val infos = androidx.work.WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWork(cn.funddb.fear.data.worker.HOURLY_WORK_NAME)
                .get(5, java.util.concurrent.TimeUnit.SECONDS)
            val state = infos.firstOrNull()?.state?.name ?: "未排期"
            val stateCn = when (state) {
                "ENQUEUED" -> "排期中"
                "RUNNING" -> "运行中"
                "SUCCEEDED" -> "已完成"
                "FAILED" -> "失败"
                "BLOCKED" -> "被阻塞"
                "CANCELLED" -> "已取消"
                else -> state
            }
            val prefs = getApplication<Application>()
                .getSharedPreferences("fear_prefs", android.content.Context.MODE_PRIVATE)
            val lastOk = prefs.getLong("last_worker_run", 0L)
            val lastTry = prefs.getLong("last_worker_attempt", 0L)
            val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            val okStr = if (lastOk == 0L) "从未成功" else fmt.format(java.util.Date(lastOk))
            val tryStr = if (lastTry == 0L) "从未调度" else fmt.format(java.util.Date(lastTry))
            "后台任务：$stateCn · 成功：$okStr · 调度：$tryStr"
        } catch (_: Exception) {
            "后台任务：查询失败"
        }
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(loading = s.history.isEmpty(), error = null)
            try {
                _state.value = s.copy(
                    loading = false,
                    refreshing = false,
                    latest = repo.latest(),
                    history = repo.history(),
                    rings = repo.rings(),
                    batteryIgnored = batteryIgnoredNow(),
                    workerStatus = workerStatusNow(),
                )
            } catch (e: Exception) {
                _state.value = s.copy(
                    loading = false,
                    refreshing = false,
                    error = "加载失败：${e.message}",
                    batteryIgnored = batteryIgnoredNow(),
                    workerStatus = workerStatusNow(),
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
            try {
                repo.refresh()
                cn.funddb.fear.widget.FearWidget().updateAll(getApplication())
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "刷新失败：${e.message}")
            }
            load()
        }
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
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("恐惧贪婪指数表", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "更新时间 " + (state.latest?.currentTime ?: state.latest?.point?.date ?: "--"),
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
            if (state.refreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.loading) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            } else {
                if (!state.batteryIgnored) {
                    BatteryWarningCard(vm)
                    Spacer(Modifier.height(12.dp))
                }
                GaugeCard(state.latest)
                Spacer(Modifier.height(12.dp))
                AttrRow(state.latest)
                Spacer(Modifier.height(12.dp))
                RingsCard(state.rings)
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
                "桌面组件约每小时刷新 · 数据每日更新（交易日）\n数据仅供参考，不构成投资建议",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )
            if (state.workerStatus.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    state.workerStatus,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 顶部仪表盘卡片：渐变弧 + 指针 + 中央数值。 */
@Composable
private fun GaugeCard(latest: FearLatest?) {
    val v = latest?.point?.fear
    val emotion = latest?.emotion ?: Emotion.of(v)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
    ) {
        Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                GaugeCanvas(value = v)
                Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (v == null || v.isNaN()) "--" else String.format(Locale.US, "%.0f", v),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("极度恐惧", fontSize = 12.sp, color = FearBlue)
                Text(emotion.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = emotionColor(emotion))
                Text("极度贪婪", fontSize = 12.sp, color = GreedRed)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GaugeCanvas(value: Double?) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.94f
        val r = minOf(size.width / 2f * 0.94f, size.height * 0.9f)
        val tl = Offset(cx - r, cy - r)
        val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        // 渐变弧：60 段插值 蓝->紫->红
        val segs = 60
        for (i in 0 until segs) {
            val f = i.toFloat() / segs
            val c = if (f < 0.5f) {
                lerp(FearBlue, MidPurple, f * 2)
            } else {
                lerp(MidPurple, GreedRed, (f - 0.5f) * 2)
            }
            drawArc(
                color = c,
                startAngle = 180f + 180f * f,
                sweepAngle = 180f / segs + 0.6f,
                useCenter = false,
                topLeft = tl,
                size = sz,
                style = Stroke(width = size.height * 0.085f, cap = StrokeCap.Butt),
            )
        }
        // 刻度
        for (g in 0..100 step 10) {
            val a = Math.PI + Math.PI * g / 100.0
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            val r1 = r * 0.78f
            val r2 = r * 0.68f
            drawLine(
                Color(0x66F5222D),
                Offset(cx + c * r1, cy + s * r1),
                Offset(cx + c * r2, cy + s * r2),
                strokeWidth = 3f,
            )
        }
        // 指针
        if (value != null && !value.isNaN()) {
            val frac = (value.coerceIn(0.0, 100.0) / 100.0)
            val a = Math.PI + Math.PI * frac
            val px = (cx + cos(a) * r * 0.98f).toFloat()
            val py = (cy + sin(a) * r * 0.98f).toFloat()
            drawLine(GreedRed, Offset(cx, cy), Offset(px, py), strokeWidth = 5f, cap = StrokeCap.Round)
            drawCircle(GreedRed, radius = 11f, center = Offset(px, py))
            drawCircle(Color.White, radius = 4f, center = Offset(px, py))
            drawCircle(Color(0xFF2A3442), radius = 16f, center = Offset(cx, cy))
            drawCircle(GreedRed, radius = 13f, center = Offset(cx, cy))
        }
    }
}

/** 当前指数：属性 + 数值。 */
@Composable
private fun AttrRow(latest: FearLatest?) {
    val emotion = latest?.emotion ?: Emotion.UNKNOWN
    val v = latest?.point?.fear
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前指数属性", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(emotion.label, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = emotionColor(emotion)),
                )
            }
            Box(modifier = Modifier.width(1.dp).height(44.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(Color(0xFF2A3442), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
                }
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前指数数值", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (v == null || v.isNaN()) "--" else String.format(Locale.US, "%.0f", v),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        latest?.let {
            Text(
                text = it.source.label + " · " +
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.fetchedAtMillis)),
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 往期指数：四小环。 */
@Composable
private fun RingsCard(rings: List<PastRing>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("往期指数", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            if (rings.isEmpty()) {
                Text("暂无往期数据", fontSize = 13.sp, color = Muted)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rings.take(4).forEach { RingItem(it) }
                }
            }
        }
    }
}

@Composable
private fun RingItem(ring: PastRing) {
    val c = ringColor(ring)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 11f),
                )
                val frac = if (ring.value.isNaN()) 0f else (ring.value / 100f).toFloat().coerceIn(0f, 1f)
                if (frac > 0f) {
                    drawArc(
                        color = c,
                        startAngle = -90f,
                        sweepAngle = 360f * frac,
                        useCenter = false,
                        style = Stroke(width = 11f, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                text = if (ring.value.isNaN()) "--" else String.format(Locale.US, "%.0f", ring.value),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = c,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(ring.name, fontSize = 11.sp, color = Muted)
        Text(ring.emotion.label, fontSize = 11.sp, color = c)
    }
}

/** 历史走势卡片。 */
@Composable
private fun HistoryCard(state: HomeUiState, onRange: (Range) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("历史走势", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
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
                var selectedIdx by remember(points) { mutableStateOf<Int?>(null) }
                FearChart(
                    points = points,
                    selectedIndex = selectedIdx,
                    onSelectIndex = { selectedIdx = if (selectedIdx == it) null else it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                Spacer(Modifier.height(4.dp))
                val sel = selectedIdx?.let { points.getOrNull(it) }
                if (sel?.fear != null) {
                    val se = Emotion.of(sel.fear)
                    Text(
                        "${sel.date} · ${String.format(Locale.US, "%.1f", sel.fear)} · ${se.label}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = emotionColor(se),
                    )
                } else {
                    Text(
                        "${points.first().date} ~ ${points.last().date}（${points.size}个交易日）· 点击曲线查看单日",
                        fontSize = 11.sp,
                        color = Color.Gray,
                    )
                }
            } else {
                Text("暂无历史数据", fontSize = 13.sp, color = Muted)
            }
        }
    }
}

/** 恐惧贪婪历史折线：渐变描边 + 底部填充 + 情绪分界线，点击选中单日。 */
@Composable
private fun FearChart(
    points: List<FearPoint>,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(points) {
            detectTapGestures { tap ->
                // 与绘制区同坐标：左右各 8px 内边距
                val left = 8f
                val right = size.width - 8f
                if (tap.x < left - 24f || tap.x > right + 24f) return@detectTapGestures
                val idx = ((tap.x - left) / (right - left) * (points.size - 1)).roundToInt()
                    .coerceIn(0, points.size - 1)
                onSelectIndex(idx)
            }
        },
    ) {
        val vals = points.map { it.fear ?: 50.0 }
        val left = 8f
        val right = size.width - 8f
        val top = 8f
        val bottom = size.height - 12f
        fun x(i: Int) = left + (right - left) * i / (vals.size - 1)
        fun y(v: Double) = (bottom - (bottom - top) * ((v / 100.0))).toFloat()

        listOf(10f to Color(0x330B6ECE), 30f to Color(0x331890FF), 70f to Color(0x339AA4B2), 90f to Color(0x33F5222D))
            .forEach { (t, c) ->
                drawLine(c, Offset(left, y(t.toDouble())), Offset(right, y(t.toDouble())), strokeWidth = 1.5f)
            }

        val line = Path()
        vals.forEachIndexed { i, v ->
            val px = x(i)
            val py = y(v)
            if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
        }
        // 底部填充
        val fill = Path().apply {
            addPath(line)
            lineTo(x(vals.size - 1), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(
                listOf(Color(0x551890FF), Color(0x0D1890FF), Color(0x0DF5222D)),
                startY = top,
                endY = bottom,
            ),
        )
        // 一笔连续描边：横向蓝->紫->红渐变（官方色流），杜绝分段缝隙
        drawPath(
            line,
            Brush.horizontalGradient(
                listOf(FearBlue, MidPurple, GreedRed),
                startX = left,
                endX = right,
            ),
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(Color.White, radius = 7f, center = Offset(x(vals.size - 1), y(vals.last())))
        drawCircle(FearBlue, radius = 4.5f, center = Offset(x(vals.size - 1), y(vals.last())))
        // 选中态：竖向准星 + 高亮点
        val selIdx = selectedIndex?.takeIf { it in vals.indices }
        if (selIdx != null) {
            val sx = x(selIdx)
            val sy = y(vals[selIdx])
            drawLine(
                Color.White.copy(alpha = 0.45f),
                Offset(sx, top),
                Offset(sx, bottom),
                strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            drawCircle(Color.White, radius = 10f, center = Offset(sx, sy))
            drawCircle(emotionColor(Emotion.of(vals[selIdx])), radius = 7f, center = Offset(sx, sy))
        }
    }
}

private fun emotionColor(e: Emotion): Color = when (e) {
    Emotion.EXTREME_FEAR -> Color(0xFF0B6ECE)
    Emotion.FEAR -> FearBlue
    Emotion.NEUTRAL -> Muted
    Emotion.GREED -> Color(0xFFFF7A45)
    Emotion.EXTREME_GREED -> GreedRed
    Emotion.UNKNOWN -> Muted
}

/** 环颜色优先用服务端 status_color，中立（空）用默认。 */
private fun ringColor(ring: PastRing): Color {
    val hex = ring.colorHex.trim().trimStart('#')
    if (hex.length == 6) {
        return try {
            Color(("FF$hex").toLong(16))
        } catch (_: Exception) {
            emotionColor(ring.emotion)
        }
    }
    return emotionColor(ring.emotion)
}

/** 电池优化警告卡：未加白名单时提示，避免后台任务被冻结。 */
@Composable
private fun BatteryWarningCard(vm: FearViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F17)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "⚠️ 为保证桌面小组件每小时自动刷新，请允许后台运行",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFF9F43),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "系统省电策略可能会冻结后台任务导致小组件停止更新。建议解除电池优化，并开启自启动。",
                fontSize = 11.sp,
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.requestIgnoreBattery() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("解除电池优化", fontSize = 12.sp)
                }
                if (vm.isXiaomi) {
                    Button(
                        onClick = { vm.openAutoStart() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("开启自启动", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
