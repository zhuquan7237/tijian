package com.zhuquan.tijian

import android.app.Application
import com.zhuquan.tijian.ai.AiClient
import com.zhuquan.tijian.data.Db
import com.zhuquan.tijian.data.Importer
import java.io.File

class App : Application() {
    lateinit var db: Db
    lateinit var ai: AiClient
    lateinit var importer: Importer

    override fun onCreate() {
        super.onCreate()
        // JLatexMath 初始化一次：列表行内公式渲染读它的 assets 字体与配置
        ru.noties.jlatexmath.JLatexMathAndroid.init(this)
        db = Db(this)
        ai = AiClient()
        importer = Importer(this, db, ai)
        installCrashLogger()
        com.zhuquan.tijian.review.ReviewReminder.schedule(this)
        if (BuildConfig.DEBUG && db.provider().baseUrl.isBlank()) {
            // 调试构建默认指向本机 mock 服务（10.0.2.2 = 模拟器宿主机）
            db.saveProvider(com.zhuquan.tijian.data.ProviderConfig(
                baseUrl = "http://10.0.2.2:9008/v1",
                apiKey = "",
                model = "mock-vision",
                protocol = "chat",
            ))
        }
    }

    /**
     * 崩溃日志：堆栈写到 files/crash/last_crash.txt，供下次启动检测
     * 「上次异常退出」并在设置页查看/复制。记录完交回系统默认处理，
     * 该崩还是崩，只是闪退不再无声无息。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(filesDir, "crash").apply { mkdirs() }
                val file = File(dir, "last_crash.txt")
                file.writeText(
                    buildString {
                        append("time: ").append(System.currentTimeMillis()).append('\n')
                        append("thread: ").append(thread.name).append('\n')
                        append("version: ").append(BuildConfig.VERSION_NAME).append('\n')
                        append(android.util.Log.getStackTraceString(throwable))
                    }
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** 上次崩溃日志；没有则为 null。AppVm 启动时读取并展示。 */
        fun lastCrashReport(filesDir: File): String? = runCatching {
            val f = File(filesDir, "crash/last_crash.txt")
            if (f.exists()) f.readText().take(6000) else null
        }.getOrNull()

        fun clearCrashReport(filesDir: File) {
            runCatching { File(filesDir, "crash/last_crash.txt").delete() }
        }

        /** 导入草稿文件：进程被杀前的待确认题目。 */
        fun draftsFile(filesDir: File) = File(filesDir, "import_drafts.json")
    }
}
