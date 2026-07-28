package top.yogiczy.mytv.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgrammeList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.utils.ChannelNameNormalizer
import top.yogiczy.mytv.utils.Logger
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 节目单获取
 */
class EpgRepository : FileCacheRepository("epg.json") {
    private val log = Logger.create(javaClass.simpleName)
    private val epgXmlRepository = EpgXmlRepository()

    /**
     * 解析节目单xml
     */
    private suspend fun parseFromXml(
        xmlString: String,
        filteredChannels: List<String> = emptyList(),
        filteredChannelIds: List<String> = emptyList(),
    ) = withContext(Dispatchers.Default) {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlString))

        val epgMap = mutableMapOf<String, Epg>()
        val programmesMap = mutableMapOf<String, MutableList<EpgProgramme>>()

        val filterNormalized = filteredChannels.map { ChannelNameNormalizer.normalize(it) }.toHashSet()
        val filterIdSet = filteredChannelIds.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toHashSet()

        var rawChannelCount = 0
        var includedChannelCount = 0
        var parseTimeFailureCount = 0

        val dateFormats = listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT),
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.ROOT),
            SimpleDateFormat("yyyyMMddHHmmss XXX", Locale.ROOT),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("GMT+8")
            },
        ).onEach { it.isLenient = false }
        fun parseTime(time: String?): Long {
            val t = time?.trim() ?: return 0
            if (t.length < 14) return 0
            for (format in dateFormats) {
                try {
                    return format.parse(t)?.time ?: 0
                } catch (_: Exception) {
                    // try next format
                }
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

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "channel") {
                        rawChannelCount++
                        val channelId = parser.getAttributeValue(null, "id")?.trim() ?: ""
                        val normalizedChannelId = channelId.lowercase()
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
                            programmesMap[normalizedChannelId] = mutableListOf()
                        }
                    } else if (parser.name == "programme") {
                        val channelId = parser.getAttributeValue(null, "channel")?.trim() ?: ""
                        val normalizedChannelId = channelId.lowercase()
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
                        if (startAt == 0L || endAt == 0L) parseTimeFailureCount++

                        // xmltv 未规定 channel 必须先于 programme 出现，用 getOrPut 避免乱序时丢节目；
                        // 未通过过滤的频道在最终结果中本就不会出现
                        programmesMap.getOrPut(normalizedChannelId) { mutableListOf() }.add(
                            EpgProgramme(
                                startAt = startAt,
                                endAt = endAt,
                                title = title,
                            )
                        )
                    }
                }
            }
            eventType = parser.next()
        }

        val result = epgMap.map { (channelId, epg) ->
            val programmes = (programmesMap[channelId] ?: emptyList()).sortedBy { it.startAt }
            epg.copy(programmes = EpgProgrammeList(programmes))
        }

        val totalProgrammes = result.sumOf { it.programmes.size }
        val nonEmptyChannels = result.count { it.programmes.isNotEmpty() }
        log.i(
            "解析节目单完成，原始频道=${rawChannelCount}，保留=${includedChannelCount}，" +
                    "有节目=${nonEmptyChannels}，总节目=${totalProgrammes}，时间解析失败=${parseTimeFailureCount}"
        )

        return@withContext EpgList(result)
    }

    suspend fun getEpgList(
        xmlUrl: String,
        filteredChannels: List<String> = emptyList(),
        filteredChannelIds: List<String> = emptyList(),
        refreshTimeThreshold: Int,
    ) = withContext(Dispatchers.Default) {
        try {
            // 不再按小时阈值跳过 EPG 刷新，避免设置项导致节目单为空
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < refreshTimeThreshold) {
                log.i("EPG刷新时间阈值(${refreshTimeThreshold})大于当前小时，已忽略，继续刷新")
            }

            // 每次启动都重新解析 EPG，避免旧缓存导致节目单不更新
            val xmlJson = getOrRefresh(0L) {
                val xmlString = epgXmlRepository.getEpgXml(xmlUrl)
                Json.encodeToString(parseFromXml(xmlString, filteredChannels, filteredChannelIds).value)
            }

            EpgList(Json.decodeFromString<List<Epg>>(xmlJson))
        } catch (ex: Exception) {
            log.e("获取节目单失败", ex)
            throw Exception(ex)
        }
    }
}

/**
 * 节目单xml获取
 */
private class EpgXmlRepository : FileCacheRepository("epg.xml") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取远程xml
     */
    private suspend fun fetchXml(url: String): String = withContext(Dispatchers.IO) {
        log.d("获取远程节目单xml: $url")

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        try {
            with(client.newCall(request).execute()) {
                if (!isSuccessful) {
                    throw Exception("获取远程节目单xml失败: $code")
                }

                // 按内容嗅探（而非 URL 后缀）识别 gzip/纯文本，
                // 兼容 epg.xml?token=xx、epg.php 等地址
                val bytes = body!!.bytes()
                return@with when {
                    bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte() ->
                        java.util.zip.GZIPInputStream(bytes.inputStream())
                            .bufferedReader(Charsets.UTF_8).readText()

                    else -> String(bytes, Charsets.UTF_8)
                }
            }
        } catch (ex: Exception) {
            throw Exception("获取远程节目单xml失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取xml
     */
    suspend fun getEpgXml(url: String): String {
        return getOrRefresh(0) {
            fetchXml(url)
        }
    }
}
