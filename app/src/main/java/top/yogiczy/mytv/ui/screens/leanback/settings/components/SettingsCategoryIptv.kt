package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.foundation.lazy.list.items
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackQrcodeDialog
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.HttpServer
import top.yogiczy.mytv.ui.utils.LiveSettingsBus
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.ui.utils.tvTouchClickable
import top.yogiczy.mytv.utils.humanizeMs
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryIptv(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "数字选台",
                supportingContent = "通过数字选择频道",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvChannelNoSelectEnable,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.iptvChannelNoSelectEnable =
                        !settingsViewModel.iptvChannelNoSelectEnable
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "换台反转",
                supportingContent = if (settingsViewModel.iptvChannelChangeFlip) "方向键上：下一个频道；方向键下：上一个频道"
                else "方向键上：上一个频道；方向键下：下一个频道",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvChannelChangeFlip,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.iptvChannelChangeFlip =
                        !settingsViewModel.iptvChannelChangeFlip
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "换台预缓冲",
                supportingContent = "提前缓冲预测频道和光标停留的频道，换台更快（性能弱的设备可关闭）",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvPrebufferEnable,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.iptvPrebufferEnable =
                        !settingsViewModel.iptvPrebufferEnable
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "直播源精简",
                supportingContent = if (settingsViewModel.iptvSourceSimplify) "显示精简直播源(仅央视、地方卫视)" else "显示完整直播源",
                trailingContent = {
                    Switch(checked = settingsViewModel.iptvSourceSimplify, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.iptvSourceSimplify = !settingsViewModel.iptvSourceSimplify
                },
            )
        }

        item {
            var showDialog by remember { mutableStateOf(false) }

            LeanbackSettingsCategoryListItem(
                headlineContent = "直播源缓存时间",
                supportingContent = "短按选择，长按设为0小时",
                trailingContent = settingsViewModel.iptvSourceCacheTime.humanizeMs(),
                onSelected = { showDialog = true },
                onLongSelected = {
                    settingsViewModel.iptvSourceCacheTime = 0
                },
            )

            LeanbackSettingsValueSelectDialog(
                showDialogProvider = { showDialog },
                onDismissRequest = { showDialog = false },
                title = "直播源缓存时间",
                options = listOf(0L, 1L, 2L, 3L, 6L, 12L, 24L, 48L, 72L).map {
                    (if (it == 0L) "不缓存" else "${it}小时") to it * 1000 * 60 * 60
                },
                currentValueProvider = { settingsViewModel.iptvSourceCacheTime },
                onSelected = { settingsViewModel.iptvSourceCacheTime = it },
            )
        }

        item {
            var showDialog by remember { mutableStateOf(false) }

            LeanbackSettingsCategoryListItem(
                headlineContent = "自定义直播源",
                supportingContent = if (settingsViewModel.iptvSourceUrl != Constants.IPTV_SOURCE_URL) settingsViewModel.iptvSourceUrl else null,
                trailingContent = if (settingsViewModel.iptvSourceUrl != Constants.IPTV_SOURCE_URL) "已启用" else "未启用",
                onSelected = { showDialog = true },
                remoteConfig = true,
            )

            LeanbackSettingsIptvSourceHistoryDialog(showDialogProvider = { showDialog },
                onDismissRequest = { showDialog = false },
                iptvSourceHistoryProvider = {
                    settingsViewModel.iptvSourceUrlHistoryList.filter {
                        it != Constants.IPTV_SOURCE_URL
                    }.toImmutableList()
                },
                currentIptvSourceProvider = { settingsViewModel.iptvSourceUrl },
                onSelected = {
                    showDialog = false
                    if (settingsViewModel.iptvSourceUrl != it) {
                        settingsViewModel.iptvSourceUrl = it
                        coroutineScope.launch { IptvRepository().clearCache() }
                        // 与网页推送路径对齐：软重启使新源生效
                        LiveSettingsBus.recreateAppRequests.tryEmit(Unit)
                        LeanbackToastState.I.showToast("直播源已切换，正在刷新...")
                    }
                },
                onDeleted = {
                    settingsViewModel.iptvSourceUrlHistoryList -= it
                })
        }

        item {
            var showDialog by remember { mutableStateOf(false) }
            val hiddenGroups = settingsViewModel.iptvSourceHiddenGroupList

            LeanbackSettingsCategoryListItem(
                headlineContent = "频道分组显示管理",
                supportingContent = "选择直播源中哪些分组显示/隐藏，隐藏的分组不出现在选台列表",
                trailingContent = if (hiddenGroups.isEmpty()) "全部显示" else "隐藏${hiddenGroups.size}个分组",
                onSelected = { showDialog = true },
            )

            LeanbackSettingsIptvGroupVisibleDialog(
                showDialogProvider = { showDialog },
                onDismissRequest = { showDialog = false },
            )
        }

        item {
            var showConfirm by remember { mutableStateOf(false) }

            LeanbackSettingsCategoryListItem(
                headlineContent = "清除缓存",
                supportingContent = "清除直播源/节目单缓存和可播放域名记忆（需二次确认）",
                onSelected = { showConfirm = true },
            )

            LeanbackSettingsConfirmDialog(
                showDialogProvider = { showConfirm },
                onDismissRequest = { showConfirm = false },
                title = "清除缓存",
                text = "将清除直播源缓存、节目单缓存和可播放域名记忆，下次启动重新拉取。确定继续吗？",
                onConfirm = {
                    settingsViewModel.iptvPlayableHostList = emptySet()
                    coroutineScope.launch {
                        IptvRepository().clearCache()
                        top.yogiczy.mytv.data.repositories.epg.EpgRepository().clearCache()
                    }
                    LeanbackToastState.I.showToast("清除缓存成功")
                },
            )
        }
    }
}

