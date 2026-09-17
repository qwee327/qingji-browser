package com.lightbrowser.browser

import com.lightbrowser.data.BrowserExtension
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 扩展脚本引擎：兼容 Tampermonkey/油猴风格的 .user.js 脚本。
 * 解析 ==UserScript== 元数据（@name/@match/@include/@exclude/@run-at），
 * 按 Chrome match pattern 规则匹配网址，在页面开始/完成时注入执行。
 *
 * 说明：WebView 无 chrome.* 扩展 API，本引擎覆盖内容脚本类能力，
 * 不支持桌面版 CRX 扩展的后台 Service Worker 等机制。
 */
object Userscripts {

    const val RUN_AT_START = "document-start"
    const val RUN_AT_END = "document-end"

    data class ParsedScript(
        val name: String,
        val matches: List<String>,
        val excludes: List<String>,
        val runAt: String,
        val code: String
    )

    @Volatile
    private var active: List<ParsedScript> = emptyList()

    /** 由 Application 的数据库观察者调用，保持内存中启用脚本为最新 */
    fun update(extensions: List<BrowserExtension>) {
        active = extensions.filter { it.enabled }.map { parse(it.code) }
    }

    /** 解析 ==UserScript== 元数据块；无元数据时按「所有网址、页面完成后运行」处理 */
    fun parse(code: String): ParsedScript {
        val meta = Regex("==UserScript==([\\s\\S]*?)==/UserScript==")
            .find(code)?.groupValues?.get(1).orEmpty()

        fun values(key: String): List<String> =
            Regex("^\\s*//\\s*@$key\\s+(.+?)\\s*$", RegexOption.MULTILINE)
                .findAll(meta).map { it.groupValues[1] }.toList()

        val name = values("name").firstOrNull()?.ifBlank { null } ?: "未命名脚本"
        val includes = (values("match") + values("include")).ifEmpty { listOf("*://*/*") }
        val runAt = if (values("run-at").firstOrNull() == RUN_AT_START) RUN_AT_START else RUN_AT_END
        return ParsedScript(name, includes, values("exclude"), runAt, code)
    }

    /** 某网址 + 注入时机下应执行的脚本 */
    fun scriptsFor(url: String, runAt: String): List<ParsedScript> =
        active.filter { it.runAt == runAt && matches(url, it.matches, it.excludes) }

    fun matches(url: String, includes: List<String>, excludes: List<String>): Boolean {
        if (excludes.any { matchPattern(url, it) }) return false
        return includes.any { matchPattern(url, it) }
    }

    /** Chrome match pattern（scheme://host/path，* 通配），另兼容 /regex/ 与纯 glob 写法 */
    fun matchPattern(url: String, pattern: String): Boolean =
        runCatching { patternRegex(pattern).matches(url) }.getOrDefault(false)

    private val regexCache = HashMap<String, Regex>()

    private fun patternRegex(pattern: String): Regex = regexCache.getOrPut(pattern) {
        val p = pattern.trim()
        if (p.length > 2 && p.startsWith("/") && p.endsWith("/")) {
            return@getOrPut Regex(p.substring(1, p.length - 1))
        }
        val schemeSep = p.indexOf("://")
        if (schemeSep < 0) return@getOrPut Regex("^" + globBody(p) + "$")
        val scheme = p.substring(0, schemeSep)
        val rest = p.substring(schemeSep + 3)
        val slash = rest.indexOf('/')
        val host = if (slash < 0) rest else rest.substring(0, slash)
        val path = if (slash < 0) "" else rest.substring(slash)
        val schemePart = if (scheme == "*") "https?" else Regex.escape(scheme)
        val hostPart = when {
            host == "*" -> "[^/]+"
            host.startsWith("*.") -> "([^/]+\\.)?" + Regex.escape(host.substring(2))
            else -> Regex.escape(host)
        }
        Regex("^" + schemePart + "://" + hostPart + globBody(path) + "$")
    }

    private fun globBody(glob: String): String = buildString {
        for (c in glob) {
            if (c == '*') append(".*") else append(Regex.escape(c.toString()))
        }
    }

    /** 包裹用户代码：异常不影响页面，错误输出到控制台 */
    fun wrap(code: String): String =
        "(function(){try{\n$code\n}catch(e){console.error('[轻级扩展]', e)}})();"

    /** 从 URL 下载脚本文本，失败返回 null */
    suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            text
        }.getOrNull()
    }
}
