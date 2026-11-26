package top.yogiczy.mytv.ui.screens.leanback.classicpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgrammeList
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvGroupIdx
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.entities.findByIptv
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.classicpanel.components.LeanbackClassicPanelEpgList
import top.yogiczy.mytv.ui.screens.leanback.classicpanel.components.LeanbackClassicPanelIptvGroupList
import top.yogiczy.mytv.ui.screens.leanback.classicpanel.components.LeanbackClassicPanelIptvList
import top.yogiczy.mytv.ui.screens.leanback.panel.PanelAutoCloseState
import top.yogiczy.mytv.ui.screens.leanback.panel.rememberPanelAutoCloseState
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import kotlin.math.max

@Composable
fun LeanbackClassicPanelScreen(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    iptvFavoriteEnableProvider: () -> Boolean = { true },
    iptvFavoriteListProvider: () -> ImmutableList<String> = { persistentListOf() },
    iptvFavoriteListVisibleProvider: () -> Boolean = { false },
    onIptvFavoriteListVisibleChange: (Boolean) -> Unit = {},
    onIptvSelected: (Iptv) -> Unit = {},
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onPlayCatchup: (Iptv, EpgProgramme) -> Unit = { _, _ -> },
    onIptvFocusedPreview: (Iptv) -> Unit = {},
    onClose: () -> Unit = {},
    autoCloseState: PanelAutoCloseState = rememberPanelAutoCloseState(
        timeout = Constants.UI_SCREEN_AUTO_CLOSE_DELAY,
        onTimeout = onClose,
    ),
) {
    LaunchedEffect(Unit) {
        autoCloseState.active()
    }

    LeanbackClassicPanelScreenWrapper(
        modifier = modifier.onPreviewKeyEvent {
            autoCloseState.active()
            false
        },
        onClose = onClose,
    ) {
        LeanbackClassicPanelScreenContent(
            iptvGroupListProvider = iptvGroupListProvider,
            epgListProvider = epgListProvider,
            currentIptvProvider = currentIptvProvider,
            showProgrammeProgressProvider = showProgrammeProgressProvider,
            onIptvSelected = onIptvSelected,
            iptvFavoriteEnableProvider = iptvFavoriteEnableProvider,
            iptvFavoriteListProvider = iptvFavoriteListProvider,
            iptvFavoriteListVisibleProvider = iptvFavoriteListVisibleProvider,
            onIptvFavoriteListVisibleChange = onIptvFavoriteListVisibleChange,
            onIptvFavoriteToggle = onIptvFavoriteToggle,
            onPlayCatchup = onPlayCatchup,
            onIptvFocusedPreview = onIptvFocusedPreview,
            onUserAction = { autoCloseState.active() },
        )
    }
}

@Composable
private fun LeanbackClassicPanelScreenWrapper(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val childPadding = rememberLeanbackChildPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
    ) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .padding(
                    top = childPadding.top + 8.dp,
                    start = childPadding.start,
                    bottom = childPadding.bottom + 8.dp,
                    end = childPadding.end,
                )
                .clip(MaterialTheme.shapes.small),
        ) {
            content()
        }
    }
}

