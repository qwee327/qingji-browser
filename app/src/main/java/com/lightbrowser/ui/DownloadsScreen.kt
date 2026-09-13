package com.lightbrowser.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import java.util.Locale

private data class DownloadEntry(
    val id: Long,
    val title: String,
    val status: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val mimeType: String
)

private fun queryDownloads(context: Context): List<DownloadEntry> {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = dm.query(DownloadManager.Query()) ?: return emptyList()
    val list = mutableListOf<DownloadEntry>()
    cursor.use {
        val idIdx = it.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleIdx = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val soFarIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val mimeIdx = it.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
        while (it.moveToNext()) {
            list.add(
                DownloadEntry(
                    id = it.getLong(idIdx),
                    title = it.getString(titleIdx) ?: "未命名文件",
                    status = it.getInt(statusIdx),
                    totalBytes = it.getLong(totalIdx),
                    downloadedBytes = it.getLong(soFarIdx),
                    mimeType = it.getString(mimeIdx) ?: "*/*"
                )
            )
        }
    }
    return list.sortedByDescending { it.id }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1073741824.0)
}

private fun statusText(entry: DownloadEntry): String = when (entry.status) {
    DownloadManager.STATUS_RUNNING ->
        "下载中 ${formatSize(entry.downloadedBytes)} / ${formatSize(entry.totalBytes)}"
    DownloadManager.STATUS_PAUSED -> "已暂停"
    DownloadManager.STATUS_PENDING -> "等待下载"
    DownloadManager.STATUS_SUCCESSFUL -> "已完成 · ${formatSize(entry.totalBytes)}"
    DownloadManager.STATUS_FAILED -> "下载失败"
    else -> "未知状态"
}

private fun openDownload(context: Context, entry: DownloadEntry) {
    try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(entry.id) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, entry.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavController) {
    val context = LocalContext.current
    var downloads by remember { mutableStateOf<List<DownloadEntry>>(emptyList()) }

    // 每秒轮询一次下载进度
    LaunchedEffect(Unit) {
        while (true) {
            downloads = queryDownloads(context)
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载内容") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { downloads = queryDownloads(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无下载内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(downloads, key = { it.id }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = entry.status == DownloadManager.STATUS_SUCCESSFUL) {
                                openDownload(context, entry)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            statusText(entry),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (entry.status == DownloadManager.STATUS_RUNNING && entry.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (entry.downloadedBytes.toFloat() / entry.totalBytes.toFloat())
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        } else if (entry.status == DownloadManager.STATUS_RUNNING) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}
