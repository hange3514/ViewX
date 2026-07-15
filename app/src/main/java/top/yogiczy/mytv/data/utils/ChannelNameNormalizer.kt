package top.yogiczy.mytv.data.utils

/**
 * 频道名称归一化工具，用于解决直播源 tvg-name 与 EPG xmltv display-name
 * 因空格、后缀、大小写、全角等差异导致无法匹配的问题。
 */
object ChannelNameNormalizer {

    /**
     * 需要移除的常见画质/类型后缀，按长度降序排列，避免短后缀误删长后缀。
     */
    private val SUFFIXES = listOf(
        "highdefinition",
        "blueray",
        "blue-ray",
        "1080p",
        "720p",
        "uhd",
        "fhd",
        "4k",
        "8k",
        "sd",
        "hd",
        "dtv",
        "atv",
        "高清",
        "超清",
        "标清",
        "蓝光",
        "数字",
        "频道",
        "电视",
    )

    /**
     * 把频道名称转成可用于匹配的标准形式：
     * 1. 全角字符转半角；
     * 2. 去除所有空白；
     * 3. 转小写；
     * 4. 去除画质/类型后缀。
     */
    fun normalize(name: String): String {
        var result = name.trim()

        // 全角转半角（U+FF01..U+FF5E 及全角空格 U+3000）
        result = result.map { c ->
            when (c) {
                '\u3000' -> ' '
                in '\uFF01'.. '\uFF5E' -> (c.code - 0xFEE0).toChar()
                else -> c
            }
        }.joinToString("")

        // 去除所有空白
        result = result.replace(Regex("\\s+"), "")

        // 转小写
        result = result.lowercase()

        // 循环移除后缀
        var changed = true
        while (changed) {
            changed = false
            for (suffix in SUFFIXES) {
                if (result.endsWith(suffix, ignoreCase = true)) {
                    result = result.dropLast(suffix.length)
                    changed = true
                    break
                }
            }
        }

        return result
    }

    /**
     * 判断两个频道名称是否指同一频道。
     * 规则：
     * - 归一化后完全相同；
     * - 或其中一个是另一个的前缀，且较长者下一个字符不是数字
     *   （用于区分 CCTV-1 与 CCTV-11，同时允许 CCTV-1 匹配 CCTV-1综合高清）。
     */
    fun matches(channelA: String, channelB: String): Boolean {
        val a = normalize(channelA)
        val b = normalize(channelB)
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false

        if (a.length < b.length) {
            return b.startsWith(a) && !b.getOrNull(a.length)?.isDigit()!!
        } else {
            return a.startsWith(b) && !a.getOrNull(b.length)?.isDigit()!!
        }
    }
}

fun String.matchesChannel(other: String): Boolean =
    ChannelNameNormalizer.matches(this, other)
