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
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.findByIptv
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
    private val standbyVideoPlayerState: LeanbackVideoPlayerState,
    private val extraStandbyVideoPlayerState: LeanbackVideoPlayerState,
    private val iptvGroupList: IptvGroupList,
    private val epgListProvider: () -> EpgList,
) : Loggable() {
    private val playerStates =
        listOf(videoPlayerState, standbyVideoPlayerState, extraStandbyVideoPlayerState)

    private var _currentIptv by mutableStateOf(Iptv())
    val currentIptv get() = _currentIptv

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentIptvUrlIdx get() = _currentIptvUrlIdx

    /** 当前活跃（显示画面）的播放器序号；预缓冲命中时在三个播放器间轮换 */
    private var _activePlayerIndex by mutableIntStateOf(0)
    val activePlayerIndex get() = _activePlayerIndex

    private val activePlayer get() = playerStates[_activePlayerIndex]

    /** 每个播放器当前加载的内容 URL，预缓冲命中判定与"来路"保留都靠它 */
    private val playerContents = MutableList<String?>(playerStates.size) { null }

    /** 预缓冲总开关（待机播放器持续异常时自动停用） */
    private var _prebufferAvailable = true

    /** 双待机（上下两个方向同时预缓冲）；设备撑不住时自动降级为按方向单待机 */
    private var _dualStandbyAvailable = true

    private var _standbyErrorCount = 0

    /** 最近换台方向：1 下一个，-1 上一个，用于预测预缓冲目标 */
    private var _lastFlipDirection = 1

    private var predictionJob: Job? = null
    private var cursorPrebufferJob: Job? = null

    private var _isReplayMode by mutableStateOf(false)
    val isReplayMode get() = _isReplayMode

    private var _replayProgramme by mutableStateOf<EpgProgramme?>(null)
    val replayProgramme get() = _replayProgramme

    private var _isReplayPaused by mutableStateOf(false)
    val isReplayPaused get() = _isReplayPaused

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
        // 三个播放器注册同一套处理器，按当前角色路由事件
        playerStates.forEachIndexed { index, state ->
            state.onReady {
                if (index == _activePlayerIndex) onActiveReady() else onStandbyReady(state)
            }
            state.onError {
                if (index == _activePlayerIndex) onActiveError() else onStandbyError()
            }
            state.onCutoff {
                if (index == _activePlayerIndex) onActiveCutoff()
            }
        }

        changeCurrentIptv(iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        })
    }

    private fun onActiveReady() {
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

    private fun onActiveError() {
        // 先记录失败线路的域名，changeCurrentIptv 会更新 idx，
        // 之后再读 urlList[idx] 读到的是新线路，会误删好域名
        val failedUrlHost = getUrlHost(_currentIptv.urlList[_currentIptvUrlIdx])

        if (_currentIptvUrlIdx < _currentIptv.urlList.size - 1) {
            changeCurrentIptv(_currentIptv, _currentIptvUrlIdx + 1)
        }

        // 从记忆中删除不可播放的域名
        SP.iptvPlayableHostList -= failedUrlHost
    }

    private fun onActiveCutoff() {
        if (_isReplayMode && _replayProgramme != null) {
            val programme = _replayProgramme!!
            val now = System.currentTimeMillis()
            val positionMs = _replayCurrentPositionMs + (now - _replayPositionBaseTime)
            val totalDuration = _replayCatchupEndAt - programme.startAt
            if (positionMs >= totalDuration - REPLAY_END_ADVANCE_THRESHOLD_MS) {
                // 当前回放片段播完，按时间顺序接续下一片段
                playNextProgrammeOrExit()
            } else {
                // 未播完却被判定断流，按当前位置重试
                log.d("回放断流，按当前位置重试: ${programme.title}")
                seekToProgramme(programme, positionMs.coerceIn(0, max(0, totalDuration)))
            }
        } else {
            changeCurrentIptv(_currentIptv, _currentIptvUrlIdx)
        }
    }

    /**
     * 待机播放器缓冲完成：冻结（暂停）以停止解码消耗，已缓冲数据保留，
     * 主备交换时 play 即可秒出画面
     */
    private fun onStandbyReady(state: LeanbackVideoPlayerState) {
        _standbyErrorCount = 0
        coroutineScope.launch {
            delay(2000)
            // 期间若已被提升为活跃播放器则不处理
            if (state != activePlayer) state.pause()
        }
    }

    /**
     * 待机播放器异常：连续失败先降级为单待机（猜方向），再失败则完全停用。
     * 单次失败可能只是预测到的频道本身不可用，连续失败才说明设备解码能力受限
     */
    private fun onStandbyError() {
        _standbyErrorCount++
        if (_standbyErrorCount >= 3 && _dualStandbyAvailable) {
            log.d("待机播放器连续异常，降级为单方向预缓冲")
            _dualStandbyAvailable = false
        }
        if (_standbyErrorCount >= 6 && _prebufferAvailable) {
            log.d("待机播放器持续异常，停用换台预缓冲")
            _prebufferAvailable = false
        }
    }

    /**
     * 频道的最优线路（优先可播放域名记忆）
     */
    private fun bestUrlOrNull(iptv: Iptv): String? {
        if (iptv.urlList.isEmpty()) return null
        val idx = max(0, iptv.urlList.indexOfFirst {
            SP.iptvPlayableHostList.contains(getUrlHost(it))
        })
        return iptv.urlList[idx]
    }

    /**
     * 预缓冲指定频道到指定播放器（内容未变化时跳过）
     */
    private fun prebufferInto(playerIndex: Int, targetIptv: Iptv) {
        if (!_prebufferAvailable || !SP.iptvPrebufferEnable) return
        if (_isReplayMode) return
        if (playerIndex == _activePlayerIndex) return
        if (targetIptv == _currentIptv) return
        val url = bestUrlOrNull(targetIptv) ?: return
        if (playerContents[playerIndex] == url) return

        playerContents[playerIndex] = url
        log.d("预缓冲[$playerIndex]: ${targetIptv.name} $url")
        playerStates[playerIndex].setVolume(0f)
        playerStates[playerIndex].prepare(url)
    }

    /**
     * 换台稳定后预测预缓冲：
     * 双待机模式上下两个方向都预缓冲；降级模式只按最近换台方向猜一个。
     * 旧活跃播放器保留刚切走的频道内容，往回翻台也能秒切
     */
    private fun schedulePrediction() {
        predictionJob?.cancel()
        predictionJob = coroutineScope.launch {
            delay(500)
            if (!_prebufferAvailable || !SP.iptvPrebufferEnable || _isReplayMode) return@launch

            val forwardIptv = if (_lastFlipDirection >= 0) getNextIptv() else getPrevIptv()
            val backwardIptv = if (_lastFlipDirection >= 0) getPrevIptv() else getNextIptv()
            val forwardUrl = bestUrlOrNull(forwardIptv)
            val backwardUrl = if (_dualStandbyAvailable) bestUrlOrNull(backwardIptv) else null

            val standbyIdxs = playerStates.indices.filter { it != _activePlayerIndex }

            // 后退方向：没有待机持有时，放进未持有前进内容的那个待机
            if (backwardUrl != null && standbyIdxs.none { playerContents[it] == backwardUrl }) {
                standbyIdxs.firstOrNull { playerContents[it] != forwardUrl }
                    ?.let { prebufferInto(it, backwardIptv) }
            }
            // 前进方向：没有待机持有时，放进未持有后退内容的那个待机
            if (forwardUrl != null && standbyIdxs.none { playerContents[it] == forwardUrl }) {
                standbyIdxs.firstOrNull { playerContents[it] != backwardUrl }
                    ?.let { prebufferInto(it, forwardIptv) }
            }
        }
    }

    /**
     * 选台面板光标停留时（去抖 400ms）预缓冲光标所在频道
     */
    fun prebufferChannel(iptv: Iptv) {
        if (!_prebufferAvailable || !SP.iptvPrebufferEnable || _isReplayMode) return
        cursorPrebufferJob?.cancel()
        cursorPrebufferJob = coroutineScope.launch {
            delay(400)
            if (iptv == _currentIptv) return@launch
            val url = bestUrlOrNull(iptv) ?: return@launch
            if (playerContents.any { it == url }) return@launch
            val standbyIdxs = playerStates.indices.filter { it != _activePlayerIndex }
            standbyIdxs.firstOrNull()?.let { prebufferInto(it, iptv) }
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

        // 无可用线路的频道（如空源/解析异常）直接忽略，防止下标越界
        if (iptv.urlList.isEmpty()) return

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

        val hitIndex = playerContents.indexOfFirst { it != null && it == url }
        if (_prebufferAvailable && SP.iptvPrebufferEnable &&
            hitIndex >= 0 && hitIndex != _activePlayerIndex
        ) {
            // 命中预缓冲：交换主备角色，待机播放器直接出画面（秒切）；
            // 旧活跃播放器保留刚切走的频道内容，往回翻台也能秒切
            val oldActive = activePlayer
            _activePlayerIndex = hitIndex
            val newActive = activePlayer
            newActive.setVolume(1f)
            newActive.play()
            oldActive.setVolume(0f)
            log.d("命中预缓冲，主备交换（活跃播放器=$_activePlayerIndex）")
            // 交换来的播放器早已 ready（play 不会再触发 onReady），
            // 手动补一次，否则左下角信息条永远不会自动隐藏
            onActiveReady()
        } else {
            activePlayer.prepare(url)
            playerContents[_activePlayerIndex] = url
        }
        schedulePrediction()
    }

    fun changeCurrentIptvToPrev() {
        if (!shouldFlipChannel()) return
        _lastFlipDirection = -1
        changeCurrentIptv(getPrevIptv())
    }

    fun changeCurrentIptvToNext() {
        if (!shouldFlipChannel()) return
        _lastFlipDirection = 1
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
     * 回放地址解析：catchup="append" 形式的源，catchup-source 是拼接到播放地址后的
     * 相对后缀（如 ?playbackbegin=...&playbackend=...），需要拼接当前线路地址；
     * 完整 URL（含 ://）则直接使用。两种源的回放方式对用户无感兼容
     */
    private fun resolveCatchupUrl(catchupUrl: String): String {
        if (catchupUrl.isBlank()) return ""
        if (catchupUrl.contains("://")) return catchupUrl
        val baseUrl = _currentIptv.urlList.getOrNull(_currentIptvUrlIdx) ?: return ""
        return baseUrl + catchupUrl
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

        val catchupUrl = resolveCatchupUrl(CatchupUrlBuilder.build(
            iptv.catchupSource,
            programme.startAt,
            catchupEndAt,
        ))
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
        activePlayer.prepare(catchupUrl)
    }

    private fun startReplayProgressTracking() {
        _replayProgressJob?.cancel()
        _replayProgressJob = coroutineScope.launch {
            while (isActive && _isReplayMode) {
                delay(1000)
                if (_isReplayPaused) {
                    // 暂停时冻结进度：只刷新基准时间，不累加位置，
                    // 否则恢复播放后位置虚高，seek 会跳错位置
                    _replayPositionBaseTime = System.currentTimeMillis()
                    continue
                }
                val elapsed = System.currentTimeMillis() - _replayPositionBaseTime
                if (elapsed > 0) {
                    _replayCurrentPositionMs += elapsed
                    _replayPositionBaseTime = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * 回放暂停/继续切换
     */
    fun toggleReplayPause() {
        if (!_isReplayMode) return
        _isReplayPaused = !_isReplayPaused
        if (_isReplayPaused) activePlayer.pause() else activePlayer.play()
    }

    private fun resetReplayMode() {
        _isReplayMode = false
        _isReplayPaused = false
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
     * 回放时快进/快退到绝对位置（距片段开头 targetMs 毫秒），供触屏拖拽进度条使用
     */
    fun seekReplayToPosition(targetMs: Long) {
        if (!_isReplayMode || _replayProgramme == null) return
        val now = System.currentTimeMillis()
        val currentMs = _replayCurrentPositionMs + (now - _replayPositionBaseTime)
        seekReplay(targetMs - currentMs)
    }

    /**
     * 回放时快进/快退
     *
     * 大多数 IPTV 回放源通过改变 `playseek` 时间窗口来定位，播放器自身的 seek 往往无效。
     * 这里根据当前播放位置重新构造 catchup URL，让服务器返回从目标时间开始的片段。
     * 支持跨片段：快退越过本片段开头时进入上一片段末尾继续，
     * 快进越过本片段末尾时进入下一片段开头继续。
     */
    fun seekReplay(offsetMs: Long) {
        if (!_isReplayMode || _replayProgramme == null) return

        val programme = _replayProgramme!!
        val totalDuration = _replayCatchupEndAt - programme.startAt
        if (totalDuration <= 0) return

        val now = System.currentTimeMillis()
        // 播放器对这种流通常不报告进度，基于本地维护的位置估算
        val currentMs = _replayCurrentPositionMs + (now - _replayPositionBaseTime)
        val targetMs = currentMs + offsetMs

        when {
            targetMs < 0 -> {
                // 快退越过本片段开头 → 进入上一片段末尾继续快退
                val prev = getSiblingProgramme(-1)
                if (prev == null) {
                    seekToProgramme(programme, 0)
                } else {
                    val prevDuration = max(0, minOf(prev.endAt, now) - prev.startAt)
                    seekToProgramme(prev, (prevDuration + targetMs).coerceIn(0, prevDuration))
                }
            }

            targetMs > totalDuration -> {
                // 快进越过本片段末尾 → 进入下一片段开头继续快进
                val next = getSiblingProgramme(1)
                if (next == null || next.startAt > now) {
                    // 没有更多已播出的片段，最多快进到本片段末尾
                    seekToProgramme(programme, totalDuration)
                } else {
                    val nextDuration = max(0, minOf(next.endAt, now) - next.startAt)
                    seekToProgramme(next, (targetMs - totalDuration).coerceIn(0, nextDuration))
                }
            }

            else -> seekToProgramme(programme, targetMs)
        }
    }

    /**
     * 当前回放片段播完后，按时间顺序接续播放下一片段；没有更多片段时回到直播
     */
    private fun playNextProgrammeOrExit() {
        val now = System.currentTimeMillis()
        val next = getSiblingProgramme(1)
        if (next == null || next.startAt > now) {
            exitReplayMode()
        } else {
            log.d("回放片段结束，接续下一片段: ${next.title}")
            playCatchup(_currentIptv, next)
        }
    }

    /**
     * 当前频道的节目单（按开始时间升序）
     */
    private fun getReplayProgrammes(): List<EpgProgramme> =
        epgListProvider().findByIptv(_currentIptv)?.programmes ?: emptyList()

    /**
     * 当前回放片段的上一个（direction=-1）或下一个（direction=1）片段
     */
    private fun getSiblingProgramme(direction: Int): EpgProgramme? {
        val programme = _replayProgramme ?: return null
        val programmes = getReplayProgrammes()
        val index = programmes.indexOfFirst { it.startAt == programme.startAt }
        if (index < 0) return null
        return programmes.getOrNull(index + direction)
    }

    /**
     * 跳转到指定片段的指定位置（距片段开头 targetMs 毫秒）
     */
    private fun seekToProgramme(programme: EpgProgramme, targetMs: Long) {
        val now = System.currentTimeMillis()
        val catchupEndAt = minOf(programme.endAt, now)
        val newStartAt = programme.startAt + targetMs
        val catchupUrl = resolveCatchupUrl(CatchupUrlBuilder.build(
            _currentIptv.catchupSource,
            newStartAt,
            catchupEndAt,
        ))
        if (catchupUrl.isBlank()) return

        _replayProgramme = programme
        _replayCatchupEndAt = catchupEndAt
        _replayCurrentPositionMs = targetMs
        _replayPositionBaseTime = now

        log.d("回放seek: ${programme.title}, target=${targetMs}ms, url=$catchupUrl")
        // seek 后强制恢复播放：pause 后 prepare 不会自动恢复 playWhenReady
        _isReplayPaused = false
        activePlayer.prepare(catchupUrl)
        activePlayer.play()
    }

}

@Composable
fun rememberLeanbackMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    standbyVideoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(
        // 待机播放器：小缓冲，只用于预缓冲，避免与主播放器争抢内存
        minBufferMs = 5_000,
        maxBufferMs = 5_000,
        bufferForPlaybackMs = 500,
        bufferForPlaybackAfterRebufferMs = 1_000,
    ),
    extraStandbyVideoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(
        minBufferMs = 5_000,
        maxBufferMs = 5_000,
        bufferForPlaybackMs = 500,
        bufferForPlaybackAfterRebufferMs = 1_000,
    ),
    iptvGroupList: IptvGroupList = IptvGroupList(),
    epgListProvider: () -> EpgList = { EpgList() },
) = remember {
    LeanbackMainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        standbyVideoPlayerState = standbyVideoPlayerState,
        extraStandbyVideoPlayerState = extraStandbyVideoPlayerState,
        iptvGroupList = iptvGroupList,
        epgListProvider = epgListProvider,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}

/** 换台去抖窗口：该时间内的重复换台触发会被忽略 */
private const val CHANNEL_FLIP_DEBOUNCE_MS = 300L

/** 回放片段距末尾不足该时长时，断流回调视为"片段播完"而接续下一片段 */
private const val REPLAY_END_ADVANCE_THRESHOLD_MS = 30_000L