package top.yogiczy.mytv.ui.screens.leanback.settings

import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel
import top.yogiczy.mytv.ui.utils.SP
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class LeanbackSettingsViewModel : ViewModel() {

    var appBootLaunch by spBacked({ SP.appBootLaunch }, { SP.appBootLaunch = it })
    var appLastLatestVersion by spBacked({ SP.appLastLatestVersion }, { SP.appLastLatestVersion = it })
    var appDeviceDisplayType by spBacked({ SP.appDeviceDisplayType }, { SP.appDeviceDisplayType = it })

    var debugShowFps by spBacked({ SP.debugShowFps }, { SP.debugShowFps = it })
    var debugShowVideoPlayerMetadata by spBacked(
        { SP.debugShowVideoPlayerMetadata },
        { SP.debugShowVideoPlayerMetadata = it },
    )

    var iptvLastIptvIdx by spBacked({ SP.iptvLastIptvIdx }, { SP.iptvLastIptvIdx = it })
    var iptvChannelChangeFlip by spBacked({ SP.iptvChannelChangeFlip }, { SP.iptvChannelChangeFlip = it })
    var iptvPrebufferEnable by spBacked({ SP.iptvPrebufferEnable }, { SP.iptvPrebufferEnable = it })
    var iptvSourceHiddenGroupList by spBacked(
        { SP.iptvSourceHiddenGroupList },
        { SP.iptvSourceHiddenGroupList = it },
    )
    var iptvSourceSimplify by spBacked({ SP.iptvSourceSimplify }, { SP.iptvSourceSimplify = it })
    var iptvSourceCacheTime by spBacked({ SP.iptvSourceCacheTime }, { SP.iptvSourceCacheTime = it })
    var iptvSourceUrl by spBacked({ SP.iptvSourceUrl }, { SP.iptvSourceUrl = it })
    var iptvPlayableHostList by spBacked({ SP.iptvPlayableHostList }, { SP.iptvPlayableHostList = it })
    var iptvChannelNoSelectEnable by spBacked(
        { SP.iptvChannelNoSelectEnable },
        { SP.iptvChannelNoSelectEnable = it },
    )
    var iptvSourceUrlHistoryList by spBacked(
        { SP.iptvSourceUrlHistoryList },
        { SP.iptvSourceUrlHistoryList = it },
    )
    var iptvChannelFavoriteEnable by spBacked(
        { SP.iptvChannelFavoriteEnable },
        { SP.iptvChannelFavoriteEnable = it },
    )
    var iptvChannelFavoriteListVisible by spBacked(
        { SP.iptvChannelFavoriteListVisible },
        { SP.iptvChannelFavoriteListVisible = it },
    )
    var iptvChannelFavoriteList by spBacked(
        { SP.iptvChannelFavoriteList },
        { SP.iptvChannelFavoriteList = it },
    )

    var epgEnable by spBacked({ SP.epgEnable }, { SP.epgEnable = it })
    var epgReplayEnable by spBacked({ SP.epgReplayEnable }, { SP.epgReplayEnable = it })
    var epgXmlUrl by spBacked({ SP.epgXmlUrl }, { SP.epgXmlUrl = it })
    var epgRefreshTimeThreshold by spBacked(
        { SP.epgRefreshTimeThreshold },
        { SP.epgRefreshTimeThreshold = it },
    )
    var epgXmlUrlHistoryList by spBacked({ SP.epgXmlUrlHistoryList }, { SP.epgXmlUrlHistoryList = it })

    var uiShowEpgProgrammeProgress by spBacked(
        { SP.uiShowEpgProgrammeProgress },
        { SP.uiShowEpgProgrammeProgress = it },
    )
    var uiUseClassicPanelScreen by spBacked(
        { SP.uiUseClassicPanelScreen },
        { SP.uiUseClassicPanelScreen = it },
    )
    var uiDensityScaleRatio by spBacked({ SP.uiDensityScaleRatio }, { SP.uiDensityScaleRatio = it })
    var uiFontScaleRatio by spBacked({ SP.uiFontScaleRatio }, { SP.uiFontScaleRatio = it })
    var uiTimeShowMode by spBacked({ SP.uiTimeShowMode }, { SP.uiTimeShowMode = it })
    var uiPipMode by spBacked({ SP.uiPipMode }, { SP.uiPipMode = it })

    var updateForceRemind by spBacked({ SP.updateForceRemind }, { SP.updateForceRemind = it })

    var videoPlayerUserAgent by spBacked({ SP.videoPlayerUserAgent }, { SP.videoPlayerUserAgent = it })
    var videoPlayerLoadTimeout by spBacked(
        { SP.videoPlayerLoadTimeout },
        { SP.videoPlayerLoadTimeout = it },
    )
    var videoPlayerAspectRatio by spBacked(
        { SP.videoPlayerAspectRatio },
        { SP.videoPlayerAspectRatio = it },
    )
}

/**
 * SP 门面委托：ViewModel 不再手工镜像 SP（27 个 key × 8 行模板，且会被
 * HttpServer 直写绕过导致镜像过期），统一改为读时取 SP、写时落 SP，
 * 本 VM 内的写入通过版本号触发重组保持响应式
 */
private fun <T> spBacked(read: () -> T, write: (T) -> Unit): ReadWriteProperty<Any?, T> =
    object : ReadWriteProperty<Any?, T> {
        private val version = mutableLongStateOf(0L)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            version.longValue // 订阅版本号，本 VM 写入后触发重组
            return read()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            write(value)
            version.longValue++
        }
    }
