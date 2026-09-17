package com.lightbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lightbrowser.browser.WebCallbacks
import com.lightbrowser.data.BrowserSettings
import com.lightbrowser.data.ThemeMode
import com.lightbrowser.ui.AdBlockSettingsScreen
import com.lightbrowser.ui.BookmarksScreen
import com.lightbrowser.ui.BrowserScreen
import com.lightbrowser.ui.DownloadsScreen
import com.lightbrowser.ui.HistoryScreen
import com.lightbrowser.ui.RuleListScreen
import com.lightbrowser.ui.SettingsScreen
import com.lightbrowser.ui.SiteBlockRulesScreen
import com.lightbrowser.ui.TabSwitcherScreen
import com.lightbrowser.ui.theme.LightBrowserTheme

class MainActivity : ComponentActivity(), WebCallbacks {

    private val app: BrowserApplication get() = application as BrowserApplication

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            pendingPermissionRequest?.let { request ->
                val granted = request.resources.filter { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            grants[Manifest.permission.CAMERA] == true
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            grants[Manifest.permission.RECORD_AUDIO] == true
                        else -> false
                    }
                }.toTypedArray()
                if (granted.isNotEmpty()) request.grant(granted) else request.deny()
            }
            pendingPermissionRequest = null

            pendingGeoCallback?.let { callback ->
                val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                callback.invoke(pendingGeoOrigin, granted, false)
            }
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tabManager = app.tabManager
        tabManager.callbacks = this
        tabManager.exitFullscreenAction = { onExitFullscreen() }

        setContent {
            val settingsState by app.settingsRepository.settings.collectAsState(initial = null)
            val settings = settingsState
            tabManager.settingsProvider = { settings ?: BrowserSettings() }

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemDark
            }
            tabManager.darkThemeProvider = { darkTheme }

            LaunchedEffect(settings, darkTheme) {
                if (settings != null) {
                    tabManager.ensureInitialized()
                    tabManager.applySettings(settings, darkTheme)
                }
            }

            LightBrowserTheme(
                darkTheme = darkTheme,
                dynamicColor = settings?.dynamicColor ?: true
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "browser") {
                        composable("browser") { BrowserScreen(navController) }
                        composable("tabs") { TabSwitcherScreen(navController) }
                        composable("bookmarks") { BookmarksScreen(navController) }
                        composable("history") { HistoryScreen(navController) }
                        composable("downloads") { DownloadsScreen(navController) }
                        composable("settings") { SettingsScreen(navController) }
                        composable("adblock") { AdBlockSettingsScreen(navController) }
                        composable("adblock_sites") { SiteBlockRulesScreen(navController) }
                        composable("adblock_rules/{type}") { entry ->
                            RuleListScreen(
                                navController,
                                entry.arguments?.getString("type") ?: "tracker"
                            )
                        }
                    }
                }
            }
        }

        handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** 处理其它应用通过「打开方式」发来的链接 */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.data?.toString() ?: return
        app.tabManager.openExternalUrl(url)
    }

    // ---------- WebCallbacks ----------

    override fun onShowFileChooser(
        params: WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>
    ) {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        try {
            fileChooserLauncher.launch(params.createIntent())
        } catch (e: Exception) {
            filePathCallback = null
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val needed = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                else -> null
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            request.grant(request.resources)
        } else {
            pendingPermissionRequest = request
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onGeolocationPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            callback.invoke(origin, true, false)
        } else {
            pendingGeoOrigin = origin
            pendingGeoCallback = callback
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (app.tabManager.fullscreenActive) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        app.tabManager.fullscreenActive = true
        val decorView = window.decorView as ViewGroup
        decorView.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        WindowCompat.getInsetsController(window, decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onExitFullscreen() {
        fullscreenView?.let { (window.decorView as ViewGroup).removeView(it) }
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        app.tabManager.fullscreenActive = false
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }
}
