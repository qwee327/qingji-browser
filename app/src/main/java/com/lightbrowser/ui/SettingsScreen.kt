package com.lightbrowser.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lightbrowser.BrowserApplication
import com.lightbrowser.browser.SearchEngines
import com.lightbrowser.data.BrowserSettings
import com.lightbrowser.data.ThemeMode
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BrowserApplication
    val repo = app.settingsRepository
    val scope = rememberCoroutineScope()
    val viewModel: BrowserViewModel = viewModel()
    val settings by repo.settings.collectAsState(initial = BrowserSettings())
    val history by viewModel.history.collectAsState()

    var showEngineDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionTitle("常规")
            SettingsClickItem(
                title = "搜索引擎",
                subtitle = SearchEngines.byId(settings.searchEngineId).displayName,
                onClick = { showEngineDialog = true }
            )
            SettingsClickItem(
                title = "主题",
                subtitle = when (settings.themeMode) {
                    ThemeMode.SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                },
                onClick = { showThemeDialog = true }
            )
            SettingsSwitchItem(
                title = "动态取色 (Material You)",
                subtitle = "根据壁纸自动生成配色，需要 Android 12 及以上",
                checked = settings.dynamicColor,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onCheckedChange = { scope.launch { repo.setDynamicColor(it) } }
            )
            SettingsSwitchItem(
                title = "网页深色模式",
                subtitle = "深色主题下自动调暗网页内容",
                checked = settings.darkWebContentEnabled,
                onCheckedChange = { scope.launch { repo.setDarkWebContent(it) } }
            )

            // 文字大小
            var zoom by remember(settings.textZoom) { mutableFloatStateOf(settings.textZoom.toFloat()) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("网页文字大小", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${zoom.roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    onValueChangeFinished = {
                        scope.launch { repo.setTextZoom(zoom.roundToInt()) }
                    },
                    valueRange = 50f..200f,
                    steps = 14
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle("隐私与安全")
            SettingsClickItem(
                title = "拦截跟踪器和广告",
                subtitle = adBlockLevelLabel(settings.adBlockLevel),
                onClick = { navController.navigate("adblock") }
            )
            SettingsSwitchItem(
                title = "JavaScript",
                subtitle = "允许网页运行脚本（部分网站需要）",
                checked = settings.javaScriptEnabled,
                onCheckedChange = { scope.launch { repo.setJavaScript(it) } }
            )
            SettingsSwitchItem(
                title = "阻止第三方 Cookie",
                subtitle = "减少跨站跟踪，部分网站登录可能受影响",
                checked = settings.blockThirdPartyCookies,
                onCheckedChange = { scope.launch { repo.setBlockThirdPartyCookies(it) } }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle("标签页")
            SettingsSwitchItem(
                title = "启动时恢复标签页",
                subtitle = "重新打开上次未关闭的标签页",
                checked = settings.restoreTabsOnStart,
                onCheckedChange = { scope.launch { repo.setRestoreTabsOnStart(it) } }
            )
            SettingsSwitchItem(
                title = "退出时清除浏览数据",
                subtitle = "下次启动时清除历史记录、Cookie 与标签页",
                checked = settings.clearDataOnExit,
                onCheckedChange = { scope.launch { repo.setClearDataOnExit(it) } }
            )
            SettingsSwitchItem(
                title = "默认使用桌面版网站",
                subtitle = "以桌面浏览器标识请求网页",
                checked = settings.desktopModeDefault,
                onCheckedChange = { scope.launch { repo.setDesktopModeDefault(it) } }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle("存储")
            SettingsClickItem(
                title = "清除历史记录",
                subtitle = "共 ${history.size} 条记录",
                onClick = { showClearHistoryConfirm = true }
            )
            SettingsClickItem(
                title = "清除 Cookie",
                subtitle = "将退出所有网站的登录状态",
                onClick = {
                    app.tabManager.clearCookies()
                    Toast.makeText(context, "Cookie 已清除", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsClickItem(
                title = "清除网页缓存",
                subtitle = "释放已缓存网页占用的空间",
                onClick = {
                    app.tabManager.clearWebCache()
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionTitle("关于")
            SettingsClickItem(
                title = "轻级浏览器",
                subtitle = "版本 1.1.0 · 基于 Android System WebView · 最低支持 Android 10",
                onClick = { }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("选择搜索引擎") },
            text = {
                Column {
                    SearchEngines.list.forEach { engine ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { repo.setSearchEngine(engine.id) }
                                    showEngineDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = settings.searchEngineId == engine.id,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(engine.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEngineDialog = false }) { Text("取消") }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色"
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { repo.setThemeMode(mode) }
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("取消") }
            }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("清空历史记录？") },
            text = { Text("将删除全部 ${history.size} 条浏览记录，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryConfirm = false
                    viewModel.clearHistory()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsClickItem(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
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
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
