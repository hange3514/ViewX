package top.yogiczy.mytv.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import top.yogiczy.mytv.data.entities.findCurrent

/**
 * 频道节目单
 */
@Serializable
@Immutable
data class Epg(
    /**
     * 频道名称
     */
    val channel: String = "",

    /**
     * 节目列表
     */
    val programmes: EpgProgrammeList = EpgProgrammeList(),

    /**
     * EPG 源中的频道 ID，可用于精确匹配。
     */
    val channelId: String = "",
) {
    companion object {
        /**
         * 当前节目/下一个节目
         */
        fun Epg.currentProgrammes(time: Long): EpgProgrammeCurrent? {
            if (programmes.isEmpty()) return null

            val index = programmes.binarySearchBy(time) { it.startAt }
            val currentIndex = if (index >= 0) index else -index - 2
            val currentProgramme = programmes.getOrNull(currentIndex) ?: return null

            if (time !in currentProgramme.startAt..<currentProgramme.endAt) return null

            return EpgProgrammeCurrent(
                now = currentProgramme,
                next = if (currentIndex + 1 < programmes.size) programmes[currentIndex + 1] else null,
            )
        }
    }
}
