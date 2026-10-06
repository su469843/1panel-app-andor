package com.panelone.client.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** 全局 JSON 配置：面板升级新增字段/未知枚举都不能让 APP 崩掉 */
val PanelJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

class ApiException(val code: Int, override val message: String) : Exception(message)

object ApiErrors {

    fun friendly(code: Int, message: String): String = when (code) {
        401 -> message.ifBlank { "未授权" } +
            "（检查：面板「设置 → API 接口」是否开启、密钥是否正确、手机出口 IP 是否在接口白名单内）"
        403 -> message.ifBlank { "没有权限访问该接口" }
        404 -> message.ifBlank { "接口不存在：可能是面板版本选错了，试试切换版本或选「自动」" }
        -2 -> message.ifBlank { "网络不可达" }
        -3 -> message.ifBlank { "TLS/证书错误" }
        -4 -> message.ifBlank { "面板返回了非 JSON 内容：请检查地址与安全入口" }
        else -> message.ifBlank { "请求失败（code=$code）" }
    }

    fun of(t: Throwable): String = when (t) {
        is ApiException -> t.message
        is SSLException -> "TLS/证书错误：${t.message ?: "握手失败"}（可在服务器设置里打开「忽略证书错误」）"
        is IOException -> "网络错误：${t.message ?: "无法连接"}"
        else -> t.message ?: t.toString()
    }
}

fun <T> ApiEnvelope<T>.dataOrThrow(): T {
    if (code != 200) throw ApiException(code, ApiErrors.friendly(code, message))
    return data ?: throw ApiException(code, "面板返回了空数据")
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val HEX = "0123456789abcdef".toCharArray()

/** 与 1Panel 服务端 GenerateMD5 一致：小写十六进制 */
private fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
    val out = StringBuilder(digest.size * 2)
    digest.forEach { b ->
        val v = b.toInt()
        out.append(HEX[(v shr 4) and 0x0f])
        out.append(HEX[v and 0x0f])
    }
    return out.toString()
}

private fun normalizeBase(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
    return withScheme.trimEnd('/')
}

/**
 * 1Panel API 客户端。
 *
 * 认证（与 backend/middleware/session.go 完全一致）：
 *   1Panel-Timestamp: 秒级时间戳
 *   1Panel-Token:     md5("1panel" + apiKey + timestamp)
 * 面板侧还要求：设置里打开「API 接口」，并把手机出口 IP 加入白名单；时间戳容差由
 * 「密钥有效期」设置决定（默认 0 = 不校验时间）。
 */
class PanelClient(
    val config: ServerConfig,
    /** 可变：自动探测时在同一客户端上切换方言，避免反复创建 OkHttpClient */
    var dialect: PanelDialect,
) {

    private val base: String = normalizeBase(config.baseUrl)
    private val http: OkHttpClient = buildHttp(config)

    val baseUrl: String get() = base

    /**
     * 底层请求。[path] 是**绝对路径**（已含 /api/v1 或 /api/v2 前缀）。
     * 1Panel 的错误响应 HTTP 状态码恒为 200、错误码在 JSON 外壳的 code 里，
     * 未注册路由则可能返回 200 + HTML，因此 [requireJsonShape] 用来挡住后者。
     */
    suspend fun raw(
        method: String,
        path: String,
        body: String? = null,
        requireJsonShape: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val builder = Request.Builder()
            .url(base + path)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN")
            .header("User-Agent", "PanelOne-Android/1.0")

        if (dialect.supportsApiKey && config.apiKey.isNotBlank()) {
            builder.header("1Panel-Token", md5Hex("1panel" + config.apiKey + timestamp))
            builder.header("1Panel-Timestamp", timestamp)
        }

        val request = if (body != null) {
            builder.method(method, body.toRequestBody(JSON_MEDIA)).build()
        } else {
            builder.method(method, null).build()
        }

        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful && text.isBlank()) {
                    throw ApiException(response.code, "HTTP ${response.code} ${response.message}")
                }
                val head = text.trimStart()
                if (requireJsonShape && head.isNotEmpty() && !head.startsWith("{") && !head.startsWith("[")) {
                    throw ApiException(
                        -4,
                        "面板返回了非 JSON 内容（可能是地址/端口/安全入口不对）：${head.take(80)}",
                    )
                }
                text
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: SSLException) {
            throw ApiException(-3, ApiErrors.of(e))
        } catch (e: IOException) {
            throw ApiException(-2, ApiErrors.of(e))
        }
    }

    private fun buildHttp(config: ServerConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (config.trustAll) {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }
}

/* -------------------- 通用请求包装 -------------------- */

suspend inline fun <reified T> PanelClient.getData(path: String): T =
    PanelJson.decodeFromString<ApiEnvelope<T>>(raw("GET", path)).dataOrThrow()

suspend inline fun <reified T> PanelClient.postData(path: String, body: String): T =
    PanelJson.decodeFromString<ApiEnvelope<T>>(raw("POST", path, body)).dataOrThrow()

/** 只关心成功与否的写操作（启动/停止/重启等） */
suspend fun PanelClient.action(method: String, path: String, body: String? = null) {
    val envelope = PanelJson.decodeFromString<ApiEnvelope<JsonElement>>(raw(method, path, body))
    if (envelope.code != 200) {
        throw ApiException(envelope.code, ApiErrors.friendly(envelope.code, envelope.message))
    }
}