@Composable
private fun LeanbackClassicPanelScreenContent(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    onIptvSelected: (Iptv) -> Unit = {},
    iptvFavoriteEnableProvider: () -> Boolean = { true },
    iptvFavoriteListProvider: () -> ImmutableList<String> = { persistentListOf() },
    iptvFavoriteListVisibleProvider: () -> Boolean = { false },
    onIptvFavoriteListVisibleChange: (Boolean) -> Unit = {},
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onPlayCatchup: (Iptv, EpgProgramme) -> Unit = { _, _ -> },
    onIptvFocusedPreview: (Iptv) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val iptvGroupList = iptvGroupListProvider()
    val iptvFavoriteList = iptvFavoriteListProvider()
    val iptvFavoriteEnable = iptvFavoriteEnableProvider()
    val focusManager = LocalFocusManager.current

    val favoriteIptvList = remember(iptvGroupList, iptvFavoriteList) {
        val favoriteSet = iptvFavoriteList.toHashSet()
        iptvGroupList.iptvList.filter { favoriteSet.contains(it.channelName) }
    }

    val displayGroupList = remember(iptvGroupList, iptvFavoriteEnable) {
        if (iptvFavoriteEnable)
            IptvGroupList(listOf(LeanbackClassicPanelScreenFavoriteIptvGroup) + iptvGroupList)
        else
            iptvGroupList
    }

    var focusedIptvGroup by remember {
        mutableStateOf(
            if (iptvFavoriteEnable && iptvFavoriteListVisibleProvider())
                LeanbackClassicPanelScreenFavoriteIptvGroup
            else
                displayGroupList.getOrNull(max(0, displayGroupList.iptvGroupIdx(currentIptvProvider())))
                    ?: IptvGroup()
        )
    }

    var focusedIptv by remember { mutableStateOf(currentIptvProvider()) }
    var focusedIptvFocusRequester by remember { mutableStateOf(FocusRequester.Default) }

    var epgListVisible by remember { mutableStateOf(false) }
    LaunchedEffect(epgListVisible) {
        if (epgListVisible) {
            withFrameNanos { }
            focusManager.moveFocus(FocusDirection.Right)
        }
    }

    val displayGroupListProvider = remember(displayGroupList) { { displayGroupList } }
    val initialIptvGroupProvider = remember {
        {
            if (iptvFavoriteEnable && iptvFavoriteListVisibleProvider())
                LeanbackClassicPanelScreenFavoriteIptvGroup
            else
                iptvGroupList.find { it.iptvList.contains(currentIptvProvider()) }
                    ?: IptvGroup()
        }
    }
    val onIptvGroupFocused = remember {
        { iptvGroup: IptvGroup ->
            focusedIptvGroup = iptvGroup
            onIptvFavoriteListVisibleChange(iptvGroup == LeanbackClassicPanelScreenFavoriteIptvGroup)
        }
    }
    val exitFocusRequesterProvider = remember(focusedIptvFocusRequester) {
        { focusedIptvFocusRequester }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))) {
        Row(modifier = Modifier.weight(1f)) {
            LeanbackClassicPanelIptvGroupList(
                iptvGroupListProvider = displayGroupListProvider,
                initialIptvGroupProvider = initialIptvGroupProvider,
                onIptvGroupFocused = onIptvGroupFocused,
                exitFocusRequesterProvider = exitFocusRequesterProvider,
                onUserAction = onUserAction,
            )

            val iptvGroupProvider = remember(focusedIptvGroup) { { focusedIptvGroup } }
            val iptvListProvider = remember(focusedIptvGroup, favoriteIptvList) {
                {
                    if (focusedIptvGroup == LeanbackClassicPanelScreenFavoriteIptvGroup)
                        IptvList(favoriteIptvList)
                    else
                        focusedIptvGroup.iptvList
                }
            }
            val onIptvFocused = remember {
                { iptv: Iptv, focusRequester: FocusRequester ->
                    focusedIptv = iptv
                    focusedIptvFocusRequester = focusRequester
                    // 光标停留时通知上层去抖预缓冲
                    onIptvFocusedPreview(iptv)
                }
            }
            val isFavoriteListProvider = remember(focusedIptvGroup) {
                { focusedIptvGroup == LeanbackClassicPanelScreenFavoriteIptvGroup }
            }

            LeanbackClassicPanelIptvList(
                modifier = Modifier
                    .handleLeanbackKeyEvents(
                        onRight = { epgListVisible = true },
                    ),
                iptvGroupProvider = iptvGroupProvider,
                iptvListProvider = iptvListProvider,
                epgListProvider = epgListProvider,
                initialIptvProvider = currentIptvProvider,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onIptvFocused = onIptvFocused,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                onUserAction = onUserAction,
                isFavoriteListProvider = isFavoriteListProvider,
            )

            val focusedIptvProvider = remember(focusedIptv) { { focusedIptv } }
            // 注意：epgListProvider 必须作为 remember 的 key。
            // EPG 是异步加载的（Ready 状态先携带空 epgList，加载完成后再 copy 更新），
            // 若不指定 key，这里会一直持有面板首次组合时的旧 provider（闭包内是空的 epgList），
            // 导致 EPG 在面板打开期间加载完成后，按右键只隐藏提示条却显示不出节目单
            val focusedIptvEpgProvider = remember(epgListProvider, focusedIptv) {
                { epgListProvider().findByIptv(focusedIptv) }
            }
            val focusedIptvFocusRequesterProvider =
                remember(focusedIptvFocusRequester) { { focusedIptvFocusRequester } }

            if (epgListVisible) {
                LeanbackClassicPanelEpgList(
                    iptvProvider = focusedIptvProvider,
                    epgProvider = focusedIptvEpgProvider,
                    exitFocusRequesterProvider = focusedIptvFocusRequesterProvider,
                    onReturnToChannels = { epgListVisible = false },
                    onPlayCatchup = onPlayCatchup,
                    onUserAction = onUserAction,
                )
            }
        }
        Text(
            text = if (epgListVisible) "← 频道" else "节目单 →",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        onUserAction()
                        if (epgListVisible) {
                            epgListVisible = false
                            if (focusedIptvFocusRequester != FocusRequester.Default) {
                                focusedIptvFocusRequester.requestFocus()
                            }
                        } else {
                            epgListVisible = true
                        }
                    })
                },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackClassicPanelScreenPreview() {
    LeanbackTheme {
        LeanbackClassicPanelScreen(
            iptvGroupListProvider = { IptvGroupList.EXAMPLE },
            epgListProvider = {
                EpgList(IptvGroupList.EXAMPLE.iptvList.map {
                    Epg(
                        channel = it.channelName,
                        programmes = EpgProgrammeList(List(5) { idx ->
                            EpgProgramme(
                                startAt = System.currentTimeMillis() + idx * 60 * 60 * 1000L,
                                endAt = System.currentTimeMillis() + (idx + 1) * 60 * 60 * 1000L,
                                title = "${it.channelName}节目${idx + 1}",
                            )
                        })
                    )
                })
            },
        )
    }
}

val LeanbackClassicPanelScreenFavoriteIptvGroup = IptvGroup(name = "我的收藏")
