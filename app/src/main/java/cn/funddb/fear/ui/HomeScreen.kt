package cn.funddb.fear.ui

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.funddb.fear.data.model.Emotion
import cn.funddb.fear.data.model.FearFactor
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

private val FearBlue = Color(0xFF1890FF)
private val GreedRed = Color(0xFFF5222D)
private val MidPurple = Color(0xFF7B5CFF)
private val NeutralGray = Color(0xFF9AA4B2)

/** 深浅两套配色，情绪色（蓝/红）两套共用。 */
data class Palette(
    val bg: Color,
    val card: Color,
    val inner: Color,
    val text: Color,
    val muted: Color,
    val track: Color,
    val hub: Color,
    val warnBg: Color,
)

private val DarkPalette = Palette(
    bg = Color(0xFF0E1116),
    card = Color(0xFF161C24),
    inner = Color(0xFF1E2632),
    text = Color.White,
    muted = Color(0xFF9AA4B2),
    track = Color(0xFF232B36),
    hub = Color(0xFF2A3442),
    warnBg = Color(0xFF2A1F17),
)
private val LightPalette = Palette(
    bg = Color(0xFFF4F6F9),
    card = Color.White,
    inner = Color(0xFFE9EDF3),
    text = Color(0xFF141A20),
    muted = Color(0xFF67707C),
    track = Color(0xFFE0E6ED),
    hub = Color.White,
    warnBg = Color(0xFFFFF3E2),
)
private val LocalPal = compositionLocalOf { DarkPalette }

@Composable
private fun pal() = LocalPal.current

private fun palette(dark: Boolean) = if (dark) DarkPalette else LightPalette

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
    val factors: List<FearFactor> = emptyList(),
    val range: Range = Range.Y1,
    val error: String? = null,
    val batteryIgnored: Boolean = true,
    val workerStatus: String = "",
    val isDark: Boolean = true,
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
        _state.value = _state.value.copy(isDark = prefs().getBoolean("dark_mode", true))
        load()
    }

    fun toggleTheme() {
        val v = !_state.value.isDark
        prefs().edit().putBoolean("dark_mode", v).apply()
        _state.value = _state.value.copy(isDark = v)
    }

    private fun prefs() = getApplication<Application>().getSharedPreferences(
        "fear_prefs", android.content.Context.MODE_PRIVATE,
    )

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
                    factors = repo.factors(),
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
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val shareScope = rememberCoroutineScope()
    CompositionLocalProvider(LocalPal provides palette(state.isDark)) {
    Scaffold(
        containerColor = pal().bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("恐惧贪婪指数表", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "更新时间 " + (state.latest?.currentTime ?: state.latest?.point?.date ?: "--"),
                            fontSize = 11.sp,
                            color = pal().muted,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        shareScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val bmp = cn.funddb.fear.share.SharePoster.render(state.latest, state.history)
                            if (bmp != null) cn.funddb.fear.share.SharePoster.share(ctx, bmp)
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "分享", tint = pal().text)
                    }
                    IconButton(onClick = { vm.toggleTheme() }) {
                        Icon(
                            if (state.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "切换主题",
                            tint = pal().text,
                        )
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = pal().text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal().bg),
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
                    BatteryWarningCard(vm, state.isDark)
                    Spacer(Modifier.height(12.dp))
                }
                GaugeCard(state.latest, state.isDark)
                Spacer(Modifier.height(12.dp))
                AttrRow(state.latest, state.isDark)
                Spacer(Modifier.height(12.dp))
                RingsCard(state.rings, state.isDark)
                Spacer(Modifier.height(12.dp))
                HistoryCard(state, state.isDark) { vm.selectRange(it) }
                Spacer(Modifier.height(12.dp))
                FactorsCard(state.factors, state.isDark)
                Spacer(Modifier.height(12.dp))
                NotifySettingsCard(state.isDark)
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
                color = pal().muted,
                textAlign = TextAlign.Center,
            )
            if (state.workerStatus.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    state.workerStatus,
                    fontSize = 10.sp,
                    color = pal().muted,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    }
}

