package com.panelone.client.data

/**
 * 接口家族（= 用户在设置里选的「面板版本」）。
 *
 * 关键事实（来自 1Panel 官方源码取证，2026-02）：
 *  - 1.0~1.9        ：`backend/` 单体架构，**不支持 API 密钥**；
 *  - 1.1~1.10.33    ：`backend/` 单体架构，接口前缀 `/api/v1`，实时占用 `POST /dashboard/current`；
 *  - 1.10.34 及以上、2.x：`core/`(面板) + `agent/`(主机) 架构，接口前缀 `/api/v2`（面板自身接口在 `/api/v2/core`），
 *                      实时占用改成 `GET /dashboard/current/{ioOption}/{netOption}`，排序字段 `createdAt`。
 *
 * 所以「面板版本号」和「接口家族」不能划等号（例：v1.10.34-lts 这个 tag 实际是新架构），
 * 自动模式必须以接口探测为准，版本号只用于展示。
 */
enum class PanelLine(val label: String, val desc: String) {
    AUTO("自动", "连接后自动探测接口家族"),
    V1("v1 · 经典", "1.1 ~ 1.10.33，接口前缀 /api/v1"),
    V2("v2 · 新架构", "1.10.34 及以上、2.x，接口前缀 /api/v2"),
    V3("v3", "预留：官方尚未发布 3.x，暂按 v2 契约执行"),
    V4("v4", "预留：官方尚未发布 4.x，暂按 v2 契约执行"),

    /** 内部使用：1.0~1.9，不支持 API 密钥，仅用于提示 */
    LEGACY("1.0", "1.0~1.9 远古版本，不支持 API 密钥"),
    ;

