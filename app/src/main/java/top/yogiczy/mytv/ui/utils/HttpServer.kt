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

    fun start(context: Context, showToast: (String) -> Unit) {
        appContext = context.applicationContext
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
                    handleGetSettings(response)
                }

                server.post("/api/settings") { request, response ->
                    handleSetSettings(request, response)
                }

                server.post("/api/upload/apk") { request, response ->
                    handleUploadApk(request, response, context)
                }

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
            send(context.resources.openRawResource(id).readBytes().decodeToString())
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
        val body = request.getBody<JSONObjectBody>().get()
        val iptvSourceUrl = body.get("iptvSourceUrl").toString()
        val epgXmlUrl = body.get("epgXmlUrl").toString()
        val videoPlayerUserAgent = body.get("videoPlayerUserAgent").toString()

        if (SP.iptvSourceUrl != iptvSourceUrl) {
            SP.iptvSourceUrl = iptvSourceUrl
            IptvRepository().clearCache()
        }

        if (SP.epgXmlUrl != epgXmlUrl) {
            SP.epgXmlUrl = epgXmlUrl
            EpgRepository().clearCache()
        }

        SP.videoPlayerUserAgent = videoPlayerUserAgent

        wrapResponse(response).send("success")
    }

    private fun handleUploadApk(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context,
    ) {
        val body = request.getBody<MultipartFormDataBody>()

        val os = uploadedApkFile.outputStream()
        val contentLength = request.headers["Content-Length"]?.toLong() ?: 1
        var hasReceived = 0L

        body.setMultipartCallback { part ->
            if (part.isFile) {
                body.setDataCallback { _, bb ->
                    val byteArray = bb.allByteArray
                    hasReceived += byteArray.size
                    showToast("正在接收文件: ${(hasReceived * 100f / contentLength).toInt()}%")
                    os.write(byteArray)
                }
            }
        }

        body.setEndCallback {
            showToast("文件接收完成")
            body.dataEmitter.close()
            os.flush()
            os.close()
            ApkInstaller.installApk(context, uploadedApkFile.path)
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

                val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                for (linkAddr in linkProps.linkAddresses) {
                    val address = linkAddr.address ?: continue
                    if (address !is Inet4Address || address.isLoopbackAddress) continue

                    var score = 0
                    if (address.isSiteLocalAddress) score += 10
                    when {
                        isWifi -> score += 5
                        isEthernet -> score += 4
                        isVpn -> score -= 5
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
                for (linkAddr in linkProps.linkAddresses) {
                    val address = linkAddr.address ?: continue
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        address.hostAddress?.let {
                            log.d("IP from active network: $it")
                            return it
                        }
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