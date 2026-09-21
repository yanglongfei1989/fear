package cn.funddb.fear.data.repo

import android.content.Context
import cn.funddb.fear.data.api.ApiProvider
import cn.funddb.fear.data.api.FundDbApi
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
 * 数据策略：镜像优先（明文、稳定、日更）-> 本地缓存 -> 韭圈儿直连尝试。
 *
 * 说明：镜像是 A 股全市场恐惧贪婪（5 因子），与 funddb 同概念；
 * 直连 funddb 加密接口待签名补齐（见 FundDbCrypto），目前仅做 Best-effort 尝试，
 * 成功后会覆盖为 FUNDDB_DIRECT 来源。
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
     * 韭圈儿直连尝试：明文因子接口探活 + 加密主接口解密。
     * 成功（解密出合法 JSON）才落库并返回 FUNDDB_DIRECT，否则返回 null 走镜像。
     */
    private suspend fun tryDirect(symbol: Symbol): DataSource? {
        return try {
            val body = FundDbCrypto.buildSignedBody(FundDbApi.bodyOf(symbol))
            val resp = ApiProvider.funddb.kjtlConnect(body)
            val blob = resp.body()?.string()?.trim()?.trim('"') ?: return null
            if (blob.length < 100) return null
            val plain = FundDbCrypto.decryptCurrent(blob) ?: return null
            if (!plain.trimStart().startsWith("{")) return null
            // 明文结构与 kjtlother 一致：{data:{xAxis:{categories},series:[{...}]}}，解析落库
            parseFundDbPlain(plain, symbol)
            DataSource.FUNDDB_DIRECT
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun parseFundDbPlain(plain: String, symbol: Symbol) {
        // 轻量解析，避免 Moshi 动态类型坑：只抽 categories + 前两条 series
        val data = org.json.JSONObject(plain).getJSONObject("data")
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