/** 顶部仪表盘卡片：渐变弧 + 弹簧指针 + 滚动数字。 */
@Composable
private fun GaugeCard(latest: FearLatest?, dark: Boolean) {
    val v = latest?.point?.fear
    val emotion = latest?.emotion ?: Emotion.of(v)
    // 数字：平滑滚动；指针：弹簧回弹
    val target = if (v == null || v.isNaN()) 0f else v.toFloat().coerceIn(0f, 100f)
    val numV by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "gaugeNumber",
    )
    val needleV by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "gaugeNeedle",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = pal().card),
    ) {
        Column(Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                GaugeCanvas(value = needleV.toDouble(), dark = dark)
                Column(
                    modifier = Modifier.fillMaxSize().padding(bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (v == null || v.isNaN()) "--" else String.format(Locale.US, "%.0f", numV),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = pal().text,
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
private fun GaugeCanvas(value: Double?, dark: Boolean) {
    val hub = palette(dark).hub
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
            drawCircle(hub, radius = 16f, center = Offset(cx, cy))
            drawCircle(GreedRed, radius = 13f, center = Offset(cx, cy))
        }
    }
}

/** 当前指数：属性 + 数值。 */
@Composable
private fun AttrRow(latest: FearLatest?, dark: Boolean) {
    val emotion = latest?.emotion ?: Emotion.UNKNOWN
    val v = latest?.point?.fear
    val pal = palette(dark)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = pal.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前指数属性", fontSize = 12.sp, color = pal.muted)
                Spacer(Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(emotion.label, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = emotionColor(emotion)),
                )
            }
            Box(modifier = Modifier.width(1.dp).height(44.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(pal.track, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
                }
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前指数数值", fontSize = 12.sp, color = pal.muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (v == null || v.isNaN()) "--" else String.format(Locale.US, "%.0f", v),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = pal.text,
                )
            }
        }
        latest?.let {
            Text(
                text = it.source.label + " · " +
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.fetchedAtMillis)),
                fontSize = 11.sp,
                color = pal.muted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 往期指数：四小环。 */
@Composable
private fun RingsCard(rings: List<PastRing>, dark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = pal().card),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("往期指数", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = pal().text)
            Spacer(Modifier.height(12.dp))
            if (rings.isEmpty()) {
                Text("暂无往期数据", fontSize = 13.sp, color = pal().muted)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rings.take(4).forEachIndexed { index, ring -> RingItem(ring, index, dark) }
                }
            }
        }
    }
}

@Composable
private fun RingItem(ring: PastRing, index: Int = 0, dark: Boolean = true) {
    val c = ringColor(ring)
    val track = palette(dark).track
    val muted = palette(dark).muted
    val targetFrac = if (ring.value.isNaN()) 0f else (ring.value / 100f).toFloat().coerceIn(0f, 1f)
    val animFrac by animateFloatAsState(
        targetValue = targetFrac,
        animationSpec = tween(durationMillis = 900, delayMillis = index * 80, easing = FastOutSlowInEasing),
        label = "ringArc",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 11f),
                )
                val frac = animFrac
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
        Text(ring.name, fontSize = 11.sp, color = muted)
        Text(ring.emotion.label, fontSize = 11.sp, color = c)
    }
}

/** 历史走势卡片。 */
@Composable
private fun HistoryCard(state: HomeUiState, dark: Boolean, onRange: (Range) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = pal().card),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("历史走势", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = pal().text)
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
            Spacer(Modifier.height(4.dp))
            var showIndex by remember(points) { mutableStateOf(false) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = showIndex,
                    onClick = { showIndex = !showIndex },
                    label = { Text("叠加上证", fontSize = 12.sp) },
                )
                if (showIndex) {
                    Text("— 恐贪指数", fontSize = 11.sp, color = FearBlue)
                    Text("┄ 上证指数", fontSize = 11.sp, color = Color(0xFFE8C547))
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
                    onSelectIndex = { selectedIdx = it },
                    showIndex = showIndex,
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
                        "${points.first().date} ~ ${points.last().date}（${points.size}个交易日）· 按住左右滑动查看单日",
                        fontSize = 11.sp,
                        color = pal().muted,
                    )
                }
            } else {
                Text("暂无历史数据", fontSize = 13.sp, color = pal().muted)
            }
        }
    }
}

