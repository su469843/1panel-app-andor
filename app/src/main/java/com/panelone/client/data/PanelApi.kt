package com.panelone.client.data

import kotlinx.serialization.encodeToString

/**
 * 高层业务接口：把「接口家族差异」全部吃掉，界面层只调这些方法。
 * 路径取自 1Panel 官方源码：
 *  - 经典家族：backend/router/ro_*.go（1.10.33-lts 实测）
 *  - 新架构  ：agent/router/ro_*.go + core/router/ro_*.go（2.3.2 实测）
 * 两者的相对路径一致，差异只有前缀与少量参数形态，全部由 [PanelDialect] 描述。
 */

private fun DashboardCurrent.isEmpty(): Boolean =
    cpuTotal == 0 && memoryTotal == 0L && diskData.isEmpty()

/* -------------------- 概览 -------------------- */

suspend fun PanelClient.overview(): Overview {
    val base = fetchBase()
    return Overview(base, fetchCurrent(base))
}

private suspend fun PanelClient.fetchBase(): DashboardBase {
    // 首选基础信息接口：经典家族的返回里直接带 currentInfo，新架构也保留主机信息与数量统计
    runCatching { getData<DashboardBase>(dialect.url(dialect.baseInfo())) }.getOrNull()?.let { return it }
    // 退路：只有系统信息接口的版本
    val osPath = dialect.osInfoPath
        ?: throw ApiException(-5, "当前家族的 ${dialect.apiPrefix} 没有可用的系统信息接口")
    return getData(dialect.url(osPath))
}

private suspend fun PanelClient.fetchCurrent(base: DashboardBase): DashboardCurrent {
    val live = runCatching { currentLive() }.getOrNull()
    if (live != null && !live.isEmpty()) return live
    base.currentInfo?.takeIf { !it.isEmpty() }?.let { return it }
    return live ?: DashboardCurrent()
}

private suspend fun PanelClient.currentLive(): DashboardCurrent =
    if (dialect.currentHasBody) {
        postData(dialect.url(dialect.current()), PanelJson.encodeToString(DashboardCurrentReq()))
    } else {
        getData(dialect.url(dialect.current()))
    }

/* -------------------- 容器 -------------------- */

suspend fun PanelClient.containers(
    page: Int = 1,
    pageSize: Int = 50,
    name: String = "",
    state: String = "all",
): PageResult<ContainerItem> = postData(
    dialect.url(dialect.containerSearchPath),
    PanelJson.encodeToString(
        PageContainerReq(
            page = page,
            pageSize = pageSize,
            name = name,
            state = state,
            orderBy = dialect.orderByCreated,
        ),
    ),
)

/** 容器实时 CPU/内存占用；老版本没有该接口时返回空表，不影响列表展示 */
suspend fun PanelClient.containerStats(): Map<String, ContainerStatsItem> {
    val path = dialect.containerStatsPath ?: return emptyMap()
    val list = runCatching { getData<List<ContainerStatsItem>>(dialect.url(path)) }.getOrDefault(emptyList())
    return list.associateBy { it.containerID }
}

suspend fun PanelClient.containerOperate(names: List<String>, operation: String) = action(
    "POST",
    dialect.url(dialect.containerOperatePath),
    PanelJson.encodeToString(ContainerOperationReq(names = names, operation = operation)),
)

/* -------------------- 应用 -------------------- */

suspend fun PanelClient.installedApps(
    page: Int = 1,
    pageSize: Int = 50,
    name: String = "",
): PageResult<InstalledApp> = postData(
    dialect.url(dialect.appSearchPath),
    PanelJson.encodeToString(AppInstalledSearchReq(page = page, pageSize = pageSize, name = name)),
)

suspend fun PanelClient.appOperate(installId: Long, operate: String) = action(
    "POST",
    dialect.url(dialect.appOperatePath),
    PanelJson.encodeToString(AppInstalledOperateReq(installId = installId, operate = operate)),
)

/* -------------------- 网站 -------------------- */

suspend fun PanelClient.websites(
    page: Int = 1,
    pageSize: Int = 50,
    name: String = "",
): PageResult<WebsiteItem> = postData(
    dialect.url(dialect.websiteSearchPath),
    PanelJson.encodeToString(
        WebsiteSearchReq(
            page = page,
            pageSize = pageSize,
            name = name,
            orderBy = dialect.orderByCreated,
        ),
    ),
)

suspend fun PanelClient.websiteOperate(id: Long, operate: String) = action(
    "POST",
    dialect.url(dialect.websiteOperatePath),
    PanelJson.encodeToString(WebsiteOperateReq(id = id, operate = operate)),
)

/* -------------------- 接口家族识别 -------------------- */

