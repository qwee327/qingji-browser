package com.lightbrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightbrowser.browser.BrowserTab

/**
 * 顶部多标签栏：地址栏下方常驻的横向滚动标签列表。
 * 点击切换标签、点 X 关闭、末尾 + 新建；当前标签高亮并自动滚动到可见位置。
 */
@Composable
fun TabStrip(
    tabs: List<BrowserTab>,
    currentTabId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 当前标签变化时，滚动使其可见
    LaunchedEffect(currentTabId, tabs.size) {
        val index = tabs.indexOfFirst { it.id == currentTabId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
            TabStripItem(
                tab = tab,
                selected = tab.id == currentTabId,
                onClick = { onSelect(tab.id) },
                onClose = { onClose(tab.id) }
            )
        }
        item(key = "__add_tab__") {
            IconButton(onClick = onAdd, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "新标签页",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TabStripItem(
    tab: BrowserTab,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        // 选中标签与网页内容区同色，视觉上与页面连为一体
        color = when {
            selected -> MaterialTheme.colorScheme.surface
            tab.isIncognito -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        modifier = Modifier.height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 10.dp, end = 2.dp)
        ) {
            // 图标：无痕标识 > 网站图标 > 标题首字母
            val icon = tab.favicon
            when {
                tab.isIncognito -> Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = "无痕标签页",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                icon != null -> Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).clip(RoundedCornerShape(2.dp))
                )
                else -> Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            tab.displayTitle.take(1),
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                tab.displayTitle,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 96.dp)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(22.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭标签页",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
