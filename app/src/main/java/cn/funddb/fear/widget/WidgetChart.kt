package cn.funddb.fear.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * 给桌面小组件渲染迷你走势图（Glance 没有 Canvas，只能先画成 Bitmap 再展示）。
 * 配色与 App 内保持一致：横向蓝->紫->红 + 底部淡填充 + 末点高亮。
 */
object WidgetChart {

    private const val BLUE = 0xFF1890FF.toInt()
    private const val PURPLE = 0xFF7B5CFF.toInt()
    private const val RED = 0xFFF5222D.toInt()

    fun render(values: List<Double>, width: Int = 640, height: Int = 220): Bitmap? {
        val vals = values.filterNot { it.isNaN() }.takeLast(120)
        if (vals.size < 2) return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val padL = 6f
        val padR = 6f
        val padT = 14f
        val padB = 14f
        fun x(i: Int) = padL + (width - padL - padR) * i / (vals.size - 1)
        fun y(v: Double) = (padT + (height - padT - padB) * (1 - (v.coerceIn(0.0, 100.0) / 100.0))).toFloat()

        val line = Path()
        vals.forEachIndexed { i, v ->
            if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v))
        }
        // 底部填充
        val fill = Path(line)
        fill.lineTo(x(vals.size - 1), height - padB)
        fill.lineTo(x(0), height - padB)
        fill.close()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, padT, 0f, height - padB,
                intArrayOf(0x551890FF.toInt(), 0x0D1890FF.toInt(), 0x0DF5222D.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        }
        c.drawPath(fill, fillPaint)
        // 渐变主线（一笔连续）
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = LinearGradient(
                padL, 0f, width - padR, 0f,
                intArrayOf(BLUE, PURPLE, RED),
                null, Shader.TileMode.CLAMP,
            )
        }
        c.drawPath(line, linePaint)
        // 末点
        val lx = x(vals.size - 1)
        val ly = y(vals.last())
        c.drawCircle(lx, ly, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawCircle(lx, ly, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE })
        return bmp
    }
}
