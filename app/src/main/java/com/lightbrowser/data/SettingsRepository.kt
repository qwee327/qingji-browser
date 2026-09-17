package com.lightbrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "browser_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 拦截等级：无拦截 / 仅拦截跟踪器 / 拦截跟踪器和广告 */
object AdBlockLevel {
    const val OFF = 0
    const val TRACKERS = 1
    const val TRACKERS_AND_ADS = 2
}

data class BrowserSettings(
    val searchEngineId: String = "baidu",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themePreset: String = "classic",
    val adBlockLevel: Int = AdBlockLevel.TRACKERS_AND_ADS,
    val strictBlocking: Boolean = false,
    val builtinTrackerRules: Boolean = true,
    val builtinAdRules: Boolean = true,
    val darkWebContentEnabled: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val blockThirdPartyCookies: Boolean = false,
    val textZoom: Int = 100,
    val desktopModeDefault: Boolean = false,
    val restoreTabsOnStart: Boolean = true,
    val clearDataOnExit: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val AD_BLOCK = booleanPreferencesKey("ad_block") // 旧版开关，仅用于迁移
        val AD_BLOCK_LEVEL = intPreferencesKey("ad_block_level")
        val STRICT_BLOCKING = booleanPreferencesKey("strict_blocking")
        val BUILTIN_TRACKER_RULES = booleanPreferencesKey("builtin_tracker_rules")
        val BUILTIN_AD_RULES = booleanPreferencesKey("builtin_ad_rules")
        val DARK_WEB = booleanPreferencesKey("dark_web_content")
        val JAVA_SCRIPT = booleanPreferencesKey("java_script")
        val BLOCK_3P_COOKIES = booleanPreferencesKey("block_3p_cookies")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val RESTORE_TABS = booleanPreferencesKey("restore_tabs")
        val CLEAR_ON_EXIT = booleanPreferencesKey("clear_on_exit")
    }

    val settings: Flow<BrowserSettings> = context.settingsDataStore.data.map { p ->
        BrowserSettings(
            searchEngineId = p[Keys.SEARCH_ENGINE] ?: "baidu",
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            themePreset = p[Keys.THEME_PRESET] ?: "classic",
            // 旧版布尔开关迁移：旧用户关闭过广告拦截则对应「无拦截」
            adBlockLevel = p[Keys.AD_BLOCK_LEVEL]
                ?: if (p[Keys.AD_BLOCK] == false) AdBlockLevel.OFF else AdBlockLevel.TRACKERS_AND_ADS,
            strictBlocking = p[Keys.STRICT_BLOCKING] ?: false,
            builtinTrackerRules = p[Keys.BUILTIN_TRACKER_RULES] ?: true,
            builtinAdRules = p[Keys.BUILTIN_AD_RULES] ?: true,
            darkWebContentEnabled = p[Keys.DARK_WEB] ?: true,
            javaScriptEnabled = p[Keys.JAVA_SCRIPT] ?: true,
            blockThirdPartyCookies = p[Keys.BLOCK_3P_COOKIES] ?: false,
            textZoom = p[Keys.TEXT_ZOOM] ?: 100,
            desktopModeDefault = p[Keys.DESKTOP_MODE] ?: false,
            restoreTabsOnStart = p[Keys.RESTORE_TABS] ?: true,
            clearDataOnExit = p[Keys.CLEAR_ON_EXIT] ?: false
        )
    }

    suspend fun setSearchEngine(id: String) =
        context.settingsDataStore.edit { it[Keys.SEARCH_ENGINE] = id }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = v }

    suspend fun setThemePreset(id: String) =
        context.settingsDataStore.edit { it[Keys.THEME_PRESET] = id }

    suspend fun setAdBlockLevel(level: Int) =
        context.settingsDataStore.edit { it[Keys.AD_BLOCK_LEVEL] = level }

    suspend fun setStrictBlocking(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.STRICT_BLOCKING] = v }

    suspend fun setBuiltinTrackerRules(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.BUILTIN_TRACKER_RULES] = v }

    suspend fun setBuiltinAdRules(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.BUILTIN_AD_RULES] = v }

    suspend fun setDarkWebContent(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.DARK_WEB] = v }

    suspend fun setJavaScript(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.JAVA_SCRIPT] = v }

    suspend fun setBlockThirdPartyCookies(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.BLOCK_3P_COOKIES] = v }

    suspend fun setTextZoom(v: Int) =
        context.settingsDataStore.edit { it[Keys.TEXT_ZOOM] = v }

    suspend fun setDesktopModeDefault(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.DESKTOP_MODE] = v }

    suspend fun setRestoreTabsOnStart(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.RESTORE_TABS] = v }

    suspend fun setClearDataOnExit(v: Boolean) =
        context.settingsDataStore.edit { it[Keys.CLEAR_ON_EXIT] = v }
}
