package com.panelone.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panelone.client.data.Repo
import com.panelone.client.ui.AppsScreen
import com.panelone.client.ui.ConnectErrorScreen
import com.panelone.client.ui.ConnectingScreen
import com.panelone.client.ui.ContainersScreen
import com.panelone.client.ui.DashboardScreen
import com.panelone.client.ui.ServerListScreen
import com.panelone.client.ui.SettingsScreen
import com.panelone.client.ui.WebsitesScreen
import com.panelone.client.ui.theme.PanelOneTheme
import com.panelone.client.vm.ServersViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(applicationContext)
        setContent {
            PanelOneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val server = Repo.active

    if (server == null) {
        ServerListScreen()
        return
    }

    LaunchedEffect(server.id) {
        if (!Repo.isConnected()) {
            Repo.connect()
        }
    }

    val error = Repo.connectError
    when {
        Repo.isConnected() -> MainScaffold()
        error != null -> {
            val vm: ServersViewModel = viewModel()
            ConnectErrorScreen(
                serverName = server.displayName(),
                error = error,
                onRetry = { vm.connect() },
                onSwitch = { Repo.disconnect() },
            )
        }
        else -> ConnectingScreen()
    }
}

private data class TabItem(val label: String, val iconRes: Int)

private val TABS = listOf(
    TabItem("概览", R.drawable.ic_tab_dashboard),
    TabItem("容器", R.drawable.ic_tab_container),
    TabItem("应用", R.drawable.ic_tab_app),
    TabItem("网站", R.drawable.ic_tab_website),
    TabItem("设置", R.drawable.ic_tab_settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val server = Repo.active

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = server?.displayName() ?: "PanelOne",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = Repo.detected?.display() ?: "未识别面板版本",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                0 -> DashboardScreen()
                1 -> ContainersScreen()
                2 -> AppsScreen()
                3 -> WebsitesScreen()
                else -> SettingsScreen()
            }
        }
    }
}
