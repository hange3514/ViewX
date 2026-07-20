package top.yogiczy.mytv.data.utils

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "我的电视"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/hange3514/ViewX"

    /**
     * IPTV源地址
     */
    const val IPTV_SOURCE_URL = "http://192.168.0.110:8088/iptv_local.m3u8"

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单XML地址
     */
    const val EPG_XML_URL = "http://192.168.0.110:8088/xmltv.xml"

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 0 // 测试期间允许任意时间刷新

    /**
     * Git最新版本信息
     * 发版时在本仓库创建 release（tag 如 v1.4.5）并把 APK 作为第一个附件上传
     */
    const val GIT_RELEASE_LATEST_URL =
        "https://api.github.com/repos/hange3514/ViewX/releases/latest"

    /**
     * GitHub加速代理地址
     * 注意：这类公益代理稳定性差，失效时下载逻辑会回退到 GitHub 直连
     */
    const val GITHUB_PROXY = "https://ghfast.top/"

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 快捷面板超时未操作自动关闭界面
     */
    const val UI_QUICK_PANEL_SCREEN_AUTO_CLOSE_DELAY = 1000L * 5 // 5秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SHOW_RANGE = 1000L * 30 // 前后30秒

    /**
     * 界面 临时面板界面显示时间
     */
    const val UI_TEMP_PANEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒
}