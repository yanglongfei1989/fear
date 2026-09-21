package cn.funddb.fear.data.repo

import android.content.Context
import cn.funddb.fear.data.api.ApiProvider
import cn.funddb.fear.data.api.MirrorFetcher
import cn.funddb.fear.data.crypto.FundDbCrypto
import cn.funddb.fear.data.db.FearDatabase
import cn.funddb.fear.data.db.FearEntity
import cn.funddb.fear.data.db.FearMeta
import cn.funddb.fear.data.model.DEFAULT_SYMBOL
import cn.funddb.fear.data.model.DataSource
import cn.funddb.fear.data.model.Emotion
import cn.funddb.fear.data.model.FearLatest
import cn.funddb.fear.data.model.FearPoint
import cn.funddb.fear.data.model.PastRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据策略（单口径：上证/官方默认）：
 *  1. funddb 直连：kjtlconnect（历史序列）+ getbasedata（当前值/官方属性/往期四环）；
 *  2. 开源镜像降级；3. 本地缓存。来源标注在 UI。
 */
class FearRepository(private val context: Context) {

    private val dao = FearDatabase.get(context).fearDao()
    private var lastSource: DataSource = DataSource.CACHE

    private val sym: String get() = DEFAULT_SYMBOL.name

    suspend fun history(): List<FearPoint> = withContext(Dispatchers.IO) {
        val cached = dao.history(sym).map { FearPoint(it.date, it.fear, it.indexValue) }
        if (cached.isNotEmpty()) return@withContext cached
        refresh()
        dao.history(sym).map { FearPoint(it.date, it.fear, it.indexValue) }
    }

    suspend fun latest(): FearLatest? = withContext(Dispatchers.IO) {
        if (dao.count(sym) == 0) refresh()
        val rows = dao.latestTwo(sym)
        if (rows.isEmpty()) return@withContext null
        val cur = rows[0]
        val prev = rows.getOrNull(1)
        val deriv = if (cur.fear != null && prev?.fear != null) cur.fear - prev.fear else null
        val meta = dao.meta(sym)
        FearLatest(
            point = FearPoint(cur.date, cur.fear, cur.indexValue),
            derivative = deriv,
            emotionLabel = meta?.statusStr,
            currentTime = meta?.currentTime,
            source = lastSource,
            fetchedAtMillis = meta?.fetchedAt ?: cur.fetchedAt,
        )
    }

    suspend fun rings(): List<PastRing> = withContext(Dispatchers.IO) {
        if (dao.meta(sym) == null) refresh()
        parseRings(dao.meta(sym)?.ringsJson).ifEmpty { fallbackRings() }
    }

    /** 往期四环降级：用历史序列按交易日偏移估算（1/5/22/252）。 */
    private suspend fun fallbackRings(): List<PastRing> {
        val hist = dao.history(sym)
        if (hist.isEmpty()) return emptyList()
        fun atBack(offset: Int): FearEntity = hist.getOrElse(hist.size - 1 - offset.coerceAtMost(hist.size - 1)) { hist.last() }
        return listOf(
            ringOf("1日前", atBack(1).fear),
            ringOf("1周前", atBack(5).fear),
            ringOf("1月前", atBack(22).fear),
            ringOf("1年前", atBack(252).fear),
        )
    }

    private fun ringOf(name: String, value: Double?): PastRing {
        val v = value ?: Double.NaN
        val e = Emotion.of(value)
        return PastRing(name, v, e, "")
    }

    /** 拉取并落库，返回本次实际来源。 */
    suspend fun refresh(): DataSource = withContext(Dispatchers.IO) {
        // 1) 直连：历史序列 + 数值面板
        runCatching { refreshDirect() }.getOrNull()?.let {
            lastSource = it
            return@withContext it
        }
        // 2) 镜像降级
        runCatching { refreshMirror() }.getOrNull()?.let {
            lastSource = it
            return@withContext it
        }
        lastSource = DataSource.CACHE
        DataSource.CACHE
    }

    private suspend fun refreshDirect(): DataSource {
        // 历史序列（正常加密；异常时服务端可能回明文，兼容两种）
        val resp = ApiProvider.funddb.kjtlConnect(FundDbCrypto.buildSignedBody(DEFAULT_SYMBOL))
        val blob = resp.body()?.string()?.trim()?.removeSurrounding("\"") ?: throw IllegalStateException("空响应")
        if (blob.length < 100) throw IllegalStateException("响应过短")
        val root = FundDbCrypto.decryptToJson(blob) ?: plainJson(blob)
            ?: throw IllegalStateException("解密失败")
        if (root.optInt("code", -1) != 0) throw IllegalStateException("code != 0")
        parseSeries(root)
        // 数值面板（正常明文；兼容加密回包）
        runCatching { refreshMeta() }
        return DataSource.FUNDDB_DIRECT
    }

