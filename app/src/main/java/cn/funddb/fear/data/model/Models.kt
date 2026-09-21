package cn.funddb.fear.data.model

/** 指数标的：funddb 原站支持上证指数 / 沪深300 两个口径。 */
enum class Symbol(val guCode: String, val label: String) {
    SHANGHAI("000001.SH", "上证指数"),
    HS300("000300.SH", "沪深300"),
}

/** 情绪区间（0-100，与 funddb / CNN 口径一致）。 */
enum class Emotion(val label: String) {
    EXTREME_FEAR("极度恐惧"),
    FEAR("恐惧"),
    NEUTRAL("中性"),
    GREED("贪婪"),
    EXTREME_GREED("极度贪婪"),
    UNKNOWN("未知"),
    ;

    companion object {
        fun of(value: Double?): Emotion {
            if (value == null || value.isNaN()) return UNKNOWN
            return when {
                value < 25 -> EXTREME_FEAR
                value < 45 -> FEAR
                value <= 55 -> NEUTRAL
                value <= 75 -> GREED
                else -> EXTREME_GREED
            }
        }
    }
}

/** 单日数据点：恐惧贪婪值 + 对应大盘点位。 */
data class FearPoint(
    val date: String,
    val fear: Double?,
    val index: Double?,
)

/** 最新一条 + 环比变化。 */
data class FearLatest(
    val point: FearPoint,
    /** 与上一交易日差值，正数=情绪回暖 */
    val derivative: Double?,
    val symbol: Symbol,
    /** 数据来源标注：MIRROR / FUNDDB_DIRECT / CACHE */
    val source: DataSource,
    val fetchedAtMillis: Long,
)

enum class DataSource(val label: String) {
    FUNDDB_DIRECT("韭圈儿直连"),
    MIRROR("开源镜像"),
    CACHE("本地缓存"),
}
