package com.lightbrowser

import android.app.Application
import android.webkit.WebView
import com.lightbrowser.browser.AdBlocker
import com.lightbrowser.browser.TabManager
import com.lightbrowser.data.AppDatabase
import com.lightbrowser.data.SettingsRepository

class BrowserApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val tabManager: TabManager by lazy { TabManager(this) }

    override fun onCreate() {
        super.onCreate()
        AdBlocker.load(this)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
