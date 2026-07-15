package top.yogiczy.mytv.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgramme.Companion.isLive
import top.yogiczy.mytv.data.entities.currentAndNext
import top.yogiczy.mytv.data.entities.findCurrent

/**
 * remember 当前正在播放的节目，只在节目边界变化时重组，
 * 避免每秒读取 CurrentTime 导致整个 item 反复重组。
 */
@Composable
fun rememberCurrentProgramme(programmes: List<EpgProgramme>): EpgProgramme? {
    val initial = remember(programmes) {
        programmes.findCurrent(System.currentTimeMillis())
    }

    val currentProgramme by produceState(
        initialValue = initial,
        key1 = programmes,
    ) {
        var last = value
        snapshotFlow { CurrentTime.ms.value }
            .collect { time ->
                val current = programmes.findCurrent(time)
                if (current != last) {
                    value = current
                    last = current
                }
            }
    }

    return currentProgramme
}

/**
 * remember 当前节目/下一个节目，只在节目边界变化时重组。
 */
@Composable
fun rememberCurrentProgrammes(programmes: List<EpgProgramme>): Pair<EpgProgramme?, EpgProgramme?> {
    val initial = remember(programmes) {
        programmes.currentAndNext(System.currentTimeMillis())
    }

    val currentProgrammes by produceState(
        initialValue = initial,
        key1 = programmes,
    ) {
        var last = value
        snapshotFlow { CurrentTime.ms.value }
            .collect { time ->
                val current = programmes.currentAndNext(time)
                if (current != last) {
                    value = current
                    last = current
                }
            }
    }

    return currentProgrammes
}

/**
 * remember 指定节目是否正在直播，只在跨越开始/结束边界时重组。
 */
@Composable
fun rememberProgrammeIsLive(programme: EpgProgramme): Boolean {
    val isLive by produceState(
        initialValue = programme.isLive(System.currentTimeMillis()),
        key1 = programme,
    ) {
        var last = value
        snapshotFlow { CurrentTime.ms.value }
            .collect { time ->
                val live = programme.isLive(time)
                if (live != last) {
                    value = live
                    last = live
                }
            }
    }
    return isLive
}

/**
 * remember 指定节目是否可回放，只在跨越开始时间时重组。
 */
@Composable
fun rememberProgrammeIsReplayable(
    programme: EpgProgramme,
    hasCatchup: Boolean,
): Boolean {
    if (!hasCatchup) return false

    val isReplayable by produceState(
        initialValue = programme.startAt <= System.currentTimeMillis(),
        key1 = programme,
    ) {
        var last = value
        snapshotFlow { CurrentTime.ms.value }
            .collect { time ->
                val replayable = programme.startAt <= time
                if (replayable != last) {
                    value = replayable
                    last = replayable
                }
            }
    }
    return isReplayable
}
