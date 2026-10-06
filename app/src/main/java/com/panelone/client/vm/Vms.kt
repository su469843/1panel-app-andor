package com.panelone.client.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panelone.client.data.ApiErrors
import com.panelone.client.data.ContainerItem
import com.panelone.client.data.ContainerStatsItem
import com.panelone.client.data.DetectedPanel
import com.panelone.client.data.InstalledApp
import com.panelone.client.data.Overview
import com.panelone.client.data.Repo
import com.panelone.client.data.ServerConfig
import com.panelone.client.data.WebsiteItem
import com.panelone.client.data.containerOperate
import com.panelone.client.data.containerStats
import com.panelone.client.data.containers
import com.panelone.client.data.installedApps
import com.panelone.client.data.overview
import com.panelone.client.data.appOperate
import com.panelone.client.data.websiteOperate
import com.panelone.client.data.websites
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50
private const val HISTORY_SIZE = 60

/** 概览：系统信息 + 实时占用，保留最近 60 个采样点画曲线 */
class DashboardViewModel : ViewModel() {

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var data by mutableStateOf<Overview?>(null)
        private set
    var history by mutableStateOf<List<Float>>(emptyList())
        private set

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            if (data == null) loading = true
            try {
                val fresh = Repo.client().overview()
                data = fresh
                error = null
                val sample = fresh.current.cpuUsedPercent.toFloat()
                history = (history + sample).takeLast(HISTORY_SIZE)
            } catch (t: Throwable) {
                error = ApiErrors.of(t)
            } finally {
                loading = false
            }
        }
    }

    fun reset() {
        job?.cancel()
        data = null
        error = null
        history = emptyList()
        loading = false
    }
}

/** 容器列表：搜索 + 状态筛选 + 启停操作 + 实时占用 */
class ContainersViewModel : ViewModel() {

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var items by mutableStateOf<List<ContainerItem>>(emptyList())
        private set
    var stats by mutableStateOf<Map<String, ContainerStatsItem>>(emptyMap())
        private set
    var total by mutableStateOf(0L)
        private set
    var keyword by mutableStateOf("")
        private set
    var filter by mutableStateOf("all")
        private set
    var busyNames by mutableStateOf<Set<String>>(emptySet())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var page = 1
    private var job: Job? = null

    fun setKeyword(value: String) {
        keyword = value
    }

    fun setFilter(value: String) {
        if (filter == value) return
        filter = value
        load(reset = true)
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (items.size.toLong() >= total) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            try {
                val target = if (reset) 1 else page + 1
                val result = Repo.client().containers(
                    page = target,
                    pageSize = PAGE_SIZE,
                    name = keyword.trim(),
                    state = filter,
                )
                items = if (reset) result.items else items + result.items
                total = result.total
                page = target
                error = null
                stats = Repo.client().containerStats()
            } catch (t: Throwable) {
                error = ApiErrors.of(t)
            } finally {
                loading = false
            }
        }
    }

    fun operate(name: String, operation: String) {
        if (busyNames.contains(name)) return
        busyNames = busyNames + name
        viewModelScope.launch {
            try {
                Repo.client().containerOperate(listOf(name), operation)
                message = "$name 操作成功"
                load(reset = true)
            } catch (t: Throwable) {
                message = "$name 操作失败：${ApiErrors.of(t)}"
            } finally {
                busyNames = busyNames - name
            }
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun reset() {
        job?.cancel()
        items = emptyList()
        stats = emptyMap()
        total = 0
        error = null
        page = 1
        loading = false
    }
}

/** 已安装应用 */
class AppsViewModel : ViewModel() {

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var items by mutableStateOf<List<InstalledApp>>(emptyList())
        private set
    var total by mutableStateOf(0L)
        private set
    var keyword by mutableStateOf("")
        private set
    var busyIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var page = 1
    private var job: Job? = null

    fun setKeyword(value: String) {
        keyword = value
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (items.size.toLong() >= total) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            try {
                val target = if (reset) 1 else page + 1
                val result = Repo.client().installedApps(target, PAGE_SIZE, keyword.trim())
                items = if (reset) result.items else items + result.items
                total = result.total
                page = target
                error = null
            } catch (t: Throwable) {
                error = ApiErrors.of(t)
            } finally {
                loading = false
            }
        }
    }

    fun operate(app: InstalledApp, operation: String) {
        if (busyIds.contains(app.id)) return
        busyIds = busyIds + app.id
        viewModelScope.launch {
            try {
                Repo.client().appOperate(app.id, operation)
                message = "${app.name} 操作成功"
                load(reset = true)
            } catch (t: Throwable) {
                message = "${app.name} 操作失败：${ApiErrors.of(t)}"
            } finally {
                busyIds = busyIds - app.id
            }
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun reset() {
        job?.cancel()
        items = emptyList()
        total = 0
        error = null
        page = 1
        loading = false
    }
}

/** 网站 */
class WebsitesViewModel : ViewModel() {

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var items by mutableStateOf<List<WebsiteItem>>(emptyList())
        private set
    var total by mutableStateOf(0L)
        private set
    var keyword by mutableStateOf("")
        private set
    var busyIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var page = 1
    private var job: Job? = null

    fun setKeyword(value: String) {
        keyword = value
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (items.size.toLong() >= total) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            try {
                val target = if (reset) 1 else page + 1
                val result = Repo.client().websites(target, PAGE_SIZE, keyword.trim())
                items = if (reset) result.items else items + result.items
                total = result.total
                page = target
                error = null
            } catch (t: Throwable) {
                error = ApiErrors.of(t)
            } finally {
                loading = false
            }
        }
    }

    fun operate(site: WebsiteItem, operation: String) {
        if (busyIds.contains(site.id)) return
        busyIds = busyIds + site.id
        viewModelScope.launch {
            try {
                Repo.client().websiteOperate(site.id, operation)
                message = "${site.primaryDomain} 操作成功"
                load(reset = true)
            } catch (t: Throwable) {
                message = "${site.primaryDomain} 操作失败：${ApiErrors.of(t)}"
            } finally {
                busyIds = busyIds - site.id
            }
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun reset() {
        job?.cancel()
        items = emptyList()
        total = 0
        error = null
        page = 1
        loading = false
    }
}

/** 服务器管理：增删改 + 测试连接 */
class ServersViewModel : ViewModel() {

    var testing by mutableStateOf(false)
        private set
    var testResult by mutableStateOf<String?>(null)
        private set
    var testError by mutableStateOf<String?>(null)
        private set

    fun test(config: ServerConfig) {
        testing = true
        testResult = null
        testError = null
        viewModelScope.launch {
            try {
                val detected: DetectedPanel = com.panelone.client.data.testConnection(config)
                testResult = "连接成功 · 识别为 ${detected.display()}"
            } catch (t: Throwable) {
                testError = ApiErrors.of(t)
            } finally {
                testing = false
            }
        }
    }

    fun clearTest() {
        testResult = null
        testError = null
    }

    fun save(config: ServerConfig): ServerConfig = Repo.upsert(config)

    fun delete(id: String) = Repo.remove(id)

    fun select(id: String) = Repo.select(id)

    fun connect() {
        viewModelScope.launch { Repo.connect() }
    }
}
