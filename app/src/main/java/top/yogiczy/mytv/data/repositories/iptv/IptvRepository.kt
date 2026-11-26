package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.download
import top.yogiczy.mytv.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.data.repositories.iptv.parser.m3uAttribute
import top.yogiczy.mytv.data.repositories.iptv.parser.m3uHeader
import top.yogiczy.mytv.utils.Logger
import java.util.concurrent.TimeUnit

data class LoadedPlaylist(val groups: IptvGroupList, val embeddedEpgUrl: String?)

/**
 * 直播源获取
 */
class IptvRepository : FileCacheRepository("iptv-viewx-v2.txt") {
    private val log = Logger.create(javaClass.simpleName)

    companion object {
        private val X_TVG_URL_REGEX = m3uAttribute("x-tvg-url")
        private val ABSOLUTE_URL = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
        private val httpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    }

    /**
     * 简化规则
     */
    private fun simplifyTest(iptv: Iptv): Boolean {
        return iptv.name.lowercase().startsWith("cctv") || iptv.name.endsWith("卫视")
    }

    private suspend fun parseSource(sourceUrl: String, sourceData: String): IptvGroupList =
        withContext(Dispatchers.Default) {
            val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
            val groups = parser.parse(sourceData).mapNotNull { group ->
                val channels = group.iptvList.mapNotNull { channel ->
                    val urls = channel.urlList.filter {
                        ABSOLUTE_URL.matches(it) || it.startsWith("/") || it.startsWith("data:")
                    }
                    channel.copy(urlList = urls).takeIf { urls.isNotEmpty() }
                }
                group.copy(iptvList = IptvList(channels)).takeIf { channels.isNotEmpty() }
            }
            require(groups.isNotEmpty()) { "直播源未包含可播放的频道" }
            IptvGroupList(groups)
        }

    /**
     * 获取直播源分组列表
     */
    suspend fun getIptvGroupList(
        sourceUrl: String,
        cacheTime: Long,
        simplify: Boolean = false,
    ): IptvGroupList = loadPlaylist(sourceUrl, cacheTime, simplify).groups

    suspend fun loadPlaylist(
        sourceUrl: String,
        cacheTime: Long,
        simplify: Boolean,
    ): LoadedPlaylist {
        try {
            var fetchedPlaylist: Pair<String, IptvGroupList>? = null
            val sourceData = getOrRefresh(cacheTime, sourceUrl) {
                httpClient.download(sourceUrl) { it.string() }.also { data ->
                    fetchedPlaylist = data to parseSource(sourceUrl, data)
                }
            }

            val groupList = fetchedPlaylist?.takeIf { it.first === sourceData }?.second
                ?: parseSource(sourceUrl, sourceData)
            log.i("解析直播源完成：${groupList.size}个分组，${groupList.iptvList.size}个频道")

            val groups = if (simplify) {
                val filtered = groupList.map { group ->
                    group.copy(iptvList = IptvList(group.iptvList.filter(::simplifyTest)))
                }.filter { it.iptvList.isNotEmpty() }
                if (filtered.isEmpty()) groupList else IptvGroupList(filtered)
            } else groupList
            val embeddedEpgUrl = sourceData.m3uHeader()?.let(X_TVG_URL_REGEX::find)
                ?.groupValues?.get(1)?.substringBefore(',')?.trim()?.takeIf(String::isNotBlank)
            return LoadedPlaylist(groups, embeddedEpgUrl)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw ex
        }
    }
}
