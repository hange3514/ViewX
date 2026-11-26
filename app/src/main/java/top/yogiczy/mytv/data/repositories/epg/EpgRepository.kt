package top.yogiczy.mytv.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgrammeList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.download
import top.yogiczy.mytv.data.utils.ChannelNameNormalizer
import top.yogiczy.mytv.utils.Logger
import java.io.IOException
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * 节目单获取
 */
class EpgRepository : FileCacheRepository("epg-viewx-v2.json") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 解析节目单xml
     */
    private fun parseFromXml(
        stream: InputStream,
        filteredChannels: List<String> = emptyList(),
        filteredChannelIds: List<String> = emptyList(),
    ): EpgList {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)
        require(parser.nextTag() == XmlPullParser.START_TAG && parser.name == "tv") {
            "节目单不是有效的 XMLTV 文件"
        }

        val epgMap = mutableMapOf<String, Epg>()
        val programmesMap = mutableMapOf<String, MutableList<EpgProgramme>>()
        val seenChannels = mutableSetOf<String>()

        val filterNormalized = filteredChannels.map { ChannelNameNormalizer.normalize(it) }.toHashSet()
        val filterIdSet = filteredChannelIds.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toHashSet()

        var rawChannelCount = 0
        var includedChannelCount = 0
        var parseTimeFailureCount = 0

        val colonOffset = Regex("([+-]\\d{2}):(\\d{2})$")
        val dateFormats = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT),
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.ROOT),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("GMT+8")
            },
        ).onEach { it.isLenient = false }
        fun parseTime(time: String?): Long {
            val t = time?.trim() ?: return 0
            if (t.length < 14) return 0
            // 旧版 Android 的 SimpleDateFormat 不支持 XXX。将 ISO 时区标准化后按 Z 解析。
            val normalized = if (t.endsWith('Z')) {
                t.dropLast(1) + "+0000"
            } else {
                colonOffset.replace(t, "$1$2")
            }
            for (format in dateFormats) {
                val position = ParsePosition(0)
                val date = format.parse(normalized, position)
                if (date != null && position.index == normalized.length) return date.time
            }
            log.w("无法解析节目时间: $t")
            return 0
        }

        /**
         * 跳过未知标签的整个子树。
         * 不能用 nextText()：遇到嵌套标签（如 credits/rating）会直接抛异常导致整个节目单解析失败
         */
        fun skipSubTree() {
            var depth = 1
            while (depth > 0) {
                val next = parser.next()
                // 畸形/截断的 XML 没有闭合标签时，next() 会一直停在 END_DOCUMENT，必须兜底退出
                if (next == XmlPullParser.END_DOCUMENT) return
                when (next) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                }
            }
        }

        var rootClosed = false
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.depth != 2) {
                        skipSubTree()
                    } else if (parser.name == "channel") {
                        rawChannelCount++
                        val channelId = parser.getAttributeValue(null, "id")?.trim() ?: ""
                        val normalizedChannelId = channelId.lowercase()
                        seenChannels.add(normalizedChannelId)
                        var channelName = ""

                        var inner = parser.nextToken()
                        // 缺闭合标签的畸形 XML 兜底退出，防死循环
                        while (inner != XmlPullParser.END_DOCUMENT &&
                            !(inner == XmlPullParser.END_TAG && parser.name == "channel")
                        ) {
                            if (inner == XmlPullParser.START_TAG && parser.name == "display-name") {
                                channelName = parser.nextText()
                            } else if (inner == XmlPullParser.START_TAG) {
                                skipSubTree()
                            }
                            inner = parser.nextToken()
                        }

                        val normalizedChannelName = ChannelNameNormalizer.normalize(channelName)
                        val shouldInclude = filteredChannels.isEmpty()
                                || filterIdSet.contains(normalizedChannelId)
                                || filterNormalized.contains(normalizedChannelName)
                                || filterNormalized.any {
                                    ChannelNameNormalizer.matchesNormalized(it, normalizedChannelName)
                                }

                        if (shouldInclude) {
                            includedChannelCount++
                            epgMap[normalizedChannelId] = Epg(channelName, EpgProgrammeList(), channelId)
                        } else {
                            programmesMap.remove(normalizedChannelId)
                        }
                    } else if (parser.name == "programme") {
                        val channelId = parser.getAttributeValue(null, "channel")?.trim() ?: ""
                        val normalizedChannelId = channelId.lowercase()
                        if (normalizedChannelId in seenChannels && normalizedChannelId !in epgMap) {
                            skipSubTree()
                            eventType = parser.next()
                            continue
                        }
                        val startTime = parser.getAttributeValue(null, "start")
                        val stopTime = parser.getAttributeValue(null, "stop")
                        var title = ""

                        var inner = parser.nextToken()
                        while (inner != XmlPullParser.END_DOCUMENT &&
                            !(inner == XmlPullParser.END_TAG && parser.name == "programme")
                        ) {
                            if (inner == XmlPullParser.START_TAG && parser.name == "title") {
                                title = parser.nextText()
                            } else if (inner == XmlPullParser.START_TAG) {
                                skipSubTree()
                            }
                            inner = parser.nextToken()
                        }

                        val startAt = parseTime(startTime)
                        val endAt = parseTime(stopTime)
                        if (startAt == 0L || endAt <= startAt) {
                            parseTimeFailureCount++
                        } else {
                            // 源内乱序时先保存节目，等频道定义出现后再确定是否保留。
                            programmesMap.getOrPut(normalizedChannelId) { mutableListOf() }.add(
                                EpgProgramme(startAt = startAt, endAt = endAt, title = title)
                            )
                        }
                    } else {
                        skipSubTree()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.depth == 1 && parser.name == "tv") {
                    rootClosed = true
                }
            }
            eventType = parser.next()
        }
        require(rootClosed) { "节目单 XML 文件不完整" }

        val result = epgMap.map { (channelId, epg) ->
            val programmes = (programmesMap[channelId] ?: emptyList()).sortedBy { it.startAt }
            epg.copy(programmes = EpgProgrammeList(programmes))
        }

        val totalProgrammes = result.sumOf { it.programmes.size }
        require(totalProgrammes > 0) { "节目单未包含当前频道的有效节目" }
        val nonEmptyChannels = result.count { it.programmes.isNotEmpty() }
        log.i(
            "解析节目单完成，原始频道=${rawChannelCount}，保留=${includedChannelCount}，" +
                    "有节目=${nonEmptyChannels}，总节目=${totalProgrammes}，时间解析失败=${parseTimeFailureCount}"
        )

        return EpgList(result)
    }

    suspend fun clearAllCache() = clearCache()

    suspend fun getEpgList(
        xmlUrls: List<String>,
        filteredChannels: List<String> = emptyList(),
        filteredChannelIds: List<String> = emptyList(),
        refreshIntervalHours: Int,
        forceRefresh: Boolean = false,
    ) = withContext(Dispatchers.Default) {
        val cacheTime = when {
            forceRefresh -> 0L
            refreshIntervalHours <= 0 -> TimeUnit.MINUTES.toMillis(30)
            else -> TimeUnit.HOURS.toMillis(refreshIntervalHours.toLong())
        }
        val cacheKey = Json.encodeToString(
            listOf(xmlUrls, filteredChannels.distinct().sorted(), filteredChannelIds.distinct().sorted())
        )
        var parsedResult: Pair<String, CachedEpg>? = null
        val json = getOrRefresh(cacheTime, cacheKey) {
            val epg = fetchFromSources(xmlUrls, filteredChannels, filteredChannelIds)
            Json.encodeToString(epg).also { parsedResult = it to epg }
        }
        val snapshot = parsedResult?.takeIf { it.first === json }?.second ?: Json.decodeFromString<CachedEpg>(json)
        EpgList(snapshot.programmes) to xmlUrls[snapshot.sourceIndex]
    }

    private suspend fun fetchFromSources(
        urls: List<String>,
        channels: List<String>,
        channelIds: List<String>,
    ): CachedEpg {
        var failure: Exception? = null
        for ((index, url) in urls.withIndex()) {
            try {
                val epg = httpClient.download(url) { body ->
                    val source = body.source()
                    val gzip = source.request(2) &&
                        source.buffer[0] == 0x1F.toByte() && source.buffer[1] == 0x8B.toByte()
                    val stream = source.inputStream()
                    if (gzip) GZIPInputStream(stream).use { parseFromXml(it, channels, channelIds) }
                    else parseFromXml(stream, channels, channelIds)
                }
                return CachedEpg(index, epg.value)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                log.w("第 ${index + 1} 个节目单地址无有效内容", ex)
                failure = ex
            }
        }
        throw failure ?: IOException("没有设置节目单地址")
    }

    @Serializable
    private data class CachedEpg(val sourceIndex: Int, val programmes: List<Epg>)

    private companion object {
        val httpClient = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    }
}
