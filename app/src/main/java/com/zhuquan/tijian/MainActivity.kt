package com.zhuquan.tijian

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import java.lang.ref.WeakReference
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.zhuquan.tijian.ui.AppRoot
import com.zhuquan.tijian.ui.AppVm
import com.zhuquan.tijian.ui.TijianTheme

class MainActivity : ComponentActivity() {

    companion object {
        var current: WeakReference<MainActivity>? = null
            private set
    }

    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        handleIntent(intent)
        setContent {
            TijianTheme { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(com.zhuquan.tijian.review.ReviewReminder.EXTRA_OPEN, false)) {
            intent.removeExtra(com.zhuquan.tijian.review.ReviewReminder.EXTRA_OPEN)
            vm.openTodayReview()
            return
        }
        val uris = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> extractedStreams(intent)?.let { uris.addAll(it) }
            Intent.ACTION_SEND_MULTIPLE -> extractedStreams(intent)?.let { uris.addAll(it) }
        }
        if (uris.isEmpty()) {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
        }
        if (uris.isNotEmpty()) vm.importUris(uris, "share")
    }

    private fun extractedStreams(intent: Intent): List<Uri>? {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { return it }
            return listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        }
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { return it }
        @Suppress("DEPRECATION")
        return listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }
}
