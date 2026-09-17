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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lightbrowser.BrowserApplication
import com.lightbrowser.browser.Userscripts
import com.lightbrowser.data.BrowserExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExtensionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BrowserApplication
    private val db = app.database

    val extensions: StateFlow<List<BrowserExtension>> = db.extensionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 安装脚本，返回脚本名 */
    suspend fun install(code: String): String {
        val parsed = Userscripts.parse(code)
        db.extensionDao().upsert(BrowserExtension(name = parsed.name, code = code))
        return parsed.name
    }

    suspend fun fetch(url: String): String? = Userscripts.fetch(url)

    fun toggle(extension: BrowserExtension) {
        viewModelScope.launch(Dispatchers.IO) {
            db.extensionDao().upsert(extension.copy(enabled = !extension.enabled))
        }
    }

    fun remove(extension: BrowserExtension) {
        viewModelScope.launch(Dispatchers.IO) { db.extensionDao().delete(extension) }
    }
}

/**
 * 扩展程序管理页：安装/启用/停用/删除油猴风格 .user.js 扩展脚本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(navController: NavController) {
    val viewModel: ExtensionViewModel = viewModel()
    val extensions by viewModel.extensions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扩展程序") },
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
                Icon(Icons.Default.Add, contentDescription = "安装扩展")
            }
        }
    ) { padding ->
        if (extensions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有安装扩展",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "支持油猴（Tampermonkey）风格的 .user.js 扩展脚本\n" +
                            "点击右下角 +，粘贴脚本代码或从 URL 在线安装\n" +
                            "脚本会在匹配的网页加载时自动运行",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(extensions, key = { it.id }) { extension ->
                    val parsed = remember(extension.code) { Userscripts.parse(extension.code) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            tint = if (extension.enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                extension.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (extension.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                parsed.matches.joinToString("、") +
                                    if (parsed.runAt == Userscripts.RUN_AT_START) " · 页面开始运行"
                                    else " · 页面完成运行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Switch(
                            checked = extension.enabled,
                            onCheckedChange = { viewModel.toggle(extension) }
                        )
                        IconButton(onClick = { viewModel.remove(extension) }) {
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
        AddExtensionDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun AddExtensionDialog(
    viewModel: ExtensionViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("安装扩展脚本") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("脚本 URL（.user.js）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = !busy && url.isNotBlank(),
                        onClick = {
                            busy = true
                            status = "正在获取…"
                            scope.launch {
                                val text = viewModel.fetch(url.trim())
                                busy = false
                                if (text != null) {
                                    code = text
                                    status = "已获取，点击安装完成"
                                } else {
                                    status = "获取失败，请检查链接"
                                }
                            }
                        }
                    ) { Text("获取") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("或在此粘贴脚本代码（==UserScript== …）") },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && code.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        val name = viewModel.install(code)
                        busy = false
                        status = null
                        onDismiss()
                        // 安装结果通过空状态消失/列表刷新体现；name 可用于提示
                        android.util.Log.i("Extensions", "已安装扩展：$name")
                    }
                }
            ) { Text("安装") }
        },
        dismissButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("取消") }
        }
    )
}
