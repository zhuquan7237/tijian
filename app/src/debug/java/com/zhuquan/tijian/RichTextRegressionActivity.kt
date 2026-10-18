package com.zhuquan.tijian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.zhuquan.tijian.ui.RichText

/** Debug-only host: exercises the production Compose/WebView bridge without user data. */
class RichTextRegressionActivity : ComponentActivity() {
    val content = mutableStateOf("initial")
    val width = mutableStateOf(300)
    val fontScale = mutableStateOf(1f)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                Column(Modifier.width(width.value.dp).verticalScroll(rememberScrollState())) {
                    RichText(content.value)
                }
            }
        }
    }
}
