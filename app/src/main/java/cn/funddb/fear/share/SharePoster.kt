package cn.funddb.fear.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.content.FileProvider
import cn.funddb.fear.data.model.Emotion
import cn.funddb.fear.data.model.FearLatest
import cn.funddb.fear.data.model.FearPoint
import java.io.File
import java.io.FileOutputStream

/**
 * 分享海报：1080 宽长图（标题 + 数值情绪 + 走势 + 日期来源），走系统分享。
 */
object SharePoster {

    private const val BLUE = 0xFF1890FF.toInt()
    private const val PURPLE = 0xFF7B5CFF.toInt()
    private const val RED = 0xFFF5222D.toInt()

    fun render(
        latest: FearLatest?,
        history: List<FearPoint>,
        width: Int = 1080,
    ): Bitmap? {
        val v = latest?.point?.fear?.takeUnless { it.isNaN() } ?: return null
        val emotion = latest.emotion
        val pad = 72f
        var y = pad + 90f
        val hTitle = 200f
        val hValue = 300f
        val hChart = 420f
        val hFoot = 220f
        val height = (y + hTitle + hValue + hChart + hFoot + pad).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF101418.toInt())

        fun text(s: String, x: Float, yy: Float, size: Float, color: Int, bold: Boolean, center: Boolean = false) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
            }
            c.drawText(s, x, yy, p)
        }

        val emoColor = when (emotion) {
            Emotion.EXTREME_FEAR -> 0xFF0B6ECE.toInt()
            Emotion.FEAR -> BLUE
            Emotion.NEUTRAL -> 0xFF9AA4B2.toInt()
            Emotion.GREED -> 0xFFFF7A45.toInt()
            Emotion.EXTREME_GREED -> RED
            Emotion.UNKNOWN -> 0xFF9AA4B2.toInt()
        }
        text("恐惧贪婪指数", pad, y, 64f, 0xFFFFFFFF.toInt(), true)
        y += 84f
        text("更新时间 ${latest.currentTime ?: latest.point.date}", pad, y, 36f, 0xFF9AA4B2.toInt(), false)
        y += hTitle
        text(String.format(java.util.Locale.US, "%.0f", v), pad, y, 220f, emoColor, true)
        text(emotion.label, pad + 420f, y - 20f, 84f, emoColor, true)
        y += hValue

        // 走势（复用小组件渲染器规格放大）
        val vals = history.mapNotNull { it.fear }.takeLast(120)
        if (vals.size >= 2) {
            val cw = width - pad * 2
            val ch = hChart - 40f
            val cx0 = pad
            val cy0 = y
            fun xx(i: Int) = cx0 + cw * i / (vals.size - 1)
            fun yy(vv: Double) = (cy0 + ch * (1 - (vv.coerceIn(0.0, 100.0) / 100.0))).toFloat()
            val line = android.graphics.Path()
            vals.forEachIndexed { i, vv ->
                if (i == 0) line.moveTo(xx(i), yy(vv)) else line.lineTo(xx(i), yy(vv))
            }
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 9f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                shader = LinearGradient(
                    cx0, 0f, cx0 + cw, 0f,
                    intArrayOf(BLUE, PURPLE, RED), null, Shader.TileMode.CLAMP,
                )
            }
            c.drawPath(line, lp)
        }
        y += hChart
        text(
            "数据来源：韭圈儿 funddb · 仅供参考，不构成投资建议",
            pad, y + 60f, 32f, 0xFF6B7684.toInt(), false,
        )
        return bmp
    }

    /** 保存到缓存并调起系统分享。 */
    fun share(context: Context, bmp: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "fear-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "分享恐惧贪婪指数").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