@Composable
private fun LeanbackSettingsIptvSourceHistoryDialog(
    modifier: Modifier = Modifier,
    showDialogProvider: () -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
    iptvSourceHistoryProvider: () -> ImmutableList<String> = { persistentListOf() },
    currentIptvSourceProvider: () -> String = { Constants.IPTV_SOURCE_URL },
    onSelected: (String) -> Unit = {},
    onDeleted: (String) -> Unit = {},
) {
    val iptvSourceHistoryProviderValue = iptvSourceHistoryProvider()
    val iptvSourceHistory = remember(iptvSourceHistoryProviderValue) {
        listOf(Constants.IPTV_SOURCE_URL) + iptvSourceHistoryProviderValue
    }
    val currentIptvSource = currentIptvSourceProvider()

    if (showDialogProvider()) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = modifier,
            onDismissRequest = onDismissRequest,
            confirmButton = { Text(text = "短按切换；长按删除历史记录") },
            title = { Text("历史直播源") },
            text = {
                var hasFocused by remember { mutableStateOf(false) }

                TvLazyColumn(
                    state = rememberTvLazyListState(
                        max(0, iptvSourceHistory.indexOf(currentIptvSource) - 2),
                    ),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(iptvSourceHistory, key = { it }) { source ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            if (source == currentIptvSource && !hasFocused) {
                                hasFocused = true
                                focusRequester.requestFocus()
                            }
                        }

                        androidx.tv.material3.ListItem(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .tvTouchClickable(
                                    onClick = { onSelected(source) },
                                    onLongClick = { onDeleted(source) },
                                ),
                            selected = currentIptvSource == source,
                            onClick = { onSelected(source) },
                            onLongClick = { onDeleted(source) },
                            headlineContent = {
                                androidx.tv.material3.Text(
                                    text = if (source == Constants.IPTV_SOURCE_URL) "默认直播源（网络需要支持ipv6）" else source,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = if (isFocused) Int.MAX_VALUE else 2,
                                )
                            },
                            trailingContent = {
                                if (currentIptvSource == source) {
                                    androidx.tv.material3.Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "checked",
                                    )
                                }
                            },
                        )
                    }

                    item {
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        var showDialog by remember { mutableStateOf(false) }

                        androidx.tv.material3.ListItem(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .tvTouchClickable(onClick = { showDialog = true }),
                            selected = false,
                            onClick = { showDialog = true },
                            headlineContent = {
                                androidx.tv.material3.Text("添加其他直播源")
                            },
                        )

                        LeanbackQrcodeDialog(
                            text = HttpServer.serverUrl,
                            description = "扫码前往设置页面",
                            showDialogProvider = { showDialog },
                            onDismissRequest = { showDialog = false },
                        )
                    }
                }
            },
        )
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryIptvPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryIptv(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = LeanbackSettingsViewModel().apply {
                iptvSourceCacheTime = 3_600_000
                iptvSourceUrl = "https://iptv-org.github.io/iptv/iptv.m3u"
                iptvSourceUrlHistoryList = setOf(
                    "https://iptv-org.github.io/iptv/iptv.m3u",
                    "https://iptv-org.github.io/iptv/iptv2.m3u",
                    "https://iptv-org.github.io/iptv/iptv3.m3u",
                )
            },
        )
    }
}
/**
 * 频道分组显示管理对话框：列出直播源全部分组，开关控制显示/隐藏；
 * 关闭对话框时如有变更，触发软重启使选台列表按新配置重建
 */
@Composable
private fun LeanbackSettingsIptvGroupVisibleDialog(
    modifier: Modifier = Modifier,
    showDialogProvider: () -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    if (!showDialogProvider()) return

    var groupNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadFailed by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            // 走缓存，不会重新拉网络
            groupNames = IptvRepository().getIptvGroupList(
                sourceUrl = SP.iptvSourceUrl,
                cacheTime = SP.iptvSourceCacheTime,
                simplify = SP.iptvSourceSimplify,
            ).map { it.name }
        } catch (ex: Exception) {
            loadFailed = true
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier,
        onDismissRequest = {
            if (changed) {
                LeanbackToastState.I.showToast("分组显示已更新，正在刷新...")
                LiveSettingsBus.recreateAppRequests.tryEmit(Unit)
            }
            onDismissRequest()
        },
        confirmButton = { Text(text = "短按切换显示/隐藏；返回键关闭并生效") },
        title = { Text("频道分组显示管理") },
        text = {
            when {
                loadFailed -> Text("直播源加载失败，请检查网络或先返回播放页")
                groupNames.isEmpty() -> Text("加载中...")
                else -> TvLazyColumn(
                    state = rememberTvLazyListState(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groupNames, key = { it }) { groupName ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        val hiddenGroups = settingsViewModel.iptvSourceHiddenGroupList
                        val visible = groupName !in hiddenGroups

                        androidx.tv.material3.ListItem(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .tvTouchClickable(onClick = {
                                    changed = true
                                    val current = SP.iptvSourceHiddenGroupList
                                    settingsViewModel.iptvSourceHiddenGroupList =
                                        if (groupName in current) current - groupName
                                        else current + groupName
                                }),
                            selected = false,
                            // 触摸/遥控器统一点按切换；直接从 SP 读写避免捕获过期集合
                            onClick = {
                                changed = true
                                val current = SP.iptvSourceHiddenGroupList
                                settingsViewModel.iptvSourceHiddenGroupList =
                                    if (groupName in current) current - groupName
                                    else current + groupName
                            },
                            headlineContent = {
                                androidx.tv.material3.Text(
                                    text = groupName,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                )
                            },
                            trailingContent = {
                                Switch(checked = visible, onCheckedChange = null)
                            },
                        )
                    }
                }
            }
        },
    )
}
