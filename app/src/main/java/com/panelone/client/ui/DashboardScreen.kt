package com.panelone.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panelone.client.data.DashboardCurrent
import com.panelone.client.data.Repo
import com.panelone.client.vm.DashboardViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private const val REFRESH_INTERVAL_MS = 5000L

private fun DashboardCurrent.cpuPercentValue(): Double = when {
    cpuUsedPercent > 0.0 -> cpuUsedPercent
    cpuPercent.isNotEmpty() -> cpuPercent.last()
    else -> 0.0
}

private fun DashboardCurrent.memoryPercentValue(): Double = when {
    memoryUsedPercent > 0.0 -> memoryUsedPercent
    memoryTotal > 0 -> memoryUsed.toDouble() * 100.0 / memoryTotal.toDouble()
    else -> 0.0
}

/** 经典家族给 memoryAvailable，新架构给 memoryFree */
private fun DashboardCurrent.memoryFreeValue(): Long = when {
    memoryAvailable > 0 -> memoryAvailable
    memoryFree > 0 -> memoryFree
    memoryTotal > memoryUsed -> memoryTotal - memoryUsed
    else -> 0
}

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val serverId = Repo.active?.id

    LaunchedEffect(serverId) {
        if (serverId == null) return@LaunchedEffect
        vm.reset()
        while (true) {
            vm.refresh()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    val overview = vm.data

    StateBox(
        loading = vm.loading && overview == null,
        error = if (overview == null) vm.error else null,
        onRetry = { vm.refresh() },
    ) {
        if (overview == null) {
            ConnectingScreen("正在读取面板状态…")
            return@StateBox
        }
        val base = overview.base
        val current = overview.current

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "系统信息") {
                    InfoRow("主机名", base.hostname)
                    InfoRow("系统", listOf(base.platform, base.platformVersion).filter { it.isNotBlank() }.joinToString(" "))
                    InfoRow("内核", "${base.kernelVersion} (${base.kernelArch})")
                    InfoRow("CPU", base.cpuModelName)
                    InfoRow("核心数", "${base.cpuCores} 核 / ${base.cpuLogicalCores} 线程")
                    InfoRow("IP", base.ipv4Addr)
                    InfoRow("运行时长", current.timeSinceUptime.ifBlank { formatUptime(current.uptime) })
                }
            }

            item {
                SectionCard(
                    title = "CPU",
                    trailing = { Text(formatPercent(current.cpuPercentValue()), fontWeight = FontWeight.Bold) },
                ) {
                    Sparkline(
                        values = vm.history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        maxValue = 100f,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LoadItem("1 分钟", current.load1, Modifier.weight(1f))
                        LoadItem("5 分钟", current.load5, Modifier.weight(1f))
                        LoadItem("15 分钟", current.load15, Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionCard(title = "内存") {
                    MetricBar(
                        label = "已用 ${formatBytes(current.memoryUsed)} / ${formatBytes(current.memoryTotal)}",
                        percent = current.memoryPercentValue(),
                        detail = "可用 ${formatBytes(current.memoryFreeValue())}",
                    )
                    if (current.swapMemoryTotal > 0) {
                        Spacer(Modifier.height(14.dp))
                        MetricBar(
                            label = "Swap ${formatBytes(current.swapMemoryUsed)} / ${formatBytes(current.swapMemoryTotal)}",
                            percent = current.swapMemoryUsedPercent,
                            barColor = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            if (current.diskData.isNotEmpty()) {
                item {
                    SectionCard(title = "磁盘") {
                        current.diskData.forEachIndexed { index, disk ->
                            if (index > 0) Spacer(Modifier.height(14.dp))
                            MetricBar(
                                label = "${disk.path}  ${formatBytes(disk.used)} / ${formatBytes(disk.total)}",
                                percent = disk.usedPercent,
                                barColor = diskColor(disk.usedPercent),
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "网络与磁盘 IO") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LoadItem("上传", current.netBytesSent.toDouble(), Modifier.weight(1f), isBytes = true)
                        LoadItem("下载", current.netBytesRecv.toDouble(), Modifier.weight(1f), isBytes = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LoadItem("读", current.ioReadBytes.toDouble(), Modifier.weight(1f), isBytes = true)
                        LoadItem("写", current.ioWriteBytes.toDouble(), Modifier.weight(1f), isBytes = true)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountCard("网站", base.websiteNumber, Modifier.weight(1f))
                    CountCard("应用", base.appInstalledNumber, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountCard("数据库", base.databaseNumber, Modifier.weight(1f))
                    CountCard("计划任务", base.cronjobNumber, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LoadItem(label: String, value: Double, modifier: Modifier = Modifier, isBytes: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (isBytes) formatBytes(value.toLong()) else String.format(Locale.US, "%.2f", value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CountCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun diskColor(percent: Double) = when {
    percent >= 90 -> MaterialTheme.colorScheme.error
    percent >= 75 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}