    private fun plainJson(text: String): JSONObject? = try {
        val o = JSONObject(FundDbCrypto.extractJson(text.trim()))
        if (o.has("code") && o.has("data")) o else null
    } catch (_: Exception) {
        null
    }

    private suspend fun refreshMeta() {
        val resp = ApiProvider.funddb.kjtlBasedata(FundDbCrypto.buildSignedEmptyBody())
        val text = resp.body()?.string()?.trim() ?: return
        if (text.length < 20) return
        // 明文优先，加密回包则解密（与服务端行为对齐）
        val root = try {
            JSONObject(FundDbCrypto.extractJson(text))
        } catch (_: Exception) {
            null
        }?.takeIf { it.optInt("code", -999) == 0 }
            ?: text.removeSurrounding("\"").let { FundDbCrypto.decryptToJson(it) }
            ?: return
        if (root.optInt("code", -1) != 0) return
        val d = root.getJSONObject("data")
        val rings = d.optJSONArray("list") ?: JSONArray()
        val out = ArrayList<PastRing>(rings.length())
        for (i in 0 until rings.length()) {
            val o = rings.optJSONObject(i) ?: continue
            val series = o.optJSONObject("data")?.optJSONArray("series")
            val frac = series?.optJSONObject(0)?.optDouble("data", Double.NaN) ?: Double.NaN
            val label = o.optString("status_str", "")
            out.add(
                PastRing(
                    name = o.optString("name", ""),
                    value = if (frac.isNaN()) Double.NaN else frac * 100,
                    emotion = Emotion.fromLabel(label).takeUnless { it == Emotion.UNKNOWN }
                        ?: Emotion.of(if (frac.isNaN()) null else frac * 100),
                    colorHex = o.optString("status_color", ""),
                ),
            )
        }
        dao.upsertMeta(
            FearMeta(
                symbol = sym,
                num = d.optDouble("num", Double.NaN).takeUnless { it.isNaN() },
                statusStr = d.optString("status_str", null),
                currentTime = d.optString("current_time", null),
                ringsJson = JSONArray(out.map {
                    JSONObject().put("name", it.name).put("value", it.value)
                        .put("label", it.emotion.label).put("color", it.colorHex)
                }).toString(),
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun parseSeries(root: JSONObject) {
        // {data:{xAxis:{categories},series:[{恐惧贪婪},{大盘指数}]}}
        val data = root.getJSONObject("data")
        val cats = data.getJSONObject("xAxis").getJSONArray("categories")
        val series = data.getJSONArray("series")
        val fearArr = series.optJSONObject(0)?.optJSONArray("data")
        val idxArr = series.optJSONObject(1)?.optJSONArray("data")
        val now = System.currentTimeMillis()
        val rows = (0 until cats.length()).map { i ->
            FearEntity(
                date = cats.getString(i),
                fear = fearArr?.optDouble(i, Double.NaN)?.takeUnless { it.isNaN() },
                indexValue = idxArr?.optDouble(i, Double.NaN)?.takeUnless { it.isNaN() },
                symbol = sym,
                fetchedAt = now,
            )
        }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
    }

    private suspend fun refreshMirror(): DataSource {
        val res = MirrorFetcher.fetch()
        val n = minOf(res.chart.dates.size, res.chart.fgi.size)
        val now = System.currentTimeMillis()
        val rows = (0 until n).map { i ->
            FearEntity(
                date = res.chart.dates[i],
                fear = res.chart.fgi.getOrNull(i),
                indexValue = res.chart.hs300.getOrNull(i),
                symbol = sym,
                fetchedAt = now,
            )
        }
        if (rows.isEmpty()) throw IllegalStateException("镜像空数据")
        dao.upsertAll(rows)
        return DataSource.MIRROR
    }

    private fun parseRings(json: String?): List<PastRing> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PastRing(
                    name = o.optString("name"),
                    value = o.optDouble("value", Double.NaN),
                    emotion = Emotion.fromLabel(o.optString("label")).takeUnless { it == Emotion.UNKNOWN }
                        ?: Emotion.of(o.optDouble("value", Double.NaN).takeUnless { it.isNaN() }),
                    colorHex = o.optString("color"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
