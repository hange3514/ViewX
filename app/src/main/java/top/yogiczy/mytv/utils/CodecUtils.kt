package top.yogiczy.mytv.utils

import android.media.MediaCodecList

/**
 * 检测设备是否支持 AVS2 硬解
 */
object CodecUtils {

    fun hasAvs2Decoder(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { codecInfo ->
                codecInfo.supportedTypes.any { type ->
                    type.equals("video/avs2", ignoreCase = true)
                            || type.equals("video/avs2", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
