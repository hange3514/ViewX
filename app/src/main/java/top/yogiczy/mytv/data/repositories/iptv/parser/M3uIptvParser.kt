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

        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXTINF")) return@forEachIndexed

            val name = line.split(",").last()
            val channelName = Regex("tvg-name=\"(.+?)\"").find(line)?.groupValues?.get(1) ?: name
            val tvgId = Regex("tvg-id=\"(.+?)\"").find(line)?.groupValues?.get(1) ?: ""
            val groupName = Regex("group-title=\"(.+?)\"").find(line)?.groupValues?.get(1) ?: "其他"
            val catchup = Regex("catchup=\"(.+?)\"").find(line)?.groupValues?.get(1) ?: ""
            val catchupSource = Regex("catchup-source=\"(.+?)\"").find(line)?.groupValues?.get(1) ?: ""
            val catchupDays = Regex("catchup-days=\"(.+?)\"").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            iptvList.add(
                IptvResponseItem(
                    name = name.trim(),
                    channelName = channelName.trim(),
                    tvgId = tvgId.trim(),
                    groupName = groupName.trim(),
                    url = lines[index + 1].trim(),
                    catchup = catchup.trim(),
                    catchupSource = catchupSource.trim(),
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
}