package top.yogiczy.mytv.data.entities

import androidx.compose.runtime.Immutable

/**
 * 直播源分组列表
 */
@Immutable
data class IptvGroupList(
    val value: List<IptvGroup> = emptyList(),
) : List<IptvGroup> by value {
    /**
     * 已缓存的扁平频道列表，避免每次访问都执行 flatMap。
     */
    val iptvList: IptvList = IptvList(value.flatMap { it.iptvList })

    /**
     * 频道 -> 全局下标的缓存映射，避免 O(n) 查找。
     */
    private val iptvIndexMap: Map<Iptv, Int> =
        iptvList.withIndex().associate { it.value to it.index }

    /**
     * 频道 -> 所属分组下标的缓存映射。
     */
    private val iptvGroupIndexMap: Map<Iptv, Int> = buildMap {
        value.forEachIndexed { groupIdx, group ->
            group.iptvList.forEach { put(it, groupIdx) }
        }
    }

    companion object {
        val EXAMPLE = IptvGroupList(List(5) { groupIdx ->
            IptvGroup(
                name = "频道分组${groupIdx + 1}",
                iptvList = IptvList(
                    List(10) { idx ->
                        Iptv(
                            name = "频道${groupIdx + 1}-${idx + 1}",
                            channelName = "频道${groupIdx + 1}-${idx + 1}",
                            urlList = emptyList(),
                        )
                    },
                )
            )
        })

        fun IptvGroupList.iptvGroupIdx(iptv: Iptv) = iptvGroupIndexMap[iptv] ?: -1

        fun IptvGroupList.iptvGroupOf(iptv: Iptv) =
            iptvGroupIndexMap[iptv]?.let { value.getOrNull(it) }

        fun IptvGroupList.iptvIdx(iptv: Iptv) = iptvIndexMap[iptv] ?: -1
    }
}
