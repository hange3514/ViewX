package top.yogiczy.mytv.ui.screens.leanback.main.components

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Loggable
import kotlin.math.max

@Stable
class LeanbackMainContentState(
    private val coroutineScope: CoroutineScope,
    private val activePlayer: LeanbackVideoPlayerState,
    private val iptvGroupList: IptvGroupList,
    private val epgListProvider: () -> EpgList,
) : Loggable() {
    private var _currentIptv by mutableStateOf(Iptv())
    val currentIptv get() = _currentIptv

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentIptvUrlIdx get() = _currentIptvUrlIdx

    private var activeLiveUrl: String? = null
    private var prebufferEnabled = SP.iptvPrebufferEnable
    private var networkPreloadAttempted = false
    private var lastFocusedPreloadUrl: String? = null
    private var preloadGuardJob: Job? = null
    private var preloadCooldownUntilMs = 0L

    /** 最近换台方向：1 下一个，-1 上一个，用于预测预缓冲目标 */
    private var _lastFlipDirection = 1

    private var predictionJob: Job? = null
    private var cursorPrebufferJob: Job? = null
    private var playbackSuspended = false
    private var pendingRecovery: PendingRecovery? = null
    private val failedLiveUrls = mutableSetOf<String>()
    private var liveReadyAt: Long? = null
    private var tempPanelHideJob: Job? = null
    private var firstFrameRequestedAt: Long? = null
    private var firstFramePrefetched = false

    private enum class PendingRecovery { ERROR, CUTOFF, ENDED }

    private var _isReplayMode by mutableStateOf(false)
    val isReplayMode get() = _isReplayMode

    private var _replayProgramme by mutableStateOf<EpgProgramme?>(null)
    val replayProgramme get() = _replayProgramme

    private var _isReplayPaused by mutableStateOf(false)
    val isReplayPaused get() = _isReplayPaused

    private var _replayCatchupEndAt by mutableStateOf(0L)

    private var _replayCurrentPositionMs by mutableStateOf(0L)
    val replayCurrentPositionMs get() = _replayCurrentPositionMs

    private var replayStartOffsetMs = 0L
    private var replayRetryProgressBaselineMs = 0L
    private var replayRetryCount = 0
    private var replayProgressJob: Job? = null

    private var _isPanelVisible by mutableStateOf(false)
    var isPanelVisible
        get() = _isPanelVisible
        set(value) {
            if (value != _isPanelVisible) lastFocusedPreloadUrl = null
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
        activePlayer.onReady(::onActiveReady)
        activePlayer.onError(::onActiveError)
        activePlayer.onCutoff(::onActiveCutoff)
        activePlayer.onBuffering {
            if (activePlayer.hasStartedPlayback) protectActivePlayback()
        }
        activePlayer.onEnded(::onActiveEnded)
        activePlayer.onFirstFrame(::onActivePlaybackStarted)
    }

    /**
     * 初始开播。从 remember 的组合期移到 LaunchedEffect 调用，
     * 避免组合被放弃时已 prepare 的播放器无释放路径、早期事件丢失
     */
    fun start() {
        Log.i(PLAYBACK_TAG, "standby_policy enabled=false network_only=true")
        changeCurrentIptv(iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        })
    }

    private fun onActiveReady() {
        pendingRecovery = null
        if (playbackSuspended) return
        cutoffRetryJob?.cancel()
        if (activePlayer.hasStartedPlayback) {
            onActivePlaybackStarted(null)
            // 视图可能先收到首帧，再收到 READY；此时补上一次预测。
            if (predictionJob?.isActive != true) schedulePrediction()
        }
    }

    private fun beginPlaybackAttempt() {
        tempPanelHideJob?.cancel()
        firstFrameRequestedAt = SystemClock.elapsedRealtime()
        firstFramePrefetched = false
    }

    private fun onActivePlaybackStarted(renderTimeMs: Long?) {
        if (playbackSuspended) return
        val requestedAt = firstFrameRequestedAt ?: return
        if (renderTimeMs != null && renderTimeMs < requestedAt) return
        firstFrameRequestedAt = null
        val mode = if (_isReplayMode) "replay" else "live"
        if (renderTimeMs != null) {
            Log.i(PLAYBACK_TAG, "first_frame elapsed_ms=${renderTimeMs - requestedAt} mode=$mode prefetched=$firstFramePrefetched")
        }
        tempPanelHideJob?.cancel()
        tempPanelHideJob = coroutineScope.launch {
            delay(Constants.UI_TEMP_PANEL_SCREEN_SHOW_DURATION)
            _isTempPanelVisible = false
        }
        if (_isReplayMode) return
        liveReadyAt = SystemClock.uptimeMillis()
        _currentIptv.urlList.getOrNull(_currentIptvUrlIdx)?.let {
            SP.iptvPlayableHostList += getUrlHost(it)
        }
        schedulePrediction()
    }

    private fun onActiveError() {
        protectActivePlayback()
        if (playbackSuspended || (_isReplayMode && _isReplayPaused)) {
            pendingRecovery = PendingRecovery.ERROR
            return
        }
        pendingRecovery = null
        // 回放态的错误单独处理：切直播线路对回放源失败无意义，提示并退出回放
        if (_isReplayMode) {
            returnToLiveAfterReplayError()
            return
        }

        val urls = _currentIptv.urlList
        val failedUrl = urls.getOrNull(_currentIptvUrlIdx) ?: return
        resetFailedUrlsAfterStablePlayback()
        liveReadyAt = null
        failedLiveUrls.add(failedUrl)
        SP.iptvPlayableHostList -= getUrlHost(failedUrl)

        // 首选线路可能在列表中间或末尾。按原顺序绕回，跳过本轮失败地址。
        val nextUrlIdx = (1 until urls.size).asSequence()
            .map { (_currentIptvUrlIdx + it) % urls.size }
            .firstOrNull { urls[it] !in failedLiveUrls }
        if (nextUrlIdx != null) {
            changeLiveSource(_currentIptv, nextUrlIdx, isRecovery = true)
        } else {
            firstFrameRequestedAt = null
            val error = activePlayer.error ?: "播放中断"
            activePlayer.stop()
            activePlayer.error = error
            _isTempPanelVisible = false
        }
    }

    private fun resetFailedUrlsAfterStablePlayback() {
        val readyAt = liveReadyAt ?: return
        if (SystemClock.uptimeMillis() - readyAt >= 60_000L) failedLiveUrls.clear()
    }

    private var cutoffRetryCount = 0
    private var lastCutoffAt = 0L
    private var cutoffRetryJob: Job? = null

    private fun onActiveEnded() {
        if (!_isReplayMode) {
            onActiveCutoff()
        } else if (playbackSuspended || _isReplayPaused) {
            pendingRecovery = PendingRecovery.ENDED
        } else {
            playNextProgrammeOrExit()
        }
    }

    private fun onActiveCutoff() {
        protectActivePlayback()
        if (playbackSuspended || (_isReplayMode && _isReplayPaused)) {
            if (pendingRecovery == null) pendingRecovery = PendingRecovery.CUTOFF
            return
        }

        if (_isReplayMode) {
            val programme = _replayProgramme ?: return
            updateReplayPosition()
            if (replayRetryCount >= REPLAY_MAX_RETRIES) {
                returnToLiveAfterReplayError()
                return
            }
            replayRetryCount++
            if (!seekToProgramme(programme, _replayCurrentPositionMs, resetRetries = false)) {
                returnToLiveAfterReplayError()
            }
        } else {
            resetFailedUrlsAfterStablePlayback()
            liveReadyAt = null
            // 直播断流：线性退避重试（1/2/3/4/5s），一分钟稳定则重置计数；
            // 超过上限按播放错误处理（尝试切换线路或停在错误态），不再无限重连
            val now = SystemClock.uptimeMillis()
            if (now - lastCutoffAt > 60_000) cutoffRetryCount = 0
            lastCutoffAt = now

            if (cutoffRetryCount >= 5) {
                log.d("断流重试超过上限，停止重连")
                cutoffRetryCount = 0
                onActiveError()
                return
            }
            cutoffRetryCount++
            val delayMs = cutoffRetryCount * 1000L
            log.d("断流重连（第${cutoffRetryCount}次，${delayMs}ms 后）")
            cutoffRetryJob?.cancel()
            cutoffRetryJob = coroutineScope.launch {
                delay(delayMs)
                if (!playbackSuspended) {
                    changeLiveSource(_currentIptv, _currentIptvUrlIdx, isRecovery = true)
                }
            }
        }
    }

    fun setPrebufferEnabled(enabled: Boolean) {
        if (prebufferEnabled == enabled) return
        prebufferEnabled = enabled
        if (enabled) {
            networkPreloadAttempted = false
            lastFocusedPreloadUrl = null
            schedulePrediction()
        } else {
            discardPreload()
        }
    }

    private fun canPrebuffer(): Boolean =
        prebufferEnabled && !playbackSuspended && !_isReplayMode &&
            activePlayer.isReady && activePlayer.hasStartedPlayback &&
            SystemClock.elapsedRealtime() >= preloadCooldownUntilMs

    private fun cancelPreloadJobs() {
        predictionJob?.cancel()
        cursorPrebufferJob?.cancel()
        preloadGuardJob?.cancel()
        preloadGuardJob = null
    }

    private fun protectActivePlayback(bufferedMs: Long = activePlayer.bufferedDurationMs) {
        if (!_isReplayMode) {
            preloadCooldownUntilMs = SystemClock.elapsedRealtime() + PRELOAD_COOLDOWN_MS
        }
        if (activePlayer.hasLivePreload) {
            Log.d(PLAYBACK_TAG, "preload_cancelled_active_buffer buffer_ms=$bufferedMs")
        }
        discardPreload()
    }

    private fun discardPreload() {
        cancelPreloadJobs()
        activePlayer.cancelLivePreload()
    }

    private fun watchActiveBufferWhilePreloading() {
        if (!activePlayer.hasLivePreload || preloadGuardJob?.isActive == true) return
        preloadGuardJob = coroutineScope.launch {
            while (activePlayer.hasLivePreload) {
                delay(PRELOAD_GUARD_INTERVAL_MS)
                if (!activePlayer.hasLivePreload) break
                val bufferedMs = activePlayer.bufferedDurationMs
                if (!activePlayer.isPlaying || bufferedMs <= PRELOAD_STOP_BUFFER_MS) {
                    protectActivePlayback(bufferedMs)
                    break
                }
            }
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

    private fun prebuffer(targetIptv: Iptv, fromPanelFocus: Boolean = false) {
        if (!canPrebuffer() || targetIptv == _currentIptv) return
        val url = bestUrlOrNull(targetIptv) ?: return
        if (activeLiveUrl == url) return
        if (fromPanelFocus) {
            if (lastFocusedPreloadUrl == url) return
            lastFocusedPreloadUrl = url
        } else if (networkPreloadAttempted) return
        networkPreloadAttempted = true

        val bufferedMs = activePlayer.bufferedDurationMs
        if (!activePlayer.isPlaying || bufferedMs < PRELOAD_START_BUFFER_MS) {
            Log.d(PLAYBACK_TAG, "preload_skipped_active_buffer buffer_ms=$bufferedMs")
            return
        }
        activePlayer.preloadLive(url)
        watchActiveBufferWhilePreloading()
    }

    private fun schedulePrediction() {
        predictionJob?.cancel()
        if (!canPrebuffer() || cursorPrebufferJob?.isActive == true) return
        // 同一次直播只自动尝试一次，弱网恢复或候选过期后也不反复发起。
        if (networkPreloadAttempted) return
        predictionJob = coroutineScope.launch {
            delay(PRELOAD_PREDICTION_DELAY_MS)
            prebuffer(if (_lastFlipDirection >= 0) getNextIptv() else getPrevIptv())
        }
    }

    /**
     * 选台面板光标停留时（去抖 400ms）预缓冲光标所在频道
     */
    fun prebufferChannel(iptv: Iptv) {
        cursorPrebufferJob?.cancel()
        if (iptv == _currentIptv) {
            lastFocusedPreloadUrl = null
            return
        }
        if (!canPrebuffer()) return
        predictionJob?.cancel()
        cursorPrebufferJob = coroutineScope.launch {
            delay(400)
            if (_isPanelVisible) prebuffer(iptv, fromPanelFocus = true)
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
        changeLiveSource(iptv, urlIdx, isRecovery = false)
    }

    private fun changeLiveSource(iptv: Iptv, urlIdx: Int?, isRecovery: Boolean) {
        _isPanelVisible = false

        // 无可用线路的频道（如空源/解析异常）直接忽略，防止下标越界
        if (iptv.urlList.isEmpty()) return

        if (iptv == _currentIptv && urlIdx == null && !_isReplayMode && activePlayer.error == null) return

        // 开始新的播放尝试后，先前的断流退避不再适用。
        cutoffRetryJob?.cancel()
        cancelPreloadJobs()
        if (!isRecovery) {
            failedLiveUrls.clear()
            networkPreloadAttempted = false
        }
        if (!isRecovery || iptv != _currentIptv || urlIdx != _currentIptvUrlIdx) {
            cutoffRetryCount = 0
            lastCutoffAt = 0L
        }
        pendingRecovery = null
        liveReadyAt = null

        // 切台或切线路时退出回放模式
        resetReplayMode()

        if (!isRecovery && iptv == _currentIptv && urlIdx != _currentIptvUrlIdx) {
            _currentIptv.urlList.getOrNull(_currentIptvUrlIdx)?.let {
                SP.iptvPlayableHostList -= getUrlHost(it)
            }
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
        log.d("播放${iptv.name}（${_currentIptvUrlIdx + 1}/${_currentIptv.urlList.size}）")

        beginPlaybackAttempt()
        activeLiveUrl = url
        val reused = activePlayer.prepareLive(
            url,
            allowPreload = prebufferEnabled && !isRecovery && !playbackSuspended,
        )
        if (activeLiveUrl != url) return
        firstFramePrefetched = reused
        if (!playbackSuspended) activePlayer.play()
    }

    fun changeCurrentIptvToPrev() = flipChannel(-1)

    fun changeCurrentIptvToNext() = flipChannel(1)

    private var lastChannelFlipAt = 0L

    private fun flipChannel(direction: Int) {
        val now = SystemClock.uptimeMillis()
        // 防止部分电视重复派发同一次按键，同时允许用户立即反向换台。
        if (direction == _lastFlipDirection && now - lastChannelFlipAt < CHANNEL_FLIP_DEBOUNCE_MS) {
            log.d("忽略同方向重复换台按键")
            return
        }
        lastChannelFlipAt = now
        _lastFlipDirection = direction
        changeCurrentIptv(if (direction > 0) getNextIptv() else getPrevIptv())
    }

    /**
     * 回放地址解析：catchup="append" 形式的源，catchup-source 是拼接到播放地址后的
     * 相对后缀（如 ?playbackbegin=...&playbackend=...），需要拼接**目标频道**的线路地址；
     * 完整 URL（含 ://）则直接使用。两种源的回放方式对用户无感兼容
     */
    private fun resolveCatchupUrl(catchupUrl: String, iptv: Iptv): String {
        if (catchupUrl.isBlank()) return ""
        if (catchupUrl.contains("://")) return catchupUrl
        val urlIdx = max(0, iptv.urlList.indexOfFirst {
            SP.iptvPlayableHostList.contains(getUrlHost(it))
        })
        val baseUrl = iptv.urlList.getOrNull(urlIdx) ?: return ""
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
        val catchupEndAt = minOf(programme.endAt, now)
        if (catchupEndAt <= programme.startAt) return

        val catchupUrl = resolveCatchupUrl(CatchupUrlBuilder.build(
            iptv.catchupSource,
            programme.startAt,
            catchupEndAt,
        ), iptv)
        if (catchupUrl.isBlank()) return

        cutoffRetryJob?.cancel()
        discardPreload()
        _isPanelVisible = false
        _isTempPanelVisible = true
        _isReplayMode = true
        _isReplayPaused = false
        _replayProgramme = programme
        _replayCatchupEndAt = catchupEndAt
        _replayCurrentPositionMs = 0L
        replayStartOffsetMs = 0L
        replayRetryProgressBaselineMs = 0L
        replayRetryCount = 0

        // 目标频道可能与当前频道不同（从其他频道的节目单进回放）：
        // 线路下标必须按目标频道重算，否则越界；append 型源的 base URL 也必须用目标频道
        _currentIptv = iptv
        _currentIptvUrlIdx = max(0, iptv.urlList.indexOfFirst {
            SP.iptvPlayableHostList.contains(getUrlHost(it))
        })
        SP.iptvLastIptvIdx = iptvGroupList.iptvIdx(_currentIptv)

        activeLiveUrl = null
        pendingRecovery = null
        liveReadyAt = null

        beginPlaybackAttempt()
        activePlayer.prepare(catchupUrl)
        if (!playbackSuspended) activePlayer.play()
        startReplayProgressTracking()
    }

    private fun startReplayProgressTracking() {
        replayProgressJob?.cancel()
        if (playbackSuspended || _isReplayPaused || !_isReplayMode) return
        replayProgressJob = coroutineScope.launch {
            while (isActive && _isReplayMode) {
                delay(1000)
                updateReplayPosition()
            }
        }
    }

    private fun updateReplayPosition() {
        val programme = _replayProgramme ?: return
        val duration = (_replayCatchupEndAt - programme.startAt).coerceAtLeast(0L)
        val sampled = replayStartOffsetMs + activePlayer.currentPositionMs.coerceAtLeast(0L)
        _replayCurrentPositionMs = sampled.coerceIn(0L, duration)
        if (_replayCurrentPositionMs - replayRetryProgressBaselineMs >= 60_000L) {
            replayRetryCount = 0
            replayRetryProgressBaselineMs = _replayCurrentPositionMs
        }
    }

    private fun returnToLiveAfterReplayError() {
        LeanbackToastState.I.showToast("回放播放失败，已退回直播")
        exitReplayMode()
    }

    /**
     * 回放暂停/继续切换
     */
    fun toggleReplayPause() {
        if (!_isReplayMode) return
        _isReplayPaused = !_isReplayPaused
        if (_isReplayPaused) {
            updateReplayPosition()
            replayProgressJob?.cancel()
            activePlayer.pause()
        } else {
            playActive()
            startReplayProgressTracking()
        }
    }

    /**
     * 退到后台时暂停播放和预读，冻结回放进度及重连。
     */
    fun pausePlayback() {
        if (playbackSuspended) return
        playbackSuspended = true
        if (cutoffRetryJob?.isActive == true && pendingRecovery == null) {
            pendingRecovery = PendingRecovery.CUTOFF
        }
        cutoffRetryJob?.cancel()
        resetFailedUrlsAfterStablePlayback()
        liveReadyAt = null
        if (_isReplayMode) updateReplayPosition()
        replayProgressJob?.cancel()
        discardPreload()
        activePlayer.pause()
    }

    /**
     * 回到前台时恢复播放，保持用户主动暂停的回放。
     */
    fun resumeActivePlayer() {
        val wasSuspended = playbackSuspended
        playbackSuspended = false
        if (_isReplayMode && _isReplayPaused) return
        if (wasSuspended && !_isReplayMode && activePlayer.hasStartedPlayback) {
            liveReadyAt = SystemClock.uptimeMillis()
        }
        playActive()
        startReplayProgressTracking()
        if (wasSuspended) schedulePrediction()
    }

    private fun playActive() {
        if (playbackSuspended) return
        val recovery = pendingRecovery
        pendingRecovery = null
        when (recovery) {
            PendingRecovery.ERROR -> onActiveError()
            PendingRecovery.ENDED -> onActiveEnded()
            PendingRecovery.CUTOFF -> {
                if (_isReplayMode) {
                    onActiveCutoff()
                } else {
                    changeLiveSource(_currentIptv, _currentIptvUrlIdx, isRecovery = true)
                    return
                }
            }
            null -> Unit
        }
        activePlayer.play()
    }

    private fun resetReplayMode() {
        _isReplayMode = false
        _isReplayPaused = false
        _replayProgramme = null
        _replayCatchupEndAt = 0L
        _replayCurrentPositionMs = 0L
        replayStartOffsetMs = 0L
        replayRetryProgressBaselineMs = 0L
        replayRetryCount = 0
        replayProgressJob?.cancel()
        replayProgressJob = null
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
        if (!_isReplayMode) return
        seekToProgramme(_replayProgramme ?: return, targetMs)
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
        updateReplayPosition()
        val targetMs = _replayCurrentPositionMs + offsetMs

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
        if (!SP.epgReplayEnable || next == null || minOf(next.endAt, now) <= next.startAt) {
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
    private fun seekToProgramme(
        programme: EpgProgramme,
        targetMs: Long,
        resetRetries: Boolean = true,
    ): Boolean {
        val now = System.currentTimeMillis()
        val catchupEndAt = minOf(programme.endAt, now)
        val duration = catchupEndAt - programme.startAt
        if (duration <= 0L) return false
        // 回看地址的时间参数精确到秒，至少保留最后一秒的可播窗口。
        val offset = targetMs.coerceIn(0L, (duration - 1000L).coerceAtLeast(0L))
        val newStartAt = programme.startAt + offset
        val catchupUrl = resolveCatchupUrl(CatchupUrlBuilder.build(
            _currentIptv.catchupSource,
            newStartAt,
            catchupEndAt,
        ), _currentIptv)
        if (catchupUrl.isBlank()) return false

        _replayProgramme = programme
        _replayCatchupEndAt = catchupEndAt
        _replayCurrentPositionMs = offset
        replayStartOffsetMs = offset
        if (resetRetries) {
            replayRetryCount = 0
            replayRetryProgressBaselineMs = offset
        }
        // seek 后强制恢复播放：pause 后 prepare 不会自动恢复 playWhenReady
        _isReplayPaused = false
        pendingRecovery = null
        beginPlaybackAttempt()
        activePlayer.prepare(catchupUrl)
        if (!playbackSuspended) activePlayer.play()
        startReplayProgressTracking()
        return true
    }

}

@Composable
fun rememberLeanbackMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState,
    iptvGroupList: IptvGroupList = IptvGroupList(),
    epgListProvider: () -> EpgList = { EpgList() },
) = remember {
    LeanbackMainContentState(
        coroutineScope = coroutineScope,
        activePlayer = videoPlayerState,
        iptvGroupList = iptvGroupList,
        epgListProvider = epgListProvider,
    )
}.also { state ->
    // 初始开播放在 effect 里，不进组合期
    LaunchedEffect(state) { state.start() }
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}

/** 换台去抖窗口：该时间内的重复换台触发会被忽略 */
private const val CHANNEL_FLIP_DEBOUNCE_MS = 300L

private const val REPLAY_MAX_RETRIES = 5
private const val PRELOAD_PREDICTION_DELAY_MS = 2_000L
private const val PRELOAD_START_BUFFER_MS = 3_000L
private const val PRELOAD_STOP_BUFFER_MS = 2_000L
private const val PRELOAD_GUARD_INTERVAL_MS = 200L
private const val PRELOAD_COOLDOWN_MS = 10_000L
private const val PLAYBACK_TAG = "ViewXPlayback"
