package top.yogiczy.mytv.data.repositories.iptv.parser

import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList

class M3uIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.m3uHeader() != null
    }

    override suspend fun parse(data: String): IptvGroupList {
        val iptvList = mutableListOf<IptvResponseItem>()

        // #EXTM3U 全局头可声明默认回放方式（如 catchup="append" catchup-source="?playbackbegin=..."），
        // 频道行未声明时继承；频道行声明了则以频道行为准
        var globalCatchup = ""
        var globalCatchupSource = ""
        data.m3uHeader()?.let { header ->
            globalCatchup = CATCHUP_REGEX.find(header)?.groupValues?.get(1) ?: ""
            globalCatchupSource = CATCHUP_SOURCE_REGEX.find(header)?.groupValues?.get(1) ?: ""
        }

        var pendingEntry: String? = null
        data.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingEntry = line
                return@forEach
            }
            // 播放地址前可带 #EXTVLCOPT、#KODIPROP 等指令或空行。
            if (line.isBlank() || line.startsWith('#')) return@forEach
            val entry = pendingEntry ?: return@forEach
            pendingEntry = null

            val comma = metadataEnd(entry)
            if (comma < 0) return@forEach
            val attributes = entry.substring(0, comma)
            val tvgName = TVG_NAME_REGEX.find(attributes)?.groupValues?.get(1)?.trim()
            val name = entry.substring(comma + 1).trim().ifBlank { tvgName.orEmpty() }
            if (name.isEmpty()) return@forEach
            val tvgId = TVG_ID_REGEX.find(attributes)?.groupValues?.get(1) ?: ""
            val groupName = GROUP_TITLE_REGEX.find(attributes)?.groupValues?.get(1) ?: "其他"
            val catchup = CATCHUP_REGEX.find(attributes)?.groupValues?.get(1) ?: ""
            val catchupSource = CATCHUP_SOURCE_REGEX.find(attributes)?.groupValues?.get(1) ?: ""
            val catchupDays = CATCHUP_DAYS_REGEX.find(attributes)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            iptvList.add(
                IptvResponseItem(
                    name = name,
                    channelName = tvgName?.ifEmpty { name } ?: name,
                    tvgId = tvgId.trim(),
                    groupName = groupName.trim(),
                    url = line,
                    catchup = catchup.trim().ifBlank { globalCatchup.trim() },
                    catchupSource = catchupSource.trim().ifBlank { globalCatchupSource.trim() },
                    catchupDays = catchupDays,
                )
            )
        }

        return IptvGroupList(iptvList.groupBy { it.groupName }.map { groupEntry ->
            IptvGroup(
                name = groupEntry.key,
                iptvList = IptvList(groupEntry.value.groupBy { it.name }.map { nameEntry ->
                    Iptv(
                        name = nameEntry.key,
                        channelName = nameEntry.value.first().channelName,
                        urlList = nameEntry.value.map { it.url }.distinct(),
                        catchup = nameEntry.value.first().catchup,
                        catchupSource = nameEntry.value.first().catchupSource,
                        catchupDays = nameEntry.value.first().catchupDays,
                        tvgId = nameEntry.value.first().tvgId,
                    )
                })
            )
        })
    }

    private fun metadataEnd(line: String): Int {
        var quoted = false
        var escaped = false
        for ((index, character) in line.withIndex()) {
            when {
                escaped -> escaped = false
                quoted && character == '\\' -> escaped = true
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> return index
            }
        }
        return -1
    }

    private data class IptvResponseItem(
        val name: String,
        val channelName: String,
        val tvgId: String,
        val groupName: String,
        val url: String,
        val catchup: String = "",
        val catchupSource: String = "",
        val catchupDays: Int = 0,
    )

    private companion object {
        // 正则提前编译，避免逐行构造
        val TVG_NAME_REGEX = m3uAttribute("tvg-name")
        val TVG_ID_REGEX = m3uAttribute("tvg-id")
        val GROUP_TITLE_REGEX = m3uAttribute("group-title")
        val CATCHUP_REGEX = m3uAttribute("catchup")
        val CATCHUP_SOURCE_REGEX = m3uAttribute("catchup-source")
        val CATCHUP_DAYS_REGEX = m3uAttribute("catchup-days")
    }
}

internal fun String.m3uHeader(): String? =
    trimStart { it.isWhitespace() || it == '\uFEFF' }
        .lineSequence()
        .firstOrNull()
        ?.takeIf { it.startsWith("#EXTM3U", ignoreCase = true) }

internal fun m3uAttribute(name: String): Regex =
    Regex("(?:^|\\s)$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
