package com.lightbrowser.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast

object DownloadHelper {

    fun download(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(context, "该链接不支持下载", Toast.LENGTH_SHORT).show()
                return
            }
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription(url)
                setMimeType(mimeType ?: "application/octet-stream")
                userAgent?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "开始下载：$fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
