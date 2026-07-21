package top.yogiczy.mytv.ui.screens.leanback.main.components

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.findByIptv
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.ui.screens.leanback.classicpanel.LeanbackClassicPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackVisible
import top.yogiczy.mytv.ui.screens.leanback.monitor.LeanbackMonitorScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelChannelNoSelectScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelDateTimeScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.LeanbackPanelTempScreen
import top.yogiczy.mytv.ui.screens.leanback.panel.rememberLeanbackPanelChannelNoSelectState
import top.yogiczy.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelScreen
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsScreen
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.update.LeanbackUpdateScreen
import top.yogiczy.mytv.ui.screens.leanback.video.LeanbackVideoScreen
import top.yogiczy.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.handleLeanbackDragGestures
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents

private const val REPLAY_SEEK_OFFSET_MS = 30_000L
private const val REPLAY_SEEK_CONTINUOUS_OFFSET_MS = 10_000L
private const val REPLAY_SEEK_CONTINUOUS_INITIAL_DELAY_MS = 400L
private const val REPLAY_SEEK_CONTINUOUS_INTERVAL_MS = 200L

/** 回放模式下双击返回键退出的间隔窗口 */
private const val REPLAY_EXIT_DOUBLE_BACK_INTERVAL_MS = 2_000L

