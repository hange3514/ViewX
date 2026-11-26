package top.yogiczy.mytv.data.repositories

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal class HttpStatusException(val status: Int) : IOException("服务器返回 HTTP $status")

internal suspend fun <T> OkHttpClient.download(
    url: String,
    readBody: (ResponseBody) -> T,
): T = suspendCancellableCoroutine { continuation ->
    val call = newCall(Request.Builder().url(url).build())
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resumeWith(runCatching {
                response.use {
                    if (!it.isSuccessful) throw HttpStatusException(it.code)
                    readBody(it.body ?: throw IOException("服务器未返回数据"))
                }
            })
        }
    })
}
