package top.yogiczy.mytv.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.body.MultipartFormDataBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.R
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.ApkInstaller
import top.yogiczy.mytv.utils.Loggable
import top.yogiczy.mytv.utils.Logger
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object HttpServer : Loggable() {
    private const val SERVER_PORT = 10481

    private val uploadedApkFile = File(AppGlobal.cacheDir, "uploaded_apk.apk").apply {
        deleteOnExit()
    }

    private var appContext: Context? = null
    private var showToast: (String) -> Unit = { }

    val serverUrl: String
        get() = "http://${getLocalIpAddress(appContext)}:${SERVER_PORT}"

    @Volatile
    private var started = false

    @Synchronized
    fun start(context: Context, showToast: (String) -> Unit) {
        appContext = context.applicationContext
        // 幂等：Activity 重建（如推送直播源后的软重启）会重复调用，
        // 重复 listen 同一端口会误报"设置服务启动失败"
        this.showToast = showToast
        if (started) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = AsyncHttpServer()
                server.listen(AsyncServer.getDefault(), SERVER_PORT)

                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.index)
                }
                server.get("/index_css.css") { _, response ->
                    handleRawResource(response, context, "text/css", R.raw.index_css)
                }
                server.get("/index_js.js") { _, response ->
                    handleRawResource(response, context, "text/javascript", R.raw.index_js)
                }

                server.get("/api/settings") { _, response ->
                    runCatching { handleGetSettings(response) }
                        .onFailure { response.code(500); response.send("error") }
                }

                server.post("/api/settings") { request, response ->
                    runCatching { handleSetSettings(request, response) }
                        .onFailure { response.code(400); response.send("bad request") }
                }

                server.post("/api/upload/apk") { request, response ->
                    runCatching { handleUploadApk(request, response, context) }
                        .onFailure { response.code(500); response.send("error") }
                }

                // 成功 listen 后才置位，失败允许下次重试
                started = true
                HttpServer.showToast = showToast
                log.i("服务已启动: 0.0.0.0:${SERVER_PORT}")
            } catch (ex: Exception) {
                log.e("服务启动失败: ${ex.message}", ex)
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "设置服务启动失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun wrapResponse(response: AsyncHttpServerResponse) = response.apply {
        headers.set(
            "Access-Control-Allow-Methods", "POST, GET, DELETE, PUT, OPTIONS"
        )
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set(
            "Access-Control-Allow-Headers", "Origin, Content-Type, X-Auth-Token"
        )
    }

    private fun handleRawResource(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        id: Int,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.resources.openRawResource(id).use { it.readBytes() }.decodeToString())
        }
    }

    private fun handleGetSettings(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Json.encodeToString(
                    AllSettings(
                        appTitle = Constants.APP_TITLE,
                        appRepo = Constants.APP_REPO,
                        iptvSourceUrl = SP.iptvSourceUrl,
                        epgXmlUrl = SP.epgXmlUrl,
                        videoPlayerUserAgent = SP.videoPlayerUserAgent,
                        logHistory = Logger.history,
                    )
                )
            )
        }
    }

    private fun handleSetSettings(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        // 非 JSON 请求体（表单/旧版页面/第三方脚本）直接 400，不能炸 AsyncServer 线程
        val body = try {
            request.getBody<JSONObjectBody>().get()
        } catch (ex: Exception) {
            response.code(400)
            response.send("bad request")
            return
        }
        // 字段可能缺失（第三方调用/旧版页面），缺省保持当前值，避免 JSONException
        val iptvSourceUrl = body.optString("iptvSourceUrl", SP.iptvSourceUrl)
        val epgXmlUrl = body.optString("epgXmlUrl", SP.epgXmlUrl)
        val videoPlayerUserAgent = body.optString("videoPlayerUserAgent", SP.videoPlayerUserAgent)

        if (SP.iptvSourceUrl != iptvSourceUrl) {
            SP.iptvSourceUrl = iptvSourceUrl
            IptvRepository().clearCache()
            showToast("直播源已更新，正在刷新...")
            LiveSettingsBus.recreateAppRequests.tryEmit(Unit)
        }

        if (SP.epgXmlUrl != epgXmlUrl) {
            SP.epgXmlUrl = epgXmlUrl
            EpgRepository().clearAllCache()
            showToast("节目单地址已更新，正在刷新...")
            LiveSettingsBus.epgRefreshRequests.tryEmit(Unit)
        }

        SP.videoPlayerUserAgent = videoPlayerUserAgent

        wrapResponse(response).send("success")
    }

    /** 上传大小上限 300MB，防恶意/异常请求写满磁盘 */
    private val maxUploadBytes = 300L * 1024 * 1024

    @Volatile
    private var uploadInProgress = false

    private fun handleUploadApk(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context,
    ) {
        val contentLength = request.headers["Content-Length"]?.toLongOrNull() ?: 0L
        if (contentLength > maxUploadBytes) {
            response.code(413)
            response.send("too large")
            return
        }

        // 上传真正串行：写文件发生在异步回调里，@Synchronized 包不住，用状态位拦截并发
        synchronized(this) {
            if (uploadInProgress) {
                response.code(409)
                response.send("upload in progress")
                return
            }
            uploadInProgress = true
        }

        val body = request.getBody<MultipartFormDataBody>()
        uploadedApkFile.delete()
        val os = uploadedApkFile.outputStream()
        var hasReceived = 0L

        try {
            body.setMultipartCallback { part ->
                if (part.isFile) {
                    body.setDataCallback { _, bb ->
                        val byteArray = bb.allByteArray
                        hasReceived += byteArray.size
                        showToast("正在接收文件: ${(hasReceived * 100f / maxOf(contentLength, 1)).toInt()}%")
                        os.write(byteArray)
                    }
                }
            }

            body.setEndCallback {
                try {
                    os.flush()
                    showToast("文件接收完成")
                    body.dataEmitter.close()
                    if (!ApkInstaller.installApk(context, uploadedApkFile.path)) {
                        showToast("无法调起安装界面，请通过U盘手动安装")
                    }
                } finally {
                    try {
                        os.close()
                    } catch (_: Exception) {
                    }
                    uploadInProgress = false
                }
            }
        } catch (ex: Exception) {
            try {
                os.close()
            } catch (_: Exception) {
            }
            uploadInProgress = false
            throw ex
        }

        wrapResponse(response).send("success")
    }

    private fun getLocalIpAddress(context: Context?): String {
        val defaultIp = "0.0.0.0"
        if (context == null) return defaultIp

        // 1. 优先从 ConnectivityManager 取 Wi-Fi / 以太网 IP，不受接口名差异影响
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return defaultIp
            val candidates = mutableListOf<Pair<Inet4Address, Int>>()

            for (network in cm.allNetworks ?: emptyArray()) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                val linkProps = cm.getLinkProperties(network) ?: continue

                // VPN（如 V2RAYN）会创建虚拟网卡，其地址对局域网访问毫无意义，直接排除
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue

                val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                for (linkAddr in linkProps.linkAddresses) {
                    val address = linkAddr.address ?: continue
                    if (address !is Inet4Address || address.isLoopbackAddress) continue

                    var score = 0
                    if (address.isSiteLocalAddress) score += 10
                    when {
                        isWifi -> score += 5
                        isEthernet -> score += 4
                        isCellular -> score -= 10
                    }
                    candidates.add(address to score)
                }
            }

            candidates.maxByOrNull { it.second }?.first?.hostAddress?.let {
                log.d("IP from ConnectivityManager: $it")
                return it
            }
        } catch (ex: Exception) {
            log.e("ConnectivityManager IP detection failed", ex)
        }

        // 2. 兜底：取当前默认网络的 IPv4 地址
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return defaultIp
                val activeNetwork = cm.activeNetwork ?: return defaultIp
                val linkProps = cm.getLinkProperties(activeNetwork) ?: return defaultIp

                // VPN 开启后默认网络往往是虚拟网卡（tun/ppp），跳过走网卡枚举兜底
                val ifName = linkProps.interfaceName?.lowercase() ?: ""
                val isVpnLike = ifName.contains("tun") || ifName.contains("ppp") || ifName.contains("vpn")

                if (!isVpnLike) {
                    // 优先 site-local（局域网）地址
                    val addresses = linkProps.linkAddresses
                        .mapNotNull { it.address }
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress }
                    (addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull())
                        ?.hostAddress?.let {
                            log.d("IP from active network: $it")
                            return it
                        }
                }
            } catch (ex: Exception) {
                log.e("Active network IP detection failed", ex)
            }
        }

        // 3. 最后兜底：遍历网卡，优先 site-local 和 Wi-Fi/以太网接口名
        try {
            val candidates = mutableListOf<Pair<Inet4Address, Int>>()
            val en = NetworkInterface.getNetworkInterfaces()

            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                if (!intf.isUp || intf.isLoopback) continue

                val name = intf.name.lowercase()
                val isPreferred = name.contains("wlan") || name.contains("eth") || name.contains("usb") || name.contains("rndis")
                val isIgnored = name.contains("tun") || name.contains("ppp") || name.contains("vpn")
                        || name.contains("rmnet") || name.contains("ccmni") || name.contains("dummy")

                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val address = enumIpAddr.nextElement()
                    if (address !is Inet4Address || address.isLoopbackAddress) continue

                    var score = 0
                    if (address.isSiteLocalAddress) score += 10
                    if (isPreferred) score += 5
                    if (isIgnored) score -= 10
                    candidates.add(address to score)
                }
            }

            candidates.maxByOrNull { it.second }?.first?.hostAddress?.let {
                log.d("IP from NetworkInterface: $it")
                return it
            }
        } catch (ex: SocketException) {
            log.e("NetworkInterface IP detection failed", ex)
        }

        return defaultIp
    }
}

@Serializable
private data class AllSettings(
    val appTitle: String,
    val appRepo: String,
    val iptvSourceUrl: String,
    val epgXmlUrl: String,
    val videoPlayerUserAgent: String,

    val logHistory: List<Logger.HistoryItem>,
)