/** 恐惧贪婪历史折线：渐变描边 + 底部填充 + 情绪分界线，按住滑动选中单日。 */
@Composable
private fun FearChart(
    points: List<FearPoint>,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit,
    showIndex: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(points) {
            // 按住即选中，左右拖动连续切换（与绘制区同坐标系）
            awaitEachGesture {
                val down = awaitFirstDown()
                onSelectIndex(idxOf(down.position.x, size.width.toFloat(), points.size))
                drag(down.id) { change ->
                    onSelectIndex(idxOf(change.position.x, size.width.toFloat(), points.size))
                    change.consume()
                }
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
        // 上证叠加：归一化到图表高度，黄色虚线（只看形态对照）
        if (showIndex) {
            val rawIdx = points.map { it.index }
            val known = rawIdx.filterNotNull()
            if (known.size >= 2) {
                val lo = known.min()
                val hi = known.max()
                val span = (hi - lo).takeIf { it > 0 } ?: 1.0
                var lastV = known.first()
                val idxPath = Path()
                rawIdx.forEachIndexed { i, v ->
                    if (v != null) lastV = v
                    val ny = bottom - (bottom - top) * ((lastV - lo) / span)
                    if (i == 0) idxPath.moveTo(x(i), ny.toFloat()) else idxPath.lineTo(x(i), ny.toFloat())
                }
                drawPath(
                    idxPath,
                    Color(0xFFE8C547),
                    style = Stroke(
                        width = 3.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)),
                    ),
                )
            }
        }
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

/** 把触摸 x 坐标映射到最近的数据点下标（与绘制区左右 8px 内边距一致）。 */
private fun idxOf(xPx: Float, widthPx: Float, n: Int): Int {
    if (n <= 1) return 0
    val left = 8f
    val right = widthPx - 8f
    if (right <= left) return 0
    return ((xPx - left) / (right - left) * (n - 1)).roundToInt().coerceIn(0, n - 1)
}

private fun emotionColor(e: Emotion): Color = when (e) {
    Emotion.EXTREME_FEAR -> Color(0xFF0B6ECE)
    Emotion.FEAR -> FearBlue
    Emotion.NEUTRAL -> NeutralGray
    Emotion.GREED -> Color(0xFFFF7A45)
    Emotion.EXTREME_GREED -> GreedRed
    Emotion.UNKNOWN -> NeutralGray
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
private fun BatteryWarningCard(vm: FearViewModel, dark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = pal().warnBg),
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
                color = pal().muted,
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

/** 提醒设置：阈值告警上下限（默认 90/10，与网页订阅一致）。 */
@Composable
private fun NotifySettingsCard(dark: Boolean) {
    val ctx = LocalContext.current
    val notifier = remember { cn.funddb.fear.notify.Notifier }
    var highText by remember {
        mutableStateOf(notifier.highThreshold(ctx).toInt().toString())
    }
    var lowText by remember {
        mutableStateOf(notifier.lowThreshold(ctx).toInt().toString())
    }
    var savedTick by remember { mutableStateOf(0) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = pal().card),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("提醒设置", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = pal().text)
            Spacer(Modifier.height(4.dp))
            Text(
                "指数进入极端区间时推送一次；每天 9 点后推送交易日早报（周末不打扰）",
                fontSize = 11.sp,
                color = pal().muted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = highText,
                    onValueChange = { highText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("贪婪告警 ≥", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lowText,
                    onValueChange = { lowText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("恐惧告警 ≤", fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val hi = highText.toIntOrNull()?.coerceIn(1, 100)
                            ?: notifier.highThreshold(ctx).toInt()
                        val lo = lowText.toIntOrNull()?.coerceIn(0, 99)
                            ?: notifier.lowThreshold(ctx).toInt()
                        highText = hi.toString()
                        lowText = lo.toString()
                        notifier.saveThresholds(ctx, hi.toFloat(), lo.toFloat())
                        savedTick++
                    },
                ) {
                    Text("保存", fontSize = 12.sp)
                }
            }
            if (savedTick > 0) {
                Spacer(Modifier.height(4.dp))
                Text("已保存：≥$highText 贪婪告警，≤$lowText 恐惧告警", fontSize = 11.sp, color = FearBlue)
            }
        }
    }
}

/** 六大因子：横滑列表，每项名称 + 最新值 + 状态 + 迷你走势。 */
@Composable
private fun FactorsCard(factors: List<FearFactor>, dark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = pal().card),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("六大因子", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = pal().text)
            Spacer(Modifier.height(4.dp))
            Text("谁在推动情绪变化", fontSize = 11.sp, color = pal().muted)
            Spacer(Modifier.height(10.dp))
            if (factors.isEmpty()) {
                Text("暂无因子数据，下拉刷新试试", fontSize = 13.sp, color = pal().muted)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    factors.forEach { FactorItem(it) }
                }
            }
        }
    }
}

@Composable
private fun FactorItem(f: FearFactor) {
    val c = parseHexColor(f.statusColorHex, FearBlue)
    Card(
        modifier = Modifier.width(148.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = pal().inner),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(f.name.ifBlank { f.title }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal().text)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = f.latestValue?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = c,
                )
                if (f.unit.isNotBlank()) {
                    Spacer(Modifier.width(2.dp))
                    Text(f.unit, fontSize = 10.sp, color = pal().muted)
                }
            }
            Text(
                f.statusName.ifBlank { "—" },
                fontSize = 11.sp,
                color = c,
            )
            Spacer(Modifier.height(6.dp))
            FactorSparkline(points = f.points.map { it.second }, color = c)
        }
    }
}

@Composable
private fun FactorSparkline(points: List<Double>, color: Color) {
    val vals = remember(points) {
        val clean = points.filterNot { it.isNaN() }
        if (clean.size <= 60) clean else clean.filterIndexed { i, _ -> i % (clean.size / 60 + 1) == 0 }
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        if (vals.size < 2) return@Canvas
        val lo = vals.min()
        val hi = vals.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val left = 2f
        val right = size.width - 2f
        val top = 4f
        val bottom = size.height - 4f
        fun x(i: Int) = left + (right - left) * i / (vals.size - 1)
        fun y(v: Double) = (bottom - (bottom - top) * ((v - lo) / span)).toFloat()
        val path = Path()
        vals.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
        }
        drawPath(path, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val lx = x(vals.size - 1)
        val ly = y(vals.last())
        drawCircle(Color.White, radius = 5f, center = Offset(lx, ly))
        drawCircle(color, radius = 3f, center = Offset(lx, ly))
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    val h = hex.trim().trimStart('#')
    if (h.length == 6 || h.length == 8) {
        return try {
            Color((if (h.length == 6) "FF$h" else h).toLong(16))
        } catch (_: Exception) {
            fallback
        }
    }
    return fallback
}
