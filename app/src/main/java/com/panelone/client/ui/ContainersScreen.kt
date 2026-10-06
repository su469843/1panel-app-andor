package com.panelone.client.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panelone.client.R
import com.panelone.client.data.ContainerItem
import com.panelone.client.data.Repo
import com.panelone.client.vm.ContainersViewModel

@Composable
fun ContainersScreen(vm: ContainersViewModel = viewModel()) {
    val serverId = Repo.active?.id
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<ContainerItem?>(null) }

    LaunchedEffect(serverId) {
        if (serverId == null) return@LaunchedEffect
        vm.reset()
        vm.refresh()
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { text ->
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.keyword,
            onValueChange = { vm.setKeyword(it) },
            placeholder = { Text("按名称搜索容器") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.refresh() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = vm.filter == "all",
                onClick = { vm.setFilter("all") },
                label = { Text("全部") },
            )
            FilterChip(
                selected = vm.filter == "running",
                onClick = { vm.setFilter("running") },
                label = { Text("运行中") },
            )
            FilterChip(
                selected = vm.filter == "exited",
                onClick = { vm.setFilter("exited") },
                label = { Text("已停止") },
            )
        }

        StateBox(
            loading = vm.loading && vm.items.isEmpty(),
            error = if (vm.items.isEmpty()) vm.error else null,
            onRetry = { vm.refresh() },
            empty = vm.items.isEmpty(),
            emptyText = "没有匹配的容器",
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.items, key = { it.containerID.ifBlank { it.name } }) { container ->
                    ContainerCard(
                        container = container,
                        busy = vm.busyNames.contains(container.name),
                        cpuPercent = vm.stats[container.containerID]?.cpuPercent,
                        memoryPercent = vm.stats[container.containerID]?.memoryPercent,
                        onOperate = { operation -> vm.operate(container.name, operation) },
                        onDelete = { pendingDelete = container },
                    )
                }
                if (vm.items.size.toLong() < vm.total) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            OutlinedButton(onClick = { vm.loadMore() }, enabled = !vm.loading) {
                                Text("加载更多（${vm.items.size} / ${vm.total}）")
                            }
                        }
                    }
                }
                if (vm.error != null && vm.items.isNotEmpty()) {
                    item {
                        Text(
                            text = vm.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { container ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除容器") },
            text = { Text("确定删除容器「${container.name}」吗？该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.operate(container.name, "remove")
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ContainerCard(
    container: ContainerItem,
    busy: Boolean,
    cpuPercent: Double?,
    memoryPercent: Double?,
    onOperate: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val running = isRunningState(container.state)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = container.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = container.imageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { onOperate(if (running) "stop" else "start") }) {
                        if (running) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_stop),
                                contentDescription = "停止",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "启动",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("启动") },
                            enabled = !running,
                            onClick = { menu = false; onOperate("start") },
                        )
                        DropdownMenuItem(
                            text = { Text("停止") },
                            enabled = running,
                            onClick = { menu = false; onOperate("stop") },
                        )
                        DropdownMenuItem(
                            text = { Text("重启") },
                            onClick = { menu = false; onOperate("restart") },
                        )
                        DropdownMenuItem(
                            text = { Text("暂停") },
                            enabled = running,
                            onClick = { menu = false; onOperate("pause") },
                        )
                        DropdownMenuItem(
                            text = { Text("恢复") },
                            onClick = { menu = false; onOperate("unpause") },
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(container.state)
                if (container.ports.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = container.ports.take(3).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            if (cpuPercent != null || memoryPercent != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "CPU ${formatPercent(cpuPercent ?: 0.0)} · 内存 ${formatPercent(memoryPercent ?: 0.0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (container.isFromApp || container.isFromCompose) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        if (container.isFromApp) append("应用商店部署")
                        if (container.isFromCompose) {
                            if (isNotEmpty()) append(" · ")
                            append("Compose 编排")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