    companion object {
        /** 选择器里展示的项（LEGACY 只作为探测结论出现） */
        val selectable: List<PanelLine> = listOf(AUTO, V1, V2, V3, V4)

        fun fromName(name: String?): PanelLine =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * 一个接口家族对应的「方言」。所有版本差异都收敛在这里，界面代码不需要知道版本。
 * 路径均为相对路径，最终 URL = baseUrl + apiPrefix + 相对路径。
 */
data class PanelDialect(
    val line: PanelLine,
    val apiPrefix: String,
    /** 是否支持 1Panel-Token / 1Panel-Timestamp 密钥认证 */
    val supportsApiKey: Boolean = true,

    /** 系统信息；老版本没有该接口时置空 */
    val osInfoPath: String? = "/dashboard/base/os",
    /** 基础信息（含主机信息与数量统计，经典家族还带 currentInfo） */
    val baseInfoPath: String = "/dashboard/base/{io}/{net}",

    /** 实时占用：经典家族 POST + body，新架构 GET + 路径参数 */
    val currentPath: String = "/dashboard/current",
    val currentHasBody: Boolean = true,

    /** 列表排序字段：经典家族用 created_at，新架构用 createdAt（枚举强校验，传错直接 400） */
    val orderByCreated: String = "created_at",

    val containerSearchPath: String = "/containers/search",
    val containerStatsPath: String? = "/containers/list/stats",
    val containerOperatePath: String = "/containers/operate",
    val appSearchPath: String = "/apps/installed/search",
    val appOperatePath: String = "/apps/installed/op",
    val websiteSearchPath: String = "/websites/search",
    val websiteOperatePath: String = "/websites/operate",

    /** 读取面板版本号的接口（POST，无参数） */
    val versionProbePath: String? = "/settings/search",

    /** 免认证指纹 A：返回体含 "ok" 即命中（经典家族根路径 /health，绝对路径） */
    val healthFingerprint: String? = null,
    /** 免认证指纹 B：返回 JSON 外壳且 code==200 即命中（绝对路径） */
    val jsonFingerprint: String? = null,
) {
    fun url(relative: String): String = apiPrefix + relative

    fun baseInfo(io: String = "all", net: String = "all"): String =
        baseInfoPath.replace("{io}", io).replace("{net}", net)

    fun current(): String =
        currentPath.replace("{io}", "all").replace("{net}", "all")
}

object Dialects {

    /** 1.1 ~ 1.10.33：backend 单体 + /api/v1 */
    val V1 = PanelDialect(
        line = PanelLine.V1,
        apiPrefix = "/api/v1",
        healthFingerprint = "/health",
        jsonFingerprint = "/api/v1/auth/setting",
    )

    /**
     * 1.10.34+ 与 2.x：core/agent 新架构 + /api/v2。
     * 面板自身接口（settings/auth）在 /api/v2/core 下，主机类接口（dashboard/containers/apps/websites）在 /api/v2 下。
     */
    val V2 = PanelDialect(
        line = PanelLine.V2,
        apiPrefix = "/api/v2",
        currentPath = "/dashboard/current/{io}/{net}",
        currentHasBody = false,
        orderByCreated = "createdAt",
        versionProbePath = "/core/settings/search",
        jsonFingerprint = "/api/v2/core/auth/setting",
    )

    /** v3/v4 官方尚未发布：先按 v2 契约执行，官方发布后在这里替换即可 */
    val V3 = V2.copy(line = PanelLine.V3)
    val V4 = V2.copy(line = PanelLine.V4)

    /** 1.0~1.9：无 API 密钥、无 /base/os、current 走 GET + 路径参数 */
    val LEGACY = PanelDialect(
        line = PanelLine.LEGACY,
        apiPrefix = "/api/v1",
        supportsApiKey = false,
        osInfoPath = null,
        currentPath = "/dashboard/current/{io}/{net}",
        currentHasBody = false,
        containerStatsPath = null,
        versionProbePath = null,
        healthFingerprint = "/health",
    )

    fun of(line: PanelLine): PanelDialect = when (line) {
        PanelLine.V1 -> V1
        PanelLine.V2, PanelLine.AUTO -> V2
        PanelLine.V3 -> V3
        PanelLine.V4 -> V4
        PanelLine.LEGACY -> LEGACY
    }

    /** 免认证指纹的尝试顺序 */
    val fingerprintOrder: List<PanelDialect> = listOf(V1, V2)

    /** 带密钥探测的尝试顺序（LEGACY 不参与，它由版本号判定） */
    val probeOrder: List<PanelDialect> = listOf(V1, V2, V3, V4)
}

/** 探测结果 */
data class DetectedPanel(
    val line: PanelLine,
    val versionText: String,
    /** settings = 面板自报版本号；probe = 免认证指纹；keyed = 带密钥探测；manual = 用户手动指定 */
    val source: String,
) {
    fun display(): String = when {
        versionText.isNotBlank() -> "$versionText · ${line.label}"
        line == PanelLine.AUTO -> "未识别"
        source == "probe" || source == "keyed" -> "${line.label}（探测）"
        else -> line.label
    }

    /** 手动选家族、但面板自报版本号指向另一个家族时给出提醒 */
    fun mismatchWarning(): String? {
        if (source != "manual" || versionText.isBlank()) return null
        val hint = VersionParser.hint(versionText) ?: return null
        if (hint == line) return null
        return "面板自报版本 $versionText 与所选「${line.label}」不一致，建议改选「自动」或按提示切换。"
    }
}

object VersionParser {

    private val VERSION_REGEX = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

    /**
     * 面板版本号 → 家族「提示」。注意：1.10.34+ 已经换到新架构，
     * 所以这个结果只用于识别「1.0~1.9 不支持密钥」以及界面展示，
     * 真正选方言请用接口探测结果。
     */
    fun hint(text: String): PanelLine? {
        val match = VERSION_REGEX.find(text.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        return when {
            major >= 4 -> PanelLine.V4
            major == 3 -> PanelLine.V3
            major == 2 -> PanelLine.V2
            major == 1 && minor >= 34 -> PanelLine.V2
            major == 1 && minor >= 10 -> PanelLine.V1
            major == 1 -> PanelLine.LEGACY
            else -> null
        }
    }
}
