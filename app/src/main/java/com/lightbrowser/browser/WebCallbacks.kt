package com.lightbrowser.browser

import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.view.View

/**
 * 需要 Activity 层能力（文件选择、运行时权限、视频全屏）的回调，
 * 由 MainActivity 实现并注入 [TabManager]。
 */
interface WebCallbacks {
    fun onShowFileChooser(params: WebChromeClient.FileChooserParams, callback: ValueCallback<Array<Uri>>)
    fun onPermissionRequest(request: PermissionRequest)
    fun onGeolocationPrompt(origin: String, callback: GeolocationPermissions.Callback)
    fun onEnterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)
    fun onExitFullscreen()
}
