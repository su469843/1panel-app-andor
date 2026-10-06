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
import com.panelone.client.data.Repo
import com.panelone.client.data.WebsiteItem
import com.panelone.client.vm.WebsitesViewModel

@Composable
fun WebsitesScreen(vm: WebsitesViewModel = viewModel()) {
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
            placeholder = { Text("按域名搜索网站") },
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
            emptyText = "还没有创建网站",
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.items, key = { it.id }) { site ->
                    WebsiteCard(
                        site = site,
                        busy = vm.busyIds.contains(site.id),
                        onOperate = { operation -> vm.operate(site, operation) },
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
private fun WebsiteCard(site: WebsiteItem, busy: Boolean, onOperate: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val running = isRunningState(site.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = site.primaryDomain.ifBlank { site.alias },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(site.type.ifBlank { "静态" })
                            if (site.protocol.isNotBlank()) append(" · ${site.protocol.uppercase()}")
                            if (site.proxy.isNotBlank()) append(" · 反代 ${site.proxy}")
                        },
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
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(site.status)
                if (site.expireDate.isNotBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "到期 ${site.expireDate.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (site.siteDir.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "目录：${site.siteDir}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
