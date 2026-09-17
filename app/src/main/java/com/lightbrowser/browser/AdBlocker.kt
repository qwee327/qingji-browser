package com.lightbrowser.browser

import android.content.Context
import android.net.Uri
import com.lightbrowser.data.AdBlockLevel

/**
 * 跟踪器/广告拦截器：域名后缀匹配，子域名自动命中。
 * 内置规则：assets/tracker_hosts.txt、assets/ad_hosts.txt，可在设置中分别开关；
 * 支持三级默认拦截（无拦截/仅跟踪器/跟踪器+广告）、按站点例外、
 * 自定义规则，以及严格阻止（主文档命中规则时拦截整个网页）。
 */
object AdBlocker {

    @Volatile
    var defaultLevel: Int = AdBlockLevel.TRACKERS_AND_ADS

    @Volatile
    var strictBlocking: Boolean = false

    @Volatile
    var builtinTrackerEnabled: Boolean = true

    @Volatile
    var builtinAdEnabled: Boolean = true

    private val builtinTrackers = HashSet<String>()
    private val builtinAds = HashSet<String>()

    @Volatile
    private var customTrackers: Set<String> = emptySet()

    @Volatile
    private var customAds: Set<String> = emptySet()

    @Volatile
    private var siteLevels: Map<String, Int> = emptyMap()

    @Volatile
    private var loaded = false

    val builtinTrackerCount: Int get() = builtinTrackers.size
    val builtinAdCount: Int get() = builtinAds.size

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        Thread {
            loadAsset(context, "tracker_hosts.txt", builtinTrackers)
            loadAsset(context, "ad_hosts.txt", builtinAds)
        }.start()
    }

    private fun loadAsset(context: Context, name: String, into: HashSet<String>) {
        try {
            context.assets.open(name).bufferedReader().forEachLine { line ->
                val host = line.trim().lowercase()
                if (host.isNotEmpty() && !host.startsWith("#")) into.add(host)
            }
        } catch (e: Exception) {
            // 规则文件缺失时静默降级为不拦截
        }
    }

    /** 由 Application 的数据库观察者调用，保持内存中自定义规则为最新 */
    fun updateCustomRules(trackerDomains: Set<String>, adDomains: Set<String>) {
        customTrackers = trackerDomains
        customAds = adDomains
    }

    /** 由 Application 的数据库观察者调用，保持内存中站点例外为最新 */
    fun updateSiteExceptions(rules: Map<String, Int>) {
        siteLevels = rules
    }

    fun hostOf(url: String?): String? =
        url?.let { runCatching { Uri.parse(it).host?.lowercase() }.getOrNull() }

    /** 站点实际生效的拦截等级：站点例外（后缀匹配）优先，其次默认等级 */
    fun effectiveLevel(pageUrl: String?): Int {
        val host = hostOf(pageUrl) ?: return defaultLevel
        var current = host
        while (current.isNotEmpty()) {
            siteLevels[current]?.let { return it }
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
        return defaultLevel
    }

    /** 子资源请求是否应拦截 */
    fun shouldBlock(requestUrl: String?, pageUrl: String?): Boolean {
        if (requestUrl == null) return false
        val level = effectiveLevel(pageUrl ?: requestUrl)
        if (level <= AdBlockLevel.OFF) return false
        val host = hostOf(requestUrl) ?: return false
        if (matchesAny(host, tracker = true)) return true
        return level >= AdBlockLevel.TRACKERS_AND_ADS && matchesAny(host, tracker = false)
    }

    /** 严格阻止：主文档地址本身命中拦截规则时拦截整个网页 */
    fun shouldBlockPage(url: String?): Boolean {
        if (!strictBlocking || url == null) return false
        val level = effectiveLevel(url)
        if (level <= AdBlockLevel.OFF) return false
        val host = hostOf(url) ?: return false
        if (matchesAny(host, tracker = true)) return true
        return level >= AdBlockLevel.TRACKERS_AND_ADS && matchesAny(host, tracker = false)
    }

    private fun matchesAny(host: String, tracker: Boolean): Boolean {
        val builtin = if (tracker) builtinTrackers else builtinAds
        val builtinOn = if (tracker) builtinTrackerEnabled else builtinAdEnabled
        val custom = if (tracker) customTrackers else customAds
        var current = host
        while (current.isNotEmpty()) {
            if (builtinOn && builtin.contains(current)) return true
            if (custom.contains(current)) return true
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
        return false
    }
}
