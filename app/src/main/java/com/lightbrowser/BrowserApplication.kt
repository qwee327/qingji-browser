package com.lightbrowser

import android.app.Application
import android.webkit.WebView
import com.lightbrowser.browser.AdBlocker
import com.lightbrowser.browser.TabManager
import com.lightbrowser.browser.Userscripts
import com.lightbrowser.data.AppDatabase
import com.lightbrowser.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BrowserApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val tabManager: TabManager by lazy { TabManager(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AdBlocker.load(this)
        // 自定义拦截规则与站点例外变化时，实时同步到拦截引擎
        appScope.launch {
            database.customRuleDao().observeAll().collect { rules ->
                AdBlocker.updateCustomRules(
                    rules.filter { it.isTracker && it.enabled }.map { it.domain }.toSet(),
                    rules.filter { !it.isTracker && it.enabled }.map { it.domain }.toSet()
                )
            }
        }
        appScope.launch {
            database.siteRuleDao().observeAll().collect { rules ->
                AdBlocker.updateSiteExceptions(rules.associate { it.host to it.level })
            }
        }
        // 扩展脚本变化时，实时同步到脚本引擎
        appScope.launch {
            database.extensionDao().observeAll().collect { extensions ->
                Userscripts.update(extensions)
            }
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
