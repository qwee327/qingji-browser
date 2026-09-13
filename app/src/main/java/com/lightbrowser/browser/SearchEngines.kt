package com.lightbrowser.browser

data class SearchEngine(
    val id: String,
    val displayName: String,
    val searchUrlPrefix: String,
    val suggestUrl: String?
)

object SearchEngines {
    val BAIDU = SearchEngine(
        id = "baidu",
        displayName = "百度",
        searchUrlPrefix = "https://www.baidu.com/s?wd=",
        suggestUrl = "https://www.baidu.com/sugrec?prod=wise&wd="
    )
    val BING = SearchEngine(
        id = "bing",
        displayName = "必应",
        searchUrlPrefix = "https://www.bing.com/search?q=",
        suggestUrl = "https://api.bing.com/qsonhs.aspx?q="
    )
    val GOOGLE = SearchEngine(
        id = "google",
        displayName = "谷歌",
        searchUrlPrefix = "https://www.google.com/search?q=",
        suggestUrl = null
    )
    val DUCKDUCKGO = SearchEngine(
        id = "duckduckgo",
        displayName = "DuckDuckGo",
        searchUrlPrefix = "https://duckduckgo.com/?q=",
        suggestUrl = null
    )

    val list = listOf(BAIDU, BING, GOOGLE, DUCKDUCKGO)

    fun byId(id: String): SearchEngine = list.firstOrNull { it.id == id } ?: BAIDU
}
