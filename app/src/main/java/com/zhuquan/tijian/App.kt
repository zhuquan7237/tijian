package com.zhuquan.tijian

import android.app.Application
import com.zhuquan.tijian.ai.AiClient
import com.zhuquan.tijian.data.Db
import com.zhuquan.tijian.data.Importer

class App : Application() {
    lateinit var db: Db
    lateinit var ai: AiClient
    lateinit var importer: Importer

    override fun onCreate() {
        super.onCreate()
        db = Db(this)
        ai = AiClient()
        importer = Importer(this, db, ai)
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
}
