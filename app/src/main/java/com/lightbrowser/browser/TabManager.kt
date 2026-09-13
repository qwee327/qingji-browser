package com.lightbrowser.browser

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.lightbrowser.data.AppDatabase
import com.lightbrowser.data.BrowserSettings
import com.lightbrowser.data.HistoryItem
import com.lightbrowser.data.TabEntity
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 多标签管理器：负责标签的创建/切换/关闭/持久化，以及 WebView 的配置与回收。
 */
class TabManager(private val appContext: Context) {

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }

    private val db = AppDatabase.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val tabs = mutableStateListOf<BrowserTab>()
    var currentTabId by mutableStateOf<String?>(null)
        private set

    var callbacks: WebCallbacks? = null
    var settingsProvider: (() -> BrowserSettings)? = null
    var darkThemeProvider: (() -> Boolean)? = null
    var exitFullscreenAction: (() -> Unit)? = null
    var fullscreenActive by mutableStateOf(false)

    private var pendingExternalUrl: String? = null

    @Volatile
    private var initialized = false

    val currentTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == currentTabId }

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        scope.launch {
            val settings = settingsProvider?.invoke() ?: BrowserSettings()
            if (settings.clearDataOnExit) {
                withContext(Dispatchers.IO) {
                    db.historyDao().clear()
                    db.tabDao().clear()
                }
                clearCookies()
            }
            val saved = if (settings.restoreTabsOnStart && !settings.clearDataOnExit) {
                withContext(Dispatchers.IO) { db.tabDao().getAll() }
            } else emptyList()
            if (saved.isNotEmpty()) {
                saved.forEach { entity ->
                    tabs.add(BrowserTab(id = entity.id, initialUrl = entity.url, initialTitle = entity.title))
                }
                currentTabId = saved.last().id
            }
            if (tabs.isEmpty()) createTab()
            pendingExternalUrl?.let { url ->
                pendingExternalUrl = null
                createTab(url)
            }
        }
    }

    /** 处理来自系统其它应用的打开链接请求 */
    fun openExternalUrl(url: String) {
        if (initialized) createTab(url) else pendingExternalUrl = url
    }

    fun createTab(url: String = "", incognito: Boolean = false, switchTo: Boolean = true): BrowserTab {
        val tab = BrowserTab(isIncognito = incognito, initialUrl = url)
        tabs.add(tab)
        if (switchTo) currentTabId = tab.id
        persistTabs()
        return tab
    }

    fun selectTab(id: String) {
        if (tabs.any { it.id == id }) {
            currentTabId = id
            persistTabs()
        }
    }

    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs.removeAt(index)
        destroyWebView(tab)
        if (currentTabId == id) {
            currentTabId = tabs.getOrNull(index)?.id ?: tabs.lastOrNull()?.id
        }
        persistTabs()
    }

    fun closeAllTabs() {
        tabs.forEach { destroyWebView(it) }
        tabs.clear()
        persistTabs()
        createTab()
    }

    private fun destroyWebView(tab: BrowserTab) {
        tab.webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        tab.webView = null
    }

    fun goHome(tab: BrowserTab) {
        tab.url = ""
        tab.title = ""
        tab.loadError = false
        tab.webView?.stopLoading()
        persistTabs()
    }

    /** 智能加载：完整 URL 直接打开，裸域名补全 https，其余交给搜索引擎 */
    fun loadInput(tab: BrowserTab, rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty()) return
        val url = toUrl(input)
        tab.loadError = false
        tab.url = url
        tab.webView?.loadUrl(url)
        persistTabs()
    }

    fun reload(tab: BrowserTab) {
        if (tab.isHome) return
        tab.loadError = false
        tab.webView?.reload()
    }

    fun toUrl(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            isLikelyUrl(input) -> "https://$input"
            else -> {
                val engine = SearchEngines.byId(settingsProvider?.invoke()?.searchEngineId ?: SearchEngines.BAIDU.id)
                engine.searchUrlPrefix + URLEncoder.encode(input, "UTF-8")
            }
        }
    }

    private fun isLikelyUrl(input: String): Boolean {
        if (input.contains(' ') || input.contains('　')) return false
        if (input.startsWith("localhost") || input.startsWith("127.")) return true
        if (!input.contains('.')) return false
        return Regex("^[\\w\\-]+(\\.[\\w\\-]+)+(:\\d+)?(/.*)?$").matches(input)
    }

    fun toggleDesktopMode(tab: BrowserTab) {
        tab.desktopMode = !tab.desktopMode
        tab.webView?.let { applyDesktopMode(it, effectiveDesktop(tab)) }
        tab.webView?.reload()
    }

    private fun effectiveDesktop(tab: BrowserTab): Boolean =
        tab.desktopMode || (settingsProvider?.invoke()?.desktopModeDefault ?: false)

    fun applySettings(settings: BrowserSettings, dark: Boolean) {
        AdBlocker.enabled = settings.adBlockEnabled
        tabs.forEach { tab ->
            tab.webView?.let { wv ->
                wv.settings.javaScriptEnabled = settings.javaScriptEnabled
                wv.settings.textZoom = settings.textZoom
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, !settings.blockThirdPartyCookies)
                applyDesktopMode(wv, effectiveDesktop(tab))
                applyDarkWebContent(wv, settings.darkWebContentEnabled && dark)
                wv.setBackgroundColor(if (dark) 0xFF141414.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun applyDesktopMode(webView: WebView, desktop: Boolean) {
        webView.settings.userAgentString = if (desktop) DESKTOP_UA else null
    }

    private fun applyDarkWebContent(webView: WebView, allow: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, allow)
        }
    }

    fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    fun clearWebCache() {
        tabs.forEach { it.webView?.clearCache(true) }
    }

    fun persistTabs() {
        val list = tabs.filter { !it.isIncognito }
            .mapIndexed { index, tab -> TabEntity(tab.id, tab.url, tab.title, index) }
        scope.launch(Dispatchers.IO) {
            db.tabDao().clear()
            if (list.isNotEmpty()) db.tabDao().upsertAll(list)
        }
    }

    fun getOrCreateWebView(tab: BrowserTab, context: Context): WebView {
        tab.webView?.let { return it }
        val settings = settingsProvider?.invoke() ?: BrowserSettings()
        val webView = WebView(context)
        tab.webView = webView
        configureWebView(webView, tab, settings)
        if (tab.url.isNotBlank()) {
            tab.isLoading = true
            webView.loadUrl(tab.url)
        }
        return webView
    }

    private fun configureWebView(webView: WebView, tab: BrowserTab, settings: BrowserSettings) {
        val ws = webView.settings
        ws.javaScriptEnabled = settings.javaScriptEnabled
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.loadsImagesAutomatically = true
        ws.loadWithOverviewMode = true
        ws.useWideViewPort = true
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        ws.textZoom = settings.textZoom
        ws.mediaPlaybackRequiresUserGesture = true
        ws.javaScriptCanOpenWindowsAutomatically = true
        ws.setSupportMultipleWindows(true)
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        ws.cacheMode = if (tab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        ws.setGeolocationEnabled(true)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !settings.blockThirdPartyCookies)
        applyDesktopMode(webView, effectiveDesktop(tab))
        val dark = darkThemeProvider?.invoke() ?: false
        applyDarkWebContent(webView, settings.darkWebContentEnabled && dark)
        webView.setBackgroundColor(if (dark) 0xFF141414.toInt() else 0xFFFFFFFF.toInt())

        webView.webViewClient = TabWebViewClient(tab)
        webView.webChromeClient = TabChromeClient(tab)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            DownloadHelper.download(webView.context, url, userAgent, contentDisposition, mimeType)
        }
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val extra = result?.extra
            when (result?.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE ->
                    if (!extra.isNullOrBlank()) {
                        tab.pendingLinkMenu = LinkMenuState(extra, isImage = false)
                        true
                    } else false
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
                    if (!extra.isNullOrBlank()) {
                        tab.pendingLinkMenu = LinkMenuState(extra, isImage = true)
                        true
                    } else false
                else -> false
            }
        }
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            tab.findResult = if (numberOfMatches <= 0) null else (activeMatchOrdinal + 1) to numberOfMatches
        }
    }

    private fun recordHistory(tab: BrowserTab) {
        if (tab.isIncognito) return
        val u = tab.url
        if (u.isBlank() || u.startsWith("about:") || u.startsWith("data:") ||
            u.startsWith("chrome-error") || u.startsWith("file:")
        ) return
        val t = tab.title.ifBlank { u }
        scope.launch(Dispatchers.IO) {
            db.historyDao().insert(HistoryItem(title = t, url = u))
        }
    }

    private inner class TabWebViewClient(private val tab: BrowserTab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url?.toString() ?: return false
            val scheme = request.url?.scheme?.lowercase() ?: return false
            return when {
                scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" -> false
                scheme == "intent" -> {
                    handleIntentScheme(view.context, url)
                    true
                }
                else -> {
                    try {
                        view.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                    true
                }
            }
        }

        private fun handleIntentScheme(context: Context, url: String) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector = null
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (!fallback.isNullOrBlank()) loadInput(tab, fallback)
                }
            } catch (_: Exception) {
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!request.isForMainFrame && AdBlocker.isAd(request.url?.toString())) {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            tab.isLoading = true
            tab.loadError = false
            tab.progress = 10
            url?.let { if (tab.url != it) tab.url = it }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            tab.isLoading = false
            tab.progress = 100
            tab.title = view.title ?: ""
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            url?.let { tab.url = it }
            persistTabs()
            recordHistory(tab)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            url?.let { tab.url = it }
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            if (!isReload) recordHistory(tab)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                tab.loadError = true
                tab.errorMessage = error.description?.toString() ?: ""
                tab.isLoading = false
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            tab.sslError = SslErrorEvent(handler, error)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            tab.webView = null
            view.destroy()
            return true
        }
    }

    private inner class TabChromeClient(private val tab: BrowserTab) : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
            if (newProgress >= 100) tab.isLoading = false
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            tab.title = title ?: ""
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            tab.favicon = icon
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            val cb = callbacks ?: return false
            cb.onShowFileChooser(fileChooserParams, filePathCallback)
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val cb = callbacks
            if (cb != null) cb.onPermissionRequest(request) else request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            val cb = callbacks
            if (cb != null) cb.onGeolocationPrompt(origin, callback) else callback.invoke(origin, false, false)
        }

        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
            callbacks?.onEnterFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            callbacks?.onExitFullscreen()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            val newTab = createTab(incognito = tab.isIncognito)
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = getOrCreateWebView(newTab, view.context)
            resultMsg.sendToTarget()
            return true
        }

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            AlertDialog.Builder(view.context)
                .setTitle(dialogTitle(url))
                .setMessage(message)
                .setPositiveButton("确定") { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            AlertDialog.Builder(view.context)
                .setTitle(dialogTitle(url))
                .setMessage(message)
                .setPositiveButton("确定") { _, _ -> result.confirm() }
                .setNegativeButton("取消") { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult
        ): Boolean {
            val input = EditText(view.context)
            input.setText(defaultValue)
            val padding = (20 * view.resources.displayMetrics.density).toInt()
            input.setPadding(padding, padding / 2, padding, 0)
            AlertDialog.Builder(view.context)
                .setTitle(dialogTitle(url))
                .setMessage(message)
                .setView(input)
                .setPositiveButton("确定") { _, _ -> result.confirm(input.text.toString()) }
                .setNegativeButton("取消") { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        private fun dialogTitle(url: String?): String =
            url?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: "网页提示"
    }
}
