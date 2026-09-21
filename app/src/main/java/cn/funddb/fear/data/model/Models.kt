package cn.funddb.fear.data.model

/** 指数标的：保留给 API 层使用，UI 只展示上证口径（官方默认）。 */
enum class Symbol(val guCode: String, val label: String) {
    SHANGHAI("000001.SH", "上证指数"),
    HS300("000300.SH", "沪深300"),
}

/** UI 唯一口径 */
val DEFAULT_SYMBOL = Symbol.SHANGHAI

/**
 * 情绪区间（funddb 官方分档，反推自线上数据：11/24=恐惧，36=中立，81=贪婪，
 * 配合订阅默认阈值 90/10，见 docs/REVERSE_NOTES.md）。
 */
enum class Emotion(val label: String) {
    EXTREME_FEAR("极度恐惧"),
    FEAR("恐惧"),
    NEUTRAL("中立"),
    GREED("贪婪"),
    EXTREME_GREED("极度贪婪"),
    UNKNOWN("未知"),
    ;

    companion object {
        fun of(value: Double?): Emotion {
            if (value == null || value.isNaN()) return UNKNOWN
            return when {
                value <= 10 -> EXTREME_FEAR
                value <= 30 -> FEAR
                value <= 70 -> NEUTRAL
                value <= 90 -> GREED
                else -> EXTREME_GREED
            }
        }

        /** 服务端 status_str 直映（精确，优先于阈值推断）。 */
        fun fromLabel(label: String?): Emotion = when (label?.trim()) {
            "极度恐惧" -> EXTREME_FEAR
            "恐惧" -> FEAR
            "中立", "中性" -> NEUTRAL
            "贪婪" -> GREED
            "极度贪婪" -> EXTREME_GREED
            else -> UNKNOWN
        }
    }
}

/** 单日数据点：恐惧贪婪值 + 对应大盘点位。 */
data class FearPoint(
    val date: String,
    val fear: Double?,
    val index: Double?,
)

/** 往期指数环（1日前/1周前/1月前/1年前，官方 getbasedata 下发）。 */
data class PastRing(
    val name: String,
    /** 0-100 展示值 */
    val value: Double,
    val emotion: Emotion,
    /** 环上颜色（服务端 status_color，中立为空，用默认灰） */
    val colorHex: String,
)

/** 最新一条 + 环比变化 + 官方属性。 */
data class FearLatest(
    val point: FearPoint,
    /** 与上一交易日差值，正数=情绪回暖 */
    val derivative: Double?,
    /** 服务端 status_str（精确情绪，如“中立”），为空时按阈值推断 */
    val emotionLabel: String?,
    /** 服务端 current_time（指数日期，如 2026-09-18） */
    val currentTime: String?,
    val source: DataSource,
    val fetchedAtMillis: Long,
) {
    val emotion: Emotion
        get() {
            val fromServer = Emotion.fromLabel(emotionLabel)
            return if (fromServer != Emotion.UNKNOWN) fromServer else Emotion.of(point.fear)
        }
}

enum class DataSource(val label: String) {
    FUNDDB_DIRECT("韭圈儿直连"),
    MIRROR("开源镜像"),
    CACHE("本地缓存"),
}
