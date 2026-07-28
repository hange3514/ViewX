package top.yogiczy.mytv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import top.yogiczy.mytv.activities.LeanbackActivity
import top.yogiczy.mytv.ui.utils.SP

/**
 * 开机自启动监听
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            // 脏值防护：SP 文件被外部工具写入异类型值时不至于崩在开机广播里
            val bootLaunch = runCatching {
                val sp: SharedPreferences = SP.getInstance(context)
                sp.getBoolean(SP.KEY.APP_BOOT_LAUNCH.name, false)
            }.getOrDefault(false)

            if (bootLaunch) {
                context.startActivity(Intent(context, LeanbackActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}
