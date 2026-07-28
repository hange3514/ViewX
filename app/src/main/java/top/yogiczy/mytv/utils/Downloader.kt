package top.yogiczy.mytv.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.io.FileOutputStream

object Downloader : Loggable() {
    suspend fun downloadTo(url: String, filePath: String, onProgressCb: ((Int) -> Unit)?) =
        withContext(Dispatchers.IO) {
            log.d("下载文件: $url")
            val interceptor = Interceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(DownloadResponseBody(originalResponse, onProgressCb)).build()
            }

            val client = OkHttpClient.Builder().addNetworkInterceptor(interceptor).build()
            val request = okhttp3.Request.Builder().url(url).build()

            try {
                with(client.newCall(request).execute()) {
                    if (!isSuccessful) {
                        throw Exception("下载文件失败: $code")
                    }

                    // 流式写盘，避免大文件（如 85MB 更新包）一次性读入内存导致 OOM
                    val file = File(filePath)
                    FileOutputStream(file).use { fos ->
                        body!!.byteStream().use { it.copyTo(fos) }
                    }
                }
            } catch (ex: Exception) {
                log.e("下载文件失败", ex)
                throw Exception("下载文件失败，请检查网络连接", ex)
            }
        }

    private class DownloadResponseBody(
        private val originalResponse: okhttp3.Response,
        private val onProgressCb: ((Int) -> Unit)?,
    ) : okhttp3.ResponseBody() {
        override fun contentLength() = originalResponse.body!!.contentLength()

        override fun contentType() = originalResponse.body?.contentType()

        override fun source(): BufferedSource {
            return object : ForwardingSource(originalResponse.body!!.source()) {
                var totalBytesRead = 0L
                var lastEmitAt = 0L

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    // contentLength 为 -1（chunked 响应）时无法计算进度；
                    // 每 chunk 都回调会产生数千个协程和 toast 洪泛，节流至 300ms 一次
                    val length = contentLength()
                    if (length > 0) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastEmitAt > 300 || bytesRead == -1L) {
                            lastEmitAt = now
                            val progress = (totalBytesRead * 100 / length).toInt()
                            CoroutineScope(Dispatchers.IO).launch {
                                onProgressCb?.invoke(progress)
                            }
                        }
                    }
                    return bytesRead
                }
            }.buffer()
        }
    }
}