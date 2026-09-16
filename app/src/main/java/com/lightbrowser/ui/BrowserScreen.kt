package com.lightbrowser.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lightbrowser.BrowserApplication
import com.lightbrowser.browser.BrowserTab
import com.lightbrowser.browser.DownloadHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BrowserApplication
    val tabManager = app.tabManager
    val viewModel: BrowserViewModel = viewModel()
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val tab = tabManager.currentTab

    var editing by remember { mutableStateOf(false) }
    var omniboxValue by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    var menuExpanded by remember { mutableStateOf(false) }
    var findBarVisible by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }

    val suggestions by viewModel.suggestions.collectAsState()
    val topSites by viewModel.topSites.collectAsState()
    val currentUrl = tab?.url.orEmpty()
    val bookmarkedFlow = remember(currentUrl) { viewModel.isBookmarked(currentUrl) }
    val bookmarked by bookmarkedFlow.collectAsState(initial = false)

    fun startEditing() {
        omniboxValue = TextFieldValue(currentUrl, selection = TextRange(0, currentUrl.length))
        editing = true
    }

    fun stopEditing() {
        editing = false
        viewModel.clearSuggestions()
        keyboardController?.hide()
    }

    fun submitInput(raw: String) {
        val target = tab ?: tabManager.createTab()
        tabManager.loadInput(target, raw)
        stopEditing()
    }

    LaunchedEffect(omniboxValue.text, editing) {
        if (editing && omniboxValue.text.isNotBlank() && omniboxValue.text != currentUrl) {
            viewModel.querySuggestions(omniboxValue.text)
        } else {
            viewModel.clearSuggestions()
        }
    }
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    BackHandler(enabled = editing) { stopEditing() }
    val webView = tab?.webView
    val canHandleBack = tab != null && !editing &&
            (tabManager.fullscreenActive || webView?.canGoBack() == true || !tab.isHome)
    BackHandler(enabled = canHandleBack) {
        when {
            tabManager.fullscreenActive -> tabManager.exitFullscreenAction?.invoke()
            webView?.canGoBack() == true -> webView.goBack()
            else -> tab?.let { tabManager.goHome(it) }
        }
    }

    // 编辑态下顶栏与页面同色，避免出现突兀的方形色块；颜色切换带过渡动画
    val topBarColor by animateColorAsState(
        targetValue = when {
            editing -> MaterialTheme.colorScheme.surface
            tab?.isIncognito == true -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(200),
        label = "topBarColor"
    )

    Column(modifier = Modifier.fillMaxSize().background(topBarColor)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        // ---------- 顶栏：地址栏 + 标签计数 + 菜单 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                IconButton(onClick = { stopEditing() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (tab?.isIncognito == true)
                    MaterialTheme.colorScheme.surfaceContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp)
                ) {
                    if (!editing) {
                        when {
                            tab?.isIncognito == true -> Icon(
                                Icons.Default.VisibilityOff, contentDescription = "无痕模式",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            currentUrl.startsWith("https://") -> Icon(
                                Icons.Default.Lock, contentDescription = "安全连接",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            currentUrl.isBlank() -> Icon(
                                Icons.Default.Search, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            else -> Icon(
                                Icons.Default.Info, contentDescription = "连接不安全",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Crossfade(
                        targetState = editing,
                        animationSpec = tween(150),
                        modifier = Modifier.weight(1f),
                        label = "omnibox"
                    ) { isEditing ->
                        if (isEditing) {
                            BasicTextField(
                                value = omniboxValue,
                                onValueChange = { omniboxValue = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { submitInput(omniboxValue.text) }
                                ),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (omniboxValue.text.isEmpty()) {
                                            Text(
                                                "搜索或输入网址",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 16.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().fillMaxHeight()
                                    .clickable { startEditing() },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = when {
                                        tab == null || tab.isHome -> "搜索或输入网址"
                                        tab.title.isNotBlank() -> tab.title
                                        else -> currentUrl
                                    },
                                    color = if (tab == null || tab.isHome)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    when {
                        editing && omniboxValue.text.isNotEmpty() ->
                            IconButton(onClick = { omniboxValue = TextFieldValue("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清空",
                                    modifier = Modifier.size(20.dp))
                            }
                        tab?.isLoading == true ->
                            IconButton(onClick = { tab.webView?.stopLoading() }) {
                                Icon(Icons.Default.Close, contentDescription = "停止加载",
                                    modifier = Modifier.size(20.dp))
                            }
                        !editing && tab != null && !tab.isHome ->
                            IconButton(onClick = { tabManager.reload(tab) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新",
                                    modifier = Modifier.size(20.dp))
                            }
                    }
                }
            }

            if (!editing) {
                // 标签页计数按钮
                IconButton(onClick = { navController.navigate("tabs") }) {
                    Box(
                        modifier = Modifier.size(24.dp).border(
                            1.6.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            RoundedCornerShape(6.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${tabManager.tabs.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 溢出菜单
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("新标签页") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { menuExpanded = false; tabManager.createTab() }
                        )
                        DropdownMenuItem(
                            text = { Text("新建无痕标签页") },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                            onClick = { menuExpanded = false; tabManager.createTab(incognito = true) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(if (bookmarked) "移除书签" else "添加书签") },
                            leadingIcon = {
                                Icon(
                                    if (bookmarked) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = null,
                                    tint = if (bookmarked) MaterialTheme.colorScheme.primary
                                    else LocalContentColor.current
                                )
                            },
                            enabled = currentUrl.isNotBlank(),
                            onClick = {
                                menuExpanded = false
                                tab?.let { viewModel.toggleBookmark(it.title, it.url) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("书签") },
                            leadingIcon = { Icon(Icons.Default.BookmarkBorder, null) },
                            onClick = { menuExpanded = false; navController.navigate("bookmarks") }
                        )
                        DropdownMenuItem(
                            text = { Text("历史记录") },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                            onClick = { menuExpanded = false; navController.navigate("history") }
                        )
                        DropdownMenuItem(
                            text = { Text("下载内容") },
                            leadingIcon = { Icon(Icons.Default.Download, null) },
                            onClick = { menuExpanded = false; navController.navigate("downloads") }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("前进") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                            enabled = tab?.canGoForward == true,
                            onClick = { menuExpanded = false; tab?.webView?.goForward() }
                        )
                        DropdownMenuItem(
                            text = { Text("分享") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            enabled = currentUrl.isNotBlank(),
                            onClick = {
                                menuExpanded = false
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    putExtra(Intent.EXTRA_SUBJECT, tab?.title ?: "")
                                }
                                context.startActivity(Intent.createChooser(send, "分享网页"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("网页内查找") },
                            leadingIcon = { Icon(Icons.Default.FindInPage, null) },
                            enabled = currentUrl.isNotBlank(),
                            onClick = { menuExpanded = false; findBarVisible = true }
                        )
                        DropdownMenuItem(
                            text = { Text("桌面版网站") },
                            leadingIcon = { Icon(Icons.Default.DesktopWindows, null) },
                            trailingIcon = {
                                if (tab?.desktopMode == true) {
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                tab?.let { tabManager.toggleDesktopMode(it) }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("设置") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menuExpanded = false; navController.navigate("settings") }
                        )
                    }
                }
            }
        }

        // ---------- 顶部多标签栏 ----------
        TabStrip(
            tabs = tabManager.tabs,
            currentTabId = tabManager.currentTabId,
            onSelect = { id ->
                stopEditing()
                tabManager.selectTab(id)
            },
            onClose = { id -> tabManager.closeTab(id) },
            onAdd = {
                stopEditing()
                tabManager.createTab()
            }
        )

        // ---------- 加载进度条 ----------
        if (tab?.isLoading == true) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }

        // ---------- 网页内容区 ----------
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val currentTab = tab
            if (currentTab == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在启动…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (currentTab.isHome) {
                // 主页只渲染 Compose 内容，不挂载 WebView，
                // 避免原生视图层级异常时遮挡顶部地址栏
                HomeContent(
                    topSites = topSites,
                    onSearch = { startEditing() },
                    onOpenUrl = { url -> tabManager.loadInput(currentTab, url) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // key(tab.id)：切换标签页时强制重建 AndroidView，保证显示的是当前标签页的 WebView
                key(currentTab.id) {
                    PullToRefreshBox(
                        isRefreshing = currentTab.isLoading,
                        onRefresh = { tabManager.reload(currentTab) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val wv = tabManager.getOrCreateWebView(currentTab, ctx)
                                (wv.parent as? ViewGroup)?.removeView(wv)
                                wv.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                wv
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .onSizeChanged { size ->
                                    // 修复部分机型（MIUI/HyperOS 等）上软键盘收起或窗口尺寸变化后
                                    // WebView 不跟随容器 resize、页面视口高度停留在旧值的问题：
                                    // 容器尺寸变化时强制 WebView 按新尺寸重排
                                    val wv = currentTab.webView ?: return@onSizeChanged
                                    if (wv.width != size.width || wv.height != size.height) {
                                        wv.measure(
                                            View.MeasureSpec.makeMeasureSpec(size.width, View.MeasureSpec.EXACTLY),
                                            View.MeasureSpec.makeMeasureSpec(size.height, View.MeasureSpec.EXACTLY)
                                        )
                                        wv.layout(0, 0, size.width, size.height)
                                        wv.requestLayout()
                                    }
                                }
                        )
                    }
                }
                if (currentTab.loadError) {
                    ErrorContent(
                        message = currentTab.errorMessage,
                        onRetry = { tabManager.reload(currentTab) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            // 编辑态下始终覆盖建议面板（无建议时显示空白页底），带淡入+展开过渡动画
            if (currentTab != null) {
                SuggestionOverlay(
                    visible = editing,
                    suggestions = suggestions,
                    onClick = { suggestion ->
                        if (suggestion.url != null) {
                            tabManager.loadInput(currentTab, suggestion.url)
                            stopEditing()
                        } else {
                            submitInput(suggestion.text)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ---------- 页内查找栏 ----------
        if (findBarVisible && tab != null) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(topBarColor)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = findText,
                    onValueChange = {
                        findText = it
                        if (it.isNotEmpty()) tab.webView?.findAllAsync(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("在网页中查找") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { tab.webView?.findNext(true) })
                )
                tab.findResult?.let { (index, total) ->
                    Text(
                        "$index/$total",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { tab.webView?.findNext(false) }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = { tab.webView?.findNext(true) }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                }
                IconButton(onClick = {
                    findBarVisible = false
                    findText = ""
                    tab.findResult = null
                    tab.webView?.clearMatches()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "关闭查找")
                }
            }
        } else {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    // ---------- SSL 证书错误对话框 ----------
    tab?.sslError?.let { event ->
        AlertDialog(
            onDismissRequest = {
                event.handler.cancel()
                tab.sslError = null
            },
            title = { Text("安全连接出现问题") },
            text = {
                Text("该网站的安全证书存在问题，继续访问可能会有风险。\n\n错误代码：${event.error.primaryError}")
            },
            confirmButton = {
                TextButton(onClick = {
                    event.handler.proceed()
                    tab.sslError = null
                }) { Text("仍然继续") }
            },
            dismissButton = {
                TextButton(onClick = {
                    event.handler.cancel()
                    tab.sslError = null
                }) { Text("返回") }
            }
        )
    }

    // ---------- 长按链接/图片菜单 ----------
    tab?.pendingLinkMenu?.let { linkMenu ->
        AlertDialog(
            onDismissRequest = { tab.pendingLinkMenu = null },
            title = {
                Text(
                    linkMenu.url.orEmpty(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
            },
            text = {
                Column {
                    Text(
                        "在新标签页打开",
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                linkMenu.url?.let {
                                    tabManager.createTab(it, incognito = tab.isIncognito, switchTo = false)
                                }
                                tab.pendingLinkMenu = null
                            }
                            .padding(vertical = 14.dp)
                    )
                    Text(
                        "复制链接地址",
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                linkMenu.url?.let { clipboard.setText(AnnotatedString(it)) }
                                tab.pendingLinkMenu = null
                            }
                            .padding(vertical = 14.dp)
                    )
                    if (linkMenu.isImage) {
                        Text(
                            "下载图片",
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    linkMenu.url?.let {
                                        DownloadHelper.download(
                                            context, it,
                                            tab.webView?.settings?.userAgentString, null, null
                                        )
                                    }
                                    tab.pendingLinkMenu = null
                                }
                                .padding(vertical = 14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tab.pendingLinkMenu = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 编辑态建议覆盖层：独立 Composable 以避开 ColumnScope/BoxScope
 * 隐式接收者导致的 AnimatedVisibility 重载歧义。
 */
@Composable
private fun SuggestionOverlay(
    visible: Boolean,
    suggestions: List<Suggestion>,
    onClick: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)) + expandVertically(tween(200)),
        exit = fadeOut(tween(120)),
        label = "suggestions"
    ) {
        if (suggestions.isNotEmpty()) {
            SuggestionsPanel(
                suggestions = suggestions,
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {}
        }
    }
}

@Composable
private fun SuggestionsPanel(
    suggestions: List<Suggestion>,
    onClick: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        LazyColumn {
            // 不使用内容 key：建议项文本可能重复，内容 key 冲突会导致闪退
            items(suggestions) { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onClick(suggestion) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (suggestion.type) {
                            SuggestionType.SEARCH -> Icons.Default.Search
                            SuggestionType.HISTORY -> Icons.Default.History
                            SuggestionType.BOOKMARK -> Icons.Default.Star
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        suggestion.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("网页无法打开", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "请检查网络连接后重试",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}
