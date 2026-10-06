package com.panelone.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panelone.client.data.PanelLine
import com.panelone.client.data.Repo
import com.panelone.client.data.ServerConfig
import com.panelone.client.vm.ServersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(vm: ServersViewModel = viewModel()) {
    var editing by remember { mutableStateOf<ServerConfig?>(null) }
    var showHelp by remember { mutableStateOf(Repo.servers.isEmpty()) }
    var pendingDelete by remember { mutableStateOf<ServerConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PanelOne · 面板助手") },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "使用说明")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.clearTest(); editing = ServerConfig() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加服务器") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (Repo.servers.isEmpty()) {
                EmptyServersHint()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(Repo.servers, key = { it.id }) { server ->
                        ServerCard(
                            config = server,
                            isActive = Repo.active?.id == server.id,
                            onSelect = { vm.select(server.id) },
                            onEdit = { vm.clearTest(); editing = server },
                            onDelete = { pendingDelete = server },
                        )
                    }
                }
            }
        }
    }

    editing?.let { config ->
        ServerEditDialog(
            initial = config,
            vm = vm,
            onDismiss = { editing = null; vm.clearTest() },
            onSaved = { editing = null; vm.clearTest() },
        )
    }

    pendingDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除服务器") },
            text = { Text("确定删除「${config.displayName()}」吗？只会删除 APP 里的配置。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(config.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
}

@Composable
private fun ServerCard(
    config: ServerConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = config.hostText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "版本：${config.line.label}${if (config.trustAll) " · 忽略证书" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditDialog(
    initial: ServerConfig,
    vm: ServersViewModel,
    onDismiss: () -> Unit,
    onSaved: (ServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var trustAll by remember { mutableStateOf(initial.trustAll) }
    var line by remember { mutableStateOf(initial.line) }
    var lineMenu by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    val canSave = url.isNotBlank() && apiKey.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "添加服务器" else "编辑服务器") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("备注名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("面板地址") },
                    placeholder = { Text("http://192.168.1.10:12345") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API 密钥") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(
                        onClick = { lineMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("面板版本：${line.label}")
                    }
                    DropdownMenu(expanded = lineMenu, onDismissRequest = { lineMenu = false }) {
                        PanelLine.selectable.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${item.label} · ${item.desc}")
                                    }
                                },
                                onClick = {
                                    line = item
                                    lineMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = trustAll, onCheckedChange = { trustAll = it })
                    Spacer(Modifier.width(8.dp))
                    Text("忽略证书错误（自签名 HTTPS）", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        vm.test(
                            initial.copy(
                                name = name.trim(),
                                baseUrl = url.trim(),
                                apiKey = apiKey.trim(),
                                trustAll = trustAll,
                                line = line,
                            ),
                        )
                    },
                    enabled = canSave && !vm.testing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (vm.testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("测试连接")
                }
                vm.testResult?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                vm.testError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val saved = vm.save(
                        initial.copy(
                            name = name.trim(),
                            baseUrl = url.trim(),
                            apiKey = apiKey.trim(),
                            trustAll = trustAll,
                            line = line,
                        ),
                    )
                    onSaved(saved)
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EmptyServersHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有添加面板", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点右下角「添加服务器」，填入面板地址和 API 密钥即可。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        ApiKeyGuide()
    }
}

@Composable
fun ApiKeyGuide() {
    SectionCard(title = "如何在 1Panel 里拿到 API 密钥") {
        GuideStep(1, "登录面板网页端，进入「面板设置 → API 接口」")
        GuideStep(2, "打开接口开关（未开启时所有接口都会返回 401）")
        GuideStep(3, "点「创建密钥」，复制生成的 API 密钥")
        GuideStep(4, "把手机的出口 IP 加入「IP 白名单」（填 0.0.0.0/0 表示不限制，安全性较低）")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "接口签名规则：1Panel-Token = md5(\"1panel\" + 密钥 + 秒级时间戳)，APP 已自动处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GuideStep(index: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用说明") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ApiKeyGuide()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "版本选择：1Panel 目前只有两代接口——「v1 · 经典」（1.1 ~ 1.10.33，前缀 /api/v1）与" +
                        "「v2 · 新架构」（1.10.34 及以上、2.x，前缀 /api/v2）。不确定就选「自动」：" +
                        "APP 会先用免认证接口指纹判断属于哪一代，再读一次版本号用于展示；v3/v4 是预留位，" +
                        "当前按 v2 契约执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
fun ConnectingScreen(message: String = "正在连接面板…") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConnectErrorScreen(serverName: String, error: String, onRetry: () -> Unit, onSwitch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("连接「$serverName」失败", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(20.dp))
        Row {
            Button(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("重试")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onSwitch) { Text("更换服务器") }
        }
        Spacer(Modifier.height(28.dp))
        ApiKeyGuide()
    }
}
