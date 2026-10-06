package com.panelone.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panelone.client.data.PanelLine
import com.panelone.client.data.Repo
import com.panelone.client.vm.ServersViewModel

@Composable
fun SettingsScreen(vm: ServersViewModel = viewModel()) {
    val server = Repo.active

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "当前连接") {
            InfoRow("名称", server?.displayName() ?: "-")
            InfoRow("地址", server?.hostText() ?: "-")
            InfoRow(
                "连接状态",
                if (Repo.isConnected()) "已连接" else "未连接",
            )
            InfoRow("识别结果", Repo.detected?.display() ?: "未识别")
            Repo.detected?.mismatchWarning()?.let { warning ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(10.dp))

            var lineMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { lineMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("面板版本：${server?.line?.label ?: PanelLine.AUTO.label}")
                }
                DropdownMenu(expanded = lineMenu, onDismissRequest = { lineMenu = false }) {
                    PanelLine.selectable.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.label} · ${item.desc}") },
                            onClick = {
                                lineMenu = false
                                server?.let { current ->
                                    vm.save(current.copy(line = item))
                                    vm.connect()
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "选「自动」时，APP 会先用免认证指纹判断接口家族（/health → v1 经典；/api/v2/core/auth/setting → v2 新架构），" +
                    "再用密钥读一次版本号展示。官方出现过「版本号 1.10.34 但接口已是新架构」的情况，" +
                    "所以家族判定以接口探测为准。某个页面提示接口不存在时，手动切换家族再试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "服务器") {
            Button(
                onClick = { Repo.disconnect() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("更换 / 添加服务器")
            }
        }

        SectionCard(title = "安全提示") {
            Text(
                text = "· API 密钥以明文保存在本机 SharedPreferences 中，请勿把手机借给他人或截图外发。\n" +
                    "· 建议只在可信内网 / VPN 下使用，并给面板配置 HTTPS。\n" +
                    "· 面板「API 接口」的 IP 白名单尽量填写具体 IP，不要长期开着 0.0.0.0/0。\n" +
                    "· 本 APP 只调用 1Panel 官方接口，不收集、不上传任何数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "关于") {
            InfoRow("APP 版本", "1.0.0")
            InfoRow("支持面板", "v1 经典（1.1~1.10.33）/ v2 新架构（1.10.34+、2.x）/ v3·v4 预留")
            InfoRow("接口认证", "1Panel-Token = md5(\"1panel\" + 密钥 + 时间戳)")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "接口路径取自 1Panel 官方源码 backend/router/ro_*.go，v1.10 LTS 与 v2.x 契约一致。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "PanelOne · 非官方客户端",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
