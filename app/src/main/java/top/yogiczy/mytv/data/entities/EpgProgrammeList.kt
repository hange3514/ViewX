package top.yogiczy.mytv.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class EpgProgrammeList(
    val value: List<EpgProgramme> = emptyList(),
) : List<EpgProgramme> by value

/**
 * 根据时间查找当前正在直播的节目。
 * 节目单按开始时间升序排列，使用二分查找替代线性扫描。
 */
fun List<EpgProgramme>.findCurrent(time: Long): EpgProgramme? {
    if (isEmpty()) return null
    val index = binarySearchBy(time) { it.startAt }
    val candidateIndex = if (index >= 0) index else -index - 2
    val programme = getOrNull(candidateIndex) ?: return null
    return if (time in programme.startAt..<programme.endAt) programme else null
}

/**
 * 查找当前正在直播节目的下标，未找到返回 -1。
 */
fun List<EpgProgramme>.indexOfCurrent(time: Long): Int {
    if (isEmpty()) return -1
    val index = binarySearchBy(time) { it.startAt }
    val candidateIndex = if (index >= 0) index else -index - 2
    val programme = getOrNull(candidateIndex) ?: return -1
    return if (time in programme.startAt..<programme.endAt) candidateIndex else -1
}

/**
 * 根据时间返回当前节目及下一个节目。
 */
fun List<EpgProgramme>.currentAndNext(time: Long): Pair<EpgProgramme?, EpgProgramme?> {
    if (isEmpty()) return null to null
    val index = binarySearchBy(time) { it.startAt }
    val currentIndex = if (index >= 0) index else -index - 2
    val current = getOrNull(currentIndex)
    return if (current != null && time in current.startAt..<current.endAt) {
        current to getOrNull(currentIndex + 1)
    } else {
        null to null
    }
}