@Composable
fun LeanbackMainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    iptvGroupList: IptvGroupList = IptvGroupList(),
    epgList: EpgList = EpgList(),
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    // 把可能影响自身重组的设置读取到本地，避免反复读取 ViewModel
    val videoPlayerAspectRatioSetting = settingsViewModel.videoPlayerAspectRatio
    val uiDensityScaleRatio = settingsViewModel.uiDensityScaleRatio
    val uiFontScaleRatio = settingsViewModel.uiFontScaleRatio

    val defaultAspectRatioProvider = remember(videoPlayerAspectRatioSetting, configuration) {
        {
            when (videoPlayerAspectRatioSetting) {
                SP.VideoPlayerAspectRatio.ORIGINAL -> null
                SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> 16f / 9f
                SP.VideoPlayerAspectRatio.FOUR_THREE -> 4f / 3f
                SP.VideoPlayerAspectRatio.AUTO -> {
                    configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
                }
            }
        }
    }

    val videoPlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = defaultAspectRatioProvider,
    )
    // 待机播放器：静音、小缓冲，用于换台预缓冲；命中时与主播放器交换角色
    val standbyVideoPlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = defaultAspectRatioProvider,
        minBufferMs = 5_000,
        maxBufferMs = 5_000,
        bufferForPlaybackMs = 500,
        bufferForPlaybackAfterRebufferMs = 1_000,
    )
    // EPG 是异步加载的，用 rememberUpdatedState 保证 state 内读取到的始终是最新节目单
    val latestEpgList by rememberUpdatedState(epgList)
    val mainContentState = rememberLeanbackMainContentState(
        videoPlayerState = videoPlayerState,
        standbyVideoPlayerState = standbyVideoPlayerState,
        iptvGroupList = iptvGroupList,
        epgListProvider = { latestEpgList },
    )
    val activeVideoPlayerState =
        if (mainContentState.activePlayerIndex == 0) videoPlayerState else standbyVideoPlayerState
    val panelChannelNoSelectState = rememberLeanbackPanelChannelNoSelectState(
        onChannelNoConfirm = {
            val channelNo = it.toIntOrNull()?.let { no -> no - 1 } ?: -1

            if (channelNo in iptvGroupList.iptvList.indices) {
                mainContentState.changeCurrentIptv(iptvGroupList.iptvList[channelNo])
            }
        }
    )

    val noOverlayVisible by remember {
        derivedStateOf {
            !mainContentState.isPanelVisible
                    && !mainContentState.isSettingsVisible
                    && !mainContentState.isQuickPanelVisible
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(noOverlayVisible) {
        // 当所有浮层关闭后，把焦点切回主播放区
        if (noOverlayVisible) {
            focusRequester.requestFocus()
        }
    }

    // 回放控制条：呼出后 5 秒无操作自动隐藏（暂停时常显，由组件内部处理）
    val replayBarVisible = remember { mutableStateOf(false) }
    val replayBarTrigger = remember { mutableIntStateOf(0) }
    val showReplayBar = remember {
        {
            replayBarVisible.value = true
            replayBarTrigger.intValue++
        }
    }
    LaunchedEffect(replayBarTrigger.intValue, mainContentState.isReplayPaused) {
        if (replayBarVisible.value && !mainContentState.isReplayPaused) {
            delay(5000)
            if (!mainContentState.isReplayPaused) replayBarVisible.value = false
        }
    }

    val seekJob = remember { mutableStateOf<Job?>(null) }
    fun startContinuousSeek(offsetMs: Long) {
        if (!mainContentState.isReplayMode) return
        seekJob.value?.cancel()
        seekJob.value = coroutineScope.launch {
            delay(REPLAY_SEEK_CONTINUOUS_INITIAL_DELAY_MS)
            while (isActive) {
                mainContentState.seekReplay(offsetMs)
                showReplayBar()
                delay(REPLAY_SEEK_CONTINUOUS_INTERVAL_MS)
            }
        }
    }
    fun stopContinuousSeek() {
        seekJob.value?.cancel()
        seekJob.value = null
    }

    // 稳定的状态提供者，避免父组件每次重组都产生新的 lambda
    val currentIptvProvider = remember { { mainContentState.currentIptv } }
    val currentIptvUrlIdxProvider = remember { { mainContentState.currentIptvUrlIdx } }
    val isReplayModeProvider = remember { { mainContentState.isReplayMode } }
    val replayProgrammeProvider = remember { { mainContentState.replayProgramme } }
    val epgProvider = remember(epgList) {
        { epgList.findByIptv(mainContentState.currentIptv) }
    }

    // 调试用：当 EPG 匹配失败时记录到日志历史，便于真机排查
    LaunchedEffect(mainContentState.currentIptv, epgList) {
        if (epgList.isNotEmpty()) {
            val iptv = mainContentState.currentIptv
            val epg = epgList.findByIptv(iptv)
            if (epg == null) {
                Log.i(
                    "LeanbackMainContent",
                    "EPG匹配失败: name=${iptv.name}, channelName=${iptv.channelName}, tvgId=${iptv.tvgId}, " +
                            "epgList.size=${epgList.size}"
                )
            }
        }
    }

    val channelNoProvider = remember {
        {
            (iptvGroupList.iptvIdx(mainContentState.currentIptv) + 1).toString()
                .padStart(2, '0')
        }
    }
    val channelNoIntProvider = remember {
        { iptvGroupList.iptvIdx(mainContentState.currentIptv) + 1 }
    }
    // 主备交换后活跃播放器会变，必须在调用时按角色动态取值，不能捕获固定实例
    val videoPlayerMetadataProvider = remember {
        {
            (if (mainContentState.activePlayerIndex == 0) videoPlayerState
            else standbyVideoPlayerState).metadata
        }
    }
    val videoPlayerAspectRatioProvider = remember {
        {
            (if (mainContentState.activePlayerIndex == 0) videoPlayerState
            else standbyVideoPlayerState).aspectRatio
        }
    }
    val showMetadataProvider = remember(settingsViewModel.debugShowVideoPlayerMetadata) {
        { settingsViewModel.debugShowVideoPlayerMetadata }
    }
    val showProgrammeProgressProvider = remember(settingsViewModel.uiShowEpgProgrammeProgress) {
        { settingsViewModel.uiShowEpgProgrammeProgress }
    }
    val iptvFavoriteEnableProvider = remember(settingsViewModel.iptvChannelFavoriteEnable) {
        { settingsViewModel.iptvChannelFavoriteEnable }
    }
    val iptvFavoriteListProvider = remember(settingsViewModel.iptvChannelFavoriteList) {
        { settingsViewModel.iptvChannelFavoriteList.toImmutableList() }
    }
    val iptvFavoriteListVisibleProvider = remember(settingsViewModel.iptvChannelFavoriteListVisible) {
        { settingsViewModel.iptvChannelFavoriteListVisible }
    }
    val uiUseClassicPanelScreenProvider = remember(settingsViewModel.uiUseClassicPanelScreen) {
        { settingsViewModel.uiUseClassicPanelScreen }
    }
    val showFpsProvider = remember(settingsViewModel.debugShowFps) {
        { settingsViewModel.debugShowFps }
    }
    val channelChangeFlipProvider = remember(settingsViewModel.iptvChannelChangeFlip) {
        { settingsViewModel.iptvChannelChangeFlip }
    }
    val channelNoSelectEnableProvider = remember(settingsViewModel.iptvChannelNoSelectEnable) {
        { settingsViewModel.iptvChannelNoSelectEnable }
    }

    // 回放模式下双击返回退出：记录第一次按返回的时间
    var lastReplayExitBackAt by remember { mutableLongStateOf(0L) }

    val onBackPressedHandler = remember {
        {
            // 优先关闭浮层，浮层都关闭后才处理退出回放/退出应用
            if (mainContentState.isPanelVisible) mainContentState.isPanelVisible = false
            else if (mainContentState.isSettingsVisible) mainContentState.isSettingsVisible = false
            else if (mainContentState.isQuickPanelVisible) mainContentState.isQuickPanelVisible = false
            else if (mainContentState.isReplayMode) {
                val now = SystemClock.uptimeMillis()
                if (now - lastReplayExitBackAt < REPLAY_EXIT_DOUBLE_BACK_INTERVAL_MS) {
                    lastReplayExitBackAt = 0L
                    mainContentState.exitReplayMode()
                } else {
                    lastReplayExitBackAt = now
                    LeanbackToastState.I.showToast("再按一次返回键退出回放")
                }
            }
            else onBackPressed()
        }
    }

    val onIptvSelected = remember { { iptv: Iptv -> mainContentState.changeCurrentIptv(iptv) } }
    val onIptvFocusPreview = remember { { iptv: Iptv -> mainContentState.prebufferChannel(iptv) } }
    val onPlayCatchup =
        remember { { iptv: Iptv, programme: EpgProgramme -> mainContentState.playCatchup(iptv, programme) } }
    val onIptvFavoriteToggle = remember(settingsViewModel.iptvChannelFavoriteEnable) {
        { iptv: Iptv ->
            if (settingsViewModel.iptvChannelFavoriteEnable) {
                if (settingsViewModel.iptvChannelFavoriteList.contains(iptv.channelName)) {
                    settingsViewModel.iptvChannelFavoriteList -= iptv.channelName
                    LeanbackToastState.I.showToast("取消收藏: ${iptv.channelName}")
                } else {
                    settingsViewModel.iptvChannelFavoriteList += iptv.channelName
                    LeanbackToastState.I.showToast("已收藏: ${iptv.channelName}")
                }
            }
        }
    }
    val onIptvFavoriteListVisibleChange = remember {
        { visible: Boolean -> settingsViewModel.iptvChannelFavoriteListVisible = visible }
    }
    val onClearCache = remember {
        {
            settingsViewModel.iptvPlayableHostList = emptySet()
            coroutineScope.launch {
                AppGlobal.cacheDir.deleteRecursively()
            }
            LeanbackToastState.I.showToast("缓存已清除，请重启应用")
        }
    }
    val onChangeVideoPlayerAspectRatio =
        remember {
            { ratio: Float ->
                (if (mainContentState.activePlayerIndex == 0) videoPlayerState
                else standbyVideoPlayerState).aspectRatio = ratio
            }
        }
    val onIptvUrlIdxChange = remember {
        { urlIdx: Int ->
            mainContentState.changeCurrentIptv(
                iptv = mainContentState.currentIptv,
                urlIdx = urlIdx,
            )
        }
    }

    // 稳定的按键回调
    val onKeyUp = remember {
        {
            if (noOverlayVisible) {
                if (channelChangeFlipProvider()) mainContentState.changeCurrentIptvToNext()
                else mainContentState.changeCurrentIptvToPrev()
            }
        }
    }
    val onKeyDown = remember {
        {
            if (noOverlayVisible) {
                if (channelChangeFlipProvider()) mainContentState.changeCurrentIptvToPrev()
                else mainContentState.changeCurrentIptvToNext()
            }
        }
    }
    val onKeyLeft = remember {
        {
            if (noOverlayVisible) {
                if (mainContentState.isReplayMode) {
                    mainContentState.seekReplay(-REPLAY_SEEK_OFFSET_MS)
                    showReplayBar()
                } else if (mainContentState.currentIptv.urlList.size > 1) {
                    mainContentState.changeCurrentIptv(
                        iptv = mainContentState.currentIptv,
                        urlIdx = mainContentState.currentIptvUrlIdx - 1,
                    )
                }
            }
        }
    }
    val onKeyRight = remember {
        {
            if (noOverlayVisible) {
                if (mainContentState.isReplayMode) {
                    mainContentState.seekReplay(REPLAY_SEEK_OFFSET_MS)
                    showReplayBar()
                } else if (mainContentState.currentIptv.urlList.size > 1) {
                    mainContentState.changeCurrentIptv(
                        iptv = mainContentState.currentIptv,
                        urlIdx = mainContentState.currentIptvUrlIdx + 1,
                    )
                }
            }
        }
    }
    val onKeySettings = remember {
        {
            Log.d(
                "LeanbackMainContent",
                "onSettings: noOverlayVisible=$noOverlayVisible, isPanelVisible=${mainContentState.isPanelVisible}, isSettingsVisible=${mainContentState.isSettingsVisible}, isQuickPanelVisible=${mainContentState.isQuickPanelVisible}"
            )
            if (noOverlayVisible) mainContentState.isQuickPanelVisible = true
        }
    }
    val onKeyNumber = remember(channelNoSelectEnableProvider) {
        { number: Int ->
            if (noOverlayVisible && channelNoSelectEnableProvider()) {
                panelChannelNoSelectState.input(number)
            }
        }
    }
    val onKeyLongDown = remember {
        { if (noOverlayVisible) mainContentState.isQuickPanelVisible = true }
    }

    LeanbackBackPressHandledArea(
        modifier = modifier,
        onBackPressed = onBackPressedHandler,
    ) {
        LeanbackVideoScreen(
            state = activeVideoPlayerState,
            showMetadataProvider = showMetadataProvider,
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable()
                .handleLeanbackKeyEvents(
                    onUp = onKeyUp,
                    onDown = onKeyDown,
                    onLeft = onKeyLeft,
                    onLeftDown = { startContinuousSeek(-REPLAY_SEEK_CONTINUOUS_OFFSET_MS) },
                    onLeftUp = { stopContinuousSeek() },
                    onRight = onKeyRight,
                    onRightDown = { startContinuousSeek(REPLAY_SEEK_CONTINUOUS_OFFSET_MS) },
                    onRightUp = { stopContinuousSeek() },
                    onSelect = {
                        if (noOverlayVisible) {
                            if (mainContentState.isReplayMode) {
                                // 回放中：OK 呼出控制条，控制条显示时再按 OK 暂停/继续
                                if (replayBarVisible.value || mainContentState.isReplayPaused) {
                                    mainContentState.toggleReplayPause()
                                } else {
                                    showReplayBar()
                                }
                            } else {
                                mainContentState.isPanelVisible = true
                            }
                        }
                    },
                    onLongSelect = { if (noOverlayVisible) mainContentState.isQuickPanelVisible = true },
                    onSettings = onKeySettings,
                    onNumber = onKeyNumber,
                    onLongDown = onKeyLongDown,
                )
                .handleLeanbackDragGestures(
                    onSwipeDown = onKeyDown,
                    onSwipeUp = onKeyUp,
                    onSwipeRight = onKeyRight,
                    onSwipeLeft = onKeyLeft,
                ),
        )

        LeanbackReplayControlBar(
            visibleProvider = { replayBarVisible.value },
            replayProgrammeProvider = replayProgrammeProvider,
            currentPositionMsProvider = { mainContentState.replayCurrentPositionMs },
            isPausedProvider = remember { { mainContentState.isReplayPaused } },
            segmentInfoProvider = remember {
                {
                    val programme = mainContentState.replayProgramme
                    val programmes = epgProvider()?.programmes ?: emptyList()
                    val index = programmes.indexOfFirst { it.startAt == programme?.startAt }
                    if (index >= 0) "第${index + 1}/${programmes.size}段" else ""
                }
            },
        )

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * uiDensityScaleRatio,
                fontScale = LocalDensity.current.fontScale * uiFontScaleRatio,
            )
        ) {
            val dateTimeVisible = remember {
                {
                    !mainContentState.isTempPanelVisible
                            && !mainContentState.isSettingsVisible
                            && !mainContentState.isPanelVisible
                            && !mainContentState.isQuickPanelVisible
                            && panelChannelNoSelectState.channelNo.isEmpty()
                }
            }
            LeanbackVisible(dateTimeVisible) {
                LeanbackPanelDateTimeScreen(
                    showModeProvider = remember {
                        { settingsViewModel.uiTimeShowMode }
                    }
                )
            }

            LeanbackPanelChannelNoSelectScreen(
                channelNoProvider = remember { { panelChannelNoSelectState.channelNo } }
            )

            val tempPanelVisible = remember {
                {
                    mainContentState.isTempPanelVisible
                            && !mainContentState.isSettingsVisible
                            && !mainContentState.isPanelVisible
                            && !mainContentState.isQuickPanelVisible
                            && panelChannelNoSelectState.channelNo.isEmpty()
                }
            }
            LeanbackVisible(tempPanelVisible) {
                LeanbackPanelTempScreen(
                    channelNoProvider = channelNoIntProvider,
                    currentIptvProvider = currentIptvProvider,
                    currentIptvUrlIdxProvider = currentIptvUrlIdxProvider,
                    epgProvider = epgProvider,
                    isReplayModeProvider = isReplayModeProvider,
                    replayProgrammeProvider = replayProgrammeProvider,
                    showProgrammeProgressProvider = showProgrammeProgressProvider,
                )
            }

            val panelVisible = remember { { !uiUseClassicPanelScreenProvider() && mainContentState.isPanelVisible } }
            LeanbackVisible(panelVisible) {
                LeanbackPanelScreen(
                    iptvGroupListProvider = { iptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = currentIptvProvider,
                    currentIptvUrlIdxProvider = currentIptvUrlIdxProvider,
                    isReplayModeProvider = isReplayModeProvider,
                    replayProgrammeProvider = replayProgrammeProvider,
                    videoPlayerMetadataProvider = videoPlayerMetadataProvider,
                    showProgrammeProgressProvider = showProgrammeProgressProvider,
                    iptvFavoriteEnableProvider = iptvFavoriteEnableProvider,
                    iptvFavoriteListProvider = iptvFavoriteListProvider,
                    iptvFavoriteListVisibleProvider = iptvFavoriteListVisibleProvider,
                    onIptvFavoriteListVisibleChange = onIptvFavoriteListVisibleChange,
                    onIptvSelected = onIptvSelected,
                    onIptvFavoriteToggle = onIptvFavoriteToggle,
                    onClose = { mainContentState.isPanelVisible = false },
                    onPlayCatchup = onPlayCatchup,
                    onIptvFocused = onIptvFocusPreview,
                )
            }

            val classicPanelVisible = remember { { uiUseClassicPanelScreenProvider() && mainContentState.isPanelVisible } }
            LeanbackVisible(classicPanelVisible) {
                LeanbackClassicPanelScreen(
                    iptvGroupListProvider = { iptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = currentIptvProvider,
                    showProgrammeProgressProvider = showProgrammeProgressProvider,
                    iptvFavoriteEnableProvider = iptvFavoriteEnableProvider,
                    iptvFavoriteListProvider = iptvFavoriteListProvider,
                    iptvFavoriteListVisibleProvider = iptvFavoriteListVisibleProvider,
                    onIptvFavoriteListVisibleChange = onIptvFavoriteListVisibleChange,
                    onIptvSelected = onIptvSelected,
                    onIptvFavoriteToggle = onIptvFavoriteToggle,
                    onClose = { mainContentState.isPanelVisible = false },
                    onPlayCatchup = onPlayCatchup,
                    onIptvFocusedPreview = onIptvFocusPreview,
                )
            }
        }

        val quickPanelVisible = remember {
            { mainContentState.isQuickPanelVisible && !mainContentState.isSettingsVisible }
        }
        LeanbackVisible(quickPanelVisible) {
            LeanbackQuickPanelScreen(
                currentIptvProvider = currentIptvProvider,
                currentIptvUrlIdxProvider = currentIptvUrlIdxProvider,
                epgProvider = epgProvider,
                isReplayModeProvider = isReplayModeProvider,
                replayProgrammeProvider = replayProgrammeProvider,
                currentIptvChannelNoProvider = channelNoProvider,
                videoPlayerMetadataProvider = videoPlayerMetadataProvider,
                videoPlayerAspectRatioProvider = videoPlayerAspectRatioProvider,
                onChangeVideoPlayerAspectRatio = onChangeVideoPlayerAspectRatio,
                onIptvUrlIdxChange = onIptvUrlIdxChange,
                onClearCache = onClearCache,
                onMoreSettings = { mainContentState.isSettingsVisible = true },
                onClose = { mainContentState.isQuickPanelVisible = false },
            )
        }

        LeanbackVisible({ mainContentState.isSettingsVisible }) {
            LeanbackSettingsScreen()
        }

        LeanbackVisible(showFpsProvider) {
            LeanbackMonitorScreen()
        }

        LeanbackUpdateScreen()
    }
}

@Composable
fun LeanbackBackPressHandledArea(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = Modifier
        .onPreviewKeyEvent {
            if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                onBackPressed()
                true
            } else {
                false
            }
        }
        .then(modifier),
    content = content,
)
