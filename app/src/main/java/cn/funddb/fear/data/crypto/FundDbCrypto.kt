package cn.funddb.fear.data.crypto

import android.util.Base64
import cn.funddb.fear.data.model.Symbol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 韭圈儿 funddb 直连接口签名与解密（2026-09-21 全链路实测通过）。
 *
 * 抓包对照：Playwright 真浏览器请求 32/32 字段复刻一致；
 * 解密对照：官方最新值 36.16（2026-09-18）与 funddb 页面一致。
 *
 * 请求：POST https://api.jiucaishuo.com/v2/kjtl/kjtlconnect
 *   明文字段：gu_code + time(-1=全部历史)；fetch 层补 type=pc / version=2.2.7 / authtoken=""
 *   act_time=当前毫秒；外加 32 个签名派生字段。
 * 签名：o = 按 key 排序拼接非空标量值 + SECRET；u = md5(o) 小写 hex；
 *   32 字段按固定下标截取（见 buildSignedBody）。
 * 解密：AES/CBC/NoPadding，key = KB + "ll1"（恰 32B），iv = (KA + "ll1").take(16)。
 *   服务端按 32B 对齐填充（填充字节值=填充长度，可能 >16），故不用 PKCS5，
 *   解出明文后用 extractJson() 截出完整 JSON（对应 Python json.raw_decode）。
 */
object FundDbCrypto {

    const val SECRET = "EWf45rlv#kfsr@k#gfksgkr"
    const val REQ_VERSION = "2.2.7"
    const val REQ_TYPE = "pc"

    // 前端混淆还原后的终值（H.e / H.a），"+ll"是 AES 补丁，"+1"是 Utf8.parse 补丁
    private const val KB = "eveqocftukbotqjcequcnkrqlw1oi"
    private const val KA = "bvroqevdjqibsdkq"

    private val KEY_BYTES = (KB + "ll1").toByteArray(Charsets.UTF_8) // 32B，AES-256
    private val IV_BYTES = (KA + "ll1").toByteArray(Charsets.UTF_8).copyOf(16)

    /** 主序列请求体：{gu_code, time} + fetch 层字段 + 签名。 */
    fun buildSignedBody(symbol: Symbol, time: Int = -1): RequestBody =
        signedBodyMap(mapOf("gu_code" to symbol.guCode, "time" to time)).toRequestBody()

    /** 数值面板请求体：无参（fetch 层补 type/version）+ 签名，如 getbasedata。 */
    fun buildSignedEmptyBody(): RequestBody = signedBodyMap(emptyMap()).toRequestBody()

    /** 返回带签名的完整参数表（不含序列化），与线上 32/32 校验一致。 */
    fun signedBodyMap(extra: Map<String, Any>): Map<String, Any> {
        val base = linkedMapOf<String, Any>(
            "type" to REQ_TYPE,
            "version" to REQ_VERSION,
            "authtoken" to "",
            "act_time" to System.currentTimeMillis(),
        )
        base.putAll(extra)
        val o = base.keys.sorted().mapNotNull { k ->
            val v = base[k]
            when {
                v == null -> null
                v is Map<*, *> || v is List<*> -> null
                v is Number && v.toLong() == 0L && v.toDouble() == 0.0 -> v.toString()
                v.toString().isEmpty() -> null
                else -> v.toString()
            }
        }.joinToString("") + SECRET
        val u = md5Hex(o)
        fun sub(a: Int, n: Int): String = u.substring(a, (a + n).coerceAtMost(u.length))
        val signed = mapOf(
            "tirgkjfs" to sub(0, 2), "abiokytke" to sub(21, 2),
            "u54rg5d" to sub(2, 2), "kf54ge7" to sub(31, 1),
            "tiklsktr4" to sub(1, 1), "lksytkjh" to sub(17, 4),
            "sbnoywr" to sub(23, 2), "bgd7h8tyu54" to sub(6, 2),
            "y654b5fs3tr" to sub(11, 1), "bioduytlw" to sub(5, 1),
            "bd4uy742" to sub(26, 1), "h67456y" to sub(16, 3),
            "bvytikwqjk" to sub(6, 2), "ngd4uy551" to sub(17, 2),
            "bgiuytkw" to sub(9, 2), "nd354uy4752" to sub(30, 1),
            "ghtoiutkmlg" to sub(11, 3), "bd24y6421f" to sub(24, 2),
            "tbvdiuytk" to sub(16, 1), "ibvytiqjek" to sub(14, 2),
            "jnhf8u5231" to sub(9, 2), "fjlkatj" to sub(2, 3),
            "hy5641d321t" to sub(25, 2), "iogojti" to sub(25, 1),
            "ngd4yut78" to sub(12, 2), "nkjhrew" to sub(26, 1),
            "yt447e13f" to sub(8, 1), "n3bf4uj7y7" to sub(18, 1),
            "nbf4uj7y432" to sub(21, 2), "yi854tew" to sub(29, 2),
            "h13ey474" to sub(29, 3), "quikgdky" to sub(27, 2),
        )
        return base + signed
    }

    private fun Map<String, Any>.toRequestBody(): RequestBody {
        val json = JSONObject()
        forEach { (k, v) -> json.put(k, v) }
        return json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
    }

    /** 解密 is_jm 接口返回的 base64 串，失败返回 null。 */
    fun decryptToJson(cipherBase64: String): JSONObject? {
        return try {
            val ct = Base64.decode(cipherBase64.trim().removeSurrounding("\""), Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(KEY_BYTES, "AES"),
                IvParameterSpec(IV_BYTES),
            )
            val text = String(cipher.doFinal(ct), Charsets.UTF_8)
            JSONObject(extractJson(text))
        } catch (_: Exception) {
            null
        }
    }

    /** 从“JSON + 尾部填充字节”中截出完整顶层 JSON（字符串/转义感知）。 */
    fun extractJson(text: String): String {
        var depth = 0
        var inStr = false
        var esc = false
        for (i in text.indices) {
            val ch = text[i]
            if (inStr) {
                if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false
            } else {
                when (ch) {
                    '"' -> inStr = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(0, i + 1)
                    }
                }
            }
        }
        return text
    }

    private fun md5Hex(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    // 保留历史密钥备查（已验证当前代，请勿删除）
    const val KEY_GEN2 = "bieyanjiulexixishuibatoufameill1"
    const val IV_GEN2 = "nengnongchulainbl1"
    const val KEY_GEN1 = "h5.jiucaishuo.comaaaaaaaaaaaaaaa"
    const val IV_GEN1 = "h5.jiucaishuo.com"
}
