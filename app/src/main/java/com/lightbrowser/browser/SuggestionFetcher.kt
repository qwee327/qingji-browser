package com.lightbrowser.browser

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 从搜索引擎在线获取搜索联想词，失败时静默返回空列表。
 */
object SuggestionFetcher {

    suspend fun fetch(engine: SearchEngine, query: String): List<String> = withContext(Dispatchers.IO) {
        val base = engine.suggestUrl ?: return@withContext emptyList()
        try {
            val conn = URL(base + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            parse(engine.id, body)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parse(engineId: String, body: String): List<String> = try {
        when (engineId) {
            "baidu" -> {
                val arr = JSONObject(body).optJSONArray("g") ?: return emptyList()
                (0 until arr.length())
                    .mapNotNull { arr.optJSONObject(it)?.optString("q") }
                    .filter { it.isNotBlank() }
                    .take(8)
            }
            "bing" -> {
                val results = JSONObject(body).optJSONObject("AS")?.optJSONArray("Results")
                    ?: return emptyList()
                val out = mutableListOf<String>()
                for (i in 0 until results.length()) {
                    val suggests = results.optJSONObject(i)?.optJSONArray("Suggests") ?: continue
                    for (j in 0 until suggests.length()) {
                        suggests.optJSONObject(j)?.optString("Txt")
                            ?.takeIf { it.isNotBlank() }
                            ?.let(out::add)
                    }
                }
                out.take(8)
            }
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}
