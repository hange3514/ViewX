package top.yogiczy.mytv.data.utils

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 根据节目起止时间生成 IPTV 回放地址
 *
 * 支持模板：
 * - `${(b)yyyyMMddHHmmss}` / `${(e)yyyyMMddHHmmss}`：开始/结束时间，
 *   可带时区后缀如 `${(b)yyyyMMddHHmmss:GMT+8}`（未指定时用设备默认时区）
 * - `${start}` / `${end}`：Unix 毫秒时间戳
 * - `${timestamp}`：开始时间 Unix 毫秒时间戳
 */
object CatchupUrlBuilder {

    private val templateRegex = Regex("\\\$\\{([^}]+)\\}")
    private val timezoneSuffixRegex = Regex(":GMT([+-]\\d{1,2})(\\d{2})?$", RegexOption.IGNORE_CASE)

    fun build(catchupSource: String, startAt: Long, endAt: Long): String {
        if (catchupSource.isBlank()) return ""

        var failed = false
        val result = templateRegex.replace(catchupSource) { matchResult ->
            val content = matchResult.groupValues[1]
            when {
                content.startsWith("(b)") -> {
                    formatTime(startAt, content.removePrefix("(b)")) ?: run { failed = true; "" }
                }

                content.startsWith("(e)") -> {
                    formatTime(endAt, content.removePrefix("(e)")) ?: run { failed = true; "" }
                }

                content == "start" || content == "timestamp" -> startAt.toString()
                content == "end" -> endAt.toString()
                else -> matchResult.value
            }
        }

        // 任一占位符格式化失败时整体放弃，避免产生 playseek=-20260714... 之类的半截 URL
        if (failed) {
            android.util.Log.w("CatchupUrlBuilder", "时间格式化失败，放弃回放地址: $catchupSource")
            return ""
        }
        return result
    }

    /**
     * 按模板格式化时间，失败返回 null。
     * 使用 Locale.ROOT，避免部分语言环境下生成非 ASCII 数字导致服务器无法解析。
     */
    private fun formatTime(timeMs: Long, pattern: String): String? {
        var timeZone: TimeZone? = null
        var p = pattern
        timezoneSuffixRegex.find(p)?.let { match ->
            timeZone = TimeZone.getTimeZone("GMT${match.groupValues[1]}${match.groupValues[2]}")
            p = p.removeRange(match.range)
        }

        return try {
            SimpleDateFormat(p, Locale.ROOT)
                .apply { timeZone?.let { tz -> this.timeZone = tz } }
                .format(timeMs)
        } catch (e: Exception) {
            android.util.Log.w("CatchupUrlBuilder", "formatTime failed: pattern=$pattern", e)
            null
        }
    }
}