/** 面板自报版本号（POST {前缀}/settings/search → data.systemVersion），取不到返回 null */
suspend fun PanelClient.panelVersionText(): String? {
    val relative = dialect.versionProbePath ?: return null
    val info = runCatching { postData<PanelSettingInfo>(dialect.url(relative), "{}") }.getOrNull()
    return info?.versionText()?.ifBlank { null }
}

/**
 * 判断某个家族是否命中。两条线各有一个免认证接口可以当指纹：
 *  - 经典家族：GET /health 返回 "ok"
 *  - 新架构  ：GET /api/v2/core/auth/setting 返回正常 JSON 外壳
 */
private suspend fun PanelClient.fingerprintHit(candidate: PanelDialect): Boolean {
    candidate.healthFingerprint?.let { path ->
        // 经典家族 /health 返回 c.JSON(200,"ok")，即响应体是 "ok"（含引号）。
        // 这里用严格比较，避免误把 HTML 报错页里的 "ok" 子串当成命中。
        val body = runCatching { raw("GET", path, null, requireJsonShape = false) }.getOrNull()?.trim()
        if (body == "\"ok\"" || body == "ok") return true
    }
    candidate.jsonFingerprint?.let { path ->
        if (runCatching { action("GET", path) }.isSuccess) return true
    }
    return false
}

/**
 * 探测接口家族：
 * 1) 先用两个免认证指纹（各 1 次请求，最省事）；
 * 2) 指纹被安全入口挡住时，带密钥用「版本号接口」判定；
 * 3) 再不行就用「实时占用」接口的形态差异判定（POST+body vs GET+路径参数）；
 * 4) 全部失败时回落到经典家族（1.10.33 LTS 装机量最大），并让用户可手动指定。
 */
private suspend fun PanelClient.probeFamily(): Pair<PanelLine, String> {
    for (candidate in Dialects.fingerprintOrder) {
        if (fingerprintHit(candidate)) return candidate.line to "probe"
    }
    for (candidate in Dialects.probeOrder) {
        dialect = candidate
        if (runCatching { panelVersionText() }.getOrNull() != null) return candidate.line to "keyed"
    }
    for (candidate in Dialects.probeOrder) {
        dialect = candidate
        val hit = runCatching {
            if (candidate.currentHasBody) {
                postData<DashboardCurrent>(
                    candidate.url(candidate.current()),
                    PanelJson.encodeToString(DashboardCurrentReq()),
                )
            } else {
                getData<DashboardCurrent>(candidate.url(candidate.current()))
            }
        }.isSuccess
        if (hit) return candidate.line to "keyed"
    }
    return PanelLine.V1 to "fallback"
}

/**
 * 识别面板：先定接口家族（决定方言），再读一次版本号仅用于展示。
 * 因为官方出现过「版本号是 1.10.34 但接口已经是新架构」的情况，
 * 家族判定永远以接口探测为准，绝不按版本号猜。
 */
suspend fun PanelClient.detect(): DetectedPanel {
    val (line, source) = probeFamily()
    dialect = Dialects.of(line)
    val version = runCatching { panelVersionText() }.getOrNull().orEmpty()

    if (version.isNotBlank() && VersionParser.hint(version) == PanelLine.LEGACY) {
        throw ApiException(
            -6,
            "检测到面板版本 $version（1.0~1.9），该版本不支持 API 密钥认证，请升级到 1.10 LTS 及以上。",
        )
    }
    return DetectedPanel(
        line = line,
        versionText = version,
        source = if (version.isNotBlank()) "settings" else source,
    )
}

class ResolvedPanel(val client: PanelClient, val detected: DetectedPanel)

/**
 * 根据「服务器配置里的版本选择」解析出可用的客户端。
 * 选了具体版本就完全尊重用户选择（只额外读一次版本号用于展示与不一致提示）；
 * 选了「自动」才做探测。
 */
suspend fun resolvePanel(config: ServerConfig): ResolvedPanel {
    if (config.baseUrl.isBlank()) throw ApiException(-1, "请先填写面板地址")

    if (config.line == PanelLine.AUTO) {
        val client = PanelClient(config, Dialects.V2)
        return ResolvedPanel(client, client.detect())
    }

    val client = PanelClient(config, Dialects.of(config.line))
    val text = runCatching { client.panelVersionText() }.getOrNull().orEmpty()
    return ResolvedPanel(client, DetectedPanel(config.line, text, "manual"))
}

/** 「测试连接」按钮：只做解析与一次真实请求，不改变当前选中的服务器 */
suspend fun testConnection(config: ServerConfig): DetectedPanel {
    val resolved = resolvePanel(config)
    resolved.client.overview()
    return resolved.detected
}
