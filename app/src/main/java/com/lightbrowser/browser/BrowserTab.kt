package com.lightbrowser.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class SslErrorEvent(val handler: SslErrorHandler, val error: SslError)

class LinkMenuState(val url: String?, val isImage: Boolean)

class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val isIncognito: Boolean = false,
    initialUrl: String = "",
    initialTitle: String = ""
) {
    /** WebView 实例延迟创建，由 [com.lightbrowser.browser.TabManager.getOrCreateWebView] 管理 */
    var webView: WebView? = null

    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var favicon by mutableStateOf<Bitmap?>(null)
    var progress by mutableIntStateOf(100)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var loadError by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var desktopMode by mutableStateOf(false)
    var sslError by mutableStateOf<SslErrorEvent?>(null)
    var pendingLinkMenu by mutableStateOf<LinkMenuState?>(null)
    var findResult by mutableStateOf<Pair<Int, Int>?>(null)

    val isHome: Boolean get() = url.isBlank()

    val displayTitle: String
        get() = when {
            title.isNotBlank() -> title
            isHome -> "新标签页"
            else -> url
        }

    val host: String
        get() = runCatching { android.net.Uri.parse(url).host ?: "" }.getOrDefault("")
}
