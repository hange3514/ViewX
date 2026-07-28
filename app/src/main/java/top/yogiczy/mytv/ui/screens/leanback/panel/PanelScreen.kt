package top.yogiczy.mytv.ui.screens.leanback.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgList

import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.entities.findByIptv
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.panel.components.LeanbackPanelChannelNo
import top.yogiczy.mytv.ui.screens.leanback.panel.components.LeanbackPanelDateTime
import top.yogiczy.mytv.ui.screens.leanback.panel.components.LeanbackPanelIptvFavoriteList
import top.yogiczy.mytv.ui.screens.leanback.panel.components.LeanbackPanelIptvGroupList
import top.yogiczy.mytv.ui.screens.leanback.panel.components.LeanbackPanelIptvInfo
import top.yogiczy.mytv.ui.screens.leanback.panel.components.LeanbackPanelPlayerInfo
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import top.yogiczy.mytv.ui.theme.LeanbackAlpha
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.tvTouchClickable

@Composable
fun LeanbackPanelScreen(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    currentIptvUrlIdxProvider: () -> Int = { 0 },
    isReplayModeProvider: () -> Boolean = { false },
    replayProgrammeProvider: () -> top.yogiczy.mytv.data.entities.EpgProgramme? = { null },
    videoPlayerMetadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    iptvFavoriteEnableProvider: () -> Boolean = { true },
    iptvFavoriteListProvider: () -> ImmutableList<String> = { persistentListOf() },
    iptvFavoriteListVisibleProvider: () -> Boolean = { false },
    onIptvFavoriteListVisibleChange: (Boolean) -> Unit = {},
    onIptvSelected: (Iptv) -> Unit = {},
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onPlayCatchup: (Iptv, EpgProgramme) -> Unit = { _, _ -> },
    onIptvFocused: (Iptv) -> Unit = {},
    onClose: () -> Unit = {},
    autoCloseState: PanelAutoCloseState = rememberPanelAutoCloseState(
        timeout = Constants.UI_SCREEN_AUTO_CLOSE_DELAY,
        onTimeout = onClose,
    ),
) {
    LaunchedEffect(Unit) {
        autoCloseState.active()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = LeanbackAlpha.Scrim))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    ) {
        val channelNoProvider = remember {
            {
                (iptvGroupListProvider().iptvIdx(currentIptvProvider()) + 1).toString()
                    .padStart(2, '0')
            }
        }
        LeanbackPanelScreenTopRight(channelNoProvider = channelNoProvider)

        LeanbackPanelScreenBottom(
            iptvGroupListProvider = iptvGroupListProvider,
            epgListProvider = epgListProvider,
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
            onPlayCatchup = onPlayCatchup,
            onIptvFocused = onIptvFocused,
            onClose = onClose,
            onUserAction = { autoCloseState.active() },
        )
    }
}

@Composable
fun LeanbackPanelScreenTopRight(
    modifier: Modifier = Modifier,
    channelNoProvider: () -> String = { "" },
) {
    val childPadding = rememberLeanbackChildPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = childPadding.top, end = childPadding.end),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeanbackPanelChannelNo(channelNoProvider = channelNoProvider)

            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                Spacer(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onBackground)
                        .width(2.dp)
                        .height(30.dp),
                )
            }

            LeanbackPanelDateTime()
        }
    }
}

