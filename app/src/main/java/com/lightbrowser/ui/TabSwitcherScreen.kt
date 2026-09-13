package com.lightbrowser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lightbrowser.BrowserApplication
import com.lightbrowser.browser.BrowserTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BrowserApplication
    val tabManager = app.tabManager
    var showCloseAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签页") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        tabManager.createTab(incognito = true)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = "新建无痕标签页")
                    }
                    IconButton(onClick = { showCloseAllConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "关闭所有标签页")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    tabManager.createTab()
                    navController.popBackStack()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新标签页") }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tabManager.tabs, key = { it.id }) { tab ->
                TabCard(
                    tab = tab,
                    selected = tab.id == tabManager.currentTabId,
                    onClick = {
                        tabManager.selectTab(tab.id)
                        navController.popBackStack()
                    },
                    onClose = { tabManager.closeTab(tab.id) }
                )
            }
        }
    }

    if (showCloseAllConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseAllConfirm = false },
            title = { Text("关闭所有标签页？") },
            text = { Text("将关闭全部 ${tabManager.tabs.size} 个标签页，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseAllConfirm = false
                    tabManager.closeAllTabs()
                    navController.popBackStack()
                }) { Text("全部关闭") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllConfirm = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabCard(
    tab: BrowserTab,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(170.dp),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (tab.isIncognito)
                MaterialTheme.colorScheme.surfaceContainerHighest
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 2.dp)
            ) {
                TabFavicon(tab)
                Spacer(Modifier.width(8.dp))
                Text(
                    tab.displayTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭标签页",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (tab.isIncognito) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Text(
                        if (tab.isHome) "新标签页" else tab.host,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TabFavicon(tab: BrowserTab) {
    val icon = tab.favicon
    if (icon != null) {
        androidx.compose.foundation.Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(3.dp))
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    tab.displayTitle.take(1),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
