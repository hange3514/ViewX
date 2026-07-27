package top.yogiczy.mytv.data.repositories.iptv.parser

import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList

class M3uIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.startsWith("#EXTM3U")
    }

    override suspend fun parse(data: String): IptvGroupList {
        val lines = data.split("\r\n", "\n")
        val iptvList = mutableListOf<IptvResponseItem>()

        // #EXTM3U 全局头可声明默认回放方式（如 catchup="append" catchup-source="?playbackbegin=..."），
        // 频道行未声明时继承；频道行声明了则以频道行为准
        var globalCatchup = ""
        var globalCatchupSource = ""
        lines.firstOrNull { it.startsWith("#EXTM3U") }?.let { header ->
            globalCatchup = CATCHUP_REGEX.find(header)?.groupValues?.get(1) ?: ""
            globalCatchupSource = CATCHUP_SOURCE_REGEX.find(header)?.groupValues?.get(1) ?: ""
        }

        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXTINF")) return@forEachIndexed

            // 播放地址在下一行；源文件被截断或下一行仍是指令时跳过该条目，防越界/误把注释当 URL
            val url = lines.getOrNull(index + 1)?.trim()
                ?.takeIf { it.isNotBlank() && !it.startsWith("#") }
                ?: return@forEachIndexed

            val name = line.split(",").last()
            val channelName = TVG_NAME_REGEX.find(line)?.groupValues?.get(1) ?: name
            val tvgId = TVG_ID_REGEX.find(line)?.groupValues?.get(1) ?: ""
            val groupName = GROUP_TITLE_REGEX.find(line)?.groupValues?.get(1) ?: "其他"
            val catchup = CATCHUP_REGEX.find(line)?.groupValues?.get(1) ?: ""
            val catchupSource = CATCHUP_SOURCE_REGEX.find(line)?.groupValues?.get(1) ?: ""
            val catchupDays = CATCHUP_DAYS_REGEX.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            iptvList.add(
                IptvResponseItem(
                    name = name.trim(),
                    channelName = channelName.trim(),
                    tvgId = tvgId.trim(),
                    groupName = groupName.trim(),
                    url = url,
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
                        urlList = nameEntry.value.map { it.url },
                        catchup = nameEntry.value.first().catchup,
                        catchupSource = nameEntry.value.first().catchupSource,
                        catchupDays = nameEntry.value.first().catchupDays,
                        tvgId = nameEntry.value.first().tvgId,
                    )
                })
            )
        })
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
        val TVG_NAME_REGEX = Regex("tvg-name=\"(.+?)\"")
        val TVG_ID_REGEX = Regex("tvg-id=\"(.+?)\"")
        val GROUP_TITLE_REGEX = Regex("group-title=\"(.+?)\"")
        val CATCHUP_REGEX = Regex("catchup=\"(.+?)\"")
        val CATCHUP_SOURCE_REGEX = Regex("catchup-source=\"(.+?)\"")
        val CATCHUP_DAYS_REGEX = Regex("catchup-days=\"(.+?)\"")
    }
}