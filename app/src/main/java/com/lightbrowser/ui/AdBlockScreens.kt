package com.lightbrowser.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lightbrowser.BrowserApplication
import com.lightbrowser.browser.AdBlocker
import com.lightbrowser.data.AdBlockLevel
import com.lightbrowser.data.BrowserSettings
import com.lightbrowser.data.CustomBlockRule
import com.lightbrowser.data.SiteBlockRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdBlockViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BrowserApplication
    private val db = app.database

    val siteRules: StateFlow<List<SiteBlockRule>> = db.siteRuleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customRules: StateFlow<List<CustomBlockRule>> = db.customRuleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSiteRule(host: String, level: Int) {
        val normalized = normalizeHost(host) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.siteRuleDao().upsert(SiteBlockRule(normalized, level))
        }
    }

    fun removeSiteRule(host: String) {
        viewModelScope.launch(Dispatchers.IO) { db.siteRuleDao().deleteByHost(host) }
    }

    fun addCustomRule(domain: String, isTracker: Boolean) {
        val normalized = normalizeHost(domain) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.customRuleDao().upsert(CustomBlockRule(normalized, isTracker, enabled = true))
        }
    }

    fun toggleCustomRule(rule: CustomBlockRule) {
        viewModelScope.launch(Dispatchers.IO) {
            db.customRuleDao().upsert(rule.copy(enabled = !rule.enabled))
        }
    }

    fun removeCustomRule(rule: CustomBlockRule) {
        viewModelScope.launch(Dispatchers.IO) { db.customRuleDao().delete(rule) }
    }

    /** 输入归一化：去掉协议/路径/端口，转小写；非法输入返回 null */
    private fun normalizeHost(input: String): String? {
        var s = input.trim().lowercase()
        s = s.removePrefix("http://").removePrefix("https://")
        s = s.substringBefore('/').substringBefore(':').trim().trimEnd('.')
        if (s.isEmpty() || s.contains(' ') || s.contains('　')) return null
        if (!s.contains('.') && s != "localhost") return null
        return s
    }
}

/** 拦截等级文案 */
fun adBlockLevelLabel(level: Int): String = when (level) {
    AdBlockLevel.OFF -> "无拦截"
    AdBlockLevel.TRACKERS -> "拦截跟踪器"
    else -> "拦截跟踪器和广告"
}

private val adBlockLevels = listOf(
    AdBlockLevel.OFF to "无拦截",
    AdBlockLevel.TRACKERS to "拦截跟踪器",
    AdBlockLevel.TRACKERS_AND_ADS to "拦截跟踪器和广告"
)

// ============================================================
// 拦截设置主页：默认拦截等级 / 站点例外 / 规则库 / 严格阻止
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BrowserApplication
    val repo = app.settingsRepository
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = BrowserSettings())
    val viewModel: AdBlockViewModel = viewModel()
    val siteRules by viewModel.siteRules.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拦截跟踪器和广告") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AdBlockSectionTitle("默认拦截等级")
            adBlockLevels.forEach { (level, label) ->
                AdBlockRadioRow(
                    label = label,
                    selected = settings.adBlockLevel == level,
                    onClick = { scope.launch { repo.setAdBlockLevel(level) } }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AdBlockSectionTitle("例外")
            AdBlockClickRow(
                title = "管理不同站点的拦截级别",
                subtitle = if (siteRules.isEmpty()) null else "已设置 ${siteRules.size} 个站点",
                onClick = { navController.navigate("adblock_sites") }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AdBlockSectionTitle("规则库")
            AdBlockClickRow(
                title = "管理跟踪器拦截规则",
                subtitle = null,
                onClick = { navController.navigate("adblock_rules/tracker") }
            )
            AdBlockClickRow(
                title = "管理广告拦截规则",
                subtitle = null,
                onClick = { navController.navigate("adblock_rules/ad") }
            )
            AdBlockSwitchRow(
                title = "严格阻止",
                subtitle = "允许阻止规则阻止某些网页。",
                checked = settings.strictBlocking,
                onCheckedChange = { scope.launch { repo.setStrictBlocking(it) } }
            )
        }
    }
}

