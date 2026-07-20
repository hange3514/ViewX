package top.yogiczy.mytv

import android.app.Application
import top.yogiczy.mytv.ui.utils.CurrentTime
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.CrashLogger

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AppGlobal.cacheDir = applicationContext.cacheDir
        CrashLogger.install(applicationContext)
        UnsafeTrustManager.enableUnsafeTrustManager()
        SP.init(applicationContext)
        CurrentTime.startTick()
    }
}
