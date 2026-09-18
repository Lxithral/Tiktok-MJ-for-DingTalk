package com.lxithral.mjegg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lxithral.mjegg.platform.SettingsStore
import com.lxithral.mjegg.ui.MainScreen
import com.lxithral.mjegg.ui.theme.AppTheme

/** 唯一 Activity: 主题包住一切, 页面切换交给 MainScreen 里的 HorizontalPager。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = SettingsStore.get(this)
        setContent {
            AppTheme(settings) {
                MainScreen(settings)
            }
        }
    }
}
