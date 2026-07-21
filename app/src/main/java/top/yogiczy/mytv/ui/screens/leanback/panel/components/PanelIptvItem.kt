package top.yogiczy.mytv.ui.screens.leanback.panel.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import kotlinx.coroutines.delay
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgrammeList
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.ui.screens.leanback.components.ProgrammeProgressIndicator
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.ui.utils.tvTouchClickable
import top.yogiczy.mytv.ui.utils.rememberCurrentProgramme

@Composable
fun LeanbackPanelIptvItem(
    modifier: Modifier = Modifier,
    iptvProvider: () -> Iptv = { Iptv() },
    epgProvider: () -> Epg? = { null },
    showProgrammeProgressProvider: () -> Boolean = { false },
    onIptvSelected: () -> Unit = {},
    onIptvFavoriteToggle: () -> Unit = {},
    onShowEpg: () -> Unit = {},
    initialFocusedProvider: () -> Boolean = { false },
    onHasFocused: () -> Unit = {},
    onFocused: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val iptv = iptvProvider()
    val currentProgramme = rememberCurrentProgramme(epgProvider()?.programmes ?: emptyList())
    val showProgrammeProgress = showProgrammeProgressProvider()

    LaunchedEffect(Unit) {
        if (initialFocusedProvider()) {
            onHasFocused()
            // 部分电视（如长虹）首次弹出面板时布局尚未完成，焦点请求会被静默丢弃，
            // 导致面板按键无响应；这里重试直至真正获得焦点
            repeat(20) {
                if (isFocused) return@LaunchedEffect
                focusRequester.requestFocus()
                delay(50)
            }
        }
    }

    androidx.tv.material3.Card(
        onClick = { onIptvSelected() },
        onLongClick = { onIptvFavoriteToggle() },
        modifier = modifier
            .width(130.dp)
            .height(54.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .tvTouchClickable(
                onClick = { onIptvSelected() },
                onLongClick = { onIptvFavoriteToggle() },
            )
            .handleLeanbackKeyEvents(
                // 菜单键查看节目单仍走按键处理（tv 组件没有对应回调）
                onSettings = {
                    if (isFocused) onShowEpg()
                    else focusRequester.requestFocus()
                }
            ),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.onBackground),
            ),
        ),
    ) {
        Box(
            modifier = Modifier.background(
                color = if (isFocused) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                Text(
                    text = iptv.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    color = if (isFocused) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text = currentProgramme?.title ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.alpha(0.8f),
                    color = if (isFocused) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
                )
            }

            // 节目进度条
            if (showProgrammeProgress && currentProgramme != null) {
                ProgrammeProgressIndicator(
                    programme = currentProgramme,
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = if (isFocused) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun LeanbackPanelIptvItemPreview() {
    LeanbackTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LeanbackPanelIptvItem(
                iptvProvider = { Iptv.EXAMPLE },
                epgProvider = {
                    Epg(
                        channel = Iptv.EXAMPLE.channelName,
                        programmes = EpgProgrammeList(listOf(
                            EpgProgramme(
                                startAt = System.currentTimeMillis() - 100000,
                                endAt = System.currentTimeMillis() + 200000,
                                title = "新闻联播",
                            )
                        ))
                    )
                },
                showProgrammeProgressProvider = { true },
            )

            LeanbackPanelIptvItem(
                iptvProvider = { Iptv.EXAMPLE },
                epgProvider = {
                    Epg(
                        channel = Iptv.EXAMPLE.channelName,
                        programmes = EpgProgrammeList(listOf(
                            EpgProgramme(
                                startAt = System.currentTimeMillis() - 100000,
                                endAt = System.currentTimeMillis() + 200000,
                                title = "新闻联播",
                            )
                        ))
                    )
                },
                showProgrammeProgressProvider = { true },
                initialFocusedProvider = { true },
            )
        }
    }
}
