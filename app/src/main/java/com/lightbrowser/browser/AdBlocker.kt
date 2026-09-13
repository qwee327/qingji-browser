package com.lightbrowser.browser

import android.content.Context
import android.net.Uri

/**
 * 基于域名后缀匹配的广告拦截器。
 * 规则文件：assets/adblock_hosts.txt，每行一个域名，子域名自动命中。
 */
object AdBlocker {

    private val hosts = HashSet<String>()

    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        Thread {
            try {
                context.assets.open("adblock_hosts.txt").bufferedReader().forEachLine { line ->
                    val host = line.trim().lowercase()
                    if (host.isNotEmpty() && !host.startsWith("#")) hosts.add(host)
                }
            } catch (e: Exception) {
                // 规则文件缺失时静默降级为不拦截
            }
        }.start()
    }

    fun isAd(url: String?): Boolean {
        if (!enabled || url == null) return false
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        var current = host
        while (current.isNotEmpty()) {
            if (hosts.contains(current)) return true
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
        }
        return false
    }
}
