package top.yogiczy.mytv.data.utils

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 根据节目起止时间生成 IPTV 回放地址
 *
 * 支持模板：
 * - `${(b)yyyyMMddHHmmss}` / `${(e)yyyyMMddHHmmss}`：开始/结束时间
 * - `${start}` / `${end}`：Unix 毫秒时间戳
 * - `${timestamp}`：开始时间 Unix 毫秒时间戳
 */
object CatchupUrlBuilder {

    private val templateRegex = Regex("\\\$\\{([^}]+)\\}")

    fun build(catchupSource: String, startAt: Long, endAt: Long): String {
        if (catchupSource.isBlank()) return ""

        return templateRegex.replace(catchupSource) { matchResult ->
            val content = matchResult.groupValues[1]
            when {
                content.startsWith("(b)") -> {
                    val pattern = content.removePrefix("(b)")
                    formatTime(startAt, pattern)
                }

                content.startsWith("(e)") -> {
                    val pattern = content.removePrefix("(e)")
                    formatTime(endAt, pattern)
                }

                content == "start" || content == "timestamp" -> startAt.toString()
                content == "end" -> endAt.toString()
                else -> matchResult.value
            }
        }
    }

    private fun formatTime(timeMs: Long, pattern: String): String {
        return try {
            // 使用 Locale.ROOT，避免部分语言环境下生成非 ASCII 数字导致服务器无法解析
            SimpleDateFormat(pattern, Locale.ROOT).format(timeMs)
        } catch (e: Exception) {
            android.util.Log.w("CatchupUrlBuilder", "formatTime failed: pattern=$pattern", e)
            ""
        }
    }
}
