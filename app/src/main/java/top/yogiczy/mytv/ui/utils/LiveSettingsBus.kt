package top.yogiczy.mytv.ui.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 网页端（HttpServer）修改设置后的事件总线，用于不重启应用实时生效
 */
object LiveSettingsBus {
    /**
     * EPG 地址变更：请求重新拉取节目单（不影响当前播放）
     */
    private val epgRefreshEvents = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * 直播源或分组设置变更：由主界面 ViewModel 重新加载。
     */
    private val iptvReloadEvents = MutableSharedFlow<Boolean>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val epgRefreshRequests = epgRefreshEvents.asSharedFlow()
    val iptvReloadRequests = iptvReloadEvents.asSharedFlow()

    fun requestEpgRefresh(forceRefresh: Boolean = false) {
        epgRefreshEvents.tryEmit(forceRefresh)
    }

    fun requestIptvReload(forceRefresh: Boolean = false) {
        iptvReloadEvents.tryEmit(forceRefresh)
    }

    /** 显示模式变更：重新应用 Activity 的界面配置。 */
    val recreateAppRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
