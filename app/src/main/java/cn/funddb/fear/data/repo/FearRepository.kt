package cn.funddb.fear.data.repo

import android.content.Context
import cn.funddb.fear.data.api.ApiProvider
import cn.funddb.fear.data.crypto.FundDbCrypto
import cn.funddb.fear.data.db.FearDatabase
import cn.funddb.fear.data.db.FearEntity
import cn.funddb.fear.data.model.DataSource
import cn.funddb.fear.data.model.FearLatest
import cn.funddb.fear.data.model.FearPoint
import cn.funddb.fear.data.model.Symbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据策略：韭圈儿 funddb 直连主源（官方数）-> 开源镜像降级 -> 本地缓存。
 *
 * 直连已全链路验证（签名 32/32 复刻 + AES 解密 + 32B 填充兼容），
 * 失败时自动降级，保证组件永不白屏。来源会标注在 UI 上。
 */
class FearRepository(private val context: Context) {

    private val dao = FearDatabase.get(context).fearDao()
    private val lastSource = mutableMapOf<String, DataSource>()

    suspend fun history(symbol: Symbol): List<FearPoint> = withContext(Dispatchers.IO) {
        val cached = dao.history(symbol.name).map { FearPoint(it.date, it.fear, it.indexValue) }
        if (cached.isNotEmpty()) return@withContext cached
        refresh(symbol)
        dao.history(symbol.name).map { FearPoint(it.date, it.fear, it.indexValue) }
    }

    suspend fun latest(symbol: Symbol): FearLatest? = withContext(Dispatchers.IO) {
        val two = dao.latestTwo(symbol.name)
        if (two.isEmpty()) {
            refresh(symbol)
        }
        val rows = dao.latestTwo(symbol.name)
        if (rows.isEmpty()) return@withContext null
        val cur = rows[0]
        val prev = rows.getOrNull(1)
        val deriv = if (cur.fear != null && prev?.fear != null) cur.fear - prev.fear else null
        FearLatest(
            point = FearPoint(cur.date, cur.fear, cur.indexValue),
            derivative = deriv,
            symbol = symbol,
            source = lastSource[symbol.name] ?: DataSource.CACHE,
            fetchedAtMillis = cur.fetchedAt,
        )
    }

    /** 拉取并落库，返回本次实际来源；抛异常由调用方吃掉并继续用缓存。 */
    suspend fun refresh(symbol: Symbol): DataSource = withContext(Dispatchers.IO) {
        // 1) 直连尝试（best-effort，失败不抛）
        tryDirect(symbol)?.let {
            lastSource[symbol.name] = it
            return@withContext it
        }
        // 2) 镜像主源
        val res = cn.funddb.fear.data.api.MirrorFetcher.fetch()
        val n = minOf(res.chart.dates.size, res.chart.fgi.size)
        val now = System.currentTimeMillis()
        val rows = (0 until n).map { i ->
            FearEntity(
                date = res.chart.dates[i],
                fear = res.chart.fgi.getOrNull(i),
                indexValue = res.chart.hs300.getOrNull(i),
                symbol = symbol.name,
                fetchedAt = now,
            )
        }
        if (rows.isNotEmpty()) {
            dao.upsertAll(rows)
            lastSource[symbol.name] = DataSource.MIRROR
            return@withContext DataSource.MIRROR
        }
        DataSource.CACHE
    }

    suspend fun refreshAll(): Map<Symbol, DataSource> =
        Symbol.values().associateWith { runCatching { refresh(it) }.getOrDefault(DataSource.CACHE) }

    /**
     * 韭圈儿直连：带签名请求 + AES 解密 + 落库。
     * 成功返回 FUNDDB_DIRECT，任何异常返回 null 走降级。
     */
    private suspend fun tryDirect(symbol: Symbol): DataSource? {
        return try {
            val resp = ApiProvider.funddb.kjtlConnect(FundDbCrypto.buildSignedBody(symbol))
            val blob = resp.body()?.string()?.trim()?.trim('"') ?: return null
            if (blob.length < 100) return null
            val root = FundDbCrypto.decryptToJson(blob) ?: return null
            if (root.optInt("code", -1) != 0) return null
            parseFundDbPlain(root, symbol)
            DataSource.FUNDDB_DIRECT
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun parseFundDbPlain(root: org.json.JSONObject, symbol: Symbol) {
        // 明文结构：{data:{xAxis:{categories},series:[{恐惧贪婪},{大盘指数}]}}
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
                symbol = symbol.name,
                fetchedAt = now,
            )
        }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
    }
}
