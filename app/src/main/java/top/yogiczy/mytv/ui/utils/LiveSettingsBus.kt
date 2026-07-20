package top.yogiczy.mytv.ui.utils

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 网页端（HttpServer）修改设置后的事件总线，用于不重启应用实时生效
 */
object LiveSettingsBus {
    /**
     * EPG 地址变更：请求重新拉取节目单（不影响当前播放）
     */
    val epgRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 直播源地址变更：请求重建主界面（等价于软重启，
     * 频道列表和播放状态需要整体重建，比手动退出应用再进要快）
     */
    val recreateAppRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
