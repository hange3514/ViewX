package top.yogiczy.mytv.data.entities

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * 频道节目
 */
@Serializable
@Immutable
data class EpgProgramme(
    /**
     * 开始时间（时间戳）
     */
    val startAt: Long = 0,

    /**
     * 结束时间（时间戳）
     */
    val endAt: Long = 0,

    /**
     * 节目名称
     */
    val title: String = "",
) {
    companion object {
        /**
         * 是否正在直播
         */
        fun EpgProgramme.isLive(time: Long) = time in startAt..<endAt

        /**
         * 节目进度
         */
        fun EpgProgramme.progress(time: Long): Float {
            val duration = max(1L, endAt - startAt)
            val current = max(0L, time - startAt)
            return (current.toFloat() / duration).coerceIn(0f, 1f)
        }
    }
}
