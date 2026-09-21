package cn.funddb.fear.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ---------- 开源镜像（A股恐惧贪婪，主数据源） ----------
// 数据结构见 https://github.com/qjr1997/fear-greed-index data.json
@JsonClass(generateAdapter = false)
data class MirrorResponse(
    @Json(name = "latest") val latest: MirrorLatest,
    @Json(name = "chart") val chart: MirrorChart,
)

@JsonClass(generateAdapter = false)
data class MirrorLatest(
    @Json(name = "date") val date: String,
    @Json(name = "fgi") val fgi: Double?,
    @Json(name = "derivative") val derivative: Double?,
)

@JsonClass(generateAdapter = false)
data class MirrorChart(
    @Json(name = "dates") val dates: List<String> = emptyList(),
    @Json(name = "fgi") val fgi: List<Double?> = emptyList(),
    @Json(name = "derivative") val derivative: List<Double?> = emptyList(),
    @Json(name = "hs300") val hs300: List<Double?> = emptyList(),
)

interface MirrorApi {
    @GET("qjr1997/fear-greed-index/main/data.json")
    suspend fun dataJsonRaw(): retrofit2.Response<okhttp3.ResponseBody>
}

/** 镜像 JSON 含 NaN 非法字面量（hs300 停牌日），先清洗再解析。 */
object MirrorFetcher {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(MirrorResponse::class.java)

    suspend fun fetch(): MirrorResponse {
        val raw = ApiProvider.mirror.dataJsonRaw().body()?.string()
            ?: throw IllegalStateException("镜像空响应")
        val clean = raw
            .replace(Regex("(?<=[:,\\[])\\s*-?Infinity"), "null")
            .replace(Regex("(?<=[:,\\[])\\s*NaN"), "null")
        return adapter.fromJson(clean)
            ?: throw IllegalStateException("镜像解析失败")
    }
}

// ---------- 韭圈儿 funddb 官方接口（直连，预埋） ----------
private const val FUNDDB_HOST = "https://api.jiucaishuo.com/"

interface FundDbApi {
    /** 恐惧贪婪主序列（is_jm 加密，请求体由 FundDbCrypto.buildSignedBody 构造）。 */
    @POST("v2/kjtl/kjtlconnect")
    suspend fun kjtlConnect(@Body body: okhttp3.RequestBody): retrofit2.Response<okhttp3.ResponseBody>

    /**
     * 恐惧贪婪数值面板（明文）：当前值/官方属性/往期四环/更新进度。
     * 无参调用（fetch 层补 type/version），请求体由 FundDbCrypto.signedBodyMap 构造。
     */
    @POST("v2/kjtl/getbasedata")
    suspend fun kjtlBasedata(@Body body: okhttp3.RequestBody): retrofit2.Response<okhttp3.ResponseBody>

    /** 恐惧贪婪 6 大因子名（明文）。 */
    @POST("v2/kjtl/getalltypes")
    suspend fun kjtlTypes(@Body body: okhttp3.RequestBody): retrofit2.Response<okhttp3.ResponseBody>
}

object ApiProvider {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun client(): OkHttpClient {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Referer", "https://funddb.cn/tool/fear")
                        .header("Origin", "https://funddb.cn")
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
                        )
                        .build(),
                )
            }
            .addInterceptor(log)
            .build()
    }

    val mirror: MirrorApi = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .client(client())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(MirrorApi::class.java)

    val funddb: FundDbApi = Retrofit.Builder()
        .baseUrl(FUNDDB_HOST)
        .client(client())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(FundDbApi::class.java)
}
