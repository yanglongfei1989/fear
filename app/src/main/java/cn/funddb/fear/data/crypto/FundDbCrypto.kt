package cn.funddb.fear.data.crypto

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 韭圈儿 funddb 直连接口加解密（逆向结论，2026-09-21 人工验证）。
 *
 * 服务端：POST https://api.jiucaishuo.com/v2/kjtl/kjtlconnect
 *   body = gu_code(000001.SH/000300.SH) + type(h5) + version(2.4.5) + act_time(ms)
 *         + 约 30 个签名派生字段（见 funddb 前端 app.*.js 的 b(...) 函数，
 *           由 sorted(params)+secret 做 md5 后按固定下标截取，secret = P.a.fklreialk）。
 *   未带签名时服务端仍返回 200 + 11KB 加密 blob，但无法用已知密钥解开
 *   （应为签名校验失败后的错误包，或响应密钥与请求签名绑定）。
 *
 * 前端解密链（app.*.js，变量名已还原）：
 *   k.a 初值 "nengnongchulainb" -> 被覆写为 "bvroqevdjqibsdkq"（16B）
 *   k.b 初值 "bieyanjiulexixishuibatoufamei" -> 被覆写为 "eveqocftukbotqjcequcnkrqlw1oi"（29B）
 *   H.a() = k.a 全量（经 P/z/q/E 逐级 substr 拼接还原，数学上恒等于 k.a）
 *   H.e() = k.b 全量（(i_v+i_vv+k.b) 去掉前后缀，恒等于 k.b）
 *   另有 monkey-patch：key = H.e() + "ll"，iv = H.a() + "ll"，AES/CBC/PKCS7。
 *   历史密钥（cninfo.js）：my_decode 用 h5.jiucaishuo.com*；
 *   new_my_decode 用 bieyanjiulexixishuibatoufameill1 / nengnongchulainbl1。
 *   三代密钥均已试过，对未签名请求的 blob 解出乱码 -> 需补浏览器插桩抓签名。
 *
 * 后续激活直连的方式（二选一）：
 *  1) 用 Playwright 加载 https://funddb.cn/tool/fear，拦截 XHR 拿到合法签名请求，
 *     把 signing 逻辑（含 fklreialk）完整移植到 [buildSignedBody]；
 *  2) 自建代理：服务端定时抓取后吐明文 JSON，App 只读代理。
 */
object FundDbCrypto {

    // ---- 当前前端混淆出的密钥（覆写后终值） ----
    const val KA_CURRENT = "bvroqevdjqibsdkq"
    const val KB_CURRENT = "eveqocftukbotqjcequcnkrqlw1oi"

    // ---- 上一代（AKShare cninfo.js new_my_decode） ----
    const val KEY_GEN2 = "bieyanjiulexixishuibatoufameill1"
    const val IV_GEN2 = "nengnongchulainbl1"

    // ---- 第一代（AKShare cninfo.js my_decode） ----
    const val KEY_GEN1 = "h5.jiucaishuo.comaaaaaaaaaaaaaaa"
    const val IV_GEN1 = "h5.jiucaishuo.com"

    /**
     * 按前端 monkey-patch 规则解密：key/iv 字符串各拼接 "ll" 后做 AES/CBC/PKCS7。
     * @return 解密出的 JSON 明文，失败返回 null。
     */
    fun decryptPatched(cipherBase64: String, keyStr: String, ivStr: String): String? {
        return try {
            aesCbcDecrypt(
                base64 = cipherBase64.trim().trim('"'),
                keyBytes = (keyStr + "ll").toByteArray(Charsets.UTF_8),
                ivBytes = (ivStr + "ll").toByteArray(Charsets.UTF_8),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 当前代密钥解密（未签名请求目前解不开，保留待签名补齐后验证）。 */
    fun decryptCurrent(blob: String): String? =
        decryptPatched(blob, KB_CURRENT, KA_CURRENT)

    fun aesCbcDecrypt(base64: String, keyBytes: ByteArray, ivBytes: ByteArray): String {
        val ct = Base64.decode(base64, Base64.DEFAULT)
        // CryptoJS 语义：key 按 32B 对齐（不足补 0，多余截断）；iv 取前 16B。
        val key = keyBytes.copyOf(32)
        val iv = ivBytes.copyOf(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    /**
     * TODO(直连激活): 构造带签名的请求体。
     * 需补：secret fklreialk 的取值 + md5 流程 + b(...) 的 30 个字段映射。
     * 抓包锚点见 docs/REVERSE_NOTES.md。
     */
    fun buildSignedBody(unsigned: okhttp3.RequestBody): okhttp3.RequestBody {
        // 当前仅返回未签名体：getalltypes 等明文接口可用；
        // kjtlconnect 未签名返回的 blob 暂无法解密。
        return unsigned
    }
}