// ============================================================
// 站点例外管理页
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteBlockRulesScreen(navController: NavController) {
    val viewModel: AdBlockViewModel = viewModel()
    val siteRules by viewModel.siteRules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<SiteBlockRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("站点拦截级别") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加站点")
            }
        }
    ) { padding ->
        if (siteRules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无例外站点\n点击右下角 + 为特定网站单独设置拦截等级",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(siteRules, key = { it.host }) { rule ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingRule = rule }
                            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.host, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                adBlockLevelLabel(rule.level),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.removeSiteRule(rule.host) }) {
                            Icon(
                                Icons.Default.Close, contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        SiteRuleDialog(
            title = "添加站点例外",
            initialHost = "",
            initialLevel = AdBlockLevel.OFF,
            onDismiss = { showAddDialog = false },
            onConfirm = { host, level ->
                viewModel.setSiteRule(host, level)
                showAddDialog = false
            }
        )
    }

    editingRule?.let { rule ->
        SiteRuleDialog(
            title = rule.host,
            initialHost = rule.host,
            initialLevel = rule.level,
            hostEditable = false,
            onDismiss = { editingRule = null },
            onConfirm = { host, level ->
                viewModel.setSiteRule(host, level)
                editingRule = null
            }
        )
    }
}

@Composable
private fun SiteRuleDialog(
    title: String,
    initialHost: String,
    initialLevel: Int,
    hostEditable: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var host by remember { mutableStateOf(initialHost) }
    var level by remember { mutableIntStateOf(initialLevel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hostEditable) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("站点域名（如 example.com）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
                adBlockLevels.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { level = value }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(selected = level == value, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !hostEditable || host.isNotBlank(),
                onClick = { onConfirm(host, level) }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ============================================================
// 规则库管理页（跟踪器 / 广告）
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(navController: NavController, type: String) {
    val context = LocalContext.current
    val app = context.applicationContext as BrowserApplication
    val repo = app.settingsRepository
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsState(initial = BrowserSettings())
    val viewModel: AdBlockViewModel = viewModel()
    val customRules by viewModel.customRules.collectAsState()

    val isTracker = type == "tracker"
    val title = if (isTracker) "管理跟踪器拦截规则" else "管理广告拦截规则"
    val builtinEnabled = if (isTracker) settings.builtinTrackerRules else settings.builtinAdRules

    // 内置规则从 assets 异步加载，轮询等待数量就绪
    var builtinCount by remember {
        mutableIntStateOf(if (isTracker) AdBlocker.builtinTrackerCount else AdBlocker.builtinAdCount)
    }
    LaunchedEffect(isTracker) {
        var retries = 0
        while (builtinCount == 0 && retries < 30) {
            delay(200)
            builtinCount = if (isTracker) AdBlocker.builtinTrackerCount else AdBlocker.builtinAdCount
            retries++
        }
    }

    val rulesOfType = customRules.filter { it.isTracker == isTracker }
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                AdBlockSectionTitle("内置规则")
                AdBlockSwitchRow(
                    title = if (isTracker) "内置跟踪器规则" else "内置广告规则",
                    subtitle = if (builtinCount > 0)
                        "共 $builtinCount 条域名规则，后缀匹配子域名自动命中"
                    else "规则加载中…",
                    checked = builtinEnabled,
                    onCheckedChange = {
                        scope.launch {
                            if (isTracker) repo.setBuiltinTrackerRules(it) else repo.setBuiltinAdRules(it)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AdBlockSectionTitle("自定义规则")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("域名，如 ads.example.com") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = input.isNotBlank(),
                        onClick = {
                            viewModel.addCustomRule(input, isTracker)
                            input = ""
                        }
                    ) { Text("添加") }
                }
            }
            items(rulesOfType, key = { it.domain }) { rule ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Text(
                        rule.domain,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (rule.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { viewModel.toggleCustomRule(rule) }
                    )
                    IconButton(onClick = { viewModel.removeCustomRule(rule) }) {
                        Icon(
                            Icons.Default.Close, contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                HorizontalDivider()
            }
            if (rulesOfType.isEmpty()) {
                item {
                    Text(
                        "暂无自定义规则",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

// ---------- 共用行组件 ----------

@Composable
private fun AdBlockSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AdBlockRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AdBlockClickRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdBlockSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
