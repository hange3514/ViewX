package top.yogiczy.mytv.data.entities

import androidx.compose.runtime.Immutable
import top.yogiczy.mytv.data.entities.Epg.Companion.currentProgrammes
import top.yogiczy.mytv.data.utils.ChannelNameNormalizer
import top.yogiczy.mytv.data.utils.matchesChannel
import top.yogiczy.mytv.ui.utils.CurrentTime

@Immutable
data class EpgList(
    val value: List<Epg> = emptyList(),
) : List<Epg> by value {

    /**
     * 缓存归一化后的频道名，用于模糊匹配。
     */
    val normalizedChannels: List<Pair<String, Epg>> by lazy {
        value.map { ChannelNameNormalizer.normalize(it.channel) to it }
    }

    /**
     * 按 channelId 建立的索引，用于精确匹配。
     */
    val channelIdMap: Map<String, Epg> by lazy {
        value.mapNotNull { epg ->
            epg.channelId.takeIf { it.isNotBlank() }?.let { it.lowercase() to epg }
        }.toMap()
    }
}

/**
 * 根据 [Iptv] 查找节目单。
 * 优先使用 tvg-id/channelId 精确匹配，失败再按频道名模糊匹配。
 */
fun EpgList.findByIptv(iptv: Iptv): Epg? {
    val tvgId = iptv.tvgId.trim()
    if (tvgId.isNotBlank()) {
        channelIdMap[tvgId.lowercase()]?.let { return it }
    }
    return findByChannel(iptv.channelName)
}

/**
 * 当前节目/下一个节目
 */
fun EpgList.currentProgrammes(
    iptv: Iptv,
    time: Long = CurrentTime.ms.value,
): EpgProgrammeCurrent? {
    return findByIptv(iptv)?.currentProgrammes(time)
}

/**
 * 根据频道名称查找节目单，支持模糊匹配。
 */
fun EpgList.findByChannel(channelName: String): Epg? {
    if (channelName.isBlank()) return null
    val normalized = ChannelNameNormalizer.normalize(channelName)

    // 先尝试归一化后的精确匹配
    normalizedChannels.firstOrNull { it.first == normalized }?.let { return it.second }

    // 再按前缀规则模糊匹配
    return normalizedChannels.firstOrNull { (epgNormalized, _) ->
        epgNormalized == normalized || isPrefixMatch(normalized, epgNormalized)
                || isPrefixMatch(epgNormalized, normalized)
    }?.second
}

private fun isPrefixMatch(shorter: String, longer: String): Boolean {
    if (shorter.length >= longer.length) return false
    if (!longer.startsWith(shorter)) return false
    return longer.getOrNull(shorter.length)?.isDigit()?.not() ?: true
}
