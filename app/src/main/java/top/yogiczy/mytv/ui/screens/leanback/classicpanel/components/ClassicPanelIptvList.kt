package top.yogiczy.mytv.ui.screens.leanback.classicpanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.ListItemDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.entities.findByIptv
import top.yogiczy.mytv.ui.screens.leanback.components.ProgrammeProgressIndicator
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.ui.utils.tvTouchClickable
import top.yogiczy.mytv.ui.utils.rememberCurrentProgramme
import kotlin.math.max

@Composable
fun LeanbackClassicPanelIptvList(
    modifier: Modifier = Modifier,
    iptvGroupProvider: () -> IptvGroup = { IptvGroup() },
    iptvListProvider: () -> IptvList = { IptvList() },
    epgListProvider: () -> EpgList = { EpgList() },
    initialIptvProvider: () -> Iptv = { Iptv() },
    onIptvSelected: (Iptv) -> Unit = {},
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onIptvFocused: (Iptv, FocusRequester) -> Unit = { _, _ -> },
    showProgrammeProgressProvider: () -> Boolean = { false },
    isFavoriteListProvider: () -> Boolean = { false },
    onUserAction: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val iptvList = iptvListProvider()
    val iptvGroup = iptvGroupProvider()
    val initialIptv = initialIptvProvider()
    val epgList = epgListProvider()
    var hasFocused by rememberSaveable { mutableStateOf(!iptvList.contains(initialIptv)) }
    val itemFocusRequesterList = remember(iptvList) {
        List(iptvList.size) { FocusRequester() }
    }
    var focusedIptv by remember(iptvList) { mutableStateOf(initialIptv) }

    LaunchedEffect(iptvList) {
        if (iptvList.isNotEmpty()) {
            if (hasFocused) {
                onIptvFocused(iptvList[0], itemFocusRequesterList[0])
            } else {
                onIptvFocused(
                    initialIptv,
                    itemFocusRequesterList[max(0, iptvList.indexOf(initialIptv))],
                )
            }
        }
    }

    val listState = remember(iptvGroup) {
        TvLazyListState(
            if (hasFocused) 0
            else max(0, iptvList.indexOf(initialIptv) - 2)
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    TvLazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(MaterialTheme.colorScheme.background.copy(0.8f)),
    ) {
        itemsIndexed(
            items = iptvList,
            key = { _, iptv -> iptv.urlList.firstOrNull() ?: iptv.name },
        ) { index, iptv ->
            val isSelected by remember { derivedStateOf { iptv == focusedIptv } }
            val initialFocused by remember {
                derivedStateOf { !hasFocused && iptv == initialIptv }
            }
            val onSelected = remember(iptv) { { onIptvSelected(iptv) } }
            val onFavoriteToggle = remember(iptv) {
                {
                    if (isFavoriteListProvider()) {
                        if (iptvList.size == 1) {
                            focusManager.moveFocus(FocusDirection.Left)
                        } else if (iptvList.first() == iptv) {
                            focusManager.moveFocus(FocusDirection.Down)
                        } else if (iptvList.last() == iptv) {
                            focusManager.moveFocus(FocusDirection.Up)
                        } else {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                    onIptvFavoriteToggle(iptv)
                }
            }
            val onFocused = remember(iptv, index) {
                {
                    focusedIptv = iptv
                    onIptvFocused(iptv, itemFocusRequesterList[index])
                }
            }

            LeanbackClassicPanelIptvItem(
                iptvProvider = { iptv },
                epgProvider = { epgList.findByIptv(iptv) },
                focusRequesterProvider = { itemFocusRequesterList[index] },
                isSelectedProvider = { isSelected },
                initialFocusedProvider = { initialFocused },
                onInitialFocused = { hasFocused = true },
                onFocused = onFocused,
                onSelected = onSelected,
                onFavoriteToggle = onFavoriteToggle,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
            )
        }
    }
}

@Composable
private fun LeanbackClassicPanelIptvItem(
    modifier: Modifier = Modifier,
    iptvProvider: () -> Iptv = { Iptv() },
    epgProvider: () -> Epg? = { null },
    focusRequesterProvider: () -> FocusRequester = { FocusRequester() },
    isSelectedProvider: () -> Boolean = { false },
    initialFocusedProvider: () -> Boolean = { false },
    onInitialFocused: () -> Unit = {},
    onFocused: () -> Unit = {},
    onSelected: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    showProgrammeProgressProvider: () -> Boolean = { false },
) {
    val iptv = iptvProvider()
    val focusRequester = focusRequesterProvider()
    val currentProgramme = rememberCurrentProgramme(epgProvider()?.programmes ?: emptyList())

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (initialFocusedProvider()) {
            onInitialFocused()
            // 部分电视（如长虹）首次弹出面板时布局尚未完成，焦点请求会被静默丢弃，
            // 导致面板按键无响应；这里重试直至真正获得焦点
            repeat(20) {
                if (isFocused) return@LaunchedEffect
                focusRequester.requestFocus()
                delay(50)
            }
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides if (isFocused) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier.clip(ListItemDefaults.shape().shape),
        ) {
            androidx.tv.material3.ListItem(
                modifier = modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus

                        if (isFocused) {
                            onFocused()
                        }
                    }
                    .tvTouchClickable(
                        onClick = { onSelected() },
                        onLongClick = { onFavoriteToggle() },
                    ),
                onClick = { onSelected() },
                onLongClick = { onFavoriteToggle() },
                colors = ListItemDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ),
                ),
                selected = isSelectedProvider(),
                headlineContent = {
                    Text(text = iptv.name, maxLines = 2)
                },
                supportingContent = {
                    Text(
                        text = currentProgramme?.title ?: "无节目",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.alpha(0.8f),
                    )
                },
            )

            if (showProgrammeProgressProvider() && currentProgramme != null) {
                ProgrammeProgressIndicator(
                    programme = currentProgramme,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

@Preview
@Composable
private fun LeanbackClassicPanelIptvListPreview() {
    LeanbackTheme {
        LeanbackClassicPanelIptvList(
            modifier = Modifier.padding(20.dp),
            iptvListProvider = { IptvList.EXAMPLE },
        )
    }
}
