package top.yogiczy.mytv.ui.screens.leanback.main.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvGroupOf
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.data.utils.CatchupUrlBuilder
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import top.yogiczy.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Loggable
import kotlin.math.max

@Stable
class LeanbackMainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: LeanbackVideoPlayerState,
    private val iptvGroupList: IptvGroupList,
) : Loggable() {
    private var _currentIptv by mutableStateOf(Iptv())
    val currentIptv get() = _currentIptv

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentIptvUrlIdx get() = _currentIptvUrlIdx

    private var _isReplayMode by mutableStateOf(false)
    val isReplayMode get() = _isReplayMode

    private var _replayProgramme by mutableStateOf<EpgProgramme?>(null)
    val replayProgramme get() = _replayProgramme

    private var _replayCatchupEndAt by mutableStateOf(0L)

    private var _replayCurrentPositionMs by mutableStateOf(0L)
    val replayCurrentPositionMs get() = _replayCurrentPositionMs

    private var _replayPositionBaseTime by mutableStateOf(0L)

    private var _replayProgressJob by mutableStateOf<Job?>(null)

    private var _isPanelVisible by mutableStateOf(false)
    var isPanelVisible
        get() = _isPanelVisible
        set(value) {
            _isPanelVisible = value
        }

    private var _isSettingsVisible by mutableStateOf(false)
    var isSettingsVisible
        get() = _isSettingsVisible
        set(value) {
            _isSettingsVisible = value
        }

    private var _isTempPanelVisible by mutableStateOf(false)
    var isTempPanelVisible
        get() = _isTempPanelVisible
        set(value) {
            _isTempPanelVisible = value
        }

    private var _isQuickPanelVisible by mutableStateOf(false)
    var isQuickPanelVisible
        get() = _isQuickPanelVisible
        set(value) {
            _isQuickPanelVisible = value
        }

    init {
        changeCurrentIptv(iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        })

        videoPlayerState.onReady {
            coroutineScope.launch {
                val name = _currentIptv.name
                val urlIdx = _currentIptvUrlIdx
                delay(Constants.UI_TEMP_PANEL_SCREEN_SHOW_DURATION)
                if (name == _currentIptv.name && urlIdx == _currentIptvUrlIdx) {
                    _isTempPanelVisible = false
                }
            }

            // 记忆可播放的域名
            SP.iptvPlayableHostList += getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        videoPlayerState.onError {
            if (_currentIptvUrlIdx < _currentIptv.urlList.size - 1) {
                changeCurrentIptv(_currentIptv, _currentIptvUrlIdx + 1)
            }

            // 从记忆中删除不可播放的域名
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        videoPlayerState.onCutoff {
            changeCurrentIptv(_currentIptv, _currentIptvUrlIdx)
        }

        // TODO(测试)：启动 10s 后自动进入回放模式，用于验证 seek URL 生成
        coroutineScope.launch {
            delay(10_000)
            if (_currentIptv.catchupSource.isNotBlank()) {
                val now = System.currentTimeMillis()
                playCatchup(
                    _currentIptv,
                    EpgProgramme(
                        startAt = now - 3_600_000,
                        endAt = now,
                        title = "测试回放",
                    ),
                )
            }
        }
    }

    private fun getPrevIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex - 1) {
            iptvGroupList.lastOrNull()?.iptvList?.lastOrNull() ?: Iptv()
        }
    }

    private fun getNextIptv(): Iptv {
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex + 1) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        }
    }

    fun changeCurrentIptv(iptv: Iptv, urlIdx: Int? = null) {
        _isPanelVisible = false

        if (iptv == _currentIptv && urlIdx == null && !_isReplayMode) return

        // 切台或切线路时退出回放模式
        resetReplayMode()

        if (iptv == _currentIptv && urlIdx != _currentIptvUrlIdx) {
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])
        }

        _isTempPanelVisible = true

        _currentIptv = iptv
        SP.iptvLastIptvIdx = iptvGroupList.iptvIdx(_currentIptv)

        _currentIptvUrlIdx = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentIptv.urlList.indexOfFirst {
                SP.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentIptv.urlList.size) % _currentIptv.urlList.size
        }

        val url = iptv.urlList[_currentIptvUrlIdx]
        log.d("播放${iptv.name}（${_currentIptvUrlIdx + 1}/${_currentIptv.urlList.size}）: $url")

        videoPlayerState.prepare(url)
    }

    fun changeCurrentIptvToPrev() {
        if (!shouldFlipChannel()) return
        changeCurrentIptv(getPrevIptv())
    }

    fun changeCurrentIptvToNext() {
        if (!shouldFlipChannel()) return
        changeCurrentIptv(getNextIptv())
    }

    private var lastChannelFlipAt = 0L

    /**
     * 换台去抖：部分电视（如长虹）的系统会把一次按键重复派发，
     * 导致按一次上/下键连跳两个台；极短时间内的重复触发直接忽略
     */
    private fun shouldFlipChannel(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastChannelFlipAt < CHANNEL_FLIP_DEBOUNCE_MS) {
            log.d("换台触发过于频繁，已忽略（疑似设备重复派发按键事件）")
            return false
        }
        lastChannelFlipAt = now
        return true
    }

    /**
     * 播放节目回放
     */
    fun playCatchup(iptv: Iptv, programme: EpgProgramme) {
        if (!SP.epgReplayEnable) return
        if (iptv.catchupSource.isBlank()) return

        val now = System.currentTimeMillis()
        // 已播节目按结束时间回看；正在直播的节目按当前时间时移回看
        if (programme.startAt > now) return
        val catchupEndAt = minOf(programme.endAt, now)

        val catchupUrl = CatchupUrlBuilder.build(
            iptv.catchupSource,
            programme.startAt,
            catchupEndAt,
        )
        if (catchupUrl.isBlank()) return

        _isPanelVisible = false
        _isTempPanelVisible = true
        _isReplayMode = true
        _replayProgramme = programme
        _replayCatchupEndAt = catchupEndAt
        _replayCurrentPositionMs = 0L
        _replayPositionBaseTime = now
        startReplayProgressTracking()
        _currentIptv = iptv
        SP.iptvLastIptvIdx = iptvGroupList.iptvIdx(_currentIptv)

        log.d("回放${iptv.name} - ${programme.title}: $catchupUrl")
        videoPlayerState.prepare(catchupUrl)
    }

    private fun startReplayProgressTracking() {
        _replayProgressJob?.cancel()
        _replayProgressJob = coroutineScope.launch {
            while (isActive && _isReplayMode) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - _replayPositionBaseTime
                if (elapsed > 0) {
                    _replayCurrentPositionMs += elapsed
                    _replayPositionBaseTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun resetReplayMode() {
        _isReplayMode = false
        _replayProgramme = null
        _replayCatchupEndAt = 0L
        _replayCurrentPositionMs = 0L
        _replayPositionBaseTime = 0L
        _replayProgressJob?.cancel()
        _replayProgressJob = null
    }

    /**
     * 退出回放模式，恢复当前频道直播
     */
    fun exitReplayMode() {
        if (!_isReplayMode) return
        // urlIdx 传 null，让可播放域名偏好逻辑重新选择最优线路
        changeCurrentIptv(_currentIptv, null)
    }

    /**
     * 回放时快进/快退
     *
     * 大多数 IPTV 回放源通过改变 `playseek` 时间窗口来定位，播放器自身的 seek 往往无效。
     * 这里根据当前播放位置重新构造 catchup URL，让服务器返回从目标时间开始的片段。
     */
    fun seekReplay(offsetMs: Long) {
        if (!_isReplayMode || _replayProgramme == null) return

        val programme = _replayProgramme!!
        val totalDuration = _replayCatchupEndAt - programme.startAt
        if (totalDuration <= 0) return

        val now = System.currentTimeMillis()
        // 播放器对这种流通常不报告进度，基于本地维护的位置估算
        val currentMs = _replayCurrentPositionMs + (now - _replayPositionBaseTime)
        val targetMs = (currentMs + offsetMs).coerceIn(0, totalDuration)

        val newStartAt = programme.startAt + targetMs
        val catchupUrl = CatchupUrlBuilder.build(
            _currentIptv.catchupSource,
            newStartAt,
            _replayCatchupEndAt,
        )
        if (catchupUrl.isBlank()) return

        _replayCurrentPositionMs = targetMs
        _replayPositionBaseTime = now

        log.d("回放seek: ${programme.title}, offset=${offsetMs}ms, target=$targetMs, url=$catchupUrl")
        videoPlayerState.prepare(catchupUrl)
    }

}

@Composable
fun rememberLeanbackMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    iptvGroupList: IptvGroupList = IptvGroupList(),
) = remember {
    LeanbackMainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        iptvGroupList = iptvGroupList,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}

/** 换台去抖窗口：该时间内的重复换台触发会被忽略 */
private const val CHANNEL_FLIP_DEBOUNCE_MS = 300L