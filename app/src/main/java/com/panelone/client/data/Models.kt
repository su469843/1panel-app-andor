package com.panelone.client.data

import kotlinx.serialization.Serializable

/* ------------------------------------------------------------------
 * 通用响应包装：1Panel 所有接口都返回 {code, message, data}
 * ------------------------------------------------------------------ */

@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
)

@Serializable
data class PageResult<T>(
    val total: Long = 0,
    val items: List<T> = emptyList(),
)

/* ------------------------------------------------------------------
 * 概览 / 监控
 * 字段名与 1Panel 源码 backend/app/dto/dashboard.go 的 json tag 一一对应，
 * 全部给默认值，保证面板升级新增字段时不会解析失败。
 * ------------------------------------------------------------------ */

@Serializable
data class DashboardBase(
    val websiteNumber: Int = 0,
    val databaseNumber: Int = 0,
    val cronjobNumber: Int = 0,
    val appInstalledNumber: Int = 0,
    /** 新架构（1.10.34+/2.x）才有：被管理的 agent 数量 */
    val agentNumber: Int = 0,
    val hostname: String = "",
    val os: String = "",
    val platform: String = "",
    val platformFamily: String = "",
    val platformVersion: String = "",
    val kernelArch: String = "",
    val kernelVersion: String = "",
    val virtualizationSystem: String = "",
    val ipv4Addr: String = "",
    val cpuCores: Int = 0,
    val cpuLogicalCores: Int = 0,
    val cpuModelName: String = "",
    val currentInfo: DashboardCurrent? = null,
)

@Serializable
data class DashboardCurrent(
    val uptime: Long = 0,
    val timeSinceUptime: String = "",
    val procs: Long = 0,
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    val loadUsagePercent: Double = 0.0,
    val cpuPercent: List<Double> = emptyList(),
    val cpuUsedPercent: Double = 0.0,
    val cpuTotal: Int = 0,
    val memoryTotal: Long = 0,
    val memoryAvailable: Long = 0,
    /** 新架构用 memoryFree 代替 memoryAvailable */
    val memoryFree: Long = 0,
    val memoryShard: Long = 0,
    val memoryUsed: Long = 0,
    val memoryUsedPercent: Double = 0.0,
    val swapMemoryTotal: Long = 0,
    val swapMemoryUsed: Long = 0,
    val swapMemoryUsedPercent: Double = 0.0,
    val ioReadBytes: Long = 0,
    val ioWriteBytes: Long = 0,
    val netBytesSent: Long = 0,
    val netBytesRecv: Long = 0,
    val diskData: List<DiskInfo> = emptyList(),
    val shotTime: String = "",
)

@Serializable
data class DiskInfo(
    val path: String = "",
    val type: String = "",
    val device: String = "",
    val total: Long = 0,
    val free: Long = 0,
    val used: Long = 0,
    val usedPercent: Double = 0.0,
)

/** 概览页需要的一次性数据集：系统信息 + 实时占用 */
data class Overview(
    val base: DashboardBase,
    val current: DashboardCurrent,
)

/* ------------------------------------------------------------------
 * 容器（backend/app/dto/container.go）
 * ------------------------------------------------------------------ */

@Serializable
data class ContainerItem(
    val containerID: String = "",
    val name: String = "",
    val imageName: String = "",
    val createTime: String = "",
    val state: String = "",
    val runTime: String = "",
    val network: List<String> = emptyList(),
    val ports: List<String> = emptyList(),
    val isFromApp: Boolean = false,
    val isFromCompose: Boolean = false,
    val appName: String = "",
    val appInstallName: String = "",
    val websites: List<String> = emptyList(),
)

@Serializable
data class ContainerStatsItem(
    val containerID: String = "",
    val cpuPercent: Double = 0.0,
    val memoryPercent: Double = 0.0,
    val memoryUsage: Long = 0,
    val memoryLimit: Long = 0,
)

@Serializable
data class PageContainerReq(
    val page: Int = 1,
    val pageSize: Int = 50,
    val name: String = "",
    val state: String = "all",
    val orderBy: String = "created_at",
    val order: String = "descending",
    val filters: String = "",
    val excludeAppStore: Boolean = false,
)

@Serializable
data class ContainerOperationReq(
    val names: List<String>,
    val operation: String,
)

/* ------------------------------------------------------------------
 * 应用（backend/app/dto/response/app.go 的 AppInstalledDTO + model/app_install.go）
 * ------------------------------------------------------------------ */

@Serializable
data class InstalledApp(
    val id: Long = 0,
    val name: String = "",
    val appId: Long = 0,
    val appDetailId: Long = 0,
    val version: String = "",
    val status: String = "",
    val description: String = "",
    val message: String = "",
    val containerName: String = "",
    val serviceName: String = "",
    val httpPort: Int = 0,
    val httpsPort: Int = 0,
    val appName: String = "",
    val icon: String = "",
    val canUpdate: Boolean = false,
    val path: String = "",
    val createdAt: String = "",
)

@Serializable
data class AppInstalledSearchReq(
    val page: Int = 1,
    val pageSize: Int = 50,
    val type: String = "",
    val name: String = "",
    val tags: List<String> = emptyList(),
    val update: Boolean = false,
    val unused: Boolean = false,
    val all: Boolean = false,
    val sync: Boolean = false,
)

@Serializable
data class AppInstalledOperateReq(
    val installId: Long,
    val operate: String,
)

/* ------------------------------------------------------------------
 * 网站（response.WebsiteDTO + model/website.go）
 * ------------------------------------------------------------------ */

@Serializable
data class WebsiteItem(
    val id: Long = 0,
    val protocol: String = "",
    val primaryDomain: String = "",
    val type: String = "",
    val alias: String = "",
    val remark: String = "",
    val status: String = "",
    val proxy: String = "",
    val proxyType: String = "",
    val siteDir: String = "",
    val errorLog: Boolean = false,
    val accessLog: Boolean = false,
    val expireDate: String = "",
    val appName: String = "",
    val runtimeName: String = "",
    val webSiteGroupId: Long = 0,
)

@Serializable
data class WebsiteSearchReq(
    val page: Int = 1,
    val pageSize: Int = 50,
    val name: String = "",
    val orderBy: String = "created_at",
    val order: String = "descending",
    val websiteGroupId: Long = 0,
)

@Serializable
data class WebsiteOperateReq(
    val id: Long,
    val operate: String,
)

/* ------------------------------------------------------------------
 * 面板信息（用于「自动」模式识别版本）
 * 字段按 1Panel dto.SettingInfo 的常见命名给出；识别不到就回退到接口探测。
 * ------------------------------------------------------------------ */

@Serializable
data class PanelSettingInfo(
    val systemVersion: String = "",
    val version: String = "",
    val panelName: String = "",
    val systemIP: String = "",
    val isDemo: Boolean = false,
    val isIntl: Boolean = false,
    val isOffline: Boolean = false,
) {
    fun versionText(): String = when {
        systemVersion.isNotBlank() -> systemVersion
        version.isNotBlank() -> version
        else -> ""
    }
}

/** 概览接口的请求体（v1.10 与 v2 均为 POST /dashboard/current） */
@Serializable
data class DashboardCurrentReq(
    val scope: String = "all",
    val ioOption: String = "all",
    val netOption: String = "all",
)
