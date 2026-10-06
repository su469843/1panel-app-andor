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
import com.panelone.client.data.InstalledApp
import com.panelone.client.data.Repo
import com.panelone.client.vm.AppsViewModel

@Composable
fun AppsScreen(vm: AppsViewModel = viewModel()) {
    val serverId = Repo.active?.id
    val context = LocalContext.current

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
            placeholder = { Text("按名称搜索已安装应用") },
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

        StateBox(
            loading = vm.loading && vm.items.isEmpty(),
            error = if (vm.items.isEmpty()) vm.error else null,
            onRetry = { vm.refresh() },
            empty = vm.items.isEmpty(),
            emptyText = "还没有安装任何应用",
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.items, key = { it.id }) { app ->
                    AppCard(
                        app = app,
                        busy = vm.busyIds.contains(app.id),
                        onOperate = { operation -> vm.operate(app, operation) },
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
            }
        }
    }
}

@Composable
private fun AppCard(app: InstalledApp, busy: Boolean, onOperate: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val running = isRunningState(app.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.appName.ifBlank { app.name },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (app.canUpdate) {
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("可升级") }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "v${app.version.ifBlank { "-" }} · ${app.name}",
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
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(app.status)
                if (app.httpPort > 0 || app.httpsPort > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = buildString {
                            if (app.httpPort > 0) append("HTTP ${app.httpPort}")
                            if (app.httpsPort > 0) {
                                if (isNotEmpty()) append(" · ")
                                append("HTTPS ${app.httpsPort}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (app.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            if (app.path.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "目录：${app.path}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
