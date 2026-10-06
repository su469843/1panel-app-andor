package com.panelone.client.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

/** 一台面板的配置。密钥用 SharedPreferences 明文保存（内网自用场景），详见 README 安全说明。 */
@Serializable
data class ServerConfig(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    /** 面板「设置 → API 接口」里创建的密钥 */
    val apiKey: String = "",
    /** 自签名 HTTPS 证书场景：跳过证书校验 */
    val trustAll: Boolean = false,
    /** 面板版本：自动 / v1 / v2 / v3 / v4 */
    val line: PanelLine = PanelLine.AUTO,
) {
    fun isValid(): Boolean = baseUrl.isNotBlank() && apiKey.isNotBlank()

    fun hostText(): String =
        baseUrl.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')

    fun displayName(): String = name.ifBlank { hostText() }
}

class ServerStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("panelone", Context.MODE_PRIVATE)

    fun servers(): List<ServerConfig> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching { PanelJson.decodeFromString<List<ServerConfig>>(raw) }.getOrDefault(emptyList())
    }

    fun saveServers(list: List<ServerConfig>) {
        prefs.edit().putString(KEY_SERVERS, PanelJson.encodeToString(list)).apply()
    }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActiveId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun lastDetected(): String? = prefs.getString(KEY_DETECTED, null)

    fun setLastDetected(text: String?) {
        prefs.edit().putString(KEY_DETECTED, text).apply()
    }

    private companion object {
        const val KEY_SERVERS = "servers"
        const val KEY_ACTIVE = "active_server"
        const val KEY_DETECTED = "last_detected"
    }
}

/**
 * 全局会话状态：当前选中的服务器 + 已解析好的客户端 + 探测到的版本。
 * 用 Compose 的 mutableStateOf 暴露，界面切换服务器后会自动重组刷新。
 */
object Repo {

    private var store: ServerStore? = null

    var servers by mutableStateOf<List<ServerConfig>>(emptyList())
        private set

    var active by mutableStateOf<ServerConfig?>(null)
        private set

    var detected by mutableStateOf<DetectedPanel?>(null)
        private set

    var connecting by mutableStateOf(false)
        private set

    var connectError by mutableStateOf<String?>(null)
        private set

    private var clientRef: PanelClient? = null

    fun init(context: Context) {
        if (store != null) return
        val s = ServerStore(context)
        store = s
        servers = s.servers()
        val id = s.activeId()
        active = servers.firstOrNull { it.id == id }
        detected = s.lastDetected()?.let { DetectedPanel(PanelLine.AUTO, it, "settings") }
    }

    fun upsert(config: ServerConfig): ServerConfig {
        val cfg = if (config.id.isBlank()) config.copy(id = UUID.randomUUID().toString()) else config
        val list = servers.toMutableList()
        val index = list.indexOfFirst { it.id == cfg.id }
        if (index >= 0) list[index] = cfg else list.add(cfg)
        servers = list
        store?.saveServers(list)
        if (active?.id == cfg.id) {
            active = cfg
            clientRef = null
        }
        return cfg
    }

    fun remove(id: String) {
        val list = servers.filterNot { it.id == id }
        servers = list
        store?.saveServers(list)
        if (active?.id == id) {
            active = null
            clientRef = null
            detected = null
            store?.setActiveId(null)
        }
    }

    fun select(id: String) {
        val cfg = servers.firstOrNull { it.id == id } ?: return
        active = cfg
        clientRef = null
        detected = null
        connectError = null
        store?.setActiveId(id)
    }

    fun disconnect() {
        active = null
        clientRef = null
        detected = null
        connectError = null
        store?.setActiveId(null)
    }

    /** 建立连接（自动模式下会做版本探测），成功返回 true */
    suspend fun connect(): Boolean {
        val cfg = active
        if (cfg == null) {
            connectError = "请先添加一个面板服务器"
            return false
        }
        connecting = true
        connectError = null
        return try {
            val resolved = resolvePanel(cfg)
            clientRef = resolved.client
            detected = resolved.detected
            store?.setLastDetected(resolved.detected.display())
            true
        } catch (t: Throwable) {
            clientRef = null
            detected = null
            connectError = ApiErrors.of(t)
            false
        } finally {
            connecting = false
        }
    }

    fun isConnected(): Boolean = clientRef != null

    fun clientOrNull(): PanelClient? = clientRef

    fun client(): PanelClient = clientRef ?: throw ApiException(-1, "尚未连接面板，请先选择服务器")
}
