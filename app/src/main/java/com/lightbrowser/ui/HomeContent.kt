package com.lightbrowser.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightbrowser.R
import com.lightbrowser.data.HistoryItem

private data class Shortcut(val name: String, val url: String)

private val defaultShortcuts = listOf(
    Shortcut("百度", "https://www.baidu.com"),
    Shortcut("哔哩哔哩", "https://www.bilibili.com"),
    Shortcut("知乎", "https://www.zhihu.com"),
    Shortcut("微博", "https://weibo.com"),
    Shortcut("淘宝", "https://www.taobao.com"),
    Shortcut("京东", "https://www.jd.com"),
    Shortcut("GitHub", "https://github.com"),
    Shortcut("维基百科", "https://zh.wikipedia.org")
)

/**
 * 新标签页（主页）：Logo + 搜索框 + 常用网站速拨。
 * 常用网站优先取自真实访问历史，不足时用预设站点补齐。
 */
@Composable
fun HomeContent(
    topSites: List<HistoryItem>,
    onSearch: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortcuts = remember(topSites) {
        val result = LinkedHashMap<String, Shortcut>()
        topSites.forEach { item ->
            val host = runCatching { Uri.parse(item.url).host }.getOrNull() ?: return@forEach
            if (!result.containsKey(host)) {
                result[host] = Shortcut(item.title.ifBlank { host }, item.url)
            }
        }
        defaultShortcuts.forEach { shortcut ->
            val host = runCatching { Uri.parse(shortcut.url).host }.getOrNull()
            if (host != null && !result.containsKey(host) && result.size < 8) {
                result[host] = shortcut
            }
        }
        result.values.take(8)
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 96.dp, bottom = 40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "轻级浏览器",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable(onClick = onSearch)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "搜索或输入网址",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "常用网站",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 8.dp)
                )
            }
            items(shortcuts, key = { it.url }) { shortcut ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onOpenUrl(shortcut.url) }
                        .padding(vertical = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                shortcut.name.take(1),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        shortcut.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
