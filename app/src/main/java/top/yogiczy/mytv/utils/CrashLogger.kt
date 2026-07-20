package top.yogiczy.mytv.utils

import android.content.Context
import java.io.File

/**
 * 全局崩溃日志：未捕获异常写入缓存文件，
 * 下次启动时读入日志历史（可通过设置-日志页或网页设置页查看）
 */
object CrashLogger {
    private const val CRASH_FILE_NAME = "last_crash.log"

    fun install(context: Context) {
        // 上次崩溃的日志先转存到日志历史
        val crashFile = File(context.cacheDir, CRASH_FILE_NAME)
        if (crashFile.exists()) {
            try {
                Logger.create("Crash").e("上次崩溃:\n${crashFile.readText()}")
                crashFile.delete()
            } catch (_: Exception) {
            }
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(context.cacheDir, CRASH_FILE_NAME).writeText(
                    "thread=${thread.name}\n${throwable.stackTraceToString()}"
                )
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
