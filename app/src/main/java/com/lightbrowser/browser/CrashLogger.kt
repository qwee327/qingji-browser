package com.lightbrowser.browser

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃自诊断：全局未捕获异常写入 filesDir/crash_log.txt，
 * 下次启动时在主页弹窗展示完整堆栈（可复制反馈）。
 * 后台协程的非致命异常也可通过 [record] 记录，不再导致进程被杀。
 */
object CrashLogger {

    private const val FILE_NAME = "crash_log.txt"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 安装全局崩溃捕获，必须在 Application.onCreate 尽早调用 */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, thread, throwable) }
            // 交还给系统默认处理器，保持原有崩溃行为（进程结束）
            previous?.uncaughtException(thread, throwable) ?: run {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    /** 记录非致命异常（后台协程等场景），追加写入，不中断运行 */
    fun record(context: Context, throwable: Throwable) {
        runCatching { write(context, Thread.currentThread(), throwable) }
    }

    @Synchronized
    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = "time=$time thread=${thread.name}\n" +
            "device=${Build.MANUFACTURER}/${Build.MODEL} android=${Build.VERSION.RELEASE}(API ${Build.VERSION.SDK_INT})\n\n"
        // 追加模式：保留最近几次崩溃，单文件过大时截断重写
        val f = file(context)
        val old = if (f.exists() && f.length() < 256 * 1024) f.readText() else ""
        f.writeText(header + sw + "\n\n----------\n\n" + old)
    }

    /** 读取崩溃日志（不删除） */
    fun read(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** 清除崩溃日志 */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