@Composable
private fun LeanbackPanelScreenBottom(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    currentIptvUrlIdxProvider: () -> Int = { 0 },
    isReplayModeProvider: () -> Boolean = { false },
    replayProgrammeProvider: () -> top.yogiczy.mytv.data.entities.EpgProgramme? = { null },
    videoPlayerMetadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    iptvFavoriteEnableProvider: () -> Boolean = { true },
    iptvFavoriteListProvider: () -> ImmutableList<String> = { persistentListOf() },
    iptvFavoriteListVisibleProvider: () -> Boolean = { false },
    onIptvFavoriteListVisibleChange: (Boolean) -> Unit = {},
    onIptvSelected: (Iptv) -> Unit = {},
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onPlayCatchup: (Iptv, EpgProgramme) -> Unit = { _, _ -> },
    onIptvFocused: (Iptv) -> Unit = {},
    onClose: () -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val childPadding = rememberLeanbackChildPadding()
    val epgList = epgListProvider()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LeanbackPanelIptvInfo(
                modifier = Modifier
                    .padding(start = childPadding.start),
                iptvProvider = currentIptvProvider,
                iptvUrlIdxProvider = currentIptvUrlIdxProvider,
                epgProvider = remember(epgList) {
                    { epgList.findByIptv(currentIptvProvider()) }
                },
                isReplayModeProvider = isReplayModeProvider,
                replayProgrammeProvider = replayProgrammeProvider,
            )

            LeanbackPanelPlayerInfo(
                modifier = Modifier.padding(start = childPadding.start),
                metadataProvider = videoPlayerMetadataProvider
            )

            LeanbackPanelScreenBottomIptvList(
                iptvGroupListProvider = iptvGroupListProvider,
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                iptvFavoriteEnableProvider = iptvFavoriteEnableProvider,
                iptvFavoriteListProvider = iptvFavoriteListProvider,
                iptvFavoriteListVisibleProvider = iptvFavoriteListVisibleProvider,
                onIptvFavoriteListVisibleChange = onIptvFavoriteListVisibleChange,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onPlayCatchup = onPlayCatchup,
                onIptvFocused = onIptvFocused,
                onUserAction = onUserAction,
            )
        }
    }
}

@Composable
fun LeanbackPanelScreenBottomIptvList(
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
    onIptvFocused: (Iptv) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val iptvFavoriteEnable = iptvFavoriteEnableProvider()
    var favoriteListVisible by remember { mutableStateOf(iptvFavoriteListVisibleProvider()) }

    val iptvGroupList = iptvGroupListProvider()
    val iptvFavoriteList = iptvFavoriteListProvider()
    val favoriteList = remember(iptvGroupList, iptvFavoriteList) {
        val favoriteSet = iptvFavoriteList.toHashSet()
        iptvGroupList.iptvList.filter { favoriteSet.contains(it.channelName) }
    }

    val favoriteIptvListProvider = remember(favoriteList) { { IptvList(favoriteList) } }
    val onFavoriteListClose = remember {
        {
            favoriteListVisible = false
            onIptvFavoriteListVisibleChange(false)
        }
    }
    val onToFavorite = remember(iptvFavoriteEnable, favoriteList, onIptvFavoriteListVisibleChange) {
        {
            if (iptvFavoriteEnable) {
                if (favoriteList.isNotEmpty()) {
                    favoriteListVisible = true
                    onIptvFavoriteListVisibleChange(true)
                } else {
                    LeanbackToastState.I.showToast("没有收藏的频道")
                }
            }
        }
    }

    Box(modifier = modifier.height(150.dp)) {
        if (favoriteListVisible)
            LeanbackPanelIptvFavoriteList(
                iptvListProvider = favoriteIptvListProvider,
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onPlayCatchup = onPlayCatchup,
                onClose = onFavoriteListClose,
                onUserAction = onUserAction,
            )
        else
            LeanbackPanelIptvGroupList(
                iptvGroupListProvider = iptvGroupListProvider,
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onPlayCatchup = onPlayCatchup,
                onToFavorite = onToFavorite,
                onIptvFocused = onIptvFocused,
                onUserAction = onUserAction,
            )

        // 触摸设备进入收藏列表的入口（遥控器是按上键）
        if (!favoriteListVisible && iptvFavoriteEnable && favoriteList.isNotEmpty()) {
            Text(
                text = "收藏",
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = LeanbackAlpha.ContentHigh),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        LocalContentColor.current.copy(alpha = LeanbackAlpha.TouchBackground),
                        MaterialTheme.shapes.small,
                    )
                    .tvTouchClickable(onClick = onToFavorite)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelScreenTopRightPreview() {
    LeanbackTheme {
        LeanbackPanelScreenTopRight(
            channelNoProvider = { "01" },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelScreenBottomPreview() {
    LeanbackTheme {
        LeanbackPanelScreenBottom(
            currentIptvProvider = { Iptv.EXAMPLE },
            currentIptvUrlIdxProvider = { 0 },
            iptvGroupListProvider = { IptvGroupList.EXAMPLE },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelScreenPreview() {
    LeanbackTheme {
        LeanbackPanelScreen(
            currentIptvProvider = { Iptv.EXAMPLE },
            currentIptvUrlIdxProvider = { 0 },
            iptvGroupListProvider = { IptvGroupList.EXAMPLE },
        )
    }
}